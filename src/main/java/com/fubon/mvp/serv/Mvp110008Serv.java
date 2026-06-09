package com.fubon.mvp.serv;

import java.util.List;

import javax.annotation.PostConstruct;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fubon.mvp.dao.EmailDao;
import com.fubon.mvp.dao.EmailHostDao;
import com.fubon.mvp.data.EmailDetail;
import com.fubon.mvp.data.EmailMaster;

import page2020.core.Log;

/**
 * 富邦MVP-MVP110008(三日未回覆重發驗證信)服務器
 * @author 張晏哲
 * @category 服務類
 */
@Service
public class Mvp110008Serv {

    private static Logger log = LoggerFactory.getLogger(Mvp110008Serv.class);
    
    // 啓動JOB設定值
    @Value("${default.job.5}")
    private String defaultJob5;
    // 啓動JOB開關
    private boolean job;
    
    @Autowired
    private EmailDao dao;
    @Autowired
    private EmailHostDao hostDao;
    @Autowired
    private EmailStatusServ service;
    
    /**
     * 1. 初始程序。
     */
    @PostConstruct
    public void initial() {
        
        this.job = "1".equals(this.defaultJob5);    // 啓動JOB開關
        
        if (Log.test) {
            log.info("initial: mvp110008");
        }
    }
    
    /**
     * 2. 即時交易服務。
     * @param doc 上行XML文件
     * @return 下行電文
     * 
     * 上行XML文件 範例 
     * <REQUEST>
     *   <UUID>1234567890abcdef1234567890abcdef</UUID>
     *   <BRANCH>001</BRANCH>
     *   <TELLER_NO>9999</TELLER_NO>
     *   <CHNL>01</CHNL>
     *   <SUB_CHNL>01</SUB_CHNL>
     *   <CUST_ID>A123456789</CUST_ID>
     *   <ID_TYPE>1</ID_TYPE>
     *   <CUST_NAME>王小明</CUST_NAME>
     *   <ENG_NAME>WANG XIAO MING</ENG_NAME>
     *   <PREV_EMAIL_ADDR>old@example.com</PREV_EMAIL_ADDR>
     *   <AFTER_EMAIL_ADDR>new@example.com</AFTER_EMAIL_ADDR>
     *   <ON_OFF_LINE>Y</ON_OFF_LINE>
     *   <REASON>未回覆驗證信</REASON>
     *   <REMARK>三日未回覆重發驗證信</REMARK>
     *   <QUERY_UUID></QUERY_UUID>
     *   <FROM_DATE></FROM_DATE>
     *   <TO_DATE></TO_DATE>
     *   <NEXT_KEY></NEXT_KEY>
     * </REQUEST>
     *  
     */
    public String service(Document doc) {

        log.info("inbound: " + doc.asXML());

        // 1. 設定驗證變數。
        boolean valid = false;
        boolean business = true;
        boolean database = false;

        // 2. 創建上行電文實體。
        EmailMaster master = new EmailMaster(doc, "110008");    // 交易代號：110008。
        log.info(master.toString());

        // 3. 檢查輸入格式。
        if (master.invalid110008()) {
            log.warn("check : (110008) argument errors.");
            return this.service.response(doc, valid, business, database, true).asXML();
        }
        valid = true;

        // 4. 設定初始狀態
        master.setFlag("1");            // "1": 重發標記。
        master.setStatus("00");         // "00": 處理中。
        master.setTxStatus("13");       // "13": 逾期3日未回覆。
        master.setErrorCode("");        // 清除錯誤碼。
        master.setTranCode("110008");   // 交易代號：110008。

        // 5. 儲存記錄到資料庫
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
        database = true;

        // 6. 呼叫統一處理方法 (處理逾期3日的記錄)
        processOverdueRecord(master);

        // 7. 返回下行電文。
        log.info("Mvp110008Serv : OK !");
        return this.service.response(doc, valid, business, database, true).asXML();
    }
    
    /**
     * 處理逾時3日未回覆的記錄
     * @param master 需要重發的EmailMaster記錄
     * @return 處理是否成功
     */
    public boolean processOverdueRecord(EmailMaster master) {
    	
    	String dummyXml = "<Tx><TxHead><HTXTID>110008</HTXTID></TxHead></Tx>";
        Document dummyDoc = null;
        //需要匯入 org.dom4j.DocumentHelper
        try {
			dummyDoc = DocumentHelper.parseText(dummyXml);
		} catch (DocumentException e) {
            return false;
		}
        
        // 1. 再查一次 DB，避免時間差。
        EmailMaster current = this.dao.uuid(master.getUuid());
        if (current == null) {
            return false;
        }

        // 2. 確認狀態：status="00" 且 txStatus="13"。
        if (! "00".equals(current.getStatus())) {
            return false;
        }
        if (! "13".equals(current.getTxStatus())) {
            return false;
        }

        //TODO 三日未回撥 重發驗證信
        // 3. 更新資料庫。
        current.setFlag("1");           // "1": 重發標記。
        current.setTranCode("110008");  // 交易代號：110008。
        current.setStatus("00");        // "00": 處理中。
        current.setTxStatus("01");      // "01": 收到申請。
        current.setErrorCode("");       // 清除錯誤碼。
        
        //TODO EMAILMAS
        Exception ex = this.dao.save(current);
        if (ex != null) {
            log.warn("database: email master error.");
            this.service.response(dummyDoc, true, true, false, true, ex.toString());
            return false;
        }
        
        //TODO EMAILDTL
        ex = this.dao.save(new EmailDetail(current));
        if (ex != null) {
            log.warn("database: email detail error.");
            this.service.response(dummyDoc, true, true, false, true, ex.toString());
            return false;
        }

        log.info("Mvp110008Serv : retry OK ! uuid='" + current.getUuid() + "'");

        // 4. 返回下行電文。
        this.service.response(dummyDoc, true, true, true, true);
        return true;
    }

    /**
     * 3. 定時執行。
     *    三日未回覆重發驗證信：
     *    將 TX_STATUS="13" 且 D-3 的記錄推回 TX_STATUS="01"，由 MVC110001 重新觸發流程。
     */
    //300秒
    //@Scheduled(fixedDelay=300000)
    //
    //0 0 2 * * ?
    //│ │ │ │ │ │
    //│ │ │ │ │ └─ 星期（? 表示不指定）
    //│ │ │ │ └─── 月份（* 表示每月）
    //│ │ │ └───── 日期（* 表示每日）
    //│ │ └─────── 小時（2 = 凌晨 2 點）
    //│ └───────── 分鐘（0 = 0 分）
    //└─────────── 秒（0 = 0 秒）
    //
    //每天凌晨 00:30:00 執行一次
    @Scheduled(cron = "0 30 0 * * ?", zone = "Asia/Taipei")
    public void schedule() {
        
        if (! this.job) {
            return;
        }
        
        log.info("三日未回覆重發驗證信處理");
        
        // 1. 是主服務器？
        if (! this.hostDao.isMain()) {
            return;
        }
        
        // 2. 搜尋逾時未回覆清單。
        List<EmailMaster> entities = this.dao.findOverdue3DaysAiCalling();
        if (entities.size() == 0) {
            return;
        }
        
        // 3. 處理逾時清單。
        for (EmailMaster master : entities) {
            try {
                // 呼叫處理單筆的方法
                processOverdueRecord(master);
            } catch (Exception e) {
                log.error("處理逾時記錄時發生錯誤: uuid=" + master.getUuid(), e);
            }
        }
    }
}
