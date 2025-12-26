package com.fubon.mvp.serv;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailDao;
import com.fubon.mvp.dao.EmailHostDao;
import com.fubon.mvp.dao.EmailImageDao;
import com.fubon.mvp.dao.ErrorDescDao;
import com.fubon.mvp.data.EmailDetail;
import com.fubon.mvp.data.EmailImage;
import com.fubon.mvp.data.EmailMaster;

import page2020.client.WebProxy;
import page2020.core.Log;
import page2020.util.TimeUtil;
import reactor.core.publisher.Mono;

/**
 * 富邦MVP-MVP067000(通知ESB更新客戶)服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class Mvp067000Serv {

	private static Logger log = LoggerFactory.getLogger(Mvp067000Serv.class);
	
	@Autowired
	private EmailDao dao;
	@Autowired
	private EmailHostDao hostDao;
	@Autowired
	private EmailImageDao imageDao;
	@Autowired
	private WebProxy proxy;
	@Autowired
	private ErrorDescDao error;
	@Autowired
	private EmailStatusServ service;
	
	// ESB無法連綫("E010")。
	private String notEsbCode;
	
	/**
	 * 1. 初始程序
	 */
	@PostConstruct
	public void initial() {
		
		this.notEsbCode = this.error.notEsb()[0];	// "E010": 無法連綫。
		if (Log.test) {
			log.info("initial: mvp067000");
		}
	}
	
	/**
	 * 2. 定時執行。
	 */
	@Scheduled(fixedDelay=3000)
	public void schedule() {
	
		// 1. 是主服務器？
		if (! this.hostDao.isMain()) {
			return;
		}
		
		// 2. 讀取 JOB4 清單。
		List<EmailMaster> job4List = new ArrayList<EmailMaster>();
		// (1) "15"(收到客戶確認)"
		List<EmailMaster> applyList = this.dao.txStatus("15");
		if (! applyList.isEmpty()) {
			job4List.addAll(applyList);
		}
		// (2) "02+20+E010"(無法連綫)
		List<EmailMaster> errorList = this.dao.error("02", "20", this.notEsbCode);
		if (! errorList.isEmpty()) {
			job4List.addAll(errorList);
		}

		// 3. 執行 JOB4 程序。
		for (EmailMaster item : job4List) {
			
			// (1) 避免時間差問題，再查詢一次。
			EmailMaster master = this.dao.uuid(item.getUuid());
			log.info("before : " + master.toString());
			if (! (("00".equals(master.getStatus()) && "15".equals(master.getTxStatus())) || 
					("02".equals(master.getTxStatus()) && "20".equals(master.getTxStatus())
						&& this.notEsbCode.equals(master.getErrorCode())))) {
				log.warn("check : (067000) was NOT necessary.");
				continue;
			}
			
			// (2) 保存狀態資料(送ESB之前)。
			String oldStatus = master.getStatus();
			String oldError = master.getErrorCode();
			master.setTranCode("067000");
			master.setStatus("00");		// "00": 處理中
			master.setTxStatus("20");	// "20": 發送前
			master.setErrorCode("");
			this.dao.save(master);
			this.dao.save(new EmailDetail(master));
			this.imageDao.save(new EmailImage(master));
			log.info("after : " + master.toString());
			
			// (3) 組合上行電文。
			Document doc = this.tranx(master);
			log.info("request: " + doc.asXML());
			
			// (4) 發送上行電文給ESB。
			String xml = null;
			try {
				xml = this.proxy.api("esb")
					.body(Mono.just(doc.asXML()), String.class).retrieve()
					.bodyToMono(String.class).block();
				log.info("response: " + xml);
			} catch (Exception ex) {
				// ESB無法連綫。
				if (! ("02".equals(oldStatus) && this.notEsbCode.equals(oldError))) {
					master.setStatus("02");
					master.setErrorCode(this.notEsbCode);
					this.dao.save(master);
					this.dao.save(new EmailDetail(master));
					this.imageDao.save(new EmailImage(master));
				}
				log.error(ex.toString());
				continue;
			}
			
			// (5) 設定執行狀態值。
			Document response = this.proxy.document(xml); 
			String errId = response.selectSingleNode("//HERRID").getText();
			String message = null;
			boolean query = false;
			
			// 1. 更新狀態。
			master.setTxStatus("21");	// 21=發送核心
			
			// 2. 設定回傳結果。
			// (1) ESB核心回傳成功("0000")
			if ("0000".equals(errId)) {
				master.setStatus("01");		// "01"=成功
				master.setTxStatus("00");	// "00"=成功				
				/*
				// 依據"通路表"決定是否進入"31"(發送回應前臺)。
				String resp = this.channelDao.response(master.getChannel(), master.getSubChannel());
				if (EmptyUtil.not(resp)) {
					master.setStatus("00");		// "00"=處理中
					master.setTxStatus("31");	// "31"=發送回應前臺
				} else {
					save = false;
				}
				*/
				
			// (2) "x105"碼
			} else if ("x105".equals(errId)) {
				master.setStatus("02");		// 02=失敗
				master.setErrorCode(errId);
				query = true;
				
			// (3) 失敗
			} else {
				master.setStatus("02");		// 02=失敗
				master.setErrorCode(this.proxy.value(response, "EMSGID"));
				message = this.proxy.value(response, "EMSGTXT");
			}
			
			// 3. 儲存資料。
			this.dao.save(master);
			this.dao.save(new EmailDetail(master));
			this.imageDao.save(new EmailImage(master));
			
			// 4. 再查詢。	
			if (query) {
				this.service.addToPool("084023", master);
			}
			
			// 日誌
			if (! "02".equals(master.getStatus())) {
				log.info("Mvp067000Serv: OK !");
			} else {
				log.info("Mvp067000Serv: errId='" + errId + "', status=" + master.getStatus() + ", tx-status="
					+ master.getTxStatus() + ", errorCode=" + master.getErrorCode() + ", message='" + message + "'");				
			}
		}
	}
	
	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------

	/**
	 * 1. 組合上行電文。
	 * @param master 郵件主檔
	 * @return XML文件
	 */
	private Document tranx(EmailMaster master) {
		
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("Tx");

		// (1) FMPConnectionString
		Element conn = root.addElement("FMPConnectionString");
		conn.addElement("SPName").setText("BKMVP");
		conn.addElement("LoginID").setText("BKMVP");
		conn.addElement("TxnId").setText("MVP067000");
		
		// (2) TxHead
		Element head = root.addElement("TxHead");
		head.addElement("HTXTID").setText("MVP067000");
		head.addElement("HWSID").setText("BKMVP");
		head.addElement("HTLID").setText("2004115");
		head.addElement("HSTANO").setText("7898411");
		head.addElement("TXMSRN");
		head.addElement("HSYDAY").setText(TimeUtil.taiwanDate());
		
		// (3) TxBody
		Element body = root.addElement("TxBody");
		body.addElement("uuid").setText(master.getUuid());
		//body.addElement("branch").setText(master.getBranch());
		//body.addElement("teller").setText(master.getTeller());
		body.addElement("branch").setText("00200");
		body.addElement("teller").setText("99946899");
		body.addElement("IDTYPE").setText(master.getIdType());
		body.addElement("IDNO").setText(master.getIdNo());
		body.addElement("email").setText(master.getAfterEmail());
		return doc;
	}
}
