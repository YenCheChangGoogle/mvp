package com.fubon.mvp.serv;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.PostConstruct;

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
import com.fubon.mvp.dao.ErrorDescDao;
import com.fubon.mvp.data.EmailDetail;
import com.fubon.mvp.data.EmailImage;
import com.fubon.mvp.data.EmailMaster;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import page2020.client.WebProxy;
import page2020.core.Log;
import page2020.util.TimeUtil;
import reactor.core.publisher.Mono;

/**
 * 富邦MVP-AI外撥結果報表處理服務器
 * @author 張晏哲
 * @category 服務類
 *
 * 說明：每日定時從 FTP 下載 AI 結果報表 CallList_YYYYMMDD.xlsx，
 *       解析 Excel 內容，依據「客戶選擇」欄位更新 EMAILMAS 狀態：
 *       - 客戶選擇 = "1" → TX_STATUS 設成 "01"(收到申請)，重新觸發寄信流程
 *       - 客戶選擇 = "2" → TX_STATUS 設成 "00"(全部完成)，註記客戶表示無須變更Email
 *       - 客戶選擇 = 其他 → 不處理
 *
 * Excel 欄位對應 (每日FTP收AI結果報表CallList_YYYYMMDD.xlsx)：
 *   A列: 業務別
 *   B列: 客戶ID
 *   C列: 客戶姓名
 *   D列: 本次外撥目的
 *   E列: 外撥號碼
 *   F列: 外撥結果
 *   G列: 外撥次數
 *   H列: 外撥時間
 *   I列: Q1意圖
 *   J列: 掛斷節點
 *   K列: 客戶選擇  ← 關鍵欄位
 *   L列: UUID
 *   M列: TTS1~4
 *   N列: 變數1~4
 *   O列: SMS1~5 + SMSDefault
 */
@Service
public class MvpAiResultServ {

	private static Logger log = LoggerFactory.getLogger(MvpAiResultServ.class);
	
	@Autowired
	private EmailDao dao;
	@Autowired
	private EmailHostDao hostDao;
	@Autowired
	private EmailImageDao imageDao;
	@Autowired
	private com.fubon.mvp.dao.EmailMasterRepo masterRepo;
	@Autowired
	private page2020.client.WebProxy proxy;
	@Autowired
	private ErrorDescDao error;
	
	// ESB無法連綫("E010")。
	private String notEsbCode;
	
	/**
	 * 1. 初始程序
	 */
	@PostConstruct
	public void initial() {
		
		this.notEsbCode = this.error.notEsb()[0];	// "E010": 無法連綫。
		if (Log.test) {
			log.info("initial: MvpAiResultServ");
		}
	}
	
	// Excel 欄位索引
	private static final int IDX_UUID = 11;          // L列: UUID
	private static final int IDX_CUST_CHOICE = 10;    // K列: 客戶選擇
	private static final int IDX_TELEPHONE = 4;       // E列: 外撥號碼
	private static final int IDX_CUST_NAME = 2;       // C列: 客戶姓名
	private static final int IDX_CALL_TIME = 7;       // H列: 外撥時間
	private static final int IDX_CALL_RESULT = 5;     // F列: 外撥結果
	
