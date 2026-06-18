package com.fubon.mvp.serv;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailDao;
import com.fubon.mvp.dao.EmailHostDao;
import com.fubon.mvp.dao.EmailImageDao;
import com.fubon.mvp.data.EmailDetail;
import com.fubon.mvp.data.EmailImage;
import com.fubon.mvp.data.EmailMaster;

/**
 * ============================================================================
 * 富邦MVP - ImportAiResultToProcessServ (AI外撥結果報表 → Excel解析 + 資料庫更新)
 * ============================================================================
 * @author 張晏哲
 * @category 服務類
 *
 * 【功能概述】
 * 被 ImportAiResultServ 或 MvpApiCtrl 呼叫，負責：
 *   1. 確認當前機器為 master 節點
 *   2. 讀取 AI 外撥結果報表 CallList_YYYYMMDD.xlsx
 *   3. 依「客戶選擇」欄位逐筆更新 EMAILMAS（主檔）、EMAILDTL（明細）、EMAILIMG（影像）
 *
 * 【客戶選擇對應表】
 *   選擇 = "1"   → 客戶要求重新發送驗證信
 *               → master.TX_STATUS="01" (收到申請，重新觸發)
 *               → master.STATUS="00"    (處理中)
 *               → master.FLAG="2"       (已獲取)
 *               → 明細兩筆：TX_STATUS=19 (AI回饋) + TX_STATUS=master.TX_STATUS
 *
 *   選擇 = "2"   → 客戶表示無須變更 Email，結束流程
 *               → master.TX_STATUS="00" (全部完成)
 *               → master.STATUS="01"    (全部流程完成)
 *               → master.FLAG="2"       (已獲取)
 *               → 明細兩筆：TX_STATUS=19 (AI回饋) + TX_STATUS=master.TX_STATUS
 *
 *   其他值       → 不處理，僅記錄 log
 *
 * 【Excel 欄位對應】(CallList_YYYYMMDD.xlsx)
 *   A列: 業務別          (不讀取)
 *   B列: 客戶ID          (不讀取)
 *   C列: 客戶姓名        (IDX=2, 讀取)
 *   D列: 本次外撥目的    (不讀取)
 *   E列: 外撥號碼        (IDX=4, 讀取)
 *   F列: 外撥結果        (IDX=5, 讀取)
 *   G列: 外撥次數        (不讀取)
 *   H列: 外撥時間        (IDX=7, 讀取)
 *   I列: Q1意圖          (不讀取)
 *   J列: 掛斷節點        (不讀取)
 *   K列: 客戶選擇        (IDX=10, ★關鍵欄位)
 *   L列: UUID            (IDX=11, ★關鍵欄位)
 *   M列: TTS1~4          (不讀取)
 *   N列: 變數1~4         (不讀取)
 *   O列: SMS1~5+Default  (不讀取)
 *
 * 【資料流程】
 *   Excel 檔案 (/home/mvpadm/download/CallList_YYYYMMDD.xlsx)
 *     ↓ 解析
 *   List<AiResultRow>
 *     ↓ 逐筆比對 EMAILMAS.UUID
 *     ↓ 更新 TX_STATUS、STATUS、FLAG
 *   EMAILMAS + EMAILDTL + EMAILIMG
 *
 * 【例外處理】
 *   - UUID 為空或找不到對應記錄 → 跳過此筆 (WARN)
 *   - Excel 解析失敗 → 回傳 null (ERROR)
 *   - DB 寫入失敗 → 拋出例外，交由上層處理
 * ============================================================================
 */
@Service
public class ImportAiResultToProcessServ {

	private static Logger log = LoggerFactory.getLogger(ImportAiResultToProcessServ.class);

	// -----------------------------------------------------------------
	// DAO 注入
	// -----------------------------------------------------------------
	@Autowired
	private EmailDao dao;           // EMAILMAS + EMAILDTL 的 CRUD 操作
	@Autowired
	private EmailHostDao hostDao;   // 主機節點查詢 (isMain)
	@Autowired
	private EmailImageDao imageDao; // EMAILIMG 的 CRUD 操作

