package com.fubon.mvp.serv;

import javax.annotation.PostConstruct;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailDao;
import com.fubon.mvp.dao.EmailImageDao;
import com.fubon.mvp.data.EmailDetail;
import com.fubon.mvp.data.EmailImage;
import com.fubon.mvp.data.EmailMaster;

import page2020.client.WebProxy;
import page2020.core.Log;
import page2020.util.TimeUtil;
import reactor.core.publisher.Mono;

/**
 * 富邦MVP-MVP084023(異常代碼查詢)服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class Mvp084023Serv {

	private static Logger log = LoggerFactory.getLogger(Mvp084023Serv.class);

	@Autowired
	private EmailStatusServ service;
	@Autowired
	private WebProxy proxy;
	@Autowired
	private EmailDao dao;
	@Autowired
	private EmailImageDao imageDao;
	
	// 啓動JOB設定值。
	@Value("${default.job.1}")
	private String defaultJob1;
	// 啓動JOB開關。
	private boolean job;

	/**
	 * 1. 初始程序
	 */
	@PostConstruct
	public void initial() {

		this.job = "1".equals(this.defaultJob1);	// 啓動JOB開關。
		if (Log.test) {
			log.info("initial: mvp084023");
		}
	}

	/**
	 * 2. 定時執行。
	 */
	@Scheduled(fixedDelay=1000)
	public void schudule() {
		
		// 是否啓動定時JOB？
		if (! this.job) {
			return;
		}

		// 讀取信息池("084023")。
		EmailMaster master = null;
		while ((master = (EmailMaster) this.service.removeFromPool("084023")) != null) {

			// (1) 組合上行電文。
			Document doc = this.tranx(master);
			log.info("inbound: " + doc.asXML());
			
			// (2) 發送給ESB。
			String xml = null;
			try {
				xml = this.proxy.api("esb")
					.body(Mono.just(doc.asXML()), String.class).retrieve()
					.bodyToMono(String.class).block();
				log.info("outbound: " + xml);
			} catch (Exception ex) {
				log.error(ex.toString());
				return;				
			}

			/*
			67000收到 esb回應 x105 
			會啟動084023發查 核心 
			如果收到的回應代碼為0188
			就會更新tx-status為15
			這樣定時job4就會重送至核心
			 */
			
			// (3) 解析下行電文。
			Document response = this.proxy.document(xml);
			String errCode = this.proxy.value(response, "OUT-ERR-MSG-NO");
			boolean success = "0188".equals(errCode);
			if (success) {
				master.setStatus("00");		// 00=處理中
				master.setTxStatus("15");	// 15=收到客戶確認
				master.setErrorCode("");
			} else {
				master.setStatus("02");		// 02=失敗
				master.setTxStatus("21");	// 21=發送核心
				master.setErrorCode(errCode);
			}
			
			// (4) 儲存資料庫。
			master.setTranCode("084023");
			this.dao.save(master);
			this.dao.save(new EmailDetail(master));
			this.imageDao.save(new EmailImage(master));
		 
			// 日誌
			if (success) {
				log.info("Mvp084023Serv : OK !");
			} else {
				log.info("ESB: errCode=" + errCode);
			}
		}
	}

	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------

	/**
	 * 1. 產生上行電文。
	 * @param master 郵件主檔
	 * @return XML文件
	 */
	private Document tranx(EmailMaster master) {
		
		// 產生上行電文。
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("Tx");
		
		// (1) FMPConnectionString
		Element conn = root.addElement("FMPConnectionString");
		conn.addElement("SPName").setText("BKMVP");
		conn.addElement("LoginID").setText("BKMVP");
		conn.addElement("TxnId").setText("MVP084023");
		
		// (2) TxHead
		Element head = root.addElement("TxHead");
		head.addElement("HTXTID").setText("MVP084023");
		head.addElement("HWSID").setText("BKMVP");
		head.addElement("HTLID").setText("2004115");
		head.addElement("HSTANO").setText("7898411");
		head.addElement("TXMSRN");
		head.addElement("HSYDAY").setText(TimeUtil.taiwanDate());
		
		// (3) TxBody
		Element body = root.addElement("TxBody");
		body.addElement("uuid").setText(master.getUuid());
		
		return doc;
	}
}
