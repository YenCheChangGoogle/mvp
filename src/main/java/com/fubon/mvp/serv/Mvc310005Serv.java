package com.fubon.mvp.serv;

import java.io.File;
import java.util.Date;
import java.util.List;

import javax.annotation.PostConstruct;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailDao;
import com.fubon.mvp.dao.ErrorDescDao;
import com.fubon.mvp.data.EmailMaster;

import page2020.core.Log;
import page2020.util.EmptyUtil;
import page2020.util.TimeUtil;

/**
 * 富邦MVP-MVC310005(AI外撥情形報表)服務器
 * @author 張晏哲
 * @category 服務類
 * 
 * 【功能概述】
 * 查詢 EMAILMAS 中所有 AI 外撥相關記錄（FLAG != NULL），回傳報表明細供前台展示。
 * 支援依日期範圍過濾 (FROM_DATE / TO_DATE)。
 * 
 * 【上行電文欄位】
 *   QUERY_UUID    : 指定 UUID 查詢 (可選, 不傳則查全部)
 *   FROM_DATE     : 起始日期 YYYYMMDD (可選)
 *   TO_DATE       : 結束日期 YYYYMMDD (可選)
 *   NEXT_KEY      : 分頁游標 (可選)
 */
@Service
public class Mvc310005Serv {

	private static Logger log = LoggerFactory.getLogger(Mvc310005Serv.class);
	
	@Autowired
	private EmailDao dao;
	@Autowired
	private ErrorDescDao descDao;

	/**
	 * 初始程序。
	 */
	@PostConstruct
	public void initial() {
		
		if (Log.test) {
			log.info("initial: mvc310005");
		}
	}

	/**
	 * 交易服務。
	 * @param doc 上行XML文件
	 * @return 下行電文
	 */
	public String service(Document doc) {
		
		log.info("inbound: " + doc.asXML());
		
		// 1. 設定驗證變數。
		boolean valid = false;
		boolean business = false;

		// 2. 解析上行電文查詢條件。
		String queryUuid = "";
		String fromDate = "";
		String toDate = "";
		String nextKey = "";
		
		try {
			Node qu = doc.selectSingleNode("//QUERY_UUID");
			if (qu != null) queryUuid = qu.getText().trim();
			
			Node fd = doc.selectSingleNode("//FROM_DATE");
			if (fd != null) fromDate = fd.getText().trim();
			
			Node td = doc.selectSingleNode("//TO_DATE");
			if (td != null) toDate = td.getText().trim();
			
			Node nk = doc.selectSingleNode("//NEXT_KEY");
			if (nk != null) nextKey = nk.getText().trim();
		} catch (Exception ex) {
			log.error("parse error", ex);
			return this.response(doc, valid, business).asXML();
		}

		// 3. 檢查輸入格式。
		valid = true;
		
		// 4. 檢查業務邏輯。
		business = true;
		
		// 5. 讀取資料庫 (EMAILMAS)。
		//    若傳入 QUERY_UUID 則查單筆，否則查詢全部 FLAG != NULL 之紀錄。
		List<EmailMaster> entities = null;
		if (EmptyUtil.not(queryUuid)) {
			EmailMaster master = this.dao.uuid(queryUuid);
			if (master == null || "99".equals(master.getStatus())) {
				log.warn("database : (310005) entity was empty.");
				return this.response(doc, valid, business).asXML();
			}
			entities = new java.util.ArrayList<EmailMaster>();
			entities.add(master);
		} else {
			// 查詢所有狀態為處理中(00)或已完成(01)且 FLAG 不為空之紀錄。
			List<EmailMaster> list00 = this.dao.overdueResend(); // STATUS=00, TX_STATUS IN (11,13)
			List<EmailMaster> list01 = new java.util.ArrayList<EmailMaster>();
			
			// 過濾 FLAG != NULL 的紀錄。
			entities = new java.util.ArrayList<EmailMaster>();
			for (EmailMaster m : list00) {
				if (EmptyUtil.not(m.getFlag())) {
					entities.add(m);
				}
			}
			
			// 若有指定日期範圍，再進行記憶體過濾。
			int begin = Integer.valueOf(EmptyUtil.orValue(fromDate, "0")).intValue();
			int end = Integer.valueOf(EmptyUtil.orValue(toDate, "0")).intValue();
			if (begin != 0 || end != 0) {
				List<EmailMaster> temp = new java.util.ArrayList<EmailMaster>();
				for (EmailMaster m : entities) {
					int time = Integer.valueOf(m.getChangeDate()).intValue();
					boolean ok = true;
					if (begin > 0 && time < begin) ok = false;
					if (end > 0 && time > end) ok = false;
					if (ok) temp.add(m);
				}
				entities = temp;
			}
			
			if (entities.size() == 0) {
				log.warn("database : (310005) entity was empty.");
				return this.response(doc, valid, business).asXML();
			}
		}

		// 6. 分頁處理。
		int size = entities.size();
		int beginIdx = 0;
		int endIdx = 0;
		String newValue = "";
		String hReturn = "E"; // E=結束, C=尚有
		
		if (EmptyUtil.is(nextKey)) {
			beginIdx = 0;
			if (size > 30) {
				endIdx = 30;
				newValue = "30";
				hReturn = "C";
			} else {
				endIdx = size;
			}
		} else {
			beginIdx = Integer.valueOf(nextKey).intValue();
			if (size > (beginIdx + 30)) {
				endIdx = beginIdx + 30;
				newValue = String.valueOf(endIdx);
				hReturn = "C";
			} else {
				endIdx = size;
			}
		}
		
		log.info("310005: NEXT_KEY='" + nextKey + "', begin=" + beginIdx + ", end=" + endIdx + ", total=" + size);

		// 7. 返回下行電文。
		log.info("Mvc310005Serv : OK !");
		return this.response(doc, valid, business, hReturn, newValue, entities, beginIdx, endIdx).asXML();
	}
	
	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------

