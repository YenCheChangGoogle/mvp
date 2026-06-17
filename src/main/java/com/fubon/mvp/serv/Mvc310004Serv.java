package com.fubon.mvp.serv;

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
import com.fubon.mvp.data.EmailDetail;

import page2020.core.Log;
import page2020.util.EmptyUtil;
import page2020.util.TimeUtil;

/**
 * 富邦MVP-MVC310004(EMAILDTL明細查詢)服務器
 * @author 張晏哲
 * @category 服務類
 */
@Service
public class Mvc310004Serv {

	private static Logger log = LoggerFactory.getLogger(Mvc310004Serv.class);
	
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
			log.info("initial: mvc310004");
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
		boolean database = false;

		// 2. 取得 QUERY_UUID (上行電文中的查詢條件)。
		String queryUuid = "";
		try {
			Node qn = doc.selectSingleNode("//QUERY_UUID");
			if (qn != null) {
				queryUuid = qn.getText().trim();
			}
		} catch (Exception ex) {
			log.error("parse QUERY_UUID error", ex);
			return this.response(doc, valid, business, database).asXML();
		}

		// 3. 檢查輸入格式。
		if (EmptyUtil.is(queryUuid)) {
			log.warn("check : (310004) QUERY_UUID is empty.");
			return this.response(doc, valid, business, database).asXML();
		}
		valid = true;
		
		// 4. 檢查業務邏輯。
		business = true;
		
		// 5. 讀取資料庫 (EMAILDTL)。
		List<EmailDetail> details = this.dao.details(queryUuid);
		if (details.size() == 0) {
			log.warn("database : (310004) entity was empty.");
			return this.response(doc, valid, business, database).asXML();
		}
		database = true;

		// 6. 返回下行電文。
		log.info("Mvc310004Serv : OK !");
		return this.response(doc, valid, business, database, details).asXML();
	}
	
	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------

	/**
	 * 返回下行電文 (失敗/成功共用)。
	 * @param doc XML文件
	 * @param valid 輸入格式正確
	 * @param business 業務邏輯正確
	 * @param database 資料不存在
	 * @return 下行XML文件
	 */
	private Document response(Document doc, boolean valid, boolean business, boolean database) {
		return this.response(doc, valid, business, database, null);
	}

	/**
	 * (同名異性式)返回下行電文。
	 * @param doc XML文件
	 * @param valid 輸入格式正確
	 * @param business 業務邏輯正確
	 * @param database 資料不存在
	 * @param details EmailDetail實體清單(成功時傳入)
	 * @return 下行XML文件
	 */
	private Document response(Document doc, boolean valid, boolean business, boolean database, List<EmailDetail> details) {
		
		// 1. 刪除TxBody内容並添加空内容。
		Element root = doc.getRootElement();
		Element body = root.element("TxBody");
		if (body != null) {
			root.remove(body);
		}
		body = root.addElement("TxBody");
		
		// 2. 業務邏輯。
		String[] messages = null;
		
		// (1) 輸入格式
		if (! valid) {
			messages = this.descDao.invalid();
		// (2) 業務邏輯
		} else if (! business) {
			messages = this.descDao.error(ErrorDescDao.uuid);
		// (3) 資料不存在
		} else if (! database) {
			messages = new String[] { "9999", "" };
		// 成功
		} else {
			messages = this.descDao.success();
		}
		
		// 3. 加入回應參數。
		// (1) MESSAGE
		body.addElement("MESSAGE").setText(messages[1]);
		// (2) MESSAGE_CODE
		body.addElement("MESSAGE_CODE").setText(messages[0]);
		// (3) TX_DATE
		body.addElement("TX_DATE").setText(TimeUtil.dateE(new Date()));
		// (4) TX_TIME
		body.addElement("TX_TIME").setText(TimeUtil.time());

		// 5. 成功時附加明細清單 (TxRepeat)
		if (valid && business && database && details != null) {
			for (EmailDetail dtl : details) {
				Element repeat = body.addElement("TxRepeat");
				// (1) UUID
				repeat.addElement("UUID").setText(EmptyUtil.orEmpty(dtl.getUuid()));
				// (2) CHG_DATE
				repeat.addElement("CHG_DATE").setText(EmptyUtil.orEmpty(dtl.getChangeDate()));
				// (3) CHG_TIME
				repeat.addElement("CHG_TIME").setText(EmptyUtil.orEmpty(dtl.getChangeTime()));
				// (4) RESP_DATE
				repeat.addElement("RESP_DATE").setText(EmptyUtil.orEmpty(dtl.getResponeDate()));
				// (5) RESP_TIME
				repeat.addElement("RESP_TIME").setText(EmptyUtil.orEmpty(dtl.getResponeTime()));
				// (6) TX_STATUS
				repeat.addElement("TX_STATUS").setText(EmptyUtil.orEmpty(dtl.getTxStatus()));
				// (7) ERR_CODE
				repeat.addElement("ERR_CODE").setText(EmptyUtil.orEmpty(dtl.getErrorCode()));
				// (8) DESCRIPTION
				String errCode = EmptyUtil.orEmpty(dtl.getErrorCode());
				String description = "";
				if (EmptyUtil.not(errCode)) {
					description = this.descDao.error(errCode)[1];
				}
				repeat.addElement("DESCRIPTION").setText(description);
			}
		}

		// 6. 返回下行電文。
		log.info("outbound: " + doc.asXML());
		return doc;
	}	
}
