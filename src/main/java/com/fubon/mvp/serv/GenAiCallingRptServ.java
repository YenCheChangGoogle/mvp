package com.fubon.mvp.serv;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ============================================================================
 * 富邦MVP - GenAiCallingRptServ (AI外撥報表導出服務)
 * ============================================================================
 * @author 張晏哲
 * @category 服務類
 *
 * 【功能概述】
 * 完全模擬 gen_ai_calling_rpt.sh 的執行邏輯，移除平台依賴
 * 每日凌晨 1:00 自動觸發，執行以下流程：
 *   1. RSA 私鑰解密密碼檔，取得 SQL Server 連線資訊
 *   2. 查詢主節點，確認當前機器是否為 master（僅 master 執行）
 *   3. 從 EMAILMAS 資料庫撈取 FLAG='2' 且 PHONE 不為空的外撥候選名單
 *   4. 將資料寫入 CSV 報表，並清除多餘空白
 *   5. 透過 FTP 上傳至合作廠商 FTP 伺服器
 *   6. 清理解密的設定檔（避免明碼密碼殘留）
 *
 * 【執行排程】
 *   cron = "0 0 1 * * ?"  → 台灣時間每日凌晨 1:00
 *
 * 【依賴環境】
 *   - BouncyCastle JCE provider  → RSA 私鑰解密 mvpsqlserver.conf.enc
 *   - Microsoft JDBC Driver     → 查詢 SQL Server
 *   - Apache Commons Net FTP    → 上傳 CSV 至合作廠商 FTP 伺服器
 *   - ftp.ini                   → 存放編碼後的 FTP 帳號密碼（第1列=帳號, 第2列=密碼）
 *
 * 【資料流程】
 *   EMAILMAS 資料庫 (FLAG='2' AND PHONE NOT NULL)
 *     ↓ SQL 查詢 + JDBC 導出
 *   {REPORTS_DIR}/AI_CALLING_YYYYMMDD.csv
 *     ↓ 清除空白
 *   {REPORTS_DIR}/AI_CALLING_YYYYMMDD.csv_CLEAN
 *     ↓ rename 覆蓋原檔
 *   {REPORTS_DIR}/AI_CALLING_YYYYMMDD.csv
 *     ↓ FTP 上傳
 *   合作廠商 FTP /upload/A0001527/MVP_2_AUC/
 *
 * 【CSV 欄位結構】
 *   手機號碼, 客戶ID, 客戶姓名, 本次外撥目的, TTS1, 變數1, 變數2, 變數3,
 *   TTS2, TTS3, TTS4, SMS1, SMS2, SMS3, SMS4, SMS5, SMSDefault
 *
 * 【與 ImportAiResultServ 的關聯】
 *   GenAiCallingRptServ   (01:00) → 導出外撥名單 → 上傳 FTP
 *                          ↓ 合作廠商執行 AI 外撥
 *   ImportAiResultServ    (02:00) → 下載外撥結果 → 更新 DB
 *
 * 【例外處理】
 *   - SkipExecutionException : 非 master 節點 → 記錄 INFO 等級日誌
 *   - RuntimeException       : 指令執行失敗（RSA解密/JDBC/FTP）
 *                                → 記錄 ERROR 等級日誌，需介入處理
 * ============================================================================
 */
@Service
public class GenAiCallingRptServ {
    private static final Logger log = LoggerFactory.getLogger(GenAiCallingRptServ.class);

    // BouncyCastle provider name
    private static final String BC_PROVIDER = "BC";

    // -----------------------------------------------------------------
    // 外部設定（由 application.properties 注入）
    @Value("${GenAiCallingRptServ.FTP_IP}")
    private String FTP_IP;

    // -----------------------------------------------------------------
    // 環境路徑定義（對應 .sh 中的變數）
    @Value("${mvp.home.dir:/home/mvpadm}")
    private String HOME;

    @Value("${mvp.report.dir:/home/mvpadm/reports}")
    private String REPORTS_DIR;

    @Value("${mvp.sh.dir:/home/mvpadm/sh}")
    private String SH_DIR;

    @Value("${mvp.ai_calling_filename_prefix:AI_CALLING_}")
    private String AI_CALLING_FILENAME_PREFIX;

