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
 * 富邦MVP-MVC110007(客戶確認信件)服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class Mvc110007Serv {
	
	private static Logger log = LoggerFactory.getLogger(Mvc110007Serv.class);
	
	@Autowired
	private EmailDao dao;
	@Autowired
	private EmailStatusServ service;
	@Autowired
	private EmailImageDao imageDao;
	
	/**
	 * 1. 初始程序。
	 */
	@PostConstruct
	public void initial() {
		
		if (Log.test) {
			log.info("initial: mvc110007");
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
			log.warn("check: (110007) argument has error.");
			return this.service.response("110007", false).asXML();
		}
		
		// 3. 檢查資料庫中UUID是否存在。
		EmailMaster master = this.dao.uuid(uuid);
		if (master == null || "99".equals(master.getStatus())) {
			log.warn("check : (110007) entity was missing.");
			return this.service.response("110007", false).asXML();			
		}
		
		// 4. 確認只進不退狀態："00+小於15"。
		log.info("before : " + master.toString());
		if (! (("00".equals(master.getStatus()) || "02".equals(master.getStatus())) 
				&& ("15".compareTo(master.getTxStatus()) > 0))) {
			this.dao.save(new EmailDetail(master, "15"));		// (1)明細檔。
			this.imageDao.save(new EmailImage(master, "15"));	// (2)影像檔。
			log.warn("check : (110007) was NOT necessary.");
			return this.service.response("110007", true).asXML();						
		}

		// 5. 更改"交易狀態"值。
		// (1) 主檔。
		master.setTranCode("110007");
		master.setStatus("00");		// "00": 申請中。
		master.setTxStatus("15");	// "15": 客戶確認。
		master.setErrorCode("");
		Exception ex = this.dao.save(master);
		if (ex != null) {
			log.warn("database : (110007) database has error.");
			return this.service.response("110007", false).asXML();
		}
		// (2) 明細檔。
		ex = this.dao.save(new EmailDetail(master));
		if (ex != null) {
			log.warn("service: (110007) email detail error.");
			return this.service.response("110007", false).asXML();
		}
		// (3) 影像檔記錄。
		this.imageDao.save(new EmailImage(master));
		log.info("after : " + master.toString());
		
		// 6. 返回下行電文。
		log.info("Mvc110007Serv : OK !");
		return this.service.response("110007", true).asXML();
	}
}