	// -----------------------------------------------------------------
	// Excel 欄位索引常量 (0-based)
	//   對應 CallList_YYYYMMDD.xlsx 的 A~O 列
	//   A=0, B=1, C=2, ..., K=10, L=11
	// -----------------------------------------------------------------
	//private static final int IDX_UUID = 11;           // L列: UUID (★比對 EMAILMAS 用)
	private static final int IDX_IDSN = 1;            // B列: 客戶身分字號
	private static final int IDX_CUST_CHOICE = 10;    // K列: 客戶選擇 (★決定後續處理流程)
	private static final int IDX_TELEPHONE = 4;       // E列: 外撥號碼
	private static final int IDX_CUST_NAME = 2;       // C列: 客戶姓名
	private static final int IDX_CALL_TIME = 7;       // H列: 外撥時間
	private static final int IDX_CALL_RESULT = 5;     // F列: 外撥結果

	// =================================================================
	// 【公開方法】處理 AI 回饋結果報表
	// =================================================================
	/**
	 * 入口方法 — 解析 Excel 並逐筆更新資料庫
	 *
	 * 【執行步驟】
	 *   1. 確認 master 節點
	 *   2. 解析 Excel 檔，取得 List<AiResultRow>
	 *   3. 逐筆比對 EMAILMAS.UUID，依客戶選擇更新資料
	 *   4. 統計處理結果
	 *
	 * @param excelFilePath Excel 檔案完整路徑
	 *                      範例: /home/mvpadm/download/CallList_20260618.xlsx
	 */
	public void processAiResultReport(String excelFilePath) {

		// -----------------------------------------------------------------
		// 步驟 1：確認當前機器為 master 節點
		//   避免多機部署時重複處理同一份 Excel
		// -----------------------------------------------------------------
		if (!this.hostDao.isMain()) {
			log.warn("ImportAiResultProcessServ: 非主節點，跳過處理");
			return;
		}

		log.info("ImportAiResultProcessServ: 開始處理AI回饋結果報表, 檔案=" + excelFilePath);

		// -----------------------------------------------------------------
		// 步驟 2：讀取 Excel 檔案
		//   解析 CallList_YYYYMMDD.xlsx，轉為 List<AiResultRow>
		//   若解析失敗或無資料，提前結束
		// -----------------------------------------------------------------
		List<AiResultRow> aiResultList = this.readExcel(excelFilePath);
		if (aiResultList == null || aiResultList.isEmpty()) {
			log.warn("ImportAiResultProcessServ: Excel 解析失敗或無資料");
			return;
		}

		log.info("ImportAiResultProcessServ: 解析完成，共 " + aiResultList.size() + " 筆資料");

		// -----------------------------------------------------------------
		// 步驟 3：逐筆處理
		//   統計變數：
		//     countChoice1 → 客戶選擇=1 (重新發送驗證信)
		//     countChoice2 → 客戶選擇=2 (結束流程)
		//     countSkip    → 跳過 (UUID 為空、找不到記錄、其他值)
		// -----------------------------------------------------------------
		int countChoice1 = 0;
		int otherChoice = 0;
		int statusNot00 = 0;
		for (AiResultRow row : aiResultList) {

			// ★ 前置檢查：IdNo 不能為空
			if (row.getIdNo() == null || row.getIdNo().isEmpty()) {
				log.warn("ImportAiResultProcessServ: IdNo 為空，跳過此筆");
				continue;
			}

			// ★ 查詢 EMAILMAS 主檔
			//依據身分證字號讀取實體 EmailMaster (取 STATUS=00 且排除CHANNEL=不能是JS 且排除ON_OFF_LINE=Y)
			//因為現行的資料庫可能存在 同一個身分字號 竟有多筆 因此限制只取 取 STATUS=00 且排除CHANNEL=不能是JS 且排除ON_OFF_LINE=Y 的資料
			EmailMaster master = this.dao.idNo(row.getIdNo());
			if (master == null) {
				log.warn("ImportAiResultProcessServ: 找不到 IdNo=" + row.getIdNo() + " 的記錄");
				continue;
			}
			
			//STATUS等於00
			if(master.getStatus().equals("00")) {
				// ★ 依「客戶選擇」分派處理
				String choice = row.getCustChoice();
				
				//選擇 1的
				if ("1".equals(choice)) {
					// 客戶選擇 1: 重新觸發流程（重新發送驗證信）
					this.handleChoice1(master);
					countChoice1++;
				} 
				//選擇 非1的
				else {
					// 客戶選擇 2或空白: 結束流程（註記無須變更 Email）
					this.handleChoice2(master, row);
					otherChoice++;
				}	
			}
			//STATUS不等於00 無法處理的筆數
			else {
				statusNot00++;
			}

		}

		// -----------------------------------------------------------------
		// 步驟 4：處理完成統計
		// -----------------------------------------------------------------
		log.info("ImportAiResultProcessServ: 處理完成 - "
			+ "客戶選擇1(重發)的筆數=" + countChoice1
			+ ", 客戶選擇2或空白的筆數=" + otherChoice
			+ ", 因為STATUS不等於00 無法處理的筆數="+statusNot00);
	}

