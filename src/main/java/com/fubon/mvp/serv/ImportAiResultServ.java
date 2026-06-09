package com.fubon.mvp.serv;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.StringUtil;
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
 * 完全模擬 import_ai_result.sh 的執行邏輯與環境依賴
 * 每日凌晨 2:00 自動觸發，執行以下流程：
 *   1. 解密密碼檔，取得 SQL Server 連線資訊
 *   2. 查詢主節點，確認當前機器是否為 master（僅 master 執行）
 *   3. 從 FTP 下載昨日的 AI 外撥結果報表 CallList_YYYYMMDD.xlsx
 *   4. 驗證檔案是否存在且為新檔（< 12 小時內修改）
 *   5. 直接呼叫 ImportAiResultToProcessServ 解析 Excel 並更新資料庫
 *   6. 處理完成後，將檔案移至 /processed/YYYYMM/ 備份目錄
 *
 * 【執行排程】
 *   cron = "0 0 2 * * ?"  → 台灣時間每日凌晨 2:00
 *
 * 【依賴環境】
 *   - openssl     → 解密 mvpsqlserver.conf.enc
 *   - sqlcmd      → 查詢 SQL Server（master 節點確認）
 *   - ftp         → 從 FTP 下載 AI 結果報表
 *   - decode.sh   → 解碼 ftp.ini 中的使用者/密碼
 *   - ftp.ini     → 存放編碼後的 FTP 帳號密碼（第1列=帳號, 第2列=密碼）
 *
 * 【資料流程】
 *   FTP 伺服器 (/download/CallList_YYYYMMDD.xlsx)
 *     ↓ FTP 下載
 *   /home/mvpadm/download/CallList_YYYYMMDD.xlsx
 *     ↓ Excel 解析 + DB 更新
 *   /home/mvpadm/processed/YYYYMM/CallList_YYYYMMDD.xlsx
 *
 * 【例外處理】
 *   - SkipExecutionException : 預期性跳過（非 master、檔案不存在、檔案過舊）
 *                                → 記錄 INFO 等級日誌，不視為錯誤
 *   - RuntimeException       : 非預期性錯誤（FTP 失敗、指令執行失敗）
 *                                → 記錄 ERROR 等級日誌，需介入處理
 * ============================================================================
 */
@Service
public class ImportAiResultServ {
    private static final Logger log = LoggerFactory.getLogger(ImportAiResultServ.class);

    // -----------------------------------------------------------------
    // 外部設定（由 application.properties 注入）
    //   範例: ImportAiResultServ.FTP_IP=192.168.1.100
    // -----------------------------------------------------------------
    @Value("${ImportAiResultServ.FTP_IP}")
    private String FTP_IP;

    // -----------------------------------------------------------------
    // 注入 Excel 處理服務（取代原 curl 呼叫 REST API 的做法）
    //   優點：無 HTTP 開銷、無 curl 外部依賴、例外可追溯
    // -----------------------------------------------------------------
    @Autowired
    private ImportAiResultToProcessServ importAiResultProcess;

    // -----------------------------------------------------------------
    // 環境路徑定義
    //   HOME          → mvpadm 使用者的家目錄（通常為 /home/mvpadm）
    //   DOWNLOAD_DIR  → FTP 下載暫存區
    //   SH_DIR        → shell 指令腳本目錄（含 decode.sh、ftp.ini）
    //   LOGS_DIR      → 執行日誌存放目錄
    // -----------------------------------------------------------------
    @Value("${mvp.home.dir:/home/mvpadm}") 
    private String HOME;
    
    //TODO 要處理的檔案下載的路徑
    private final String DOWNLOAD_DIR = "/home/mvpadm/download";
    
    //TODO 處理後存放檔案的目錄
    private final String PROCESSED_DIR = "/home/mvpadm/reports";
    
    //TODO 指定只要處理的檔案名稱 前綴字眼
    private final String CALLLIST_FILENAME_PREFIX="CallList_";

