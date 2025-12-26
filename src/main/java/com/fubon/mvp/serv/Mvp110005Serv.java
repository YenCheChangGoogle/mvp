package com.fubon.mvp.serv;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

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

import page2020.core.Log;

/**
 * 富邦MVP-MVC110005(寄信)服務器
 * @author MILO-GAO(高振銘)@2020
 * @category 服務類
 */
@Service
public class Mvp110005Serv {

	private static Logger log = LoggerFactory.getLogger(Mvp110005Serv.class);
	
	@Autowired
	private EmailDao dao;
	@Autowired
	private EmailHostDao hostDao;
	@Autowired
	private EmailImageDao imageDao;
	@Autowired
	private ErrorDescDao error;
	@Autowired
	private EmailWsClient email;
	
	// E100=郵件服務器無法連綫。
	private String notMailCode;
	
	/**
	 * 1. 初始程序
	 */
	@PostConstruct
	public void initial() {
		
		this.notMailCode = this.error.mail(0)[0];	// "E100"。
		if (Log.test) {
			log.info("initial: mvp110005");
		}
	}
	
	/**
	 * 2. 定時 JOB2 程序。
	 */
	@Scheduled(fixedDelay=3000)
	public void schedule() {
	
		// 1. 是主服務器？
		if (! this.hostDao.isMain()) {
			return;
		}

		// 2. 讀取 JOB2 清單。
		List<EmailMaster> job2List = new ArrayList<EmailMaster>();
		// (1) "01"(收到申請)"
		List<EmailMaster> applyList = this.dao.txStatus("01");
		if (! applyList.isEmpty()) {
			job2List.addAll(applyList);
		}
		// (2) "02+11+E100"(無法連綫)
		/* 注銷(2021/12/23 15:35)
		List<EmailMaster> errorList = this.dao.error("02", "11", this.notMailCode);
		if (! errorList.isEmpty()) {
			job2List.addAll(errorList);
		}
		*/
		
		// 3. 執行 JOB2 程序。
		for (EmailMaster master : job2List) {
			
			// 1. 保存狀態資料(寄信之前)。
			String oldStatus = master.getStatus();
			String oldError = master.getErrorCode();
			master.setTranCode("110005");
			master.setStatus("00");		// 00=處理中
			master.setTxStatus("10");	// 10=寄信前
			master.setErrorCode("");
			this.dao.save(master);
			this.dao.save(new EmailDetail(master));
			this.imageDao.save(new EmailImage(master));

			// 2. 寄信。
			int result = this.email.sendMail(master);
			log.info("email return : " + result);
			
			// 3. 保存狀態資料(寄信之後)。
			master.setTxStatus("11");	// 11=寄信後
			
			// 4. 記錄寄信結果。
			// (1) 成功。
			if (result == 1) {
				master.setErrorCode("");
				this.dao.save(master);
				this.dao.save(new EmailDetail(master));
				this.imageDao.save(new EmailImage(master));
				log.info("Mvp110005Serv : OK !");
			// (2) 無法連綫。
			} else if (result == -1) {
				// 説明：資料庫只留1筆。
				if (! ("02".equals(oldStatus) && this.notMailCode.equals(oldError))) {
					master.setStatus("02");	// "02": 失敗。
					master.setErrorCode(this.notMailCode);	// "E100": 無法連綫。
					this.dao.save(master);
					this.dao.save(new EmailDetail(master));
					this.imageDao.save(new EmailImage(master));
					log.info("Mvp110005Serv : mail hunter disconnected.");
				}
			// (3) 郵件服務器錯誤。
			} else {
				master.setStatus("02");		// "02": 失敗。
				master.setErrorCode(this.error.mail(result)[0]);	// "錯誤碼": 郵件服務器。
				this.dao.save(master);
				this.dao.save(new EmailDetail(master));
				this.imageDao.save(new EmailImage(master));
				log.info("Mvp110005Serv : errorCode=" + result);				
			}
		}
	}
}