	// =================================================================
	// 【私有方法】
	// =================================================================

	/**
	 * 讀取 Excel 檔案，解析為 AiResultRow 清單
	 *
	 * 【實作細節】
	 *   - 使用 Apache POI 3.17 讀取 .xlsx
	 *   - 僅取第一頁 (Sheet 0)
	 *   - 跳過第 1 列 (rowNum=0) 的標題列
	 *   - 僅讀取 6 個欄位 (UUID, 客戶選擇, 外撥號碼, 客戶姓名, 外撥結果, 外撥時間)
	 *   - UUID 為空的列不加入結果清單
	 *
	 * 【資源管理】
	 *   使用 try-with-resources 自動關閉 FileInputStream 與 Workbook，
	 *   避免大型 Excel 檔案造成 Memory Leak。
	 *
	 * @param filePath Excel 檔案路徑
	 * @return List<AiResultRow> 解析後的資料清單；若失敗則回傳 null
	 */
	private List<AiResultRow> readExcel(String filePath) {

		List<AiResultRow> resultList = new ArrayList<AiResultRow>();

		try (FileInputStream fis = new FileInputStream(filePath);
			 Workbook workbook = new XSSFWorkbook(fis)) {

			Sheet sheet = workbook.getSheetAt(0); // 取第一頁

			// 逐列讀取 (包含標題列)
			for (Row row : sheet) {
				
				//第一列是標題 直接略過
				if (row.getRowNum() == 0) {
					continue; // 跳過標題列 (A~O)
				}

				AiResultRow aiRow = new AiResultRow();

				// ★ IDNO (L列, IDX=1) — 比對 EMAILMAS 的關鍵欄位
				Cell idNoCell = row.getCell(IDX_IDSN);
				if (idNoCell != null) {
					aiRow.setIdNo(this.getCellStringValue(idNoCell));
				}

				// ★ 客戶選擇 (K列, IDX=10) — 決定後續處理流程
				Cell choiceCell = row.getCell(IDX_CUST_CHOICE);
				if (choiceCell != null) {
					aiRow.setCustChoice(this.getCellStringValue(choiceCell));
				}

				// 外撥號碼 (E列, IDX=4)
				Cell telCell = row.getCell(IDX_TELEPHONE);
				if (telCell != null) {
					aiRow.setTelephone(this.getCellStringValue(telCell));
				}

				// 客戶姓名 (C列, IDX=2)
				Cell nameCell = row.getCell(IDX_CUST_NAME);
				if (nameCell != null) {
					aiRow.setCustName(this.getCellStringValue(nameCell));
				}

				// 外撥結果 (F列, IDX=5)
				Cell resultCell = row.getCell(IDX_CALL_RESULT);
				if (resultCell != null) {
					aiRow.setCallResult(this.getCellStringValue(resultCell));
				}

				// 外撥時間 (H列, IDX=7)
				Cell timeCell = row.getCell(IDX_CALL_TIME);
				if (timeCell != null) {
					aiRow.setCallTime(this.getCellStringValue(timeCell));
				}

				// ★ 僅在 IDNO 有效時才加入清單
				if (aiRow.getIdNo() != null && !aiRow.getIdNo().isEmpty()) {
					resultList.add(aiRow);
				}
			}
			// try-with-resources 會自動關閉 fis 與 workbook

		} catch (IOException ex) {
			log.error("ImportAiResultProcessServ: Excel 讀取失敗 - " + ex.toString());
			return null;
		}

		return resultList;
	}

