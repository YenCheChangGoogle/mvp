package com.fubon.mvp.serv;

import java.util.List;

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

import page2020.client.WebProxy;
import page2020.core.Log;
import page2020.util.EmptyUtil;

/**
 * 富邦MVP-MVC310002(前臺查詢-單/多)服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class Mvc310002Serv {

	private static Logger log = LoggerFactory.getLogger(Mvc310001Serv.class);
	
	@Autowired
	private EmailDao dao;
	@Autowired
	private ErrorDescDao descDao;
	@Autowired
	private WebProxy proxy;
	@Autowired
	private EmailImageDao imageDao;
	
	/**
	 * 初始程序。
	 */
	@PostConstruct
	public void initial() {
		
		if (Log.test) {
			log.info("initial: mvc310002");
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

		// 2. 創建上行電文實體。
		EmailMaster master = new EmailMaster(doc, "310002");
		log.info(master.toString());
		
		// 3. 檢查輸入格式。
		if (master.invalid310002()) {
			log.warn("check : (310002) argument errors.");
			return this.body(doc, master, "", valid, business).asXML();
		}
		valid = true;

		// 4. 檢查業務邏輯。
		List<EmailMaster> entities = this.dao.query(master);
		int size = entities.size();
		if (size == 0) {
			log.info("database : (310002) no entity.");
			return this.body(doc, master, "", valid, business).asXML();
		}
		business = true;

		// 5. 影像檔記錄。
		this.imageDao.save(new EmailImage(master));

		// 6. 由 NEXT_KEY 決定資料行。
		String nextKey = master.getNextKey();		
		String newValue = "";
		int begin = 0, end = 0;
		String hReturn = "E";	// (結束='E', 尚有='C')
		// (1) 空值
		if (EmptyUtil.is(nextKey)) {
			begin = 0;
			if (size > 30) {
				end = 30;
				newValue = "30";
				hReturn = "C";
			} else {
				end = size;
			}
		// (2) 數字值
		} else {
			begin = Integer.valueOf(nextKey).intValue();
			if (size > (begin + 30)) {
				end = begin + 30;
				newValue = String.valueOf(end);
				hReturn = "C";
			} else {
				end = size;
			}
		}
		log.info("310002: UUID='" + master.getUuid() + "', CUST_ID='" + master.getIdNo() + "', "
				+ "NEXT_KEY='" + master.getNextKey() + "', begin=" + begin + ", end=" + end);

		// 7. 裝填頭部區信息。
		this.head(doc, hReturn);
		
		// 8. 裝填身體區信息。
		this.body(doc, master, newValue, valid, business);
		
		// 9. 裝填循環信息。
		Element body = (Element) doc.selectSingleNode("//TxBody");
		int count = 0;
		for (int i=begin; i < end; i++) {

			// 資料實體。
			EmailMaster data = entities.get(i);
			
			// 包裝節點。
			Element repeat = body.addElement("TxRepeat");
			
			// (1) BRANCH
			this.proxy.addElement(repeat, "BRANCH", data.getBranch());
			// (2) TELLER
			this.proxy.addElement(repeat, "TELLER", data.getTeller());
			// (3) PREV_EMAIL_ADDR		變更前EMAIL
			this.proxy.addElement(repeat, "PREV_EMAIL_ADDR", data.getPrevEmail());
			// (4) AFTER_EMAIL_ADDR		變更後EMAIL
			this.proxy.addElement(repeat, "AFTER_EMAIL_ADDR", data.getAfterEmail());
			// (5) CHNL_NAME	通路名稱
			this.proxy.addElement(repeat, "CHNL_NAME", data.getChannel());
			// (6) REASON		理由
			this.proxy.addElement(repeat, "REASON", data.getReason());
			// (7) TX_DATE		交易日期
			this.proxy.addElement(repeat, "TX_DATE", data.getChangeDate());
			// (8) TX_TIME		交易時間
			this.proxy.addElement(repeat, "TX_TIME", data.getChangeTime());
			// (9) UUID			UUID
			this.proxy.addElement(repeat, "UUID", data.getUuid());
			// (10) ON_OFF_LINE	是否已即時更新
			this.proxy.addElement(repeat, "ON_OFF_LINE", data.getOnline());
			// (11) STATUS		交易整體狀態
			this.proxy.addElement(repeat, "STATUS", data.getStatus());
			// (12) TX_STATUS	交易明細狀態
			this.proxy.addElement(repeat, "TX_STATUS", data.getTxStatus());
			// (13) DESCRIPTION	狀態敘述
			this.proxy.addElement(repeat, "DESCRIPTION", this.descDao.error(data.getErrorCode())[1]);
			// (14) ERR_CODE	錯誤代碼
			this.proxy.addElement(repeat, "ERR_CODE", data.getErrorCode());
			// (15) REMARK		備註
			this.proxy.addElement(repeat, "REMARK", data.getRemark());
			// 計數。
			count += 1;
		}
		
		// 10. 補到30筆。
		while (count < 30) {

			// 包裝節點。
			Element repeat = body.addElement("TxRepeat");

			// 空白資料。
			// (1) BRANCH
			this.proxy.addElement(repeat, "BRANCH", "");
			// (2) TELLER
			this.proxy.addElement(repeat, "TELLER", "");
			// (3) PREV_EMAIL_ADDR		變更前EMAIL
			this.proxy.addElement(repeat, "PREV_EMAIL_ADDR", "");
			// (4) AFTER_EMAIL_ADDR		變更後EMAIL
			this.proxy.addElement(repeat, "AFTER_EMAIL_ADDR", "");
			// (5) CHNL_NAME	通路名稱
			this.proxy.addElement(repeat, "CHNL_NAME", "");
			// (6) REASON		理由
			this.proxy.addElement(repeat, "REASON", "");
			// (7) TX_DATE		交易日期
			this.proxy.addElement(repeat, "TX_DATE", "");
			// (8) TX_TIME		交易時間
			this.proxy.addElement(repeat, "TX_TIME", "");
			// (9) UUID			UUID
			this.proxy.addElement(repeat, "UUID", "");
			// (10) ON_OFF_LINE	是否已即時更新
			this.proxy.addElement(repeat, "ON_OFF_LINE", "");
			// (11) STATUS		交易整體狀態
			this.proxy.addElement(repeat, "STATUS", "");
			// (12) TX_STATUS	交易明細狀態
			this.proxy.addElement(repeat, "TX_STATUS", "");
			// (13) DESCRIPTION	狀態敘述
			this.proxy.addElement(repeat, "DESCRIPTION", "");
			// (14) ERR_CODE	錯誤代碼
			this.proxy.addElement(repeat, "ERR_CODE", "");
			// (15) REMARK		備註
			this.proxy.addElement(repeat, "REMARK", "");
			
			// 計數。
			count += 1;
		}
		log.info("outbound: " + doc.asXML());
				
		// 8. 返回下行電文。
		log.info("Mvc310002Serv : OK !");
		return doc.asXML();
	}
	
	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------

	/**
	 * 1. XML文件的頭部區。
	 * @param doc XML文件。
	 * @param hReturn 判斷值(結束='E', 尚有='C')
	 * @return XML文件
	 */
	private Document head(Document doc, String hReturn) {
		
		// 1. 確認有頭部區標簽。
		Element root = doc.getRootElement();
		Element head = root.element("TxHead");
		if (head == null) {
			head = root.addElement("TxHead");
		}
		
		// 2. 確認有判斷值標簽。
		Element headReturn = head.element("HRETRN");
		if (headReturn == null) {
			headReturn = head.addElement("HRETRN");
		}
		
		// 3. 設定判斷值的值。
		headReturn.setText(hReturn);
		return doc;
	}
	
	/**
	 * 2. XML文件的身體區。
	 * @param doc XML文件。
	 * @param master 上行電文的解析信息
	 * @param nextKey 下一次主鍵值
	 * @param valid 輸入格式正確
	 * @param business 業務邏輯正確
	 * @return XML文件
	 */
	private Document body(Document doc, EmailMaster master, String nextKey, boolean valid, boolean business) {
		
		// 1. 刪除内容並添加空内容。
		Element root = doc.getRootElement();
		Element body = root.element("TxBody");
		root.remove(body);
		body = root.addElement("TxBody");
		
		// 2. 基本資料欄位。 
		// (1) CUST_ID		身份證統一編號/居留證號
		body.addElement("CUST_ID").setText(master.getIdNo());
		// (2) ID_TYPE		ID種類
		body.addElement("ID_TYPE").setText(master.getIdType());
		// (3) CUST_NAME	中文戶名(全部全形)
		body.addElement("CUST_NAME").setText(master.getChName());
		// (4) ENG_NAME		英文戶名(全部全形或全部半形)
		body.addElement("ENG_NAME").setText(master.getEnName());
		// (5) NEXT_KEY		Next Key
		body.addElement("NEXT_KEY").setText(nextKey);
		
		// 3. 返回XML文件。
		return doc;
	}	
}