	 /**
	 * 5. 處理逾時未回覆 AI 外撥邏輯 (仿照 Mvp110007Serv)。
	 * @param overdueList 逾時清單
	 */
	public void processOverdueAiCalling(List<EmailMaster> overdueList) {
		
		if (overdueList == null || overdueList.isEmpty()) {
			return;
		}
		
		log.info("MvpAiResultServ: 開始處理逾時未回覆AI外撥, 共 " + overdueList.size() + " 筆");
		
		for (EmailMaster master : overdueList) {
			try {
				// 1. 記錄舊狀態
				String oldStatus = master.getStatus();
				String oldTxStatus = master.getTxStatus();
				String oldError = master.getErrorCode();
				
				// 2. 狀態流轉: STATUS=00 + TX_STATUS=60 (請求核心資料中)
				master.setTranCode("067050");
				master.setStatus("00");
				master.setTxStatus("60");
				master.setErrorCode("");
				this.dao.save(master);
				this.dao.save(new EmailDetail(master));
				this.imageDao.save(new EmailImage(master));
				log.info("MvpAiResultServ: before process, uuid=" + master.getUuid());
				
				// 3. 組合並發送電文 067050 (獲取客戶姓名與手機號碼)
				String responseXml = this.callCoreApi(master);
				
				if (responseXml != null) {
					// 4. 解析下行電文
					Document doc = this.proxy.document(responseXml);
					String errId = doc.selectSingleNode("//HERRID").getText();
					
					// (1) ESB核心回傳成功("0000")
					if ("0000".equals(errId)) {
						String chName = this.proxy.value(doc, "CH_NAME");
						String telNo = this.proxy.value(doc, "TEL_NO");
						
						// 儲存姓名+手機至 CHECKER/TEL_NO 欄位
						master.setChecker(chName);
						master.setTelNo(telNo);
						master.setFlag("2");
						master.setStatus("01");
						master.setTxStatus("00");
						master.setErrorCode("");
						
						this.dao.save(master);
						this.dao.save(new EmailDetail(master));
						this.imageDao.save(new EmailImage(master));
						
						log.info("MvpAiResultServ: fetch success, uuid=" + master.getUuid() + ", chName=" + chName + ", telNo=" + telNo);
						
					// (2) 失敗
					} else {
						master.setStatus("02");
						master.setErrorCode(this.proxy.value(doc, "EMSGID"));
						
						this.dao.save(master);
						this.dao.save(new EmailDetail(master));
						this.imageDao.save(new EmailImage(master));
						
						log.error("MvpAiResultServ: fetch failed, uuid=" + master.getUuid() + ", errId=" + errId);
					}
				} else {
					// ESB 無法連綫，保留失敗狀態供下次重試
					if (! ("02".equals(oldStatus) && this.notEsbCode.equals(oldError))) {
						master.setStatus("02");
						master.setErrorCode(this.notEsbCode);
						this.dao.save(master);
						this.dao.save(new EmailDetail(master));
						this.imageDao.save(new EmailImage(master));
					}
					log.error("MvpAiResultServ: ESB disconnected, uuid=" + master.getUuid());
				}
				
			} catch (Exception ex) {
				log.error("MvpAiResultServ: 處理逾時記錄異常 UUID=" + master.getUuid() + " : " + ex.toString());
			}
		}
	}

	/**
	 * 私有方法：調用核心電文 067050 (仿照 Mvp110007Serv)
	 * @param master 郵件主檔
	 * @return 下行電文 XML 字串，呼叫失敗返回 null
	 */
	private String callCoreApi(EmailMaster master) {
		
		// 1. 組合上行電文 (仿照 MVP067050)
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("Tx");

		// (1) FMPConnectionString
		Element conn = root.addElement("FMPConnectionString");
		conn.addElement("SPName").setText("BKMVP");
		conn.addElement("LoginID").setText("BKMVP");
		conn.addElement("TxnId").setText("MVP067050");

		// (2) TxHead
		Element head = root.addElement("TxHead");
		head.addElement("HTXTID").setText("MVP067050");
		head.addElement("HWSID").setText("BKMVP");
		head.addElement("HTLID").setText("2004115");
		head.addElement("HSTANO").setText("7898411");
		head.addElement("TXMSRN");
		head.addElement("HSYDAY").setText(TimeUtil.taiwanDate());

		// (3) TxBody
		Element body = root.addElement("TxBody");
		body.addElement("uuid").setText(master.getUuid());
		body.addElement("branch").setText("00200");
		body.addElement("teller").setText("99946899");
		body.addElement("IDTYPE").setText(master.getIdType());
		body.addElement("IDNO").setText(master.getIdNo());

		log.info("MvpAiResultServ: ESB request - " + doc.asXML());

		// 2. 發送上行電文給 ESB
		try {
			String xml = this.proxy.api("esb")
				.body(Mono.just(doc.asXML()), String.class).retrieve()
				.bodyToMono(String.class).block();
			log.info("MvpAiResultServ: ESB response - " + xml);
			return xml;
		} catch (Exception ex) {
			log.error("MvpAiResultServ: ESB disconnected - " + ex.toString());
			return null;
		}
	}
	
