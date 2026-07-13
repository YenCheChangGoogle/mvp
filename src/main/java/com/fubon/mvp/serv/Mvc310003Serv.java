package com.fubon.mvp.serv;

import java.util.Date;
import java.util.List;

import javax.annotation.PostConstruct;

import org.dom4j.Document;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailDao;
import com.fubon.mvp.dao.ErrorDescDao;
import com.fubon.mvp.data.EmailDetail;
import com.fubon.mvp.data.EmailMaster;

import page2020.core.Log;
import page2020.util.EmptyUtil;
import page2020.util.TimeUtil;

/**
 * 富邦MVP-MVC310003(查主檔/明細)服務器
 * @author 張晏哲
 * @category 服務類
 */
@Service
public class Mvc310003Serv {

	private static Logger log = LoggerFactory.getLogger(Mvc310003Serv.class);
	
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
			log.info("initial: mvc310003");
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
		
		// 2. 創建上行電文實體。
		EmailMaster master = new EmailMaster(doc, "310003");
		log.info(master.toString());

		// 3. 檢查輸入格式。
		if (master.invalid310003()) {
			log.warn("check : (310003) argument errors 輸入參數沒通過驗證");
			return this.response(doc, false, 0).asXML();
		}
		valid = true;
		
		EmailMaster entity=null;
		// 4. 讀取資料庫 - 取得主檔資料 (CUST_ID, ID_TYPE)。
		String queryUuid = master.getQueryUuid();
		if(queryUuid!=null && queryUuid.length()>0) {
			entity = this.dao.uuid(queryUuid);
			if (entity == null) {
				log.warn("database : (310003) master entity was empty.");
				return this.response(doc, false, 1).asXML();
			}
			//else if("99".equals(entity.getStatus())) {
			//	log.warn("database : (310003) master entity was empty.");
			//	return this.response(doc, false, 2).asXML();
			//}
		}
		else if(master.getIdNo()!=null && master.getIdNo().length()>0) {
			entity=this.dao.idNo(master.getIdNo());
			if (entity == null) {
				log.warn("database : (310003) master entity was empty.");
				return this.response(doc, false, 1).asXML();
			}
			//else if("99".equals(entity.getStatus())) {
			//	log.warn("database : (310003) master entity was empty.");
			//	return this.response(doc, false, 2).asXML();
			//}
		}
		else {
			log.warn("必要條件無輸入 UUID 與 身分字號");
			return this.response(doc, false, 1).asXML();
		}
		
		// 5. 讀取明細清單 (t2)，並依 t1.ID, t2.CHG_DATE, t2.CHG_TIME 排序。
		//List<EmailDetail> details = this.dao.details(queryUuid);
		List<EmailDetail> details = this.dao.detailsByUuidOrderByResponeDateAscResponeTimeAsc(queryUuid);

		// 6. 返回下行電文。
		log.info("Mvc310003Serv : OK !");
		return this.response(doc, true, entity, details).asXML();
	}
	
	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------

	/**
	 * 返回失敗/空值下行電文。
	 */
	private Document response(Document doc, boolean valid, int errorType) {
		
		Element root = doc.getRootElement();
		Element body = root.element("TxBody");
		if (body != null) root.remove(body);
		body = root.addElement("TxBody");
		
		String[] messages = null;
		
		if(errorType==0) {
			messages=valid ? this.descDao.success() : this.descDao.invalid();
		}
		else if(errorType==1) {
			messages = valid ? this.descDao.success() : this.descDao.notfound();
		}
		else {
			messages = valid ? this.descDao.success() : this.descDao.notfound();
		}
		
		body.addElement("MESSAGE").setText(messages[1]);
		body.addElement("MESSAGE_CODE").setText(messages[0]);
		body.addElement("TX_DATE").setText(TimeUtil.dateE(new Date()));
		body.addElement("TX_TIME").setText(TimeUtil.time());
		
		log.info("outbound: " + doc.asXML());
		return doc;
	}

	/**
	 * 返回成功下行電文 (含 TxRepeat 明細)。
	 */
	private Document response(Document doc, boolean valid, EmailMaster master, List<EmailDetail> details) {
		
		Element root = doc.getRootElement();
		Element body = root.element("TxBody");
		if (body != null) root.remove(body);
		body = root.addElement("TxBody");
		
		// 1. 成功標頭資訊。
		String[] messages = this.descDao.success();
		body.addElement("MESSAGE").setText(messages[1]);
		body.addElement("MESSAGE_CODE").setText(messages[0]);
		body.addElement("TX_DATE").setText(TimeUtil.dateE(new Date()));
		body.addElement("TX_TIME").setText(TimeUtil.time());

		// 2. 客戶基本資料 (來自 t1 EMAILMAS)。
		body.addElement("CUST_ID").setText(EmptyUtil.orEmpty(master.getIdNo()));
		body.addElement("ID_TYPE").setText(EmptyUtil.orEmpty(master.getIdType()));

		// 3. 明細重複欄位 (來自 t2 EMAILDTL + ERRDESC)。
		for (EmailDetail detail : details) {
			Element repeat = body.addElement("TxRepeat");
			
			repeat.addElement("QUERY_UUID").setText(EmptyUtil.orEmpty(detail.getUuid()));
			repeat.addElement("RESP_DATE").setText(EmptyUtil.orEmpty(detail.getResponeDate()));
			repeat.addElement("RESP_TIME").setText(EmptyUtil.orEmpty(detail.getResponeTime()));
			repeat.addElement("STATUS").setText(EmptyUtil.orEmpty(master.getStatus()));
			repeat.addElement("TX_STATUS").setText(EmptyUtil.orEmpty(detail.getTxStatus()));

			String errCode = EmptyUtil.orEmpty(detail.getErrorCode());
			// 查詢狀態描述 (對應 SQL 中的子查詢: select ERR_DESC from ERRDESC where ERR_CODE=t2.ERR_CODE)。
			String[] errMsg = this.descDao.error(errCode);
			repeat.addElement("DESCRIPTION").setText(EmptyUtil.orEmpty(errMsg[1]));
			repeat.addElement("ERR_CODE").setText(errCode);
		}

		// 4. 返回下行電文。
		log.info("outbound: " + doc.asXML());
		return doc;
	}	
}
