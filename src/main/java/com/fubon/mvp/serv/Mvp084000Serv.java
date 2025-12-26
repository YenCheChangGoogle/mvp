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

import com.fubon.mvp.dao.EmailImageDao;
import com.fubon.mvp.data.EmailImage;

import page2020.client.WebProxy;
import page2020.core.Log;
import page2020.util.TimeUtil;
import reactor.core.publisher.Mono;

/**
 * 富邦MVP-MVP084000(上行存活驗證)服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class Mvp084000Serv {

	private static Logger log = LoggerFactory.getLogger(Mvp084000Serv.class);

	@Autowired
	private WebProxy proxy;
	@Autowired
	private EmailImageDao dao;
	@Autowired
	private EmailStatusServ service;
	
	// 啓動JOB設定值。
	@Value("${default.job.2}")
	private String defaultJob2;
	// 啓動JOB開關。
	private boolean job;
		
	/**
	 * 1. 初始程序
	 */
	@PostConstruct
	public void initial() {
		
		this.job = "1".equals(this.defaultJob2);	// 啓動JOB開關。
		if (Log.test) {
			log.info("initial: mvp084000");
		}
	}

	/**
	 * 2. 定時執行。
	 */
	@Scheduled(fixedRate=60000)
	public void schudule() {
		
		// 是否啓動定時JOB？
		if (! this.job) {
			return;
		}

		// 1. 組合上行電文。
		String uuid = this.service.uuid("084000");
		Document doc = this.tranx("MVP084000", uuid);
		
		// 2. 發送給ESB，並接受下行電文。
		try {
			this.proxy.api("esb")
				.body(Mono.just(doc.asXML()), String.class).retrieve()
				.bodyToMono(String.class).block();
		} catch (Exception ex) {
			log.error(ex.toString());
			return;
		}

		// 3. 記錄資料庫。
		this.dao.save(new EmailImage(uuid, "084000"));
	}

	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------

	/**
	 * 1. 組合電文。
	 * @param txnId 交易代號，如: MVP067000
	 * @param uuid UUID識別值
	 * @return XML文件
	 */
	private Document tranx(String txnId, String uuid) {
	
		// 1. 組合上行電文。
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("Tx");
		
		// (1) FMPConnectionString
		Element conn = root.addElement("FMPConnectionString");
		conn.addElement("SPName").setText("BKMVP");
		conn.addElement("LoginID").setText("BKMVP");
		conn.addElement("TxnId").setText(txnId);
		
		// (2) TxHead
		Element head = root.addElement("TxHead");
		head.addElement("HTXTID").setText(txnId);
		head.addElement("HWSID").setText("BKMVP");
		head.addElement("HTLID").setText("2004115");
		head.addElement("HSTANO").setText("7898411");
		head.addElement("TXMSRN");
		head.addElement("HSYDAY").setText(TimeUtil.taiwanDate());
		
		// (3) TxBody
		Element body = root.addElement("TxBody");
		String txCode = txnId.substring(3);
		// 1. UUID	該筆交易UUID
		body.addElement("UUID").setText(uuid);;
		// 2. BRANCH	交易行
		body.addElement("BRANCH").setText("00200");
		// 3. TELLER_NO	櫃員代號
		body.addElement("TELLER_NO").setText("99946899");
		// 4. TX_CODE	交易代號
		body.addElement("TX_CODE").setText(txCode);
		// 5. CHNL	通路
		body.addElement("CHNL").setText("0Z");
		// 6. SUB_CHNL	子通路
		body.addElement("SUB_CHNL").setText("89");
		// 7. CUST_ID	身份證統一編號/居留證號
		body.addElement("CUST_ID");
		// 8. ID_TYPE	ID種類
		body.addElement("ID_TYPE");
		// 9. CUST_NAME	中文戶名(全部全形)
		body.addElement("CUST_NAME");
		// 10. ENG_NAME	英文戶名(全部全形或全部半形)
		body.addElement("ENG_NAME");
		// 11. PREV_EMAIL_ADDR	變更前EMAIL
		body.addElement("PREV_EMAIL_ADDR");
		// 12. AFTER_EMAIL_ADDR	變更後EMAIL
		body.addElement("AFTER_EMAIL_ADDR");
		// 13. FROM_DATE	查詢交易起日期
		body.addElement("FROM_DATE");
		// 14. TO_DATE	查詢交易訖日期
		body.addElement("TO_DATE");
		// 15. REASON	相同原因
		body.addElement("REASON");
		// 16. REMARK	備註
		body.addElement("REMARK");
		// 17. ON_OFF_LINE	線上:Y，線下:N
		body.addElement("ON_OFF_LINE");
		// 18. QUERY_UUID	查詢UUID
		body.addElement("QUERY_UUID");
		// 19. NEXT_KEY	NextKey
		body.addElement("NEXT_KEY");
		
		// 2. 返回XML文件。
		return doc;
	}
}
