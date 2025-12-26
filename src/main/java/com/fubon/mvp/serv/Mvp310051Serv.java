package com.fubon.mvp.serv;

import java.util.ArrayList;
import java.util.Date;
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
 * 富邦MVP-MVP310051(狀態回送前臺)服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class Mvp310051Serv {

	private static Logger log = LoggerFactory.getLogger(Mvp310051Serv.class);
	
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
	
	// ESB無法連綫("E010")。
	private String notEsb;

	/**
	 * 1. 初始程序
	 */
	@PostConstruct
	public void initial() {
		
		this.notEsb = this.error.notEsb()[0];	// "E010"
		if (Log.test) {
			log.info("initial: mvp310051");
		}
	}
	
	/**
	 * 2. 定時 JOB5 程序。
	 */
	@Scheduled(fixedDelay=3000)
	public void schedule() {
	
		// 1. 是主服務器？
		if (! this.hostDao.isMain()) {
			return;
		}
		
		// 2. 讀取 JOB5 清單(TX_STATUS=31)。
		List<EmailMaster> job5List = new ArrayList<EmailMaster>();
		// (1) "31"(發送回應前臺)
		List<EmailMaster> applyList = this.dao.txStatus("31");
		if (! applyList.isEmpty()) {
			job5List.addAll(applyList);
		}
		// (2) "02+31+E010"(無法連綫)
		List<EmailMaster> errorList = this.dao.error("02", "31", this.notEsb);
		if (! errorList.isEmpty()) {
			job5List.addAll(errorList);
		}
		
		// 3. 執行 JOB5 程序。
		for (EmailMaster master : job5List) {
			
			// 1. 保存狀態資料(發送之前)。
			String oldStatus = master.getStatus();
			String oldError = master.getErrorCode();
			master.setTranCode("310051");
			master.setStatus("00");		// 00=處理中
			master.setErrorCode("");
			this.dao.save(master);
			this.dao.save(new EmailDetail(master));
			this.imageDao.save(new EmailImage(master));
			
			// 2. 組合上行電文。
			Document doc = DocumentHelper.createDocument();
			Element root = doc.addElement("Tx");
			// (1) FMPConnectionString
			Element conn = root.addElement("FMPConnectionString");
			conn.addElement("SPName").setText("BKMVP");
			conn.addElement("LoginID").setText("BKMVP");
			conn.addElement("TxnId").setText("MVP310051");
			// (2) TxHead
			Element head = root.addElement("TxHead");
			head.addElement("HTXTID").setText("MVP310051");
			head.addElement("HWSID").setText("BKMVP");
			head.addElement("HTLID").setText("2004115");
			head.addElement("HSTANO").setText("7898411");
			head.addElement("TXMSRN");
			head.addElement("HSYDAY").setText(TimeUtil.taiwanDate());
			// (3) TxBody
			Element body = root.addElement("TxBody");
			// uuid	uuid
			body.addElement("uuid").setText(master.getUuid());
			// TX_DATE	交易日期
			Date date = new Date();
			body.addElement("TX_DATE").setText(TimeUtil.dateE(date));
			// TX_TIME	交易時間
			body.addElement("TX_TIME").setText(TimeUtil.dateE(date));
			// STATUS	交易整體狀態
			body.addElement("STATUS").setText(master.getStatus());
			// TX_STATUS	交易明細狀態
			body.addElement("TX_STATUS").setText(master.getTxStatus());
			// ERR_CODE	ERR_CODE
			String[] errorDesc = this.error.error(master.getErrorCode());
			this.proxy.addElement(body, "ERR_CODE", errorDesc[0]);
			// ERR_DESC	ERR_DESC
			this.proxy.addElement(body, "ERR_DESC", errorDesc[1]);
			
			// 3. 發送上行電文給ESB。
			String xml = null;
			try {
				xml = this.proxy.api("esb")
					.body(Mono.just(doc.asXML()), String.class).retrieve()
					.bodyToMono(String.class).block();
				log.info("response: " + xml);
			} catch (Exception ex) {
				// ESB無法連綫。
				if (! ("02".equals(oldStatus) && this.notEsb.equals(oldError))) {
					master.setStatus("02");
					master.setErrorCode(this.notEsb);
					this.dao.save(master);
					this.dao.save(new EmailDetail(master));
					this.imageDao.save(new EmailImage(master));
				}
				log.error(ex.toString());
				continue;
			}

			// 4. 記錄執行狀態。
			Document response = this.proxy.document(xml);
			String email = response.selectSingleNode("//email").getText();
			String message = null;
			// (1) 成功
			if (master.getAfterEmail().equals(email)) {
				master.setStatus("01");		// 01=成功完成
				master.setTxStatus("00");	// 00=全部完成
				master.setErrorCode("");
			// (2) 失敗
			} else {
				master.setStatus("02");
				master.setErrorCode(response.selectSingleNode("//EMSGID").getText());
				message = response.selectSingleNode("//EMSGTXT").getText();
			}
			this.dao.save(master);
			this.dao.save(new EmailDetail(master));
			this.imageDao.save(new EmailImage(master));
			
			// 日誌
			if (! "02".equals(master.getStatus())) {
				log.info("Mvp310051Serv: OK !");
			} else {
				log.info("Mvp310051Serv: email='" + email + "', status=" + master.getStatus() + ", tx-status="
					+ master.getTxStatus() + ", errorCode=" + master.getErrorCode() + ", message='" + message + "'");
			}
		}
	}
}
