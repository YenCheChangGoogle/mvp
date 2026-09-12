package com.fubon.mvp.serv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailAiOutDao;
import com.fubon.mvp.dao.EmailHostDao;
import com.fubon.mvp.data.EmailAiOut;

/**
 * ============================================================================
 * 富邦MVP - ImportAiResultServ (AI外撥結果報表導入服務)
 * ============================================================================
 * @author 張晏哲
 * @category 服務類
 *
 * 【功能概述】
 * 每日凌晨 2:00 自動觸發，執行以下流程：
 *   1. 透過 EmailHostDao.isMain() 確認當前機器是否為 master（僅 master 執行）
 *   2. 從 FTP 下載昨日的 AI 外撥結果報表 CallList_YYYYMMDD.xlsx
 *   3. 驗證檔案是否存在
 *   4. 直接呼叫 ImportAiResultToProcessServ 解析 Excel 並更新資料庫
 *   5. 將 Excel 原始外撥結果整筆備份至 EMAILAIOUT
 *   6. 處理完成後，將檔案移至備份目錄
 *
 * 【改版說明 2026/09 — 移除 RSA 解密與手動 JDBC 連線】
 *   原本 master 節點判斷是逐行翻譯自 shell script 的做法：自行用 RSA 私鑰
 *   解密 mvpsqlserver.conf.enc 取得帳密，再用 DriverManager 手動連線查詢
 *   emailhos 表，跟 Spring 既有的 mvpDataSource 連線池完全脫鉤。
 *   確認正式機 mvpsqlserver.conf.enc 解密出的資料庫與 application.properties-prod
 *   是同一套之後，已改為直接注入 Spring 既有的 EmailHostDao.isMain()（其他服務
 *   如 ImportAiResultToProcessServ 也是用這個方法判斷 master），RSA 解密、
 *   BouncyCastle風格的PKCS包裝、手動 JDBC 連線、明碼密碼暫存檔等程式碼已全數移除。
 *
 * 【其餘改寫重點 — 移除所有平台依賴，完全使用 Java 實現】
 *   原依賴         → Java 替代方案
 *   ftp 命令     → org.apache.commons.net.ftp.FTPClient
 *   decode.sh    → java.util.Base64 + AES(解 ftp.ini 帳密)
 *   /bin/sh      → 全部移除，無 shell 呼叫
 *
 * 【新增 Maven 依賴】
 *   <!-- FTP Client -->
 *   <dependency>
 *     <groupId>commons-net</groupId>
 *     <artifactId>commons-net</artifactId>
 *     <version>3.10.0</version>
 *   </dependency>
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

    //-----------------------------------------------------------------
    //外部設定（由 application.properties 注入）
    //-----------------------------------------------------------------
    @Value("${ImportAiResultServ.FTP_IP}")
    private String FTP_IP;

    @Autowired
    private ImportAiResultToProcessServ importAiResultProcess;

    @Autowired
    private EmailAiOutDao aiOutDao;   //EMAILAIOUT 的 CRUD 操作(原始外撥結果備份)

    //主機節點判斷（取代 RSA解密+DriverManager手動查詢master的方式）
    @Autowired
    private EmailHostDao hostDao;

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

    //=================================================================
    //【排程入口】每日凌晨 2:00 觸發
    //=================================================================
   
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

    //=================================================================
    //【核心流程】
    //=================================================================
    private void runProcess() throws Exception {

        //-----------------------------------------------------------------
        //步驟 1：日期設定
        //-----------------------------------------------------------------
        log.info("步驟1 日期命名設定");
        LocalDate today = LocalDate.now();
        String fileDate   = today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String aiFilename = CALLLIST_FILENAME_PREFIX + fileDate + ".xlsx";
        String localFile  = DOWNLOAD_DIR + "/" + aiFilename;
        log.info("處理目標檔案：{}", localFile);

        //-----------------------------------------------------------------
        //步驟 2：確認當前機器是否為 master 節點
        //  原始作法：RSA解密取得連線資訊 → DriverManager查詢emailhos → 與本機hostname比對
        //  現在作法：直接用 Spring 既有的 EmailHostDao.isMain()
        //           （底層透過 mvpDataSource 連線池查詢 emailhos，且每 3 秒背景自動刷新）
        //-----------------------------------------------------------------
        log.info("步驟2 確認 master 節點");
        if (!this.hostDao.isMain()) {
            throw new SkipExecutionException("Not master node");
        }

        //-----------------------------------------------------------------
        //步驟 3：從 FTP 下載 AI 結果報表
        //  原：ftp -p -n 命令 + decode.sh (shell 呼叫)
        //  現：FTPClient (passive mode) + Base64.getDecoder()
        //-----------------------------------------------------------------
        log.info("步驟3 FTP 下載 {}", aiFilename);
        
        File ftpIni = new File(SH_DIR, "ftp2.ini");
        if (!ftpIni.exists()) {
            log.error("FTP ini file not found: {}", ftpIni.getAbsolutePath());
            return;
        }

        List<String> lines = Files.readAllLines(ftpIni.toPath(), StandardCharsets.UTF_8);
        String ftpUser = "";
        String ftpPass = "";

        //解碼 ftp.ini 中的帳號（第1列）與密碼（第2列）
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

        //-----------------------------------------------------------------
        //步驟 4：確認檔案存在
        //-----------------------------------------------------------------
        if (!Files.exists(Paths.get(localFile))) {
            throw new SkipExecutionException("要處置的檔案不存在：" + localFile);
        }
        
        /*
        //檢查檔案是否過期（12小時 = 43200秒）
        long fileAgeSeconds = (System.currentTimeMillis() / 1000)
            - Files.getLastModifiedTime(Paths.get(localFile)).to(java.util.concurrent.TimeUnit.SECONDS);
        if (fileAgeSeconds > 43200) {
            log.warn("檔案已過期（{} 秒前修改），跳過避免處理舊資料：{}", fileAgeSeconds, localFile);
            throw new SkipExecutionException("File too old: " + localFile);
        }
        log.info("檔案時效確認：{} 秒前修改", fileAgeSeconds);
        */

        //-----------------------------------------------------------------
        //步驟 5：呼叫 Excel 處理服務
        //-----------------------------------------------------------------
        log.info("步驟5 解析並更新資料：{}", localFile);
        try {
            importAiResultProcess.processAiResultReport(localFile);
        } catch (Exception ex) {
            log.error("解析下載檔案發生異常：{}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to process Excel", ex);
        }

        //-----------------------------------------------------------------
        //步驟 5.5：將 Excel 原始外撥結果整筆備份至 EMAILAIOUT
        //  ★ best-effort：僅記錄 log，不拋出例外，不影響主流程(下載/搬檔/排程)
        //-----------------------------------------------------------------
        log.info("步驟5.5 備份原始外撥結果至 EMAILAIOUT：{}", localFile);
        try {
            importRawToEmailAiOut(localFile);
        } catch (Exception ex) {
            log.error("備份原始外撥結果至 EMAILAIOUT 發生異常：{}", ex.getMessage(), ex);
        }

        //-----------------------------------------------------------------
        //步驟 6：備份已處理的檔案
        //-----------------------------------------------------------------
        Files.createDirectories(Paths.get(PROCESSED_DIR));
        Path source = Paths.get(localFile);
        Path target = Paths.get(PROCESSED_DIR, aiFilename);
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("備份完成：{}", target);
    }

    //=================================================================
    //【步驟 4 實作】FTP 下載 + ftp.ini 解碼
    //=================================================================

    /**
     * 解碼 ftp.ini 中的帳號/密碼行。
     * 對應原 decode.sh 的解碼邏輯（預設為 Base64 UTF-8）。
     *
     * @param encodedLine ftp.ini 中的一行原始文字
     * @return 解碼後的明文字串
     */
    //private String decodeFtpCredential(String encodedLine) {
    //   return new String(Base64.getDecoder().decode(encodedLine), StandardCharsets.UTF_8);
    //}

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

            ftp.enterLocalPassiveMode();          //對應 ftp -p (passive mode)
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

    //=================================================================
    //【步驟 6.5 實作】Excel 原始外撥結果 → 整筆備份至 EMAILAIOUT
    //=================================================================

    /**
     * 解析 Excel 檔案，將每一列原始資料整筆存入 EMAILAIOUT，做為原始外撥結果的備份保存。
     * 與 {@link ImportAiResultToProcessServ#processAiResultReport(String)} 的差異：
     *   該方法僅解析 6 個關鍵欄位，用來驅動 EMAILMAS/EMAILDTL/EMAILIMG 的流程狀態機；
     *   本方法則是「整列」原封不動存檔，純粹做為稽核/備查用途，兩者互不影響、互不依賴。
     *
     * 【欄位對應】(0-based，A~Y 共 25 欄，對應 CallList Excel 與 EMAILAIOUT 資料表全部欄位)
     *   0=CHNL  1=ID  2=NAME  3=PURPOSE  4=PHONE  5=STATUS  6=RETRY  7=DATETIME(★主鍵)
     *   8=INTENT  9=HANGUP  10=CHOICE  11=UUID
     *   12=TTS1  13=VAR1  14=TTS2  15=VAR2  16=TTS3  17=VAR3  18=TTS4
     *   19=SMS1  20=SMS2  21=SMS3  22=SMS4  23=SMS5  24=SMSDefault
     *
     * 【例外處理策略】
     *   - 單列解析/存檔失敗（DATETIME 空值或格式錯誤、必填欄位缺漏、PK 重複等）
     *     → 僅記錄 WARN 並跳過該列，不中斷整批匯入
     *   - 整份檔案讀取失敗（檔案損毀、找不到檔案等）
     *     → 記錄 ERROR，方法直接返回（呼叫端已將本方法包在 try-catch 中，屬 best-effort 備份）
     *
     * @param excelFilePath Excel 檔案完整路徑
     */
    private void importRawToEmailAiOut(String excelFilePath) {

        int successCount = 0;
        int skipCount = 0;

        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {

                //第一列是標題，直接略過
                if (row.getRowNum() == 0) {
                    continue;
                }

                try {
                    EmailAiOut entity = new EmailAiOut();

                    entity.setChannel(getRawCellString(row.getCell(0)));
                    entity.setIdNo(getRawCellString(row.getCell(1)));
                    entity.setName(getRawCellString(row.getCell(2)));
                    entity.setPurpose(getRawCellString(row.getCell(3)));
                    entity.setPhone(getRawCellString(row.getCell(4)));
                    entity.setStatus(getRawCellString(row.getCell(5)));
                    entity.setRetry(getRawCellString(row.getCell(6)));

                    //★ DATETIME 為主鍵，不可為空
                    LocalDateTime dateTime = getRawCellDateTime(row.getCell(7));
                    if (dateTime == null) {
                        log.warn("EMAILAIOUT備份: 第{}列 DATETIME 欄位為空或無法解析，跳過此列", row.getRowNum() + 1);
                        skipCount++;
                        continue;
                    }
                    entity.setDateTime(dateTime);

                    entity.setIntent(getRawCellString(row.getCell(8)));
                    entity.setHangup(getRawCellString(row.getCell(9)));
                    entity.setChoice(getRawCellString(row.getCell(10)));
                    entity.setUuid(getRawCellString(row.getCell(11)));

                    entity.setTts1(getRawCellString(row.getCell(12)));
                    entity.setVar1(getRawCellString(row.getCell(13)));
                    entity.setTts2(getRawCellString(row.getCell(14)));
                    entity.setVar2(getRawCellString(row.getCell(15)));
                    entity.setTts3(getRawCellString(row.getCell(16)));
                    entity.setVar3(getRawCellString(row.getCell(17)));
                    entity.setTts4(getRawCellString(row.getCell(18)));

                    entity.setSms1(getRawCellString(row.getCell(19)));
                    entity.setSms2(getRawCellString(row.getCell(20)));
                    entity.setSms3(getRawCellString(row.getCell(21)));
                    entity.setSms4(getRawCellString(row.getCell(22)));
                    entity.setSms5(getRawCellString(row.getCell(23)));
                    entity.setSmsDefault(getRawCellString(row.getCell(24)));

                    //★ 必填欄位（CHNL/ID/NAME/PURPOSE/PHONE）為空則跳過，避免違反 NOT NULL 約束
                    if (isBlank(entity.getChannel()) || isBlank(entity.getIdNo())
                            || isBlank(entity.getName()) || isBlank(entity.getPurpose())
                            || isBlank(entity.getPhone())) {
                        log.warn("EMAILAIOUT備份: 第{}列必填欄位(CHNL/ID/NAME/PURPOSE/PHONE)有缺漏，跳過此列", row.getRowNum() + 1);
                        skipCount++;
                        continue;
                    }

                    Exception saveEx = this.aiOutDao.save(entity);
                    if (saveEx != null) {
                        //常見原因：DATETIME(PK) 重複（同一秒有多筆外撥結果）
                        log.warn("EMAILAIOUT備份: 第{}列存檔失敗(可能是DATETIME主鍵重複): {}", row.getRowNum() + 1, saveEx.getMessage());
                        skipCount++;
                    } else {
                        successCount++;
                    }

                } catch (Exception rowEx) {
                    log.warn("EMAILAIOUT備份: 第{}列解析/存檔發生例外，跳過此列: {}", row.getRowNum() + 1, rowEx.toString());
                    skipCount++;
                }
            }
            //try-with-resources 會自動關閉 fis 與 workbook

        } catch (Exception ex) {
            log.error("EMAILAIOUT備份: 讀取Excel檔案失敗: {}", ex.toString(), ex);
            return;
        }

        log.info("EMAILAIOUT備份完成: 成功={}, 跳過={}", successCount, skipCount);
    }

    /**
     * 取得 Excel Cell 的字串值（支援 STRING/NUMERIC/BOOLEAN/FORMULA），null 安全。
     */
    private String getRawCellString(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double numVal = cell.getNumericCellValue();
                if (numVal == Math.floor(numVal)) {
                    return String.valueOf((long) numVal);
                } else {
                    return String.valueOf(numVal);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (IllegalStateException e) {
                    return cell.getStringCellValue().trim();
                }
            default:
                return null;
        }
    }

    /**
     * 取得 Cell 的日期時間值，支援兩種來源：
     *   1) Excel 原生日期格式 Cell（NUMERIC + isCellDateFormatted）→ 直接轉換
     *   2) 純文字時間字串（如 "2026-09-11 12:00:00"、"20260911120000"）→ 嘗試多種格式解析
     * 解析失敗回傳 null（呼叫端會跳過該列，因為 DATETIME 是 EMAILAIOUT 的主鍵，不可為空）。
     */
    private LocalDateTime getRawCellDateTime(Cell cell) {

        if (cell == null) {
            return null;
        }

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue();
            }
        } catch (Exception ex) {
            //Excel原生日期解析失敗，繼續嘗試以文字格式解析
        }

        String text = getRawCellString(cell);
        if (isBlank(text)) {
            return null;
        }
        text = text.trim();

        String[] patterns = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyyMMddHHmmss",
            "yyyy-MM-dd'T'HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignore) {
                //換下一種格式繼續嘗試
            }
        }

        log.warn("EMAILAIOUT備份: DATETIME欄位無法解析(已嘗試多種格式): {}", text);
        return null;
    }

    /** 字串是否為空白（null 或 trim 後長度為 0） */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    //=================================================================
    //【例外類別】預期性跳過（非錯誤情況）
    //=================================================================

    /**
     * 預期性跳過例外：
     *   - 當前機器非 master 節點
     *   - 檔案不存在（FTP 尚未上傳）
     * 對應 INFO 等級日誌，不觸發告警。
     */
    @SuppressWarnings("serial")
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