	/**
	 * 2. 處理 AI 結果報表 (由 Shell 腳本呼叫)。
	 * @param excelFilePath Excel 檔案完整路徑 (例如: /home/mvpadm/download/CallList_20260518.xlsx)
	 */
	public void processAiResultReport(String excelFilePath) {
		
		// 1. 是主服務器？
		if (! this.hostDao.isMain()) {
			log.warn("MvpAiResultServ: 非主節點，跳過處理");
			return;
		}
		
		log.info("MvpAiResultServ: 開始處理 AI 結果報表, 檔案=" + excelFilePath);
		
		// 2. 讀取 Excel 檔案
		List<AiResultRow> aiResultList = this.readExcel(excelFilePath);
		if (aiResultList == null || aiResultList.isEmpty()) {
			log.warn("MvpAiResultServ: Excel 解析失敗或無資料");
			return;
		}
		
		log.info("MvpAiResultServ: 解析完成，共 " + aiResultList.size() + " 筆資料");
		
		// 3. 逐筆處理
		int countChoice1 = 0;  // 客戶選擇=1 的重發數量
		int countChoice2 = 0;  // 客戶選擇=2 的完成數量
		int countSkip = 0;     // 跳過數量
		
		for (AiResultRow row : aiResultList) {
			
			// 檢查 UUID 是否有效
			if (row.getUuid() == null || row.getUuid().isEmpty()) {
				log.warn("MvpAiResultServ: UUID 為空，跳過此筆");
				countSkip++;
				continue;
			}
			
			// 查詢 EmailMaster
			EmailMaster master = this.dao.uuid(row.getUuid());
			if (master == null) {
				log.warn("MvpAiResultServ: 找不到 UUID=" + row.getUuid() + " 的記錄");
				countSkip++;
				continue;
			}
			
			String choice = row.getCustChoice();
			
			if ("1".equals(choice)) {
				// 客戶選擇 1: 重新觸發流程
				this.handleChoice1(master);
				countChoice1++;
			} else if ("2".equals(choice)) {
				// 客戶選擇 2: 註記無須變更
				this.handleChoice2(master, row);
				countChoice2++;
			} else {
				// 其他: 不處理
				countSkip++;
				log.info("MvpAiResultServ: UUID=" + row.getUuid() + ", 客戶選擇=" + choice + ", 不處理");
			}
		}
		
		// 4. 處理完成統計
		log.info("MvpAiResultServ: 處理完成 - 客戶選擇1(重發)=" + countChoice1 
				+ ", 客戶選擇2(完成)=" + countChoice2 
				+ ", 跳過=" + countSkip);
	}
	
	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------
	
	/**
	 * 1. 讀取 Excel 檔案。
	 * @param filePath Excel 檔案路徑
	 * @return AI結果清單
	 */
	private List<AiResultRow> readExcel(String filePath) {
		
		List<AiResultRow> resultList = new ArrayList<AiResultRow>();
		Workbook workbook = null;
		
		try {
			FileInputStream fis = new FileInputStream(filePath);
			workbook = new XSSFWorkbook(fis);  // .xlsx 格式
			Sheet sheet = workbook.getSheetAt(0);  // 取第一頁
			
			// 跳過標題列，從第2列開始 (rowNum=1)
			for (Row row : sheet) {
				if (row.getRowNum() == 0) {
					continue;  // 跳過標題
				}
				
				AiResultRow aiRow = new AiResultRow();
				
				// UUID (L列)
				Cell uuidCell = row.getCell(IDX_UUID);
				if (uuidCell != null) {
					aiRow.setUuid(this.getCellStringValue(uuidCell));
				}
				
				// 客戶選擇 (K列)
				Cell choiceCell = row.getCell(IDX_CUST_CHOICE);
				if (choiceCell != null) {
					aiRow.setCustChoice(this.getCellStringValue(choiceCell));
				}
				
				// 外撥號碼 (E列)
				Cell telCell = row.getCell(IDX_TELEPHONE);
				if (telCell != null) {
					aiRow.setTelephone(this.getCellStringValue(telCell));
				}
				
				// 客戶姓名 (C列)
				Cell nameCell = row.getCell(IDX_CUST_NAME);
				if (nameCell != null) {
					aiRow.setCustName(this.getCellStringValue(nameCell));
				}
				
				// 外撥結果 (F列)
				Cell resultCell = row.getCell(IDX_CALL_RESULT);
				if (resultCell != null) {
					aiRow.setCallResult(this.getCellStringValue(resultCell));
				}
				
				// 外撥時間 (H列)
				Cell timeCell = row.getCell(IDX_CALL_TIME);
				if (timeCell != null) {
					aiRow.setCallTime(this.getCellStringValue(timeCell));
				}
				
				// 只有 UUID 不為空才加入清單
				if (aiRow.getUuid() != null && ! aiRow.getUuid().isEmpty()) {
					resultList.add(aiRow);
				}
			}
			
			fis.close();
			workbook.close();
			
		} catch (IOException ex) {
			log.error("MvpAiResultServ: Excel 讀取失敗 - " + ex.toString());
			return null;
		}
		
		return resultList;
	}
	
