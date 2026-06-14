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
import org.springframework.beans.factory.annotation.Value;
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
 * 富邦MVP-MVP110007(六日未回覆AI外撥)服務器
 * @author 張晏哲
 * @category 服務類
 *
 * 說明：每3秒檢查 EMAILMAS 中已寄出驗證信但超過6天仍未回覆的記錄，
 *      透過 ESB (MVP067050) 向核心系統索取客戶姓名與手機號碼，
 *      儲存至 EMAILMAS 的 CHECKER / TEL_NO 欄位。
 *      仿照 Mvp067000Serv 之開發模式。
 *
 * ┌──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
 * │ EMAILMAS.TX_STATUS 正常狀態機流程:      01(收到申請) → 10(寄信前) → 11(寄信後) → 13(寄信後) → 15(客戶確認) → 20(發送ESB前) → 21(發送核心) → 00(全部完成)　                                │
 * ├───────────────────┬─────────────┬──────────────────┬─────────┬──────────┬────────────────────────────────────────────────────────────────────────────────────────────────────┤
 * │ EMAILMAS.STATUS   │ 00處理中     │ 01全部流程成功完成  │ 02失敗　 │ 99作廢　  │  　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　                  　          │
 * ├───────────────────┼─────────────┼──────────────────┼─────────┼──────────┼─────────────┬──────────┬────────────┬───────────┬────────────┬──────────────┬────────────┬─────────┤
 * │ EMAILMAS.TX_STATUS│ 00全部完成  　│ 01收到申請　　     │ 10寄信前 │ 13寄信後  │ 15客戶確認　  │ 17AI外撥　│ 19外撥回饋 　│ 20發送ESB前│ 21發送核心 　│ 31發送回應前台 │ 15人工啟用 　│ 99作廢 　│
 * └───────────────────┴─────────────┴──────────────────┴─────────┴──────────┴─────────────┴──────────┴────────────┴───────────┴────────────┴──────────────┴────────────┴─────────┘
 * 
 * 服務執行的狀態流：13 → 60 → 61 → 17（成功）或 → 02（失敗）
 * 
 * 6日未回覆 的判斷邏輯
 * (1) changeDate 逾期6天 且 status="00" 且 txStatus IN ("13")
 * (2) 或之前處理失敗(STATUS="02" + TX_STATUS < 15)，需要重新處理
 * (3) 或 ESB 重試(STATUS="02" + TX_STATUS="60" + ERR_CODE="E010")
 * 
 * 6日未回覆:
 *  發送上行電文給 ESB (MVP067050) 取得客戶姓名與手機號碼
 *   成功: 儲存至 EMAILMAS 的 CHECKER / TEL_NO 欄位 並更新 STATUS 與 TX_STATUS
 *
 * 註：逾期3日 由 Mvp110008Serv 負責重發驗證信
 * 註：逾期6日 由 Mvp110007Serv 負責索取資料並產生AI外撥名單
 *
 */
@Service
public class Mvp110007Serv {

	private static Logger log = LoggerFactory.getLogger(Mvp110007Serv.class);

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

	// ESB無法連綫("E010")
	private String notEsbCode;

	// 啓動JOB設定值
	@Value("${default.job.4}")
	private String defaultJob4;
	// 啓動JOB開關
	private boolean job;

	/**
	 * 初始程序
	 */
	@PostConstruct
	public void initial() {
		this.job = "1".equals(this.defaultJob4); // 啓動JOB開關

		this.notEsbCode = this.error.notEsb()[0];	// "E010": 無法連綫。
		
		if (Log.test) {
			log.info("initial: mvp110007");
		}
	}