	/**
	 * 取得 Excel Cell 的字串值 (支援多種 Cell 類型)
	 *
	 * 【處理策略】
	 *   - STRING   → 直接回傳並 trim()
	 *   - NUMERIC  → 若為整數則轉 long (去除小數點)，否則保留 double 字串
	 *   - BOOLEAN  → 轉 "true" / "false"
	 *   - FORMULA  → 先嘗試取得數值結果，失敗則回傳字串結果
	 *   - 其他     → 回傳空字串
	 *
	 * @param cell 要轉換的 Cell 物件
	 * @return 對應的字串值；若 cell 為 null 則回傳 ""
	 */
	private String getCellStringValue(Cell cell) {
		if (cell == null) {
			return "";
		}

		switch (cell.getCellType()) {
			case STRING:
				return cell.getStringCellValue().trim();
			case NUMERIC:
				// 數字類型轉字串 — 若為整數則去除小數點
				double numVal = cell.getNumericCellValue();
				if (numVal == Math.floor(numVal)) {
					return String.valueOf((long) numVal);
				} else {
					return String.valueOf(numVal);
				}
			case BOOLEAN:
				return String.valueOf(cell.getBooleanCellValue());
			case FORMULA:
				// 公式欄位：優先取得數值結果，失敗則回傳字串結果
				try {
					return String.valueOf(cell.getNumericCellValue());
				} catch (IllegalStateException e) {
					return cell.getStringCellValue().trim();
				}
			default:
				return "";
		}
	}

	/**
	 * 處理「客戶選擇 = 1」：重新觸發流程（重新發送驗證信）
	 *
	 * 【資料庫更新內容】
	 *   EMAILMAS:
	 *     TRAN_CODE = "110001"  (交易代碼)
	 *     STATUS    = "00"      (處理中)
	 *     TX_STATUS = "01"      (收到申請，重新觸發)
	 *     FLAG      = "2"       (已獲取)
	 *     ERR_CODE  = ""        (清除錯誤碼)
	 *
	 *   EMAILDTL (新增兩筆明細):
	 *     第1筆: TX_STATUS = "19"  (AI 外撥回饋)
	 *     第2筆: TX_STATUS = "01"  (收到申請，重新觸發)
	 *
	 *   EMAILIMG (新增一筆影像記錄):
	 *     若 master.uuid 為空，fallback 至 queryUuid (Transient 欄位)
	 *
	 * @param master 查詢到的 EmailMaster 物件
	 */
	private void handleChoice1(EmailMaster master) {

		// ★ EMAILDTL 第1筆：記錄 AI 外撥回饋
		EmailDetail ed = new EmailDetail(master);
		ed.setTxStatus("19"); // TX_STATUS=19 (AI 外撥回饋)
		this.dao.save(ed);

		// ★ EMAILMAS 更新主檔狀態
		master.setTranCode("110001"); // 交易代碼
		master.setStatus("00");        // 00=處理中
		master.setTxStatus("01");      // 01=收到申請 (重新觸發流程)
		master.setFlag("2");           // FLAG=2 (已獲取)
		master.setErrorCode("");       // 清除錯誤碼
		this.dao.save(master);

		// ★ EMAILDTL 第2筆：記錄狀態變更
		ed = new EmailDetail(master);
		this.dao.save(ed);

		// ★ EMAILIMG 新增影像記錄
		this.imageDao.save(new EmailImage(master));
	}

