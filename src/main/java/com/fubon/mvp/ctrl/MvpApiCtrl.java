package com.fubon.mvp.ctrl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fubon.mvp.serv.ImportAiResultToProcessServ;
import com.fubon.mvp.serv.Mvc084000Serv;
import com.fubon.mvp.serv.Mvc110001Serv;
import com.fubon.mvp.serv.Mvc110002Serv;
import com.fubon.mvp.serv.Mvc110003Serv;
import com.fubon.mvp.serv.Mvc110006Serv;
import com.fubon.mvp.serv.Mvc110007Serv;
import com.fubon.mvp.serv.Mvc310001Serv;
import com.fubon.mvp.serv.Mvc310002Serv;
import com.fubon.mvp.serv.Mvp110007Serv;
import com.fubon.mvp.serv.Mvp110008Serv;

import page2020.client.WebProxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 富邦MVP- MVP 控制器
 * @author MILO-GAO(高振銘)@2020
 * @category 組態類
 */
@RestController
@RequestMapping("/api")
public class MvpApiCtrl {

	private static Logger log = LoggerFactory.getLogger(MvpApiCtrl.class);
	
	@Autowired
	private WebProxy proxy;
	@Autowired
	private Mvc110001Serv mvc110001;
	@Autowired
	private Mvc110002Serv mvc110002;
	@Autowired
	private Mvc110003Serv mvc110003;
	@Autowired
	private Mvc110006Serv mvc110006;
	@Autowired
	private Mvc110007Serv mvc110007;
	@Autowired
	private Mvc310001Serv mvc310001;
	@Autowired
	private Mvc310002Serv mvc310002;
	@Autowired
	private Mvc084000Serv mvc084000;
	
	@Autowired
	private ImportAiResultToProcessServ importAiResultProcess;
	
	@Autowired
	private Mvp110007Serv mvp110007Serv;
	
	@Autowired
	private Mvp110008Serv mvp110008Serv;

	/**
	 * 1. MVC110001 - 前臺登錄。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/110001")
	public String mvc110001(@RequestBody String xml) {
		return mvc110001.service(this.proxy.document(xml));
	}

	/**
	 * 2. MVC110002 - 取消申請。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/110002")
	public String mvc110002(@RequestBody String xml) {
		return mvc110002.service(this.proxy.document(xml));
	}
	
	/**
	 * 2. MVC110003 - 前臺人工啟用。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/110003")
	public String mvc110003(@RequestBody String xml) {
		return mvc110003.service(this.proxy.document(xml));
	}

	/**
	 * 3. MVC110006 - 信件狀態。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/110006")
	public String mvc110006(@RequestBody String xml) {
		return mvc110006.service(this.proxy.document(xml));
	}
	
	/**
	 * 4. MVC110007 - 客戶確認信件。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/110007")
	public String mvc110007(@RequestBody String xml) {
		return mvc110007.service(this.proxy.document(xml));
	}

	/**
	 * 5. MVC310001 - 前臺查詢。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/310001")
	public String mvc310001(@RequestBody String xml) {
		return mvc310001.service(this.proxy.document(xml));
	}

	/**
	 * 6. MVC310002 - 整批查詢。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/310002")
	public String mvc310002(@RequestBody String xml) {
		return mvc310002.service(this.proxy.document(xml));
	}
	
	/**
	 * 7. MVC084000 - 下行存活驗證。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/084000")
	public String mvc084000(@RequestBody String xml) {
		return mvc084000.service(this.proxy.document(xml));
	}

	/**
	 * 8. MVP110008 - 三日未回覆 重發驗證信
	 * 
	 * (1)
	 * findOverdue3DaysAiCalling() 取得逾時清單 overdueList
	 * SELECT * FROM EMAILMAS WHERE STATUS = '00' AND TX_STATUS = '13' AND CHG_DATE < CONVERT(varchar(8), DATEADD(day, -3, GETDATE()), 112) ORDER BY ID
	 * STATUS		'00'	處理中
	 * TX_STATUS	'13'	客戶收到（已讀信但尚未確認）
	 * CHG_DATE		< D-3	最後變更日期超過 3 天
	 * 
	 * (2)
	 * 逐筆再確認（防禦性檢查）依 UUID 重新查詢 → 確認 status='00' 且  txStatus='13' → 篩選出 retryList
	 * 
	 * (3)
	 * 逐筆更新  EMAILMAS: 
	 *   FLAG='1'(重發標記)
	 *   TRAN_CODE='110008'
	 *   STATUS='00'(處理中)
	 *   TX_STATUS='01'(收到申請)
	 *   ERR_CODE=''(清除錯誤碼) 
	 * 
	 * @return 處理結果訊息
	 */
	//http://localhost:8080/mvp/api/mvp110008
	//@PostMapping(value = "/mvp110008", produces = MediaType.APPLICATION_XML_VALUE)
	@RequestMapping(
		    value = "/mvp110008",
		    method = {RequestMethod.GET, RequestMethod.POST},
		    produces = MediaType.APPLICATION_XML_VALUE
	)
	public String mvp110008() {
		log.info("三日未回覆 重發驗證信");

		mvp110008Serv.schedule();
		return "<response><status>success</status><message>mvp110008</message></response>";
	}
	
	/**
	 * 9. MVP110007 - 六日未回覆 AI外撥處理
	 * 
	 */
	//http://localhost:8080/mvp/api/mvp110007
	//@PostMapping(value = "/mvp110007", produces = MediaType.APPLICATION_XML_VALUE)
	@RequestMapping(
		    value = "/mvp110007",
		    method = {RequestMethod.GET, RequestMethod.POST},
		    produces = MediaType.APPLICATION_XML_VALUE
	)
	public String mvp110007() {
		log.info("6日未回覆 AI外撥處理");

		mvp110007Serv.schedule();
		return "<response><status>success</status><message>mvp110007</message></response>";
	}
	
	
	/**
	 * 10. AI結果報表導入 - 解析 Excel 並更新 相關資資料
	 * @param excelFilePath Excel 檔案完整路徑 (例如: /home/mvpadm/download/CallList_20260518.xlsx)
	 * @return 處理結果訊息
	 */
	//http://localhost:8080/mvp/api/airesult/import
	@PostMapping(value = "/airesult/import", produces = MediaType.APPLICATION_XML_VALUE)
	public String aiResultImport(@RequestBody String excelFilePath) {
		log.info("AI結果報表導入 - 解析 Excel 並更新 相關資資料");

	    StringBuilder result = new StringBuilder();
	    result.append("<response>");
	    try {
	        this.importAiResultProcess.processAiResultReport(excelFilePath);
	        result.append("<status>success</status>");
	        result.append("<message>AI結果報表處理完成</message>");
	    } catch (Exception ex) {
	        result.append("<status>error</status>");
	        result.append("<message>").append(ex.getMessage()).append("</message>");
	    }
	    result.append("</response>");
	    return result.toString();
	}
	
}
