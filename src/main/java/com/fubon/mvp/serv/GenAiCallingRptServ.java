package com.fubon.mvp.serv;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailHostDao;

/**
 * ============================================================================
 * 富邦MVP - GenAiCallingRptServ (AI外撥報表導出服務)
 * ============================================================================
 * @author 張晏哲
 * @category 服務類
 *
 * 【功能概述】
 * 每日凌晨自動觸發，執行以下流程：
 *   1. 透過 EmailHostDao.isMain() 確認當前機器是否為 master（僅 master 執行）
 *   2. 透過 Spring 的 mvpJdbc(JdbcTemplate) 從 EMAILMAS 資料庫撈取 FLAG='2' 且 PHONE 不為空的外撥候選名單
 *   3. 將資料寫入 CSV 報表，並清除多餘空白
 *   4. 透過 FTP 上傳至合作廠商 FTP 伺服器
 *
 * 【改版說明 2026/09】
 *   原本這裡是逐行翻譯自 shell script（gen_ai_calling_rpt.sh）的做法：
 *   自行用 RSA 私鑰解密 mvpsqlserver.conf.enc 取得帳密，再用 DriverManager
 *   手動建立一條全新的 JDBC 連線，跟 Spring 既有的 mvpDataSource 連線池、
 *   以及 application.properties 裡 Jasypt 加密的 mvp.datasource.* 完全脫鉤。
 *   確認正式機 mvpsqlserver.conf.enc 解密出來的資料庫與 application.properties-prod
 *   是同一套之後，已改為直接注入 Spring 既有元件（比照 AiEmailResultRptServ）：
 *     - master 節點判斷 → EmailHostDao.isMain()
 *     - 資料庫查詢       → mvpJdbc (JdbcTemplate，底層走 mvpDataSource 連線池)
 *   RSA 解密、BouncyCastle、手動 JDBC 連線、明碼密碼暫存檔等相關程式碼已全數移除。
 *
 * 【執行排程】
 *   cron = "0 0 1 * * ?"  → 台灣時間每日凌晨 1:00
 *
 * 【依賴環境】
 *   - Apache Commons Net FTP    → 上傳 CSV 至合作廠商 FTP 伺服器
 *   - ftp2.ini                   → 存放編碼後的 FTP 帳號密碼（第1列=帳號, 第2列=密碼）
 *
 * 【資料流程】
 *   EMAILMAS 資料庫 (透過 mvpJdbc, FLAG='2' AND PHONE NOT NULL)
 *     ↓ SQL 查詢 + JdbcTemplate 導出
 *   {REPORTS_DIR}/AI_CALLING_YYYYMMDD.csv
 *     ↓ 清除空白
 *   {REPORTS_DIR}/AI_CALLING_YYYYMMDD.csv_CLEAN
 *     ↓ rename 覆蓋原檔
 *   {REPORTS_DIR}/AI_CALLING_YYYYMMDD.csv
 *     ↓ FTP 上傳
 *   合作廠商 FTP /MVP/810SCOMM
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
 *   - RuntimeException       : 指令執行失敗（JDBC/FTP）
 *                                → 記錄 ERROR 等級日誌，需介入處理
 * ============================================================================
 */
@Service
public class GenAiCallingRptServ {
    private static final Logger log = LoggerFactory.getLogger(GenAiCallingRptServ.class);

    // 主機節點判斷（取代 RSA解密+DriverManager手動查詢master的方式）
    @Autowired
    private EmailHostDao hostDao;

    // Spring 已配置好的 JdbcTemplate（底層走 mvpDataSource 連線池，取代手動 DriverManager 連線）
    @Autowired
    private JdbcTemplate mvpJdbc;

    // -----------------------------------------------------------------
    // 外部設定（由 application.properties 注入）
    @Value("${GenAiCallingRptServ.FTP_IP}")
    private String FTP_IP;

    // -----------------------------------------------------------------
    // 環境路徑定義（對應 .sh 中的變數）
    @Value("${mvp.report.dir:/home/mvpadm/reports}")
    private String REPORTS_DIR;

    @Value("${mvp.sh.dir:/home/mvpadm/sh}")
    private String SH_DIR;

    @Value("${mvp.ai_calling_filename_prefix:AI_CALLING_}")
    private String AI_CALLING_FILENAME_PREFIX;
    
