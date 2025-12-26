package com.fubon.mvp.serv;

import java.util.Date;

import javax.annotation.PostConstruct;

import org.dom4j.Document;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailImageDao;
import com.fubon.mvp.data.EmailImage;

import page2020.core.Log;
import page2020.util.TimeUtil;

/**
 * 富邦MVP-MVP084000(下行存活驗證)服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class Mvc084000Serv {

	private static Logger log = LoggerFactory.getLogger(Mvc084000Serv.class);
	
	@Autowired
	private EmailImageDao imageDao;

	/**
	 * 1. 初始程序。
	 */
	@PostConstruct
	public void initial() {
		
		if (Log.test) {
			log.info("initial: mvc084000");
		}
	}
	
	/**
	 * 2. 交易服務。
	 * @param doc 上行XML文件
	 * @return 下行電文
	 */
	public String service(Document doc) {
		
		// log.info("inbound: " + doc.asXML());

		// 1. 讀取電文。
		Element root = (Element) doc.selectSingleNode("//Tx");
		if (root == null) {
			root = doc.addElement("Tx");
		}
		
		// 2. 讀取電文身。
		Element body = root.element("TxBody");
		if (body == null) {
			body = root.addElement("TxBody");
		}
		
		// 3. 檢查並移除識別欄位。
		Element tranCode = body.element("TRAN_CODE");
		if (tranCode != null) {
			body.remove(tranCode);
		}
		Element datetime = body.element("MVP_TIME");
		if (datetime != null) {
			body.remove(datetime);
		}
		
		// 4. 追加識別欄位。
		Date date = new Date();
		body.addElement("TRAN_CODE").setText("084000");
		body.addElement("MVP_TIME").setText(String.format("%s %s", TimeUtil.dateE(date), TimeUtil.timeE(date)));

		// 5. 記錄資料庫。
		this.imageDao.save(new EmailImage(doc, "084000"));
		
		// 返回：下行電文。
		// log.info("outbound: " + doc.asXML());
		return doc.asXML();
	}
}