    private final String SH_DIR = "/home/mvpadm/sh";
    private final String LOG_DIR = "/home/mvpadm/logs";
    
    // =================================================================
    // 【排程入口】每日凌晨 2:00 觸發
    // =================================================================
    //TODO 排程執行時間
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Taipei")
    public void execute() {
        log.info("Starting AI Result Import process...");
        try {
            // 執行核心流程
            runShellLikeProcess();
            log.info("AI Result Import process completed successfully.");
        } catch (SkipExecutionException e) {
            // 預期性跳過（如：非 master 節點、檔案不存在、檔案過舊）
            // 記錄 INFO 等級，不視為系統錯誤
            log.info("AI Result Import process skipped: {}", e.getMessage());
        } catch (Exception e) {
            // 非預期性錯誤（如：FTP 連線失敗、DB 連線失敗）
            // 記錄 ERROR 等級，需監控/告警
            log.error("Critical error during AI Result Import process: ", e);
        }
    }

    // =================================================================
    // 【核心流程】模擬 shell 腳本 import_ai_result.sh 的完整步驟
    // =================================================================
    private void runShellLikeProcess() throws Exception {
        try {

        // -----------------------------------------------------------------
        // 步驟 1：日期設定
        //   fileDate    → 昨日的日期（YYYYMMDD），用於命名檔案
        // -----------------------------------------------------------------
        log.info("步驟1 下載要處置的檔案存放 日期命名設定");

        LocalDate today = LocalDate.now();
        String fileDate = today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 組合檔案名稱與路徑
        String aiFilename = CALLLIST_FILENAME_PREFIX + fileDate + ".xlsx"; // 目標檔案：CallList_20260618.xlsx
        String localFile = DOWNLOAD_DIR + "/" + aiFilename; // 本地路徑：/home/mvpadm/download/CallList_20260618.xlsx
        String logFile = LOG_DIR + "/AI_RESULT_" + fileDate + ".log"; // 日誌路徑：/home/mvpadm/logs/AI_RESULT_20260618.log

        log.info("下載要處置的檔案存放於 {}", localFile);

        // -----------------------------------------------------------------
        // 步驟 2：解密 SQL 連線設定檔
        //   使用 RSA 私鑰解密 mvpsqlserver.conf.enc，產生明碼 mvpsqlserver.conf
        //   此檔包含：ip, port, database, sep, user, password
        // -----------------------------------------------------------------
        log.info("步驟2 解密 SQL 連線設定檔");
        executeCmd(String.format("openssl rsautl -decrypt -inkey %s/rsa.key -in %s/mvpsqlserver.conf.enc -out %s/mvpsqlserver.conf", HOME, HOME, HOME));
        Map<String, String> sqlConf = readSqlConfig(HOME + "/mvpsqlserver.conf");
        String ip = sqlConf.get("ip");
        String port = sqlConf.get("port");
        String database = sqlConf.get("database");
        String separator = sqlConf.get("sep");
        String separatorSafe = sqlConf.getOrDefault("sep", "|");
        String user = sqlConf.get("user");
        String password = sqlConf.get("password");

        // -----------------------------------------------------------------
        // 步驟 3：確認當前機器是否為 master 節點
        //   查詢 emailhos 資料表，找出 main='1' 的主機名稱
        //   若查詢失敗，等待 10 秒後重試一次
        //   若非 master，刪除設定檔後跳出（不視為錯誤）
        // -----------------------------------------------------------------
        log.info("步驟3 確認當前機器是否為 master 節點");
        String masterQuery = "set nocount on;select host_name from emailhos where main='1';";
        String master = executeSqlCmd(ip, port, database, user, password, separatorSafe, masterQuery).trim();
        // 若首次查詢返回空值，等待 10 秒後重試
        if (master.isEmpty()) {
            log.info("查詢失敗, 等待 10 秒後重試一次...");
            Thread.sleep(10000);
            master = executeSqlCmd(ip, port, database, user, password, separatorSafe, masterQuery).trim();
        }
        // 取得當前機器的主機名稱並比較
        String runMachine = java.net.InetAddress.getLocalHost().getHostName();
        if (!master.equals(runMachine)) {
            log.info("非master節點 無須執行...");
            // 清理解密的設定檔（避免明碼密碼殘留）
            throw new SkipExecutionException("Not master node");
        }

        // -----------------------------------------------------------------
        // 步驟 4：從 FTP 下載 AI 結果報表
        //   a. 讀取 ftp.ini（每列為 Base64 編碼的帳號/密碼）
        //   b. 透過 decode.sh 解碼，取得 FTP 帳號與密碼
        //   c. 執行 ftp 指令，下載檔案至 DOWNLOAD_DIR
        //   d. 檢查 FTP 回傳碼，失敗則拋出例外
        // -----------------------------------------------------------------
        log.info("步驟4 從 FTP下載AI結果報表");
        File ftpIni = new File(SH_DIR + "/ftp.ini");
        log.info("ftp.ini 路徑 "+ftpIni.getAbsolutePath());
        List<String> ftpLines = Files.readAllLines(ftpIni.toPath());
        String ftpUser = "";
        String ftpPass = "";
        int count = 0;
        // 解碼 ftp.ini 中的帳號（第1列）與密碼（第2列）
        for (String line : ftpLines) {
            if (line.trim().isEmpty()) continue;
            String decoded = executeCmdWithOutput(String.format("sh %s/decode.sh %s", SH_DIR, line)).trim();
            if (count == 0) ftpUser = decoded;
            else ftpPass = decoded;
            count++;
        }
        // 僅在成功讀取帳號密碼後才執行 FTP 下載
        if (StringUtil.isNotBlank(ftpUser) && StringUtils.isNotBlank(ftpPass)) {
            log.info("連線FTP伺服器下載檔案 "+aiFilename+" 暫存於"+DOWNLOAD_DIR);
            // 確保下載目錄存在
            Files.createDirectories(Paths.get(DOWNLOAD_DIR));
            // 啟動 ftp 程序（-p 表示 passive mode，-n 表示不自動登入）
            ProcessBuilder pb = new ProcessBuilder("ftp", "-p", "-n", FTP_IP);
            pb.redirectErrorStream(true); // 合併 stdout 與 stderr
            Process process = pb.start();
            log.info("透過 stdin 輸入 ftp 指令序列");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                writer.write("quote USER " + ftpUser + "\n");    // 帳號認證
                writer.write("quote PASS " + ftpPass + "\n");    // 密碼認證
                writer.write("cd /download\n");                  // 切換至遠端下載目錄
                writer.write("lcd " + DOWNLOAD_DIR + "\n");      // 切換至本地下載目錄
                writer.write("get " + aiFilename + "\n");        // 下載目標檔案
                writer.write("quit\n");                          // 結束連線
                writer.flush();
            }
            log.info("讀取 ftp 程序的 stdout 輸出 並記錄到日誌");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String ftpLine;
                StringBuilder ftpOutput = new StringBuilder();
                while ((ftpLine = reader.readLine()) != null) {
                    ftpOutput.append(ftpLine).append("\n");
                }
            }
            // 等待程序結束並取得回傳碼
            int exitCode = process.waitFor();
            log.info("FTP 下載結束碼 {}", exitCode);
            //檢查 FTP 是否成功，失敗則提前結束
            if (exitCode != 0) {
                String ftpError = "ERROR: FTP下載失敗 (FTP下載檔案異常)" + exitCode;
                log.error(ftpError);
                throw new RuntimeException(ftpError);
            }
        } else {
            String ftpWarning = "處置異常 ftp.ini 無法取得足夠帳號密碼, 跳過FTP下載";
            log.warn(ftpWarning);
        }
        
