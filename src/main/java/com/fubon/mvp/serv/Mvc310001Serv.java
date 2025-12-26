package com.fubon.mvp.serv;

import java.util.Date;

import javax.annotation.PostConstruct;

import org.dom4j.Document;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailDao;
import com.fubon.mvp.dao.EmailImageDao;
import com.fubon.mvp.dao.ErrorDescDao;
import com.fubon.mvp.data.EmailImage;
import com.fubon.mvp.data.EmailMaster;

import page2020.core.Log;
import page2020.util.EmptyUtil;
import page2020.util.TimeUtil;

/**
 * 富邦MVP-MVC310001(登錄查詢)服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class Mvc310001Serv {

	private static Logger log = LoggerFactory.getLogger(Mvc310001Serv.class);
	
	@Autowired
	private EmailDao dao;
	@Autowired
	private ErrorDescDao descDao;
	@Autowired
	private EmailImageDao imageDao;

	/**
	 * 初始程序。
	 */
	@PostConstruct
	public void initial() {
		
		if (Log.test) {
			log.info("initial: mvc310001");
		}
	}

	/**
	 * 2. 交易服務。
	 * @param doc 上行XML文件
	 * @return 下行電文
	 */
	public String service(Document doc) {
		
		log.info("inbound: " + doc.asXML());
		
		// 1. 設定驗證變數。
		// (1) 輸入格式正確。
		boolean valid = false;
		// (2) 業務邏輯正確。
		boolean business = false;
		// (3) 資料不存在。
		boolean database = false;

		// 2. 創建上行電文實體。
		EmailMaster master = new EmailMaster(doc, "310001");
		log.info(master.toString());

		// 3. 檢查輸入格式。
		if (master.invalid310001()) {
			log.warn("check : (310001) argument errors.");
			return this.response(doc, valid, business, database);
		}
		valid = true;
		
		// 4. 檢查業務邏輯。
		business = true;
		
		// 5. 讀取資料庫。
		EmailMaster entity = this.dao.uuid(master.getQueryUuid());
		if (entity == null || "99".equals(entity.getStatus())) {
			log.warn("database : (310001) entity was empty.");
			return this.response(doc, valid, business, database);
		}
		database = true;

		// 6. 影像檔記錄。
		this.imageDao.save(new EmailImage(master));

		// 7. 返回下行電文。
		log.info("Mvc310001Serv : OK !");
		return this.response(doc, valid, business, database, entity.getStatus(), entity.getTxStatus());
	}
	
	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------

	/**
	 * 1. 返回下行電文。
	 * @param doc XML文件
	 * @param valid 輸入格式正確
	 * @param business 業務邏輯正確
	 * @param database 資料不存在
	 * @param status 整體交易狀態
	 * @param txStatus 交易明細狀態
	 * @return 字串
	 */
	private String response(Document doc, boolean valid, boolean business, boolean database, 
			String status, String txStatus) {
		
		// 1. 刪除内容並添加空内容。
		Element root = doc.getRootElement();
		Element body = root.element("TxBody");
		root.remove(body);
		body = root.addElement("TxBody");
		
		// 2. 業務邏輯。
		String[] messages = null;
		// (1) 輸入格式
		if (! valid) {
			messages = this.descDao.invalid();
			status = "";
			txStatus = "";
		// (2) 業務邏輯
		} else if (! business) {
			messages = this.descDao.error(ErrorDescDao.uuid);
			status = "";
			txStatus = "";
		// (3) 資料不存在
		} else if (! database) {
			// (2021/12/16 16:20)
			messages = new String[] { "9999", "" };
			status = "99";
			txStatus = "99";
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
		// (5) STATUS
		body.addElement("STATUS").setText(EmptyUtil.orEmpty(status));
		// (6) TX_STATUS
		body.addElement("TX_STATUS").setText(EmptyUtil.orEmpty(txStatus));
		
		// 5. 返回下行電文。
		log.info("outbound: " + doc.asXML());
		return doc.asXML();
	}

	/**
	 * 2. (同名異性式)返回下行電文。
	 * @param valid 輸入格式正確
	 * @param business 業務邏輯正確
	 * @param database 資料不存在
	 * @return 字串
	 */
	private String response(Document doc, boolean valid, boolean business, boolean database) {
		return this.response(doc, valid, business, database, null, null);
	}	
}
