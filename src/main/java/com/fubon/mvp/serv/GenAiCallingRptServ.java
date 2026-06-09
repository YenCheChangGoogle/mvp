package com.fubon.mvp.serv;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * ============================================================================
 * 富邦MVP - GenAiCallingRptServ (AI外撥報表導出服務)
 * ============================================================================
 * @author 張晏哲
 * @category 服務類
 *
 * 【功能概述】
 * 完全模擬 gen_ai_calling_rpt.sh 的執行邏輯與環境依賴
 * 每日凌晨 1:00 自動觸發，執行以下流程：
 *   1. 解密密碼檔，取得 SQL Server 連線資訊
 *   2. 查詢主節點，確認當前機器是否為 master（僅 master 執行）
 *   3. 從 EMAILMAS 資料庫撈取 FLAG='2' 且 PHONE 不為空的外撥候選名單
 *   4. 將資料寫入 CSV 報表，並用 sed 清除多餘空白
 *   5. 透過 FTP 上傳至合作廠商 FTP 伺服器
 *   6. 清理解密的設定檔（避免明碼密碼殘留）
 *
 * 【執行排程】
 *   cron = "0 0 1 * * ?"  → 台灣時間每日凌晨 1:00
 *
 * 【依賴環境】
 *   - openssl     → 解密 mvpsqlserver.conf.enc
 *   - sqlcmd      → 查詢 SQL Server（master 節點確認 + CSV 資料提取）
 *   - sed         → 清除 CSV 中的多餘空白
 *   - ftp         → 上傳 CSV 至合作廠商 FTP 伺服器
 *   - decode.sh   → 解碼 ftp.ini 中的使用者/密碼
 *   - ftp.ini     → 存放編碼後的 FTP 帳號密碼（第1列=帳號, 第2列=密碼）
 *
 * 【資料流程】
 *   EMAILMAS 資料庫 (FLAG='2' AND PHONE NOT NULL)
 *     ↓ SQL 查詢 + sqlcmd 導出
 *   /home/mvpadm/reports/AI_CALLING_YYYYMMDD.csv
 *     ↓ sed 清除空白
 *   /home/mvpadm/reports/AI_CALLING_YYYYMMDD.csv_CLEAN
 *     ↓ mv 覆蓋原檔
 *   /home/mvpadm/reports/AI_CALLING_YYYYMMDD.csv
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
 *   - RuntimeException       : 指令執行失敗（openssl/sqlcmd/sed/ftp）
 *                                → 記錄 ERROR 等級日誌，需介入處理
 * ============================================================================
 */
@Service
public class GenAiCallingRptServ {
    private static final Logger log = LoggerFactory.getLogger(GenAiCallingRptServ.class);

    // -----------------------------------------------------------------
    // 外部設定（由 application.properties 注入）
    //   範例: GenAiCallingRptServ.FTP_IP=192.168.1.200
    // -----------------------------------------------------------------
    @Value("${GenAiCallingRptServ.FTP_IP}")
    private String FTP_IP;

    // -----------------------------------------------------------------
    // 環境路徑定義（對應 .sh 中的變數）
    //   HOME        → mvpadm 使用者的家目錄（通常為 /home/mvpadm）
    //   REPORTS_DIR → CSV 報表產出目錄
    //   SH_DIR      → shell 指令腳本目錄（含 decode.sh、ftp.ini）
    // -----------------------------------------------------------------
    
   @Value("${mvp.home.dir:/home/mvpadm}")
    private String HOME;
    
    @Value("${mvp.report.dir:/home/mvpadm/reports}")
    private String REPORTS_DIR = "/home/mvpadm/reports";
    
    @Value("${mvp.sh.dir:/home/mvpadm/sh}")
    private String SH_DIR = "/home/mvpadm/sh";
    
    @Value("${mvp.ai_calling_filename_prefix:AI_CALLING_}")
    private String AI_CALLING_FILENAME_PREFIX="AI_CALLING_";

