package com.fubon.mvp.serv;

import java.util.List;

import javax.annotation.PostConstruct;

import org.dom4j.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailDao;
import com.fubon.mvp.dao.EmailHostDao;
import com.fubon.mvp.dao.EmailImageDao;
import com.fubon.mvp.data.EmailDetail;
import com.fubon.mvp.data.EmailImage;
import com.fubon.mvp.data.EmailMaster;

import page2020.core.Log;

/**
 * 富邦MVP-MVC110001(登錄用戶)服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class Mvc110001Serv {

	private static Logger log = LoggerFactory.getLogger(Mvc110001Serv.class);
	
	@Autowired
	private EmailDao dao;
	@Autowired
	private EmailHostDao hostDao;
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
			log.info("initial: mvc110001");
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
		boolean business = true;
		// (3) 無資料庫異常。
		boolean database = false;
		
		// 2. 創建上行電文實體。
		EmailMaster master = new EmailMaster(doc, "110001");	// 交易代號：110001。
		log.info(master.toString());
		
		// 3. 檢查輸入格式(2022/01/24 12:00)。
		boolean cancel = master.isCancel();
		if ((! cancel) && master.invalid110001()) {
			log.warn("check : (110001) argument errors.");
			return this.service.response(doc, valid, business, database, true).asXML();
		}
		valid = true;

		// 4. 進行中的申請單作廢(2022/02/24 18:00)。
		this.service.cancel(master.getIdNo(), "110001", master);

		// 5. 更新資料庫。
		boolean online = "Y".equals(master.getOnline()) || "0".equals(master.getOnline());
		if (online) {
			master.setStatus("01");		// "01": 成功完成。
			master.setTxStatus("00");	// "00": 全部完成。
		} else {
			master.setStatus("00");		// "00": 處理中。
			master.setTxStatus("01");	// "01": 收到申請。			
		}
		master.setErrorCode("");		// 清除錯誤碼。
		// (1) 主檔紀錄。
		Exception ex = this.dao.save(master);
		if (ex != null) {
			log.warn("database: email master error.");
			return this.service.response(doc, valid, business, database, true, ex.toString()).asXML();
		}
		// (2) 明細檔紀錄。
		ex = this.dao.save(new EmailDetail(master));
		if (ex != null) {
			log.warn("database: email detail error.");
			return this.service.response(doc, valid, business, database, true, ex.toString()).asXML();
		}
		// (3) 影像檔記錄。
		this.imageDao.save(new EmailImage(master));
		database = true;
		
		// 5. 返回下行電文。
		log.info("Mvc110001Serv : OK !");
		return this.service.response(doc, valid, business, database, true).asXML();
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
		
		// 2. 搜尋批次作業資料清單，條件：online="Y/0" + status="00"。
		// 説明：最早的記錄放首位。
		List<EmailMaster> entities = this.dao.online();
		if (entities.size() == 0) {
			return;
		}
				
		// 3. 處理批次作業清單。
		for (EmailMaster master : entities) {

			// (1) 讀取最後1筆已完成紀錄。
			EmailMaster success = this.dao.success(master.getIdNo());

			// (2) 判斷變更日期與時間。
			boolean valid = false;		// 有效旗標。
			// i. 無完成紀錄。
			if (success == null) {
				valid = true;
			// ii. 完成時間比申請時間小(以前的)。
			} else if (success.getChangeDate().compareTo(master.getChangeDate()) <= 0 &&
						success.getChangeTime().compareTo(master.getChangeTime()) < 0) {
				valid = true;
			}
				
			// (3) 相同身份証號僅執行1次。
			// i. 無效：此批次申請單作廢("99+99")。
			if (! valid) {
				this.service.cancel(master, "110001");
				log.warn("Batch : was NOT necessary. uuid='" + master.getUuid() + "', but success='" + success.getUuid() + "'");

			// ii. 最新記錄：批次資料行已完成"01+00"，申請中的作廢("99+99")。
			} else {
				// 取消網路申請。
				this.service.cancel(master.getIdNo(), "110001", master);
				// 更新資料庫。
				// 1. 主檔紀錄。
				master.setTranCode("110001");
				master.setStatus("01");		// "01": 成功完成。
				master.setTxStatus("00");	// "00": 全部完成。
				master.setErrorCode("0000");
				this.dao.save(master);
				// 2. 明細檔紀錄。
				this.dao.save(new EmailDetail(master));
				// 3. 影像檔記錄。
				this.imageDao.save(new EmailImage(master));
				log.info("Batch : OK ! uuid='" + master.getUuid() + "'");
			}
		}
	}	
}