    @Value("${mvp.decodeFtpCredential:b77a5c561934e089}")
    private String DECODE_FTP_CREDENTIAL;

    // =================================================================
    // 【排程入口】每日凌晨 1:00 觸發
    // =================================================================
    
    //@Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Taipei")
	
    //排程執行週期設
    @Scheduled(cron = "${mvp.110007.GenAiCallingRpt.expression}", zone = "${mvp.110007.cron.zone}")
    
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
        // -----------------------------------------------------------------
        // 步驟 1：日期設定
        // -----------------------------------------------------------------
        String rundate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        log.info("Run Date: {}", rundate);

        // -----------------------------------------------------------------
        // 步驟 2：確認當前機器是否為 master 節點
        //   原始作法：RSA解密取得連線資訊 → DriverManager查詢emailhos → 與本機hostname比對
        //   現在作法：直接用 Spring 既有的 EmailHostDao.isMain()
        //            （底層透過 mvpDataSource 連線池查詢 emailhos，且每 3 秒背景自動刷新）
        // -----------------------------------------------------------------
        if (!this.hostDao.isMain()) {
            log.info("=== Running Machine Is Not Master ===\nSkipping execution.");
            throw new SkipExecutionException("Not master node");
        }
        log.info("=== Confirmed Master, continuing... ===");

        // -----------------------------------------------------------------
        // 步驟 3：產出 AI 外撥報表 CSV（取代 sqlcmd + sed）
        //   a. 寫入 CSV 標頭（17 欄）
        //   b. 執行 SQL 提取 FLAG='2' 且 PHONE 不為空的記錄
        //   c. 將資料附加至 CSV 檔案
        //   d. 清除多餘空白，再 mv 覆蓋原檔
        // -----------------------------------------------------------------
        String reportFile = AI_CALLING_FILENAME_PREFIX + rundate + ".csv";
        Path reportPath = Paths.get(REPORTS_DIR, reportFile);

        // 寫入 CSV 標頭（17 欄位）
        writeCsvHeader(reportPath);

        //TODO 執行 SQL 提取資料並附加至 CSV
        String dataQuery = "set nocount on;\n" +
            "select RTRIM(PHONE) as [手機號碼],\n" +
            "       RTRIM(ID) as [客戶ID],\n" +
            "       RTRIM(NAME) as [客戶姓名],\n" +
            
            //"       '有關您變更留存於本行的電子郵件信箱之相關訊息要通知您' as [本次外撥目的],\n" +
            //"       '您好，由於您申請變更您留存於本行的電子郵件信箱，但您尚未回覆確認您的電子郵件信箱，故本行目前尚未啟用您的電子郵件信箱，提醒您儘速完成電子郵件信箱確認回覆。' as TTS1,\n" +
            
            "      '有關您變更留存的email信箱相關訊息要通知您' as [本次外撥目的],\n"+
            "      '您申請變更email信箱的需求，因尚未完成信箱驗證，所以變更程序暫時無法生效。請您儘速確認並回覆驗證郵件，完成啟用程序。' as TTS1,\n" +
            
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
            "where STATUS='00' AND TX_STATUS='17' AND PHONE IS NOT NULL AND PHONE <> '' " +
            "AND CHG_DATE BETWEEN CONVERT(varchar(8), DATEADD(day, -28, GETDATE()), 112) AND CONVERT(varchar(8), DATEADD(day, -7, GETDATE()), 112) AND FLAG = '1' ";