	/**
	 * 返回下行電文 (失敗)。
	 */
	private Document response(Document doc, boolean valid, boolean business) {
		return this.response(doc, valid, business, "", "", null, 0, 0);
	}

	/**
	 * (同名異性式)返回下行電文。
	 * @param doc XML文件
	 * @param valid 輸入格式正確
	 * @param business 業務邏輯正確
	 * @param hReturn 結束/尚有標示
	 * @param nextKey 下一次游標值
	 * @param entities 資料清單
	 * @param beginIdx 起始索引
	 * @param endIdx 結束索引
	 * @return 下行XML文件
	 */
	private Document response(Document doc, boolean valid, boolean business, 
			String hReturn, String nextKey, List<EmailMaster> entities, int beginIdx, int endIdx) {
		
		// 1. 清除TxBody。
		Element root = doc.getRootElement();
		Element body = root.element("TxBody");
		if (body != null) {
			root.remove(body);
		}
		body = root.addElement("TxBody");
		
		// 2. 設定頭部 HRETRN。
		Element head = root.element("TxHead");
		if (head == null) {
			head = root.addElement("TxHead");
		}
		Element hRet = head.element("HRETRN");
		if (hRet == null) {
			hRet = head.addElement("HRETRN");
		}
		hRet.setText(hReturn);
		
		// 3. 業務邏輯回應。
		String[] messages = null;
		if (! valid) {
			messages = this.descDao.invalid();
		} else if (! business) {
			messages = this.descDao.error(ErrorDescDao.uuid);
		} else {
			messages = this.descDao.success();
		}
		
		// 4. 加入基本欄位。
		body.addElement("MESSAGE").setText(messages[1]);
		body.addElement("MESSAGE_CODE").setText(messages[0]);
		body.addElement("TX_DATE").setText(TimeUtil.dateE(new Date()));
		body.addElement("TX_TIME").setText(TimeUtil.time());
		body.addElement("NEXT_KEY").setText(nextKey);

		// 5. 成功時附加 AI 外撥明細清單 (TxRepeat)。
		if (valid && business && entities != null) {
			int count = 0;
			for (int i = beginIdx; i < endIdx; i++) {
				EmailMaster m = entities.get(i);
				Element repeat = body.addElement("TxRepeat");
				
				// (1) UUID
				repeat.addElement("UUID").setText(EmptyUtil.orEmpty(m.getUuid()));
				// (2) CUST_ID
				repeat.addElement("CUST_ID").setText(EmptyUtil.orEmpty(m.getIdNo()));
				// (3) CUST_NAME
				repeat.addElement("CUST_NAME").setText(EmptyUtil.orEmpty(m.getChName()));
				// (4) TEL_NO (AI外撥手機號碼)
				repeat.addElement("TEL_NO").setText(EmptyUtil.orEmpty(m.getTelNo()));
				// (5) FLAG (AI外撥標記: 1=待處理, 2=已獲取, 0=不處理)
				repeat.addElement("FLAG").setText(EmptyUtil.orEmpty(m.getFlag()));
				// (6) STATUS
				repeat.addElement("STATUS").setText(EmptyUtil.orEmpty(m.getStatus()));
				// (7) TX_STATUS
				repeat.addElement("TX_STATUS").setText(EmptyUtil.orEmpty(m.getTxStatus()));
				// (8) ERR_CODE
				repeat.addElement("ERR_CODE").setText(EmptyUtil.orEmpty(m.getErrorCode()));
				// (9) DESCRIPTION
				String errCode = EmptyUtil.orEmpty(m.getErrorCode());
				String description = "";
				if (EmptyUtil.not(errCode)) {
					description = this.descDao.error(errCode)[1];
				}
				repeat.addElement("DESCRIPTION").setText(description);
				// (10) CHG_DATE
				repeat.addElement("CHG_DATE").setText(EmptyUtil.orEmpty(m.getChangeDate()));
				// (11) CHG_TIME
				repeat.addElement("CHG_TIME").setText(EmptyUtil.orEmpty(m.getChangeTime()));
				
				count += 1;
			}
			
			// 6. 補到30筆 (空白)。
			while (count < 30) {
				Element repeat = body.addElement("TxRepeat");
				repeat.addElement("UUID").setText("");
				repeat.addElement("CUST_ID").setText("");
				repeat.addElement("CUST_NAME").setText("");
				repeat.addElement("TEL_NO").setText("");
				repeat.addElement("FLAG").setText("");
				repeat.addElement("STATUS").setText("");
				repeat.addElement("TX_STATUS").setText("");
				repeat.addElement("ERR_CODE").setText("");
				repeat.addElement("DESCRIPTION").setText("");
				repeat.addElement("CHG_DATE").setText("");
				repeat.addElement("CHG_TIME").setText("");
				count += 1;
			}
		}

		// 7. 返回下行電文。
		log.info("outbound: " + doc.asXML());
		return doc;
	}	
}
