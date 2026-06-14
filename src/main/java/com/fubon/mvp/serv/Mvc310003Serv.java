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
 * 富邦MVP-MVC310003(查詢明細)服務器
 * @author 張晏哲
 * @category 服務類
 */
@Service
public class Mvc310003Serv {

	private static Logger log = LoggerFactory.getLogger(Mvc310003Serv.class);
	
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
			log.info("initial: mvc310003");
		}
	}

	/**
	 * 交易服務。
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
		EmailMaster master = new EmailMaster(doc, "310003");
		log.info(master.toString());

		// 3. 檢查輸入格式。
		if (master.invalid310003()) {
			log.warn("check : (310003) argument errors.");
			return this.response(doc, valid, business, database).asXML();
		}
		valid = true;
		
		// 4. 檢查業務邏輯。
		business = true;
		
		// 5. 讀取資料庫。
		EmailMaster entity = this.dao.uuid(master.getQueryUuid());
		if (entity == null || "99".equals(entity.getStatus())) {
			log.warn("database : (310003) entity was empty.");
			return this.response(doc, valid, business, database).asXML();
		}
		database = true;

		// 6. 影像檔記錄。
		this.imageDao.save(new EmailImage(master));

		// 7. 返回下行電文。
		log.info("Mvc310003Serv : OK !");
		return this.response(doc, valid, business, database, entity).asXML();
	}
	
	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------

	/**
	 * 返回下行電文。
	 * @param doc XML文件
	 * @param valid 輸入格式正確
	 * @param business 業務邏輯正確
	 * @param database 資料不存在
	 * @return 下行XML文件
	 */
	private Document response(Document doc, boolean valid, boolean business, boolean database) {
		return this.response(doc, valid, business, database, null);
	}

	/**
	 * (同名異性式)返回下行電文。
	 * @param doc XML文件
	 * @param valid 輸入格式正確
	 * @param business 業務邏輯正確
	 * @param database 資料不存在
	 * @param entity EmailMaster實體(成功時傳入)
	 * @return 下行XML文件
	 */
	private Document response(Document doc, boolean valid, boolean business, boolean database, EmailMaster entity) {
		
		// 1. 刪除TxBody内容並添加空内容。
		Element root = doc.getRootElement();
		Element body = root.element("TxBody");
		if (body != null) {
			root.remove(body);
		}
		body = root.addElement("TxBody");
		
		// 2. 業務邏輯。
		String[] messages = null;
		String status = "";
		String txStatus = "";
		
		// (1) 輸入格式
		if (! valid) {
			messages = this.descDao.invalid();
		// (2) 業務邏輯
		} else if (! business) {
			messages = this.descDao.error(ErrorDescDao.uuid);
		// (3) 資料不存在
		} else if (! database) {
			messages = new String[] { "9999", "" };
			status = "99";
			txStatus = "99";
		// 成功
		} else {
			messages = this.descDao.success();
			status = EmptyUtil.orEmpty(entity.getStatus());
			txStatus = EmptyUtil.orEmpty(entity.getTxStatus());
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

		// 5. 成功時附加明細欄位
		if (valid && business && database && entity != null) {
			// (1) BRANCH
			body.addElement("BRANCH").setText(EmptyUtil.orEmpty(entity.getBranch()));
			// (2) TELLER
			body.addElement("TELLER").setText(EmptyUtil.orEmpty(entity.getTeller()));
			// (3) PREV_EMAIL_ADDR
			body.addElement("PREV_EMAIL_ADDR").setText(EmptyUtil.orEmpty(entity.getPrevEmail()));
			// (4) AFTER_EMAIL_ADDR
			body.addElement("AFTER_EMAIL_ADDR").setText(EmptyUtil.orEmpty(entity.getAfterEmail()));
			// (5) CHG_DATE
			body.addElement("CHG_DATE").setText(EmptyUtil.orEmpty(entity.getChangeDate()));
			// (6) CHG_TIME
			body.addElement("CHG_TIME").setText(EmptyUtil.orEmpty(entity.getChangeTime()));
			// (7) CHNL_NAME
			body.addElement("CHNL_NAME").setText(EmptyUtil.orEmpty(entity.getChannel()));
			// (8) REASON
			body.addElement("REASON").setText(EmptyUtil.orEmpty(entity.getReason()));
			// (9) ONLINE
			body.addElement("ONLINE").setText(EmptyUtil.orEmpty(entity.getOnline()));
			// (10) STATUS
			body.addElement("STATUS").setText(status);
			// (11) TX_STATUS
			body.addElement("TX_STATUS").setText(txStatus);
			// (12) DESCRIPTION
			String errCode = EmptyUtil.orEmpty(entity.getErrorCode());
			String description = "";
			if (EmptyUtil.not(errCode)) {
				description = this.descDao.error(errCode)[1];
			}
			body.addElement("DESCRIPTION").setText(description);
			// (13) ERR_CODE
			body.addElement("ERR_CODE").setText(errCode);
			// (14) REMARK
			body.addElement("REMARK").setText(EmptyUtil.orEmpty(entity.getRemark()));
		}

		// 6. 返回下行電文。
		log.info("outbound: " + doc.asXML());
		return doc;
	}	
}