        exportCsvData(dataQuery, reportPath, true);

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
        // 步驟 4：透過 FTP 上傳報表至合作廠商（取代 ftp shell）
        // -----------------------------------------------------------------
        processFtpUpload(reportFile);
    }

    // =================================================================
    // 【步驟 3a】寫入 CSV 標頭
    // =================================================================
    private void writeCsvHeader(Path csvPath) throws IOException {
        String header = "手機號碼,客戶ID,客戶姓名,本次外撥目的,TTS1,變數1,變數2,變數3,TTS2,TTS3,TTS4,SMS1,SMS2,SMS3,SMS4,SMS5,SMSDefault";

        if (Files.exists(csvPath)) {
            Files.delete(csvPath);
        }
        try (OutputStream os = Files.newOutputStream(csvPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            // 寫入 BOM（僅在檔案最前面一次），確保 Excel 開啟時正確辨識 UTF-8 中文編碼
            os.write(0xEF);
            os.write(0xBB);
            os.write(0xBF);
            writer.write(header);
            writer.write("\n");
        }
    }

    // =================================================================
    // 【步驟 3b】查詢資料並寫入 CSV
    //   原本：DriverManager 自建連線 → Statement → ResultSet
    //   現在：透過 Spring 的 mvpJdbc(JdbcTemplate) 向 mvpDataSource 連線池
    //         借用一條連線(用完自動歸還)，維持原本 CSV 導出邏輯不變
    // =================================================================
    private void exportCsvData(String query, Path csvPath, boolean withBom) throws Exception {

        this.mvpJdbc.execute((ConnectionCallback<Void>) conn -> {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                writeCsvRows(rs, csvPath);
                return null;

            } catch (Exception ex) {
                // ConnectionCallback 不允許拋出受檢例外，包裝成 RuntimeException
                // 呼叫端(runProcess)的 throws Exception 仍會正常往外傳遞
                throw new RuntimeException(ex);
            }
        });
    }

    private void writeCsvRows(ResultSet rs, Path csvPath) throws Exception {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        // 表頭已由 writeCsvHeader 寫入（含 BOM），這裡改用 APPEND 附加資料列，避免覆蓋表頭
        try (OutputStream os = Files.newOutputStream(csvPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
             Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            // 開始寫 CSV 資料
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) sb.append(",");
                    String val = rs.getString(i);
                    val = (val == null) ? "" : val.trim();
                    if (i == 1 && !val.isEmpty()) {
                        // 第一欄「手機號碼」：包成 ="..." 避免 Excel 自動當數字解析、去掉前導0
                        sb.append("=\"").append(val).append("\"");
                    } else {
                        sb.append(val);
                    }
                }
                writer.write(sb.toString());
                writer.write("\n");
            }
        }
        log.info("建立外撥清單檔案 CSV data exported to: {}", csvPath);
    }

    // =================================================================
    // 【步驟 3c】清除多餘空白（Java 取代 sed 's/ *//g'）
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
    // 【步驟 4】透過 FTP 上傳報表（Apache Commons Net 取代 ftp shell）
    // =================================================================
    private void processFtpUpload(String reportFile) throws Exception {
    	log.info("透過 FTP 上傳報表");
    	
        File ftpIni = new File(SH_DIR, "ftp2.ini");
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

        if (ftpUser.isEmpty() || ftpPass.isEmpty()) {
            log.error("FTP credentials are empty, cannot upload. ftpUser={}, ftpPass={}", ftpUser.isEmpty() ? "(empty)" : "***", ftpPass.isEmpty() ? "(empty)" : "***");
            return;
        }

        File localFile = new File(REPORTS_DIR, reportFile);
        if (!localFile.isFile()) {
            log.error("Local file not found: {}", localFile.getAbsolutePath());
            return;
        }
        
        //TODO 上傳遠端的路徑
        String remoteDir = "/MVP/810SCOMM";
        
        uploadFileViaFtp(FTP_IP, ftpUser, ftpPass, REPORTS_DIR, remoteDir, reportFile);
    }
    
    //解碼 FTP.ini 中的加密憑證（Base64 取代 decode.sh）
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
    private static byte[] evpBytesToKey(byte[] password, byte[] salt, int keyLen, int ivLen, String digest) throws Exception {
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
    
    /**
     * 透過 Apache Commons Net FTPClient 上傳檔案（取代 ftp shell）
     */
    private void uploadFileViaFtp(String ftpIp, String ftpUser, String ftpPass, String localDir, String remoteDir, String fileName) throws Exception {

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
                    log.info("FTP upload completed: {} 上傳遠端的路徑 {} {}", fileName, ftpIp, remoteDir);
                }
            }

            ftp.logout();
        } finally {
            if (ftp != null && ftp.isConnected()) {
                try { ftp.disconnect(); } catch (IOException e) { }
            }
        }
    }

    // =================================================================
    // 【自訂例外】用於預期性跳過執行（非錯誤情況）
    // =================================================================
    @SuppressWarnings("serial")
    private static class SkipExecutionException extends Exception {
        public SkipExecutionException(String message) {
            super(message);
        }
    }
}
