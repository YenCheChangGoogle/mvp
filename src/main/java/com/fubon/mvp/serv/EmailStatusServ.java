package com.fubon.mvp.serv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailDao;
import com.fubon.mvp.dao.ErrorDescDao;
import com.fubon.mvp.data.EmailDetail;
import com.fubon.mvp.data.EmailMaster;

import page2020.util.TimeUtil;

/**
 * 富邦MVP-郵件狀態服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class EmailStatusServ {

	private static Logger log = LoggerFactory.getLogger(EmailStatusServ.class);
	
	@Autowired
	private EmailDao dao;
	@Autowired
	private ErrorDescDao descDao;

	// 計數。
	private int count = 0;

	// 1. mvp084023 資料池
	private List<EmailMaster> mvp084023Pool = Collections.synchronizedList(new ArrayList<EmailMaster>());

	/**
	 * 1. 下行電文程序。
	 * @param doc XML文件
	 * @param valid 輸入正確
	 * @param business 業務邏輯正確
	 * @param database 資料庫正確
	 * @param errors 錯誤信息參數
	 * @return 下行XML文件
	 */
	public Document response(Document doc, boolean valid, boolean business, boolean database, 
			String... errors) {
		return this.response(doc, valid, business, database, false, errors);
	}

	/**
	 * 2. 下行電文程序。
	 * @param doc XML文件
	 * @param valid 輸入正確
	 * @param business 業務邏輯正確
	 * @param database 資料庫正確
	 * @param txDate 交易日期
	 * @param errors 錯誤信息參數
	 * @return 下行XML文件
	 */
	public Document response(Document doc, boolean valid, boolean business, boolean database, 
			boolean txDate, String... errors) {
		
		// 1. 刪除内容並添加空内容。
		Element root = doc.getRootElement();
		Element body = root.element("TxBody");
		root.remove(body);
		body = root.addElement("TxBody");

		// 2. 驗證邏輯。
		String[] messages = null;
		// (1) 輸入格式
		if (! valid) {
			messages = this.descDao.invalid();
		// (2) 業務邏輯
		} else if (! business) {
			// 來源：110001, 110002。
			if (txDate) {
				messages = this.descDao.error(ErrorDescDao.uuid);
			// 來源：其它。
			} else {
				messages = this.descDao.terminate();
			}
		// (3) 資料庫異常
		} else if (! database) {
			messages = this.descDao.database();
			messages[1] = errors[0];
		// 成功
		} else {
			messages = this.descDao.success();
		}

		// 3. 加入下行參數。
		// (1) MESSAGE
		body.addElement("MESSAGE").setText(messages[1]);
		// (2) MESSAGE_CODE
		body.addElement("MESSAGE_CODE").setText(messages[0]);

		// 4. 附加交易日期(來源：110001, 110002)。
		if (txDate) {
			// (3) TX_DATE
			body.addElement("TX_DATE").setText(TimeUtil.dateE(new Date()));
			// (4) TX_TIME
			body.addElement("TX_TIME").setText(TimeUtil.time());
		}
		
		// 5. 返回下行電文。
		log.info("outbound: " + doc.asXML());
		return doc;
	}

	/**
	 * 3. 生成Mail-Hunter下行電文。
	 * 説明: 110006, 110007用。
	 * @param txNo 交易代號
	 * @param success 布林值
	 * @return 電文
	 */
	public Document response(String txNo, boolean success) {
		
		// 1. 生成新文件。
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("Tx");
		Element head = root.addElement("TxHead");
		Element body = root.addElement("TxBody");
		
		// 2. 交易代號。
		head.addElement("HTXTID").setText(txNo);
		
		// 3. 回傳值。
		Element message = body.addElement("MESSAGE");
		if (success) {
			message.setText("0000");
		} else {
			message.setText("9999");
		}
		
		log.info("outbound: " + doc.asXML());
		return doc;
	}
	
	/**
	 * 4. 進行中的申請單作廢。
	 * @param idNo 身份証號
	 * @param tranCode 交易代號
	 * @param sid 自己的識別值(批次用)
	 * @param success 已完成實體
	 */
	public void cancel(String idNo, String tranCode, EmailMaster success) {
		
		List<EmailMaster> entities = this.dao.apply(idNo);
		for (EmailMaster entity : entities) {
			String online = entity.getOnline();
			if ("Y".equals(online) || "0".equals(online)) {
				continue;
			}
			if (success != null &&
				success.getChangeDate().compareTo(entity.getChangeDate()) <= 0 &&
				success.getChangeTime().compareTo(entity.getChangeTime()) < 0) {
				continue;
			}
			this.cancel(entity, tranCode);
		}
	}
	
	/**
	 * 5. 進行中的申請單作廢。
	 * @param idNo 身份証號
	 * @param tranCode 交易代號
	 */
	public void cancel(EmailMaster entity, String tranCode) {
		
		// (1) 主檔。
		entity.setTranCode(tranCode);	// 交易代號。
		entity.setStatus("99");			// "99": 作廢。
		entity.setTxStatus("99");		// "99": 作廢。
		entity.setErrorCode("0000");	// (2021/12/16 15:03)
		this.dao.save(entity);
		
		// (2) 明細檔。
		EmailDetail detail = new EmailDetail(entity);
		this.dao.save(detail);
		
		log.info("CANCEL : uuid='" + detail.getUuid() + "'");
		
		/* (2022/01/07 16:30)
		List<EmailDetail> details = this.dao.details(entity.getUuid());
		for (EmailDetail detail : details) {
			detail.setTxStatus("99");	// "99": 作廢。
			this.dao.save(detail);
			log.info("database detail : uuid='" + entity.getUuid() + "', status=" + entity.getStatus());
		}
		*/		
	}

	/**
	 * 6. 讀取UUID字串。
	 * @param txNo 交易代號
	 * @return UUID
	 */
	public synchronized String uuid(String txNo) {
		
		// 規則："0Z89"(4) + 交易代號(6)  + 日期(4) + 時間(6) + 流水號(5) = UUID(25)
		Date date = new Date();
		this.count = (this.count == 99999) ? 0 : (this.count + 1);
		return String.format("%s%s%s%s%05d", "0Z89", txNo, 
			TimeUtil.dateE(date).substring(4), TimeUtil.timeE(date), this.count);
	}
	
	/**
	 * 7. 新增定時事件。
	 * @param txNo 交易代號
	 * @param value 事件值
	 */
	public void addToPool(String txNo, Object value) {
		
		switch (txNo) {
		case "084023":
			this.mvp084023Pool.add((EmailMaster) value);
			break;
		default:
			return;
		}
		log.info("addToPool: txNo=" + txNo);
	}
	
	/**
	 * 8. 移除定時事件。
	 * @param txNo 交易代號
	 * @return 事件值
	 */
	public Object removeFromPool(String txNo) {
		
		Object object = null;
		switch (txNo) {
		case "084023":
			if (! this.mvp084023Pool.isEmpty()) {
				object = this.mvp084023Pool.remove(0);
			}
			break;
		}
		if (object != null) {
			log.info("removeFromPool: txNo=" + txNo);
			return object;
		}
		return null;
	}
}