        // -----------------------------------------------------------------
        // 步驟 5
        // -----------------------------------------------------------------
        // 檢查檔案是否存在
        if (!Files.exists(Paths.get(localFile))) {
            throw new SkipExecutionException("要處置的檔案不存在 " + localFile);
        }
        
        // -----------------------------------------------------------------
        // 步驟 6：呼叫 Excel 處理服務
        //   直接注入 ImportAiResultToProcessServ，取代 curl 呼叫 REST API
        //   優點：無 HTTP 開銷、無 curl 外部依賴、例外可追溯
        // -----------------------------------------------------------------
        log.info("解析下載檔案 "+localFile+" 並更新相關資料");
        try {
            importAiResultProcess.processAiResultReport(localFile);
        } catch (Exception ex) {
            log.error("解析下載檔案 發生異常 {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to process Excel", ex);
        }

        // -----------------------------------------------------------------
        // 步驟 7：備份已處理的檔案並清理
        //   將原始檔案移至 /processed/YYYYMM/ 目錄（同 filesystem 下為 rename 操作）
        //   若目標檔案已存在則覆蓋（REPLACE_EXISTING）
        //   範例: /home/mvpadm/download/CallList_20260618.xlsx → /home/mvpadm/processed/202606/CallList_20260618.xlsx
        // -----------------------------------------------------------------
        
