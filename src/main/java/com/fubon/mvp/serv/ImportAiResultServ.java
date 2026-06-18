package com.fubon.mvp.serv;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * ============================================================================
 * 富邦MVP - ImportAiResultServ (AI外撥結果報表導入服務)
 * ============================================================================
 * @author 張晏哲
 * @category 服務類
 *
 * 【功能概述】
 * 每日凌晨 2:00 自動觸發，執行以下流程：
 *   1. RSA 解密 mvpsqlserver.conf.enc，取得 SQL Server 連線資訊
 *   2. 透過 JDBC 查詢主節點，確認當前機器是否為 master（僅 master 執行）
 *   3. 從 FTP 下載昨日的 AI 外撥結果報表 CallList_YYYYMMDD.xlsx
 *   4. 驗證檔案是否存在
 *   5. 直接呼叫 ImportAiResultToProcessServ 解析 Excel 並更新資料庫
 *   6. 處理完成後，將檔案移至備份目錄
 *
 * 【改寫重點 — 移除所有平台依賴，完全使用 Java 實現】
 *   原依賴         → Java 替代方案
 *   openssl      → javax.crypto.Cipher (RSA/ECB/PKCS1Padding)
 *   sqlcmd       → java.sql.DriverManager (MSSQL JDBC)
 *   ftp 命令     → org.apache.commons.net.ftp.FTPClient
 *   decode.sh    → java.util.Base64
 *   /bin/sh      → 全部移除，無 shell 呼叫
 *
 * 【新增 Maven 依賴】
 *   <!-- SQL Server JDBC -->
 *   <dependency>
 *     <groupId>com.microsoft.sqlserver</groupId>
 *     <artifactId>mssql-jdbc</artifactId>
 *     <version>12.4.2.jre11</version>
 *   </dependency>
 *
 *   <!-- FTP Client -->
 *   <dependency>
 *     <groupId>commons-net</groupId>
 *     <artifactId>commons-net</artifactId>
 *     <version>3.10.0</version>
 *   </dependency>
 *
 * 【RSA 金鑰格式說明】
 *   支援 PKCS#8 (-----BEGIN PRIVATE KEY-----)
 *   支援 PKCS#1 (-----BEGIN RSA PRIVATE KEY-----) — 自動包裝轉換
 *   若要手動轉換 PKCS#1 → PKCS#8：
 *     openssl pkcs8 -topk8 -nocrypt -in rsa.key -out rsa_pkcs8.key
 *
 * 【decode.sh 相容性說明】
 *   假設 ftp.ini 各行為 Base64 編碼的帳號/密碼（UTF-8）。
 *   若 decode.sh 使用其他編碼方式，請修改 decodeFtpCredential()。
 *
 * 【執行排程】
 *   cron = "0 0 2 * * ?" → 台灣時間每日凌晨 2:00
 *
 * 【例外處理】
 *   - SkipExecutionException : 預期性跳過 → INFO 等級，不視為錯誤
 *   - RuntimeException       : 非預期性錯誤 → ERROR 等級，需介入處理
 * ============================================================================
 */
@Service
public class ImportAiResultServ {

    private static final Logger log = LoggerFactory.getLogger(ImportAiResultServ.class);

    // MSSQL JDBC URL template
    // encrypt=false / trustServerCertificate=true 可依環境調整
    private static final String JDBC_URL_TEMPLATE =
            "jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=false;trustServerCertificate=true";

    // -----------------------------------------------------------------
    // 外部設定（由 application.properties 注入）
    // -----------------------------------------------------------------
    @Value("${ImportAiResultServ.FTP_IP}")
    private String FTP_IP;

    @Autowired
    private ImportAiResultToProcessServ importAiResultProcess;

    @Value("${mvp.home.dir:/home/mvpadm}")
    private String HOME;

    @Value("${mvp.download.dir:/home/mvpadm/download}")
    private String DOWNLOAD_DIR;
    
    //待討論 處理的報表的目錄與原本報表目錄目前設同一個
    @Value("${mvp.processed.dir:/home/mvpadm/reports}")
    private String PROCESSED_DIR;
    
    @Value("${mvp.calllist_filename_prefix:CallList_}")
    private String CALLLIST_FILENAME_PREFIX;
    
    @Value("${mvp.sh.dir:/home/mvpadm/sh}")
    private String SH_DIR;
    
    @Value("${mvp.decodeFtpCredential:b77a5c561934e089}")
    private String DECODE_FTP_CREDENTIAL;

