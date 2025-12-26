package com.fubon.mvp.serv;

import javax.annotation.PostConstruct;

import org.dom4j.Document;
import org.dom4j.Node;
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
import page2020.util.EmptyUtil;

/**
 * 富邦MVP-MVC110006(收件狀態)服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class Mvc110006Serv {
	
	private static Logger log = LoggerFactory.getLogger(Mvc110006Serv.class);
	
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
			log.info("initial: mvc110006");
		}
	}
	
	/**
	 * 2. 交易服務。
	 * @param doc 上行XML文件
	 * @return 下行電文
	 */
	public String service(Document doc) {
		
		log.info("inbound: " + doc.asXML());
		
		// 1. 讀取輸入參數。
		String uuid = null;
		Node node = doc.selectSingleNode("//UUID");
		if (node != null) {
			uuid = node.getText();
		}
				
		// 2. 檢查輸入格式。
		// (1) UUID不可為空值。
		if (EmptyUtil.is(uuid)) {
			log.warn("check: (110006) argument has error.");
			return this.service.response("110006", false).asXML();
		}
		
		// 3. 檢查資料庫中UUID是否存在。
		EmailMaster master = this.dao.uuid(uuid);
		if (master == null || "99".equals(master.getStatus())) {
			log.warn("check : (110006) entity was missing.");
			return this.service.response("110006", false).asXML();			
		}
				
		// 4. 確認只進不退狀態："00+小於13"。 
		log.info("before : " + master.toString());
		if (! (("00".equals(master.getStatus()) || "02".equals(master.getStatus())) 
				&& ("13".compareTo(master.getTxStatus()) > 0))) {
			this.dao.save(new EmailDetail(master, "13"));		// (1)明細檔。
			this.imageDao.save(new EmailImage(master, "13"));	// (2)影像檔。
			log.warn("check : (110006) was NOT necessary.");
			return this.service.response("110006", true).asXML();			
		}

		/*
		// (2022/01/06 15:52)
		// 5. 由"ENG_NAME" = "250"判斷成功。
		boolean success = true;
		node = doc.selectSingleNode("//ENG_NAME");
		if (node != null) {
			String engName = node.getText();
			if (engName.length() > 0 && (! engName.trim().startsWith("250"))) {
				success = false;
			}
		}
		*/
		
		// 6. 更改"交易狀態"值。
		// (1) 主檔。
		master.setTranCode("110006");
		master.setStatus("00");		// "00": 申請中。
		master.setTxStatus("13");	// "13": 客戶收到。
		master.setErrorCode("");
		Exception ex = this.dao.save(master);
		if (ex != null) {
			log.warn("database : (110006) database has error.");
			return this.service.response("110006", false).asXML();
		}
		// (2) 明細檔。
		ex = this.dao.save(new EmailDetail(master));
		if (ex != null) {
			log.warn("service: (110006) email detail error.");
			return this.service.response("110006", false).asXML();
		}
		// (3) 影像檔記錄。
		this.imageDao.save(new EmailImage(master));
		log.info("after : " + master.toString());
		
		// 7. 返回下行電文。
		log.info("Mvc110006Serv : OK !");
		return this.service.response("110006", true).asXML();
	}
}