    // =================================================================
    // 【排程入口】每日凌晨 1:00 觸發
    // =================================================================
    @Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Taipei")
    public void execute() {
        log.info("Starting AI Calling Report process...");
        try {
            // 執行核心流程
            runShellLikeProcess();
            log.info("AI Calling Report process completed successfully.");
        } catch (SkipExecutionException e) {
            // 預期性跳過（非 master 節點）→ INFO 等級，不觸發告警
            log.info("Skipping execution: {}", e.getMessage());
        } catch (Exception e) {
            // 非預期性錯誤（如：FTP 連線失敗、DB 連線失敗、指令執行失敗）
            // 記錄 ERROR 等級，需監控/告警
            log.error("Critical error during AI Calling Report process: ", e);
        }
    }

    // =================================================================
    // 【核心流程】模擬 shell 腳本 gen_ai_calling_rpt.sh 的完整步驟
    // =================================================================
    private void runShellLikeProcess() throws Exception {
        try {

        // -----------------------------------------------------------------
        // 步驟 1：日期設定
        //   rundate → 當日日期（YYYYMMDD），用於命名報表檔案
        // -----------------------------------------------------------------
        String rundate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        log.info("Run Date: {}", rundate);

        // -----------------------------------------------------------------
        // 步驟 2：解密 SQL 連線設定檔
        //   使用 RSA 私鑰解密 mvpsqlserver.conf.enc，產生明碼 mvpsqlserver.conf
        //   此檔包含：ip, port, database, sep, user, password
        // -----------------------------------------------------------------
        executeCmd(String.format(
            "openssl rsautl -decrypt -inkey %s/rsa.key -in %s/mvpsqlserver.conf.enc -out %s/mvpsqlserver.conf",
            HOME, HOME, HOME
        ));

        // 讀取解密的設定檔，轉成 key-value 配對
        Map<String, String> sqlConf = readSqlConfig(HOME + "/mvpsqlserver.conf");
        String ip = sqlConf.get("ip");
        String port = sqlConf.get("port");
        String database = sqlConf.get("database");
        String separator = sqlConf.getOrDefault("sep", "|");
        String user = sqlConf.get("user");
        String password = sqlConf.get("password");

        // -----------------------------------------------------------------
        // 步驟 3：確認當前機器是否為 master 節點
        //   查詢 emailhos 資料表，找出 main='1' 的主機名稱
        //   若查詢失敗，等待 10 秒後重試一次
        //   若非 master，刪除設定檔後跳出（不視為錯誤）
        // -----------------------------------------------------------------
        String masterQuery = "set nocount on;select host_name from emailhos where main='1';";
        String master = executeSqlCmd(ip, port, database, user, password, separator, masterQuery).trim();

        // 若首次查詢返回空值，等待 10 秒後重試
        if (master.isEmpty()) {
            log.info("Master not found, sleeping 10s...");
            Thread.sleep(10000);
            master = executeSqlCmd(ip, port, database, user, password, separator, masterQuery).trim();
        }

        // 取得當前機器的主機名稱並比較
        String runMachine = java.net.InetAddress.getLocalHost().getHostName();
        if (!master.equals(runMachine)) {
            log.info("=== The Master Is {} ===\n=== Running Machine Is {} ===\nSkipping execution.", master, runMachine);
            // 清理解密的設定檔（避免明碼密碼殘留）
            Files.deleteIfExists(Paths.get(HOME + "/mvpsqlserver.conf"));
            throw new SkipExecutionException("Not master node");
        }
        log.info("=== The Master Is {} ===\n=== Running Machine Is {} ===\nConfirmed Master, continuing...", master, runMachine);

        // -----------------------------------------------------------------
        // 步驟 4：產出 AI 外撥報表 CSV
        //   a. 寫入 CSV 標頭（17 欄）
        //   b. 執行 SQL 提取 FLAG='2' 且 PHONE 不為空的記錄
        //   c. 將 sqlcmd 輸出附加至 CSV 檔案
        //   d. 用 sed 清除多餘空白，再 mv 覆蓋原檔
        // -----------------------------------------------------------------
        String reportFile = AI_CALLING_FILENAME_PREFIX + rundate + ".csv";
        File reportPath = new File(REPORTS_DIR, reportFile);

        // 寫入 CSV 標頭（17 欄位）
        String header = "手機號碼,客戶ID,客戶姓名,本次外撥目的,TTS1,變數1,變數2,變數3,TTS2,TTS3,TTS4,SMS1,SMS2,SMS3,SMS4,SMS5,SMSDefault\n";
        Files.write(reportPath.toPath(), header.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // 執行 SQL 提取資料（FLAG='2' 表示已獲取且尚未處理完成的外撥候選名單）
        // 使用 sqlcmd 的 -s ',' 以逗號分隔欄位，直接輸出 CSV 格式資料列
        String dataQuery = "set nocount on;\n" +
                "select RTRIM(PHONE) as 手機號碼,\n" +
                "       RTRIM(ID) as 客戶ID,\n" +
                "       RTRIM(NAME) as 客戶姓名,\n" +
                "       '6日未回覆' as 本次外撥目的,\n" +
                "       '' as TTS1,\n" +
                "       'NA' as 變數1,\n" +
                "       'NA' as 變數2,\n" +
                "       'NA' as 變數3,\n" +
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
                "where FLAG='2' AND PHONE IS NOT NULL AND PHONE <> '';";

        String sqlData = executeSqlCmd(ip, port, database, user, password, ",", dataQuery);
        Files.write(reportPath.toPath(), sqlData.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        
        String cleanFile = reportFile + "_CLEAN";
        try {
            // 用 sed 清除 CSV 中的多餘空白 (s/ *//g → 刪除所有空白字元)
            executeCmd(String.format("cat %s/%s | sed 's/ *//g' > %s/%s", REPORTS_DIR, reportFile, REPORTS_DIR, cleanFile));
            executeCmd(String.format("mv %s/%s %s/%s", REPORTS_DIR, cleanFile, REPORTS_DIR, reportFile));
        }
        catch(Exception e) {
            // mv 失敗時刪除殘留的 _CLEAN 檔，避免垃圾累積
            try { Files.deleteIfExists(Paths.get(cleanFile)); } catch (IOException ignored) {}
            throw e;
        }

        // -----------------------------------------------------------------
        // 步驟 5：透過 FTP 上傳報表至合作廠商
        //   a. 讀取 ftp.ini（每列為 Base64 編碼的帳號/密碼）
        //   b. 透過 decode.sh 解碼，取得 FTP 帳號與密碼
        //   c. 執行 ftp 指令，上傳檔案至 /upload/A0001527/MVP_2_AUC/
        // -----------------------------------------------------------------
        processFtpUpload(reportFile);

        } finally {
            // 無論成功或失敗，一律清理解密設定檔（避免明碼密碼殘留）
            try { Files.deleteIfExists(Paths.get(HOME + "/mvpsqlserver.conf")); }
            catch (IOException e) { log.warn("Failed to delete mvpsqlserver.conf", e); }
        }
    }
    
    /**
     * 透過 FTP 上傳報表至合作廠商 FTP 伺服器
     *
     * 【實作細節】
     *   - 讀取 ftp.ini，透過 decode.sh 解碼第1列（帳號）與第2列（密碼）
     *   - 使用 ProcessBuilder 啟動 ftp 程序（-p passive mode, -n 不自動登入）
     *   - 透過 stdin 輸入 ftp 指令序列（USER/PASS 認證 → cd/lcd → put → quit）
     *   - 等待程序結束並記錄 exitCode
     *
     * @param reportFile 要上傳的 CSV 報表名稱
     *                   範例: AI_CALLING_20260618.csv
     */
    private void processFtpUpload(String reportFile) throws Exception {
        File ftpIni = new File(SH_DIR + "/ftp.ini");
        List<String> lines = Files.readAllLines(ftpIni.toPath());

        String ftpUser = "";
        String ftpPass = "";
        int count = 0;
        // 解碼 ftp.ini 中的帳號（第1列）與密碼（第2列）
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String decoded = executeCmdWithOutput(String.format("sh %s/decode.sh %s", SH_DIR, line)).trim();
            if (count == 0) {
                ftpUser = decoded;
            } else if (count == 1) {
                ftpPass = decoded;
                break;
            }
            count++;
        }
        
        if (StringUtils.isNotEmpty(ftpUser) && StringUtils.isNotEmpty(ftpPass)) {

            File localFile = new File(REPORTS_DIR, reportFile);
            if (!localFile.isFile()) {
                log.error("Local file not found: {}", localFile.getAbsolutePath());
            } else {

                ProcessBuilder pb = new ProcessBuilder("ftp", "-p", "-n", FTP_IP);
                pb.redirectErrorStream(true);
                Process process = null;

                try {
                    process = pb.start();

                    // 透過 stdin 餵入 ftp 指令
                    try (BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(process.getOutputStream()))) {
                        writer.write("quote USER " + ftpUser + "\n");
                        writer.write("quote PASS " + ftpPass + "\n");
                        writer.write("lcd " + REPORTS_DIR + "\n");
                        writer.write("cd /upload/A0001527/MVP_2_AUC\n");
                        writer.write("put " + reportFile + "\n");
                        writer.write("quit\n");
                        writer.flush();
                    }

                    // 同步讀取 stdout，避免 pipe buffer 爆滿阻塞子程序
                    StringBuilder outputBuilder = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            outputBuilder.append(line).append("\n");
                        }
                    }

                    boolean finished = process.waitFor(60, TimeUnit.SECONDS);
                    if (!finished) {
                        log.error("FTP upload timed out after 60s, killing process");
                        process.destroyForcibly();
                    } else {
                        int exitCode = process.exitValue();
                        log.info("FTP process exited with code: {}", exitCode);
                        if (exitCode != 0) {
                            log.error("FTP upload failed!\n{}", outputBuilder);
                        }
                    }

                    log.debug("FTP output:\n{}", outputBuilder);

                } catch (IOException | InterruptedException e) {
                    Throwable cause = (e instanceof InterruptedException) ? e : e;
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    log.error("FTP upload error", e);
                    throw new Exception("FTP upload failed", e);
                } finally {
                    if (process != null && process.isAlive()) {
                        process.destroyForcibly();
                    }
                }
                
            }
        }
        else {
        	 log.error("FTP credentials are empty, cannot upload. ftpUser={}, ftpPass={}", StringUtils.isEmpty(ftpUser) ? "(empty)" : "***", StringUtils.isEmpty(ftpPass) ? "(empty)" : "***");
        }
        
    }

    /**
     * 讀取 SQL 設定檔，轉為 key-value 配對
     *
     * @param path 設定檔完整路徑（明碼版 mvpsqlserver.conf）
     * @return Map，key 為設定名稱，value 為設定值
     *         範例: {ip=192.168.1.10, port=1433, database=EMAIL, sep=|, user=sa, password=xxx}
     * @throws IOException 檔案讀取失敗
     */
    private Map<String, String> readSqlConfig(String path) throws IOException {
        Map<String, String> conf = new HashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(path));
        for (String line : lines) {
            if (line.contains("=")) {
                // 以第一個 = 為界線分割（避免密碼中包含 = 被切斷）
                String[] parts = line.split("=", 2);
                conf.put(parts[0].trim(), parts[1].trim());
            }
        }
        return conf;
    }

    /**
     * 執行 sqlcmd 指令查詢 SQL Server
     *
     * @param ip      SQL Server IP
     * @param port    SQL Server Port
     * @param db      資料庫名稱
     * @param user    使用者名稱
     * @param pass    密碼
     * @param sep     欄位分隔符號（CSV 導出用 ','，master 查詢用 '|'）
     * @param query   T-SQL 查詢指令
     * @return sqlcmd 的 stdout 輸出結果
     * @throws Exception 指令執行失敗（exitCode != 0）
     */
    private String executeSqlCmd(String ip, String port, String db, String user, String pass, String sep, String query) throws Exception {
        // sqlcmd 參數說明：
        //   -S      → 伺服器位址
        //   -h -1   → 關閉列計數器
        //   -W      → 去除結尾空白
        //   -j      → 去除訊息前導空白
        //   -s      → 欄位分隔符號
        //   -d      → 資料庫名稱
        //   -U/-P   → 使用者/密碼
        //   -I      → 允許變數使用大寫
        //   -Q      → 執行指令後結束
        String cmd = String.format(
            "sqlcmd -S %s,%s -h -1 -W -j -s '%s' -d %s -U %s -P %s -I -Q \"%s\"",
            ip, port, sep, db, user, pass, query
        );
        return executeCmdWithOutput(cmd);
    }

    /**
     * 執行 shell 指令（不須回傳值）
     *
     * @param cmd shell 指令字串
     * @throws Exception 指令執行失敗（exitCode != 0）
     */
    private void executeCmd(String cmd) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
        // 讀取 stdout 避免程序阻塞（buffer 滿會導致子程序卡住）
        drainStream(process.getInputStream());
        // 讀取 stderr 避免程序阻塞
        drainStream(process.getErrorStream());
        
        boolean finished = process.waitFor(300, TimeUnit.SECONDS);  // 5分鐘上限
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Command timed out: " + cmd);
        }
        int exitCode = process.exitValue();
        
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode + ": " + cmd);
        }
    }

    /**
     * 執行 shell 指令（須回傳 stdout 內容）
     *
     * @param cmd shell 指令字串
     * @return 指令 stdout 輸出結果
     * @throws Exception 指令執行失敗（exitCode != 0）
     */
    private String executeCmdWithOutput(String cmd) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
        StringBuilder output = new StringBuilder();
        // 讀取 stdout 並累積輸出結果
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        // 讀取 stderr 避免程序阻塞（內容丟棄）
        drainStream(process.getErrorStream());
        
        boolean finished = process.waitFor(300, TimeUnit.SECONDS);  // 5分鐘上限
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Command timed out: " + cmd);
        }
        int exitCode = process.exitValue();
        
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode + ": " + cmd);
        }
        return output.toString();
    }

    /**
     * 讀取 Stream 並丟棄內容，避免程序因 stdout/stderr buffer 滿而阻塞。
     *
     * 【背景說明】
     * Unix 的 pipe buffer 通常為 64KB。若子程序寫入大量 stdout/stderr
     * 而父程序不讀取，子程序會被 block（寫入阻塞），導致整個流程卡死。
     * 此方法確保即使不關心輸出內容，仍能清空 buffer。
     *
     * @param inputStream 要清空的 InputStream（stdout 或 stderr）
     */
    private void drainStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            while (reader.read() != -1) {
                // 逐字節讀取並丟棄，清空 buffer
            }
        } catch (IOException e) {
            // 忽略 stream 讀取錯誤（此為輔助方法，不影響主流程）
        }
    }

    /**
     * 自訂例外類別：用於預期性跳過執行（非錯誤情況）
     *
     * 【使用時機】
     *   - 當前機器非 master 節點
     *
     * 【與 RuntimeException 的差異】
     *   - SkipExecutionException → 記錄 INFO 等級，不觸發告警
     *   - RuntimeException      → 記錄 ERROR 等級，需介入處理
     */
    private static class SkipExecutionException extends Exception {
        public SkipExecutionException(String message) {
            super(message);
        }
    }
}