    // =================================================================
    // 【排程入口】每日凌晨 2:00 觸發
    // =================================================================
   
    //@Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Taipei")
    
    //排程執行週期設
    @Scheduled(cron = "${mvp.110007.ImportAiResult.expression}", zone = "${mvp.110007.cron.zone}")
    
    public void execute() {
        log.info("Starting AI Result Import process...");
        try {
            runProcess();
            log.info("AI Result Import process completed successfully.");
        } catch (SkipExecutionException e) {
            log.info("AI Result Import process skipped: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Critical error during AI Result Import process: ", e);
        }
    }

    // =================================================================
    // 【核心流程】
    // =================================================================
    private void runProcess() throws Exception {
        try {

            // -----------------------------------------------------------------
            // 步驟 1：日期設定
            // -----------------------------------------------------------------
            log.info("步驟1 日期命名設定");
            LocalDate today = LocalDate.now();
            String fileDate   = today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String aiFilename = CALLLIST_FILENAME_PREFIX + fileDate + ".xlsx";
            String localFile  = DOWNLOAD_DIR + "/" + aiFilename;
            log.info("處理目標檔案：{}", localFile);

            // -----------------------------------------------------------------
            // 步驟 2：RSA 解密 SQL 連線設定檔
            //   原：openssl rsautl -decrypt -inkey rsa.key -in *.enc -out *.conf
            //   現：javax.crypto.Cipher (RSA/ECB/PKCS1Padding)
            // -----------------------------------------------------------------
            log.info("步驟2 RSA 解密 SQL 連線設定檔");
            decryptRsaFile(
                HOME + "/rsa.key",
                HOME + "/mvpsqlserver.conf.enc",
                HOME + "/mvpsqlserver.conf"
            );
            Map<String, String> sqlConf = readSqlConfig(HOME + "/mvpsqlserver.conf");
            String ip       = sqlConf.get("ip");
            String port     = sqlConf.get("port");
            String database = sqlConf.get("database");
            String user     = sqlConf.get("user");
            String password = sqlConf.get("password");

            // -----------------------------------------------------------------
            // 步驟 3：確認當前機器是否為 master 節點
            //   原：sqlcmd -S ... -Q "..."
            //   現：java.sql.DriverManager (MSSQL JDBC)
            // -----------------------------------------------------------------
            log.info("步驟3 確認 master 節點");
            String masterQuery = "SELECT host_name FROM emailhos WHERE main='1'";
            String master = querySqlServer(ip, port, database, user, password, masterQuery).trim();

            if (master.isEmpty()) {
                log.info("查詢失敗，等待 10 秒後重試...");
                Thread.sleep(10000);
                master = querySqlServer(ip, port, database, user, password, masterQuery).trim();
            }

            String runMachine = InetAddress.getLocalHost().getHostName();
            if (!master.equals(runMachine)) {
                throw new SkipExecutionException("Not master node");
            }

            // -----------------------------------------------------------------
            // 步驟 4：從 FTP 下載 AI 結果報表
            //   原：ftp -p -n 命令 + decode.sh (shell 呼叫)
            //   現：FTPClient (passive mode) + Base64.getDecoder()
            // -----------------------------------------------------------------
            log.info("步驟4 FTP 下載 {}", aiFilename);
            
            File ftpIni = new File(SH_DIR, "ftp.ini");
            if (!ftpIni.exists()) {
                log.error("FTP ini file not found: {}", ftpIni.getAbsolutePath());
                return;
            }

            List<String> lines = Files.readAllLines(ftpIni.toPath(), StandardCharsets.UTF_8);
            String ftpUser = "";
            String ftpPass = "";

            // 解碼 ftp.ini 中的帳號（第1列）與密碼（第2列）
            int count = 0;
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String decoded = decodeFtpCredential(line.trim(), DECODE_FTP_CREDENTIAL);
                
                if (count == 0) {
                    ftpUser = decoded;
                } else if (count == 1) {
                    ftpPass = decoded;
                    break;
                }
                count++;
            }

            if (StringUtils.isNotBlank(ftpUser) && StringUtils.isNotBlank(ftpPass)) {
                Files.createDirectories(Paths.get(DOWNLOAD_DIR));
                //TODO 下載的伺服器路徑
                downloadFromFtp(FTP_IP, ftpUser, ftpPass, "/MVP/810SUPLOAD", aiFilename, localFile);
            } else {
                log.warn("ftp.ini 無法取得足夠帳號密碼，跳過 FTP 下載");
            }

            // -----------------------------------------------------------------
            // 步驟 5：確認檔案存在
            // -----------------------------------------------------------------
            if (!Files.exists(Paths.get(localFile))) {
                throw new SkipExecutionException("要處置的檔案不存在：" + localFile);
            }
            
            /*
            // 檢查檔案是否過期（12小時 = 43200秒）
            long fileAgeSeconds = (System.currentTimeMillis() / 1000)
                - Files.getLastModifiedTime(Paths.get(localFile)).to(java.util.concurrent.TimeUnit.SECONDS);
            if (fileAgeSeconds > 43200) {
                log.warn("檔案已過期（{} 秒前修改），跳過避免處理舊資料：{}", fileAgeSeconds, localFile);
                throw new SkipExecutionException("File too old: " + localFile);
            }
            log.info("檔案時效確認：{} 秒前修改", fileAgeSeconds);
            */

            // -----------------------------------------------------------------
            // 步驟 6：呼叫 Excel 處理服務（不變）
            // -----------------------------------------------------------------
            log.info("步驟6 解析並更新資料：{}", localFile);
            try {
                importAiResultProcess.processAiResultReport(localFile);
            } catch (Exception ex) {
                log.error("解析下載檔案發生異常：{}", ex.getMessage(), ex);
                throw new RuntimeException("Failed to process Excel", ex);
            }

            // -----------------------------------------------------------------
            // 步驟 7：備份已處理的檔案（不變）
            // -----------------------------------------------------------------
            Files.createDirectories(Paths.get(PROCESSED_DIR));
            Path source = Paths.get(localFile);
            Path target = Paths.get(PROCESSED_DIR, aiFilename);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("備份完成：{}", target);

        } finally {
            // 無論成功或失敗，一律清理解密設定檔（避免明碼密碼殘留）
            try { Files.deleteIfExists(Paths.get(HOME + "/mvpsqlserver.conf")); }
            catch (IOException e) { log.warn("刪除 mvpsqlserver.conf 異常", e); }
        }
    }

    // =================================================================
    // 【步驟 2 實作】RSA 解密
    // =================================================================

    /**
     * 使用 RSA 私鑰解密加密檔案，輸出明文至指定路徑。
     * 對應原指令：openssl rsautl -decrypt -inkey rsa.key -in *.enc -out *.conf
     *
     * @param keyPath       RSA 私鑰路徑（PEM 格式，PKCS#1 或 PKCS#8 均支援）
     * @param encryptedPath 加密輸入檔路徑
     * @param outputPath    解密輸出檔路徑
     */
    private void decryptRsaFile(String keyPath, String encryptedPath, String outputPath) throws Exception {
        log.info("RSA 解密：{} → {}", encryptedPath, outputPath);
        PrivateKey privateKey   = loadRsaPrivateKey(keyPath);
        byte[]     encryptedData = Files.readAllBytes(Paths.get(encryptedPath));

        // openssl rsautl 預設使用 PKCS1 v1.5 padding
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decrypted = cipher.doFinal(encryptedData);

        Files.write(Paths.get(outputPath), decrypted);
        log.info("RSA 解密完成");
    }

    /**
     * 載入 PEM 格式 RSA 私鑰。
     * 自動偵測並支援：
     *   PKCS#8  (-----BEGIN PRIVATE KEY-----)
     *   PKCS#1  (-----BEGIN RSA PRIVATE KEY-----) → 自動包裝為 PKCS#8
     */
    private PrivateKey loadRsaPrivateKey(String keyPath) throws Exception {
        String pem = new String(Files.readAllBytes(Paths.get(keyPath)), StandardCharsets.UTF_8);

        // 去除 PEM 標頭/標尾與空白字元
        String base64 = pem
            .replaceAll("-----BEGIN PRIVATE KEY-----",     "")
            .replaceAll("-----END PRIVATE KEY-----",       "")
            .replaceAll("-----BEGIN RSA PRIVATE KEY-----", "")
            .replaceAll("-----END RSA PRIVATE KEY-----",   "")
            .replaceAll("\\s+", "");

        byte[]     keyBytes = Base64.getDecoder().decode(base64);
        KeyFactory kf       = KeyFactory.getInstance("RSA");

        try {
            // 嘗試 PKCS#8 格式 (-----BEGIN PRIVATE KEY-----)
            return kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (InvalidKeySpecException e) {
            // 嘗試 PKCS#1 格式 (-----BEGIN RSA PRIVATE KEY-----)，手動包裝成 PKCS#8
            log.debug("PKCS#8 載入失敗，嘗試以 PKCS#1 包裝處理");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(wrapPkcs1ToPkcs8(keyBytes)));
        }
    }

    /**
     * 將 PKCS#1 DER 位元組包裝為 PKCS#8 DER 結構（純 Java，無外部依賴）。
     *
     * PKCS#8 ASN.1 結構：
     *   SEQUENCE {
     *     INTEGER { 0 }                          -- version
     *     SEQUENCE {                             -- AlgorithmIdentifier
     *       OID { 1.2.840.113549.1.1.1 }        -- rsaEncryption
     *       NULL {}
     *     }
     *     OCTET STRING { <pkcs1 DER bytes> }     -- privateKey
     *   }
     */
    private byte[] wrapPkcs1ToPkcs8(byte[] pkcs1) {
        // AlgorithmIdentifier: SEQUENCE { rsaEncryption OID, NULL }
        byte[] algorithmIdentifier = {
            0x30, 0x0d,
            0x06, 0x09, 0x2a, (byte)0x86, 0x48, (byte)0x86, (byte)0xf7, 0x0d, 0x01, 0x01, 0x01,
            0x05, 0x00
        };
        byte[] version    = { 0x02, 0x01, 0x00 };
        byte[] octetString = concat(new byte[]{0x04}, derLength(pkcs1.length), pkcs1);
        byte[] inner       = concat(version, algorithmIdentifier, octetString);
        return concat(new byte[]{0x30}, derLength(inner.length), inner);
    }

    /** DER length encoding (short/long form) */
    private byte[] derLength(int len) {
        if (len < 0x80)  return new byte[]{ (byte) len };
        if (len < 0x100) return new byte[]{ (byte)0x81, (byte) len };
        return new byte[]{ (byte)0x82, (byte)(len >> 8), (byte)(len & 0xff) };
    }

    /** 串接多個 byte 陣列 */
    private byte[] concat(byte[]... arrays) {
        int totalLen = 0;
        for (byte[] a : arrays) totalLen += a.length;
        byte[] result = new byte[totalLen];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    // =================================================================
    // 【步驟 3 實作】JDBC 查詢 SQL Server
    // =================================================================

    /**
     * 執行 SQL Server 查詢，回傳第一列第一欄的字串值。
     * 對應原指令：sqlcmd -S ip,port -d db -U user -P pass -Q "..."
     *
     * @param sql 查詢 SQL（T-SQL，SELECT 回傳單一欄位即可）
     * @return 第一列第一欄的值；若無結果則回傳空字串
     */
    private String querySqlServer(String ip, String port, String db,
                                   String user, String pass, String sql) throws Exception {
        log.info("JDBC 查詢：{}", sql);
        String url = String.format(JDBC_URL_TEMPLATE, ip, port, db);
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery(sql)) {
            return rs.next() ? StringUtils.defaultString(rs.getString(1)) : "";
        }
    }

    // =================================================================
    // 【步驟 4 實作】FTP 下載 + ftp.ini 解碼
    // =================================================================

    /**
     * 解碼 ftp.ini 中的帳號/密碼行。
     * 對應原 decode.sh 的解碼邏輯（預設為 Base64 UTF-8）。
     *
     * @param encodedLine ftp.ini 中的一行原始文字
     * @return 解碼後的明文字串
     */
    private String decodeFtpCredential(String encodedLine) {
        return new String(Base64.getDecoder().decode(encodedLine), StandardCharsets.UTF_8);
    }

    /**
     * 使用 Apache Commons Net FTPClient 以 Passive Mode 下載單一檔案。
     * 對應原指令：ftp -p -n FTP_IP（含 stdin 指令序列）
     *
     * @param ftpIp         FTP 伺服器 IP
     * @param ftpUser       FTP 帳號
     * @param ftpPass       FTP 密碼
     * @param remoteDir     遠端目錄（例如 /download）
     * @param filename      要下載的檔案名稱
     * @param localFilePath 本地儲存完整路徑
     */
    private void downloadFromFtp(String ftpIp, String ftpUser, String ftpPass,
                                  String remoteDir, String filename,
                                  String localFilePath) throws Exception {
        log.info("FTP 連線：{}", ftpIp);
        FTPClient ftp = new FTPClient();
        try {
            ftp.connect(ftpIp);
            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                throw new RuntimeException("FTP 連線失敗，reply code: " + reply);
            }

            if (!ftp.login(ftpUser, ftpPass)) {
                throw new RuntimeException("FTP 登入失敗，請確認帳號密碼");
            }

            ftp.enterLocalPassiveMode();          // 對應 ftp -p (passive mode)
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            ftp.changeWorkingDirectory(remoteDir);

            log.info("FTP 下載：{} → {}", filename, localFilePath);
            try (OutputStream os = new FileOutputStream(localFilePath)) {
                boolean success = ftp.retrieveFile(filename, os);
                if (!success) {
                    throw new RuntimeException(
                        "FTP 下載失敗：" + filename + "，server reply: " + ftp.getReplyString());
                }
            }
            log.info("FTP 下載成功");

        } finally {
            if (ftp.isConnected()) {
                try { ftp.logout();     } catch (IOException ignored) {}
                try { ftp.disconnect(); } catch (IOException ignored) {}
            }
        }
    }

    // =================================================================
    // 【共用工具】
    // =================================================================

    /**
     * 讀取 key=value 格式的設定檔，轉為 Map。
     * （邏輯不變，原本即為純 Java）
     */
    private Map<String, String> readSqlConfig(String path) throws IOException {
        log.info("讀取 SQL 設定檔：{}", path);
        Map<String, String> conf = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(path))) {
            if (line.contains("=")) {
                // 以第一個 = 為分界（允許密碼中包含 =）
                String[] parts = line.split("=", 2);
                conf.put(parts[0].trim(), parts[1].trim());
            }
        }
        return conf;
    }

    // =================================================================
    // 【例外類別】預期性跳過（非錯誤情況）
    // =================================================================

    /**
     * 預期性跳過例外：
     *   - 當前機器非 master 節點
     *   - 檔案不存在（FTP 尚未上傳）
     * 對應 INFO 等級日誌，不觸發告警。
     */
    private static class SkipExecutionException extends Exception {
        public SkipExecutionException(String message) { super(message); }
    }
    
    /**
     * 解碼 FTP.ini 中的加密憑證（Base64 取代 decode.sh）
     */
    private static String decodeFtpCredential(String encoded, String password) {
        try {
            byte[] cipherData = Base64.getMimeDecoder().decode(encoded);

            byte[] saltHeader = Arrays.copyOfRange(cipherData, 0, 8);
            if (!new String(saltHeader, StandardCharsets.US_ASCII).equals("Salted__")) {
                throw new IllegalArgumentException("Invalid OpenSSL salt header");
            }
            byte[] salt = Arrays.copyOfRange(cipherData, 8, 16);
            byte[] body = Arrays.copyOfRange(cipherData, 16, cipherData.length);

            //先試 SHA-256（OpenSSL >= 1.1.0 預設），再試 MD5（舊版）
            for (String digest : new String[]{"SHA-256", "MD5"}) {
                try {
                    byte[] keyAndIv = evpBytesToKey(
                            password.getBytes(StandardCharsets.UTF_8), salt, 32, 16, digest);
                    byte[] key = Arrays.copyOfRange(keyAndIv, 0, 32);
                    byte[] iv  = Arrays.copyOfRange(keyAndIv, 32, 48);

                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    cipher.init(Cipher.DECRYPT_MODE,
                            new SecretKeySpec(key, "AES"),
                            new IvParameterSpec(iv));

                    byte[] decrypted = cipher.doFinal(body);
                    String result = new String(decrypted, StandardCharsets.UTF_8).trim();
                    //System.out.println("[decodeFtpCredential] 成功使用 digest=" + digest);
                    return result;

                } catch (BadPaddingException | IllegalBlockSizeException e) {
                    //這個 digest 不對，換下一個試
                    //System.out.println("[decodeFtpCredential] digest=" + digest + " 失敗，嘗試下一個");
                }
            }
            throw new RuntimeException("所有 digest 均解密失敗，請確認密碼或加密方式");

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解碼失敗", e);
        }
    }

    //新增 digest 參數
    private static byte[] evpBytesToKey(byte[] password, byte[] salt,
                                        int keyLen, int ivLen, String digest) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance(digest);
        byte[] keyAndIv = new byte[keyLen + ivLen];
        byte[] prev = new byte[0];
        int filled = 0;
        while (filled < keyAndIv.length) {
            md.update(prev);
            md.update(password);
            md.update(salt);
            prev = md.digest();
            int copyLen = Math.min(prev.length, keyAndIv.length - filled);
            System.arraycopy(prev, 0, keyAndIv, filled, copyLen);
            filled += copyLen;
        }
        return keyAndIv;
    }
    
}