        String backupDir = PROCESSED_DIR;
        Files.createDirectories(Paths.get(backupDir));

        Path source = Paths.get(localFile);
        Path target = Paths.get(backupDir, aiFilename);
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("下載檔案處置完畢後 備份至" + target);

        } finally {
            // 無論成功或失敗，一律清理解密設定檔（避免明碼密碼殘留）
            try { Files.deleteIfExists(Paths.get(HOME + "/mvpsqlserver.conf")); }
            catch (IOException e) { log.warn("刪除 mvpsqlserver.conf 異常", e); }
        }
    }

    /**
     * 讀取 SQL 設定檔，轉為 key-value 配對
     * @param path 設定檔完整路徑（明碼版 mvpsqlserver.conf）
     * @return Map，key 為設定名稱，value 為設定值
     *         範例: {ip=192.168.1.10, port=1433, database=EMAIL, sep=|, user=sa, password=xxx}
     * @throws IOException 檔案讀取失敗
     */
    private Map<String, String> readSqlConfig(String path) throws IOException {
    	log.info("讀取 SQL 設定檔，轉為 key-value 配對");
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
     * @param ip      SQL Server IP
     * @param port    SQL Server Port
     * @param db      資料庫名稱
     * @param user    使用者名稱
     * @param pass    密碼
     * @param sep     欄位分隔符號
     * @param query   T-SQL 查詢指令
     * @return sqlcmd 的 stdout 輸出結果
     */
    private String executeSqlCmd(String ip, String port, String db, String user, String pass, String sep, String query) throws Exception {
    	log.info(query);
    	
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
     * @param cmd shell 指令字串
     * @throws Exception 指令執行失敗（exitCode != 0）
     */
    private void executeCmd(String cmd) throws Exception {
    	log.info("執行 shell 指令（不須回傳值） "+cmd);
    	
        Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
        // 讀取 stdout 避免程序阻塞（buffer 滿會導致子程序卡住）
        drainStream(process.getInputStream());
        // 讀取 stderr 避免程序阻塞
        drainStream(process.getErrorStream());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode + ": " + cmd);
        }
    }

    /**
     * 執行 shell 指令（須回傳 stdout 內容）
     * @param cmd shell 指令字串
     * @return 指令 stdout 輸出結果
     * @throws Exception 指令執行失敗（exitCode != 0）
     */
    private String executeCmdWithOutput(String cmd) throws Exception {
    	log.info(" 執行 shell 指令（須回傳 stdout 內容）"+cmd);
    	
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
        int exitCode = process.waitFor();
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
     *   - 檔案不存在（FTP 尚未上傳）
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