    // =================================================================
    // 【排程入口】每日凌晨 1:00 觸發
    // =================================================================
    @Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Taipei")
    public void execute() {
        log.info("Starting AI Calling Report process...");
        try {
            runProcess();
            log.info("AI Calling Report process completed successfully.");
        } catch (SkipExecutionException e) {
            log.info("Skipping execution: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Critical error during AI Calling Report process: ", e);
        }
    }

    // =================================================================
    // 【核心流程】模擬 shell 腳本 gen_ai_calling_rpt.sh 的完整步驟
    // =================================================================
    private void runProcess() throws Exception {
        Path decryptedConfPath = Paths.get(HOME, "mvpsqlserver.conf");
        try {
            // -----------------------------------------------------------------
            // 步驟 1：日期設定
            // -----------------------------------------------------------------
            String rundate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            log.info("Run Date: {}", rundate);

            // -----------------------------------------------------------------
            // 步驟 2：RSA 私鑰解密 SQL 連線設定檔（取代 openssl rsautl）
            // -----------------------------------------------------------------
            decryptWithRsaPrivateKey(decryptedConfPath);

            // 讀取解密的設定檔
            Map<String, String> sqlConf = readSqlConfig(decryptedConfPath.toString());
            String ip       = sqlConf.get("ip");
            String port     = sqlConf.get("port");
            String database = sqlConf.get("database");
            String user     = sqlConf.get("user");
            String password = sqlConf.get("password");

            // -----------------------------------------------------------------
            // 步驟 3：確認當前機器是否為 master 節點（取代 sqlcmd）
            //   查詢 emailhos 資料表，找出 main='1' 的主機名稱
            //   若首次查詢返回空值，等待 10 秒後重試一次
            // -----------------------------------------------------------------
            String masterQuery = "set nocount on;select host_name from emailhos where main='1';";
            String master = queryMasterNode(ip, port, database, user, password, masterQuery);

            // 若首次查詢返回空值，等待 10 秒後重試
            if (master == null || master.isEmpty()) {
                log.info("Master not found, sleeping 10s...");
                Thread.sleep(10000);
                master = queryMasterNode(ip, port, database, user, password, masterQuery);
            }

            // 取得當前機器的主機名稱並比較（原始用 getHostName，非 IP）
            String runMachine = InetAddress.getLocalHost().getHostName();
            if (master == null || !master.equals(runMachine)) {
                log.info("=== The Master Is {} ===\n=== Running Machine Is {} ===\nSkipping execution.", master, runMachine);
                throw new SkipExecutionException("Not master node");
            }
            log.info("=== The Master Is {} ===\n=== Running Machine Is {} ===\nConfirmed Master, continuing...", master, runMachine);

            // -----------------------------------------------------------------
            // 步驟 4：產出 AI 外撥報表 CSV（取代 sqlcmd + sed）
            //   a. 寫入 CSV 標頭（17 欄）
            //   b. 執行 SQL 提取 FLAG='2' 且 PHONE 不為空的記錄
            //   c. 將資料附加至 CSV 檔案
            //   d. 清除多餘空白，再 mv 覆蓋原檔
            // -----------------------------------------------------------------
            String reportFile = AI_CALLING_FILENAME_PREFIX + rundate + ".csv";
            Path reportPath = Paths.get(REPORTS_DIR, reportFile);

            // 寫入 CSV 標頭（17 欄位）
            writeCsvHeader(reportPath);

            // 執行 SQL 提取資料並附加至 CSV
            String dataQuery = "set nocount on;\n" +
                "select RTRIM(PHONE) as [手機號碼],\n" +
                "       RTRIM(ID) as [客戶ID],\n" +
                "       RTRIM(NAME) as [客戶姓名],\n" +
                "       '6日未回覆' as [本次外撥目的],\n" +
                "       '' as TTS1,\n" +
                "       'NA' as [變數1],\n" +
                "       'NA' as [變數2],\n" +
                "       'NA' as [變數3],\n" +
                "       'NA' as TTS2,\n" +
                "       'NA' as TTS3,\n" +
                "       'NA' as TTS4,\n" +
                "       'NA' as SMS1,\n" +
                "       'NA' as SMS2,\n" +
                "       'NA' as SMS3,\n" +
                "       'NA' as SMS4,\n" +
                "       'NA' as SMS5,\n" +
                "       'NA' as SMSDefault\n" +
                "from EMAILMAS \n" +
                "where STATUS='00' AND TX_STAUT='17' AND PHONE IS NOT NULL AND PHONE <> '';";

            exportCsvData(ip, port, database, user, password, dataQuery, reportPath);

            // 用 Java 清除 CSV 中的多餘空白 (對應 sed 's/ *//g')
            String cleanFile = reportFile + "_CLEAN";
            Path cleanPath = Paths.get(REPORTS_DIR, cleanFile);
            try {
                cleanWhitespace(reportPath, cleanPath);
                Files.move(cleanPath, reportPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Files.deleteIfExists(cleanPath);
                throw e;
            }

            // -----------------------------------------------------------------
            // 步驟 5：透過 FTP 上傳報表至合作廠商（取代 ftp shell）
            // -----------------------------------------------------------------
            processFtpUpload(reportFile);

        } finally {
            // 無論成功或失敗，一律清理解密設定檔（避免明碼密碼殘留）
            try { Files.deleteIfExists(decryptedConfPath); }
            catch (IOException e) { log.warn("Failed to delete mvpsqlserver.conf", e); }
        }
    }

    // =================================================================
    // 【步驟 2】RSA 私鑰解密（取代 openssl rsautl -decrypt）
    // =================================================================
    private void decryptWithRsaPrivateKey(Path outputConfPath) throws Exception {
        if (Security.getProvider(BC_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        // 讀取 RSA 私鑰檔案
        Path rsaKeyPath = Paths.get(HOME, "rsa.key");
        byte[] keyBytes = Files.readAllBytes(rsaKeyPath);
        String keyPem = new String(keyBytes, StandardCharsets.UTF_8);

        // 去除 PEM 標頭/尾
        String base64Key = keyPem
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");

        byte[] derKey = java.util.Base64.getDecoder().decode(base64Key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derKey);
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) kf.generatePrivate(keySpec);

        // RSA 私鑰解密（對應 openssl rsautl -decrypt）
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", BC_PROVIDER);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        // 讀取加密檔並解密
        Path encPath = Paths.get(HOME, "mvpsqlserver.conf.enc");
        byte[] encryptedBytes = Files.readAllBytes(encPath);
        
        
        
        
        
        /*
        // openssl rsautl 的 block size 受限於 key size；大檔案需分塊解密
        int blockSize = (privateKey.getModulus().bitLength() / 8) - 11; // PKCS1 padding overhead
        ByteArrayOutputStream decryptedOutput = new ByteArrayOutputStream();

        for (int i = 0; i < encryptedBytes.length; i += blockSize) {
            int remaining = encryptedBytes.length - i;
            int chunkSize = Math.min(blockSize, remaining);
            byte[] chunk = Arrays.copyOfRange(encryptedBytes, i, i + chunkSize);
            byte[] decryptedChunk = cipher.doFinal(chunk);
            decryptedOutput.write(decryptedChunk);
        }

        // 寫入解密後的設定檔
        Files.write(outputConfPath, decryptedOutput.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        */
        
        byte[] decrypted = cipher.doFinal(encryptedBytes);
        Files.write(outputConfPath, decrypted, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        
        
        
        
        log.info("Decrypted mvpsqlserver.conf successfully.");
    }

    // =================================================================
    // 【步驟 3】查詢 master 節點（JDBC 取代 sqlcmd）
    // =================================================================
    private String queryMasterNode(String dbIp, String dbPort, String dbName,
                                   String dbUser, String dbPass, String query) throws SQLException {
        String url = String.format(
            "jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=false;trustServerCertificate=true",
            dbIp, dbPort, dbName);

        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                return rs.getString(1).trim();
            }
            return "";
        }
    }

    // =================================================================
    // 【步驟 4a】寫入 CSV 標頭
    // =================================================================
    private void writeCsvHeader(Path csvPath) throws IOException {
        String header = "手機號碼,客戶ID,客戶姓名,本次外撥目的,TTS1,變數1,變數2,變數3," +
                        "TTS2,TTS3,TTS4,SMS1,SMS2,SMS3,SMS4,SMS5,SMSDefault";

        if (Files.exists(csvPath)) {
            Files.delete(csvPath);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            writer.write(header);
            writer.newLine();
        }
    }

    // =================================================================
    // 【步驟 4b】查詢資料並寫入 CSV（JDBC 取代 sqlcmd）
    // =================================================================
    private void exportCsvData(String dbIp, String dbPort, String dbName,
                               String dbUser, String dbPass, String query, Path csvPath) throws Exception {

        String url = String.format(
            "jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=false;trustServerCertificate=true",
            dbIp, dbPort, dbName);

        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
                while (rs.next()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i <= colCount; i++) {
                        if (i > 1) sb.append(",");
                        String val = rs.getString(i);
                        sb.append(val == null ? "" : val.trim());
                    }
                    writer.write(sb.toString());
                    writer.newLine();
                }
            }
        }

        log.info("CSV data exported to: {}", csvPath);
    }

    // =================================================================
    // 【步驟 4c】清除多餘空白（Java 取代 sed 's/ *//g'）
    // =================================================================
    private void cleanWhitespace(Path sourcePath, Path targetPath) throws IOException {
        List<String> lines = Files.readAllLines(sourcePath, StandardCharsets.UTF_8);
        try (BufferedWriter writer = Files.newBufferedWriter(targetPath, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                // 對應 sed 's/ *//g' → 刪除所有空白字元
                writer.write(line.replace(" ", ""));
                writer.newLine();
            }
        }
    }

    // =================================================================
    // 【步驟 5】透過 FTP 上傳報表（Apache Commons Net 取代 ftp shell）
    // =================================================================
    private void processFtpUpload(String reportFile) throws Exception {
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
            String decoded = decodeFtpCredential(line.trim());
            if (count == 0) {
                ftpUser = decoded;
            } else if (count == 1) {
                ftpPass = decoded;
                break;
            }
            count++;
        }

        if (ftpUser.isEmpty() || ftpPass.isEmpty()) {
            log.error("FTP credentials are empty, cannot upload. ftpUser={}, ftpPass={}",
                ftpUser.isEmpty() ? "(empty)" : "***",
                ftpPass.isEmpty() ? "(empty)" : "***");
            return;
        }

        File localFile = new File(REPORTS_DIR, reportFile);
        if (!localFile.isFile()) {
            log.error("Local file not found: {}", localFile.getAbsolutePath());
            return;
        }
        
        //TODO 上傳路徑
        String remoteDir = "/MVP/810SCOMM";
        uploadFileViaFtp(FTP_IP, ftpUser, ftpPass, REPORTS_DIR, remoteDir, reportFile);
    }

    /**
     * 解碼 FTP.ini 中的加密憑證（Base64 取代 decode.sh）
     */
    private String decodeFtpCredential(String encoded) {
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("Could not decode FTP credential as Base64, returning raw value.");
            return encoded;
        }
    }

    /**
     * 透過 Apache Commons Net FTPClient 上傳檔案（取代 ftp shell）
     */
    private void uploadFileViaFtp(String ftpIp, String ftpUser, String ftpPass,
                                  String localDir, String remoteDir, String fileName) throws Exception {

        org.apache.commons.net.ftp.FTPClient ftp = null;
        try {
            ftp = new org.apache.commons.net.ftp.FTPClient();
            ftp.connect(ftpIp, 21);
            ftp.login(ftpUser, ftpPass);
            ftp.setFileType(org.apache.commons.net.ftp.FTPClient.BINARY_FILE_TYPE);
            ftp.enterLocalPassiveMode();
            ftp.changeWorkingDirectory(remoteDir);

            Path filePath = Paths.get(localDir, fileName);
            try (InputStream localStream = Files.newInputStream(filePath)) {
                boolean success = ftp.storeFile(fileName, localStream);
                if (!success) {
                    //log.error("FTP upload failed for: {}", fileName);
                    throw new IOException("FTP upload failed for: " + fileName + ", server reply: " + ftp.getReplyString());
                } else {
                    log.info("FTP upload completed: {}", fileName);
                }
            }

            ftp.logout();
        } finally {
            if (ftp != null && ftp.isConnected()) {
                try { ftp.disconnect(); } catch (IOException e) { /* ignore */ }
            }
        }
    }

    // =================================================================
    // 【工具方法】讀取 SQL 設定檔，轉為 key-value 配對
    // =================================================================
    private Map<String, String> readSqlConfig(String path) throws IOException {
        Map<String, String> conf = new HashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(path));
        for (String line : lines) {
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                conf.put(parts[0].trim(), parts[1].trim());
            }
        }
        return conf;
    }

    // =================================================================
    // 【自訂例外】用於預期性跳過執行（非錯誤情況）
    // =================================================================
    private static class SkipExecutionException extends Exception {
        public SkipExecutionException(String message) {
            super(message);
        }
    }
}
