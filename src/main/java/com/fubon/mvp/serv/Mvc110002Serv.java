package com.fubon.mvp.serv;

import javax.annotation.PostConstruct;

import org.dom4j.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailDao;
import com.fubon.mvp.dao.EmailImageDao;
import com.fubon.mvp.data.EmailDetail;
import com.fubon.mvp.data.EmailImage;
import com.fubon.mvp.data.EmailMaster;

import page2020.core.Log;

/**
 * 富邦MVP-MVC110002(取消申請)服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class Mvc110002Serv {

	private static Logger log = LoggerFactory.getLogger(Mvc110002Serv.class);

	@Autowired
	private EmailDao dao;
	@Autowired
	private EmailImageDao imageDao;
	@Autowired
	private EmailStatusServ service;
	
	/**
	 * 1. 初始程序。
	 */
	@PostConstruct
	public void initial() {
		
		if (Log.test) {
			log.info("initial: mvc110002");
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
		boolean business = true;
		// (3) 無資料庫異常。
		boolean database = false;
		
		// 2. 創建上行電文實體。
		EmailMaster master = new EmailMaster(doc, "110002");	// 交易代號：110002。
		log.info(master.toString());
		
		// 3. 檢查作廢條件(2022/01/24 12:00)->(2024/07/10 10:00)。
		//if (! master.isCancel()) {
		//	log.warn("check : (110002) argument errors.");
		//	return this.service.response(doc, valid, business, database, true).asXML();			
		//}
		//valid = true;
		
		boolean cancel = master.isCancel();
		if ((! cancel) && master.invalid110002()) {
			log.warn("check : (110002) argument errors.");
			return this.service.response(doc, valid, business, database, true).asXML();
		}
		valid = true;
				
		// 4. 進行中的申請單作廢。
		this.service.cancel(master.getIdNo(), "110002", master);
		
		// 5. 新增交易紀錄。
		// (1) 主檔紀錄。
		master.setStatus("99");		// "99": 作廢
		master.setTxStatus("99");	// "99": 作廢
		master.setErrorCode("0099");
		Exception ex = this.dao.save(master);
		if (ex != null) {
			log.warn("service: email master error.");
			return this.service.response(doc, valid, business, database, true, ex.toString()).asXML();
		}
		// (2) 明細檔紀錄。
		ex = this.dao.save(new EmailDetail(master));
		if (ex != null) {
			log.warn("service: email detail error.");
			return this.service.response(doc, valid, business, database, true, ex.toString()).asXML();
		}
		// (3) 影像檔記錄。
		this.imageDao.save(new EmailImage(master));
		database = true;
		
		// 7. 返回下行電文。
		log.info("Mvc110002Serv : OK !");
		return this.service.response(doc, valid, business, database, true).asXML();
	}
}