	/**
	 * 處理「客戶選擇 = 2」：結束流程（註記無須變更 Email）
	 *
	 * 【資料庫更新內容】
	 *   EMAILMAS:
	 *     TRAN_CODE = "110001"  (交易代碼)
	 *     STATUS    = "01"      (全部流程完成)
	 *     TX_STATUS = "00"      (全部完成，結束流程)
	 *     FLAG      = "2"       (已獲取)
	 *     ERR_CODE  = ""        (清除錯誤碼)
	 *
	 *   EMAILDTL (新增兩筆明細):
	 *     第1筆: TX_STATUS = "19"  (AI 外撥回饋)
	 *     第2筆: TX_STATUS = "00"  (全部完成，結束流程)
	 *
	 *   EMAILIMG (新增一筆影像記錄):
	 *     若 master.uuid 為空，fallback 至 queryUuid (Transient 欄位)
	 *
	 * @param master 查詢到的 EmailMaster 物件
	 * @param aiRow  對應的 AiResultRow (目前未使用，保留供未來擴充)
	 */
	private void handleChoice2(EmailMaster master, AiResultRow aiRow) {

		// EMAILDTL 第1筆：記錄 AI 外撥回饋
		EmailDetail ed = new EmailDetail(master);
		ed.setTxStatus("19"); // TX_STATUS=19 (AI 外撥回饋)
		this.dao.save(ed);

		// EMAILMAS 更新主檔狀態
		master.setTranCode("110001");
		master.setStatus("00");
		master.setTxStatus("13");
		master.setFlag("1");
		master.setErrorCode("");
		this.dao.save(master);

		// EMAILDTL 第2筆：記錄狀態變更
		ed = new EmailDetail(master);
		this.dao.save(ed);

		// EMAILIMG 新增影像記錄
		this.imageDao.save(new EmailImage(master));
	}

	// =================================================================
	// 【內部資料結構】AiResultRow — Excel 解析後的 DTO
	// =================================================================
	/**
	 * AI 外撥結果單列資料結構
	 *
	 * 對應 CallList_YYYYMMDD.xlsx 中每一列資料，
	 * 僅包含處理流程所需的 6 個欄位。
	 */
	private static class AiResultRow {
		
		//因為外部系統的UUID 跟 mvp系統的UUID 不同定義 所以無法使用 改成使用 身分字號 來判定使用者
		//private String uuid;      // L列: UUID (比對 EMAILMAS 用)
		
		private String idNo;        // B列: 身分字號
		private String custChoice;  // K列: 客戶選擇 (1=重發, 2=結束, 其他=跳過)
		private String telephone;   // E列: 外撥號碼
		private String custName;    // C列: 客戶姓名
		private String callResult;  // F列: 外撥結果
		private String callTime;    // H列: 外撥時間
		
		//public String getUuid() { return uuid; }
		//public void setUuid(String uuid) { this.uuid = uuid; }
		
		public String getIdNo() { return idNo; }
		public void setIdNo(String idNo) { this.idNo=idNo; }

		public String getCustChoice() { return custChoice; }
		public void setCustChoice(String custChoice) { this.custChoice = custChoice; }

		public String getTelephone() { return telephone; }
		public void setTelephone(String telephone) { this.telephone = telephone; }

		public String getCustName() { return custName; }
		public void setCustName(String custName) { this.custName = custName; }

		public String getCallResult() { return callResult; }
		public void setCallResult(String callResult) { this.callResult = callResult; }

		public String getCallTime() { return callTime; }
		public void setCallTime(String callTime) { this.callTime = callTime; }
	}
}