	/**
	 * 2. 取得 Cell 的字串值。
	 */
	private String getCellStringValue(Cell cell) {
		if (cell == null) {
			return "";
		}
		
		switch (cell.getCellType()) {
			case STRING:
				return cell.getStringCellValue().trim();
			case NUMERIC:
				// 數字類型轉字串 (去掉小數點)
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
				return "";
		}
	}
	
	/**
	 * 3. 處理客戶選擇=1: 重新觸發流程 (TX_STATUS 設成 "01")。
	 * @param master 郵件主檔
	 */
	private void handleChoice1(EmailMaster master) {
		
		String oldStatus = master.getStatus();
		String oldTxStatus = master.getTxStatus();
		
		// 保存狀態資料(處理之前)
		master.setTranCode("110001");  // 使用 110001 作為交易代碼
		master.setStatus("00");        // 00=處理中
		master.setTxStatus("01");      // 01=收到申請 (重新觸發流程)
		master.setErrorCode("");
		
		// 儲存主檔
		this.dao.save(master);
		this.dao.save(new EmailDetail(master));
		this.imageDao.save(new EmailImage(master));
		
		log.info("MvpAiResultServ: 客戶選擇1-重新觸發 UUID=" + master.getUuid() 
				+ ", 舊狀態=" + oldStatus + "/" + oldTxStatus 
				+ ", 新狀態=00/01");
	}
	
	/**
	 * 4. 處理客戶選擇=2: 註記無須變更 (TX_STATUS 設成 "00")。
	 * @param master 郵件主檔
	 * @param aiRow AI結果列
	 */
	private void handleChoice2(EmailMaster master, AiResultRow aiRow) {
		
		String oldStatus = master.getStatus();
		String oldTxStatus = master.getTxStatus();
		
		// 保存狀態資料(處理之前)
		master.setTranCode("110002");  // 使用 110002 作為交易代碼(取消)
		master.setStatus("01");        // 01=成功完成
		master.setTxStatus("00");      // 00=全部完成
		master.setErrorCode("");
		
		// 在 REMARK 欄位註記 AI 外撥結果
		String aiRemark = ";AI外撥-客戶選擇2(無須變更Email)-" 
				+ "外撥時間=" + aiRow.getCallTime()
				+ ",外撥結果=" + aiRow.getCallResult()
				+ ",外撥號碼=" + aiRow.getTelephone();
		
		String oldRemark = master.getRemark();
		if (oldRemark == null) {
			oldRemark = "";
		}
		
		// 注意欄位長度上限500
		String newRemark = oldRemark + aiRemark;
		if (newRemark.length() > 500) {
			newRemark = newRemark.substring(0, 500);
		}
		master.setRemark(newRemark);
		
		// 儲存主檔
		this.dao.save(master);
		this.dao.save(new EmailDetail(master));
		this.imageDao.save(new EmailImage(master));
		
		log.info("MvpAiResultServ: 客戶選擇2-註記完成 UUID=" + master.getUuid() 
				+ ", 舊狀態=" + oldStatus + "/" + oldTxStatus 
				+ ", 新狀態=01/00, AI註記=" + aiRemark);
	}
	
	/**
	 * AI 結果列資料結構。
	 */
	private static class AiResultRow {
		private String uuid;
		private String custChoice;
		private String telephone;
		private String custName;
		private String callResult;
		private String callTime;
		
		public String getUuid() { return uuid; }
		public void setUuid(String uuid) { this.uuid = uuid; }
		
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