	/**
	 * 定時執行 (仿照 Mvp067000Serv)
	 */
	//300秒
	//@Scheduled(fixedDelay=300000)
	//每天凌晨 00:30:00 執行一次
    @Scheduled(cron = "0 30 0 * * ?", zone = "Asia/Taipei")
	public void schedule() {
    	
    	log.info("█ █ █ █ █ 處理六日未回覆AI外撥 █ █ █ █ █");
		
        if (! this.job) {
            return;
        }
		
		log.info("六日未回覆AI外撥處理");

		// 1. 是主服務器?
		if (! this.hostDao.isMain()) {
			return;
		}
		
		log.info("查詢逾期6日未回覆清單");
		// 2. 讀取 JOB 清單
		List<EmailMaster> jobList = new ArrayList<EmailMaster>();
		
		// (2.1) 查詢逾期 6日 未回覆清單
		//SQLServer語法:	SELECT * FROM EMAILMAS WHERE STATUS = '00' AND TX_STATUS = '13' AND CHG_DATE < CONVERT(varchar(8), DATEADD(day, -6, GETDATE()), 112) AND FLAG = '1' ORDER BY ID
		//MySQL語法:		SELECT * FROM EMAILMAS WHERE STATUS = '00' AND TX_STATUS = '13' AND CHG_DATE < DATE_SUB(NOW(), INTERVAL 6 DAY) AND FLAG = '1' ORDER BY ID
		List<EmailMaster> expiredList = this.dao.findOverdue6DaysAiCalling();
		if (! expiredList.isEmpty()) {
			jobList.addAll(expiredList);
		}
		
		// (2.2) 重試:之前 ESB 無法連綫失敗的記錄 (status="02" 且 txStatus="60" 且 errorCode="E010")
		//List<EmailMaster> errorList = this.dao.error("02", "60", this.notEsbCode);
		//if (! errorList.isEmpty()) {
		//	jobList.addAll(errorList);
		//}
		
		if (jobList.isEmpty()) {
			return;
		}
		
		// 3. 執行 JOB 程序
		for (EmailMaster item : jobList) {

			// (3.1) 避免時間差問題,再查詢一次 (仿照 Mvp067000Serv)
			EmailMaster master = this.dao.uuid(item.getUuid());
			if (master == null) {
				log.warn("check : (110007) entity was missing, uuid=" + item.getUuid());
				continue;
			}
			log.info("6日未回覆 處理階段1 : " + master.toString());

			// (3.2) 確認狀態是否符合處理條件。
			boolean isOverdue = "00".equals(master.getStatus()) && "13".equals(master.getTxStatus());
			boolean isRetry = "02".equals(master.getStatus()) && "80".equals(master.getTxStatus())  && this.notEsbCode.equals(master.getErrorCode());

			if (! isOverdue && ! isRetry) {
				log.warn("check : (110007) was NOT necessary, uuid=" + master.getUuid());
				continue;
			}

			// (3.3) 保存狀態資料(請求核心前)
			String oldStatus = master.getStatus();
			String oldError = master.getErrorCode();
			master.setTranCode("067050");
			master.setStatus("00");		// "00": 處理中
			master.setTxStatus("80");	// "80": 請求核心資料中 (呼叫電文取 姓名 與 電話)
			master.setErrorCode("");
			this.dao.save(master);
			this.dao.save(new EmailDetail(master));
			
			/*
			EmailImage emailImage=new EmailImage(master);
			if(emailImage.getChannel()==null) emailImage.setChannel("-");
			if(emailImage.getSubChannel()==null) emailImage.setSubChannel("-");
			this.imageDao.save(emailImage);
			
			log.info("6日未回覆 處理階段2 : " + master.toString());
			*/
			
			// (3.4) 組合上行電文
			Document doc = this.tranx(master);
			log.info("request: " + doc.asXML());

			// (3.5) 發送上行電文給 ESB
			String xml = null;
			try {
				xml = this.proxy.api("esb").body(Mono.just(doc.asXML()), String.class).retrieve().bodyToMono(String.class).block();
				log.info("6日未回覆 處理階段3 (呼叫電文中) : " + master.toString()+"電文回應內容="+xml);
			} catch (Exception ex) {
				// ESB無法連綫,保留失敗狀態供下次重試
				if (! ("02".equals(oldStatus) && this.notEsbCode.equals(oldError))) {
					master.setStatus("02");
					master.setTxStatus("80");
					master.setErrorCode(this.notEsbCode);
					this.dao.save(master);
					this.dao.save(new EmailDetail(master));
					
					/*
					emailImage=new EmailImage(master);
					if(emailImage.getChannel()==null) emailImage.setChannel("-");
					if(emailImage.getSubChannel()==null) emailImage.setSubChannel("-");
					this.imageDao.save(emailImage);
					*/
				}
				log.error("6日未回覆 處理階段3 (呼叫電文中發生例外) 暫時略過此筆 : " + master.toString()+" 例外訊息="+ex.toString());
				continue;
			}

			// (3.6) 解析下行電文並設定執行狀態值
			log.info("6日未回覆 處理階段4 (解析下行電文並設定執行狀態值) : " + master.toString());
			Document response = this.proxy.document(xml);
			String errId = response.selectSingleNode("//HERRID").getText();
			String message = null;

			// 更新 txStatus。
			master.setTxStatus("81"); // "81": 已獲取核心資料
			this.dao.save(master);
			
			log.info("6日未回覆 處理階段5 (解析下行電文) "+master.toString());
			// ESB核心回傳成功("0000")
			if ("0000".equals(errId)) {
				
				String chName = this.proxy.value(response, "CH_NAME"); //取得姓名
				String telNo = this.proxy.value(response, "TEL_NO"); //取得手機號碼。
				//String chName="測試";
				//String telNo="1234567890";
				
				//FLAG維持不變 改成收到回饋檔案時候 客戶回覆是1才更新 FLAG=2
				//master.setFlag("2");
				
				//無客戶姓名 或 外撥電話號碼 皆略過
				if (chName != null && !chName.isEmpty() && telNo != null && !telNo.isEmpty()) {

					master.setName(chName); // checker 欄位存放客戶姓名
					master.setPhone(telNo); // telNo 欄位存放手機號碼
					master.setStatus("00"); // "00": 處理中
					master.setTxStatus("17"); // "17": AI外撥
					master.setErrorCode("");
					
					message="準備AI外撥名單資料: " + master.toString() + ", chName=" + chName + ", telNo=" + telNo;
				}
				else {
					master.setStatus("00"); // "00": 處理中
					master.setErrorCode("chName 或 telNo 無值");
					
					message="無法準備AI外撥名單資料: " + master.toString() + ", chName 或 telNo 無值";
				}
				log.info("6日未回覆 處理階段6 (呼叫電文中處置) "+message);
			}
			// 失敗
			else {
				master.setStatus("00");
				master.setErrorCode(this.proxy.value(response, "EMSGID"));
				message="呼叫電文 67050 異常 " + master.toString() + ", errId=" + errId +" errorMsg=" + this.proxy.value(response, "EMSGTXT");
				
				log.error("6日未回覆 處理階段6 (呼叫電文中處置異常) "+message);
			}

			// 儲存資料庫
			this.dao.save(master); //儲存主檔
			this.dao.save(new EmailDetail(master)); //儲存紀錄
			this.imageDao.save(new EmailImage(master)); //儲存呼叫電文紀錄
			
			log.info("6日未回覆 處理階段7 處置完畢 ");
			
		}
	}

	/**
	 * 組合上行電文 (仿照 Mvp067000Serv.tranx)
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
		conn.addElement("TxnId").setText("MVP067050");

		// (2) TxHead
		Element head = root.addElement("TxHead");
		head.addElement("HTXTID").setText("MVP067050");
		head.addElement("HWSID").setText("BKMVP");
		head.addElement("HTLID").setText("2004115");
		head.addElement("HSTANO").setText("7898411");
		head.addElement("TXMSRN");
		head.addElement("HSYDAY").setText(TimeUtil.taiwanDate());

		// (3) TxBody
		Element body = root.addElement("TxBody");
		body.addElement("uuid").setText(master.getUuid()); //唯一碼
		body.addElement("branch").setText("00200");
		body.addElement("teller").setText("99946899");
		body.addElement("IDTYPE").setText(master.getIdType()); //客戶類型(自然人 或 法人)
		body.addElement("IDNO").setText(master.getIdNo()); //身分證字號

		return doc;
	}
}
