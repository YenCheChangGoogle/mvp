package com.fubon.mvp.dao;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.fubon.mvp.data.EmailDetail;
import com.fubon.mvp.data.EmailMaster;

import page2020.util.EmptyUtil;

/**
 * 富邦MVP-郵件儲存器
 * @author MILO-GAO(高振銘)@2020
 * @category 儲存類
 */
@Repository
@Transactional
public class EmailDao {

	private static Logger log = LoggerFactory.getLogger(EmailDao.class);
	
	@Autowired
	private EmailMasterRepo masterRepo;
	@Autowired
	private EmailDetailRepo detailRepo;
	
	// 常數值。
	private List<String> onlineList = new ArrayList<String>();
	
	// 初始程序。
	@PostConstruct
	public void initial() {
		
		this.onlineList.add("Y");
		this.onlineList.add("0");
	}

	/**
	 * 1. 申請中。
	 * @param idNo 身份証號
	 * @return 清單
	 */
	public List<EmailMaster> apply(String idNo) {
		return this.masterRepo.findAllByIdNoAndStatusOrderByChangeDateAscChangeTimeAsc(idNo, "00");
	}
	
	/**
	 * 2. 依據交易狀態查詢。
	 * @param txStatus 交易狀態
	 * @return 清單
	 */
	public List<EmailMaster> txStatus(String txStatus) {
		return this.masterRepo.findAllByStatusAndTxStatusOrderById("00", txStatus);
	}
	
	/**
	 * 3. 依據主狀態與交易狀態查詢。
	 * @param status 主狀態
	 * @param txStatus 交易狀態
	 * @param errorCode 錯誤碼
	 * @return 清單
	 */
	public List<EmailMaster> error(String status, String txStatus, String errorCode) {
		return this.masterRepo.findAllByStatusAndTxStatusAndErrorCodeOrderById(status, txStatus, errorCode);
	}

	/**
	 * 4. 依據上行電文查詢。
	 * @param query 查詢條件
	 * @return 清單
	 */
	public List<EmailMaster> query(EmailMaster query) {
	
		// 1. 拆解查詢條件。
		String idNo = query.getIdNo();
		String beginDate = query.getBeginDate();
		String endDate = query.getEndDate();
		
		// 2. 依據參數查詢。
		List<EmailMaster> entities = this.masterRepo.findAllByIdNoOrderByChangeDateDescChangeTimeDesc(idNo);
		
		// 3. 過濾時間。
		if (entities.size() > 0) {
			
			// (1) 輸入條件。
			int begin = 0, end = 0;
			if (EmptyUtil.not(beginDate)) {
				begin = Integer.valueOf(beginDate).intValue();
			}
			if (EmptyUtil.not(endDate)) {
				end = Integer.valueOf(endDate).intValue();
			}
			
			// (2) 特殊條件：無開始日期且無結束日期時，返回最後1筆。
			if (begin == 0 && end == 0) {
				List<EmailMaster> onlyOne = new ArrayList<EmailMaster>();
				onlyOne.add(entities.get(0));
				return onlyOne;
			}
			
			// (3) 過濾條件。
			int index = 0;
			while (index < entities.size()) {
				
				// 1. 讀取實體日期。
				EmailMaster master = entities.get(index);
				int time = Integer.valueOf(master.getChangeDate()).intValue();
				
				// 2. 過濾條件日期。
				if ((begin > 0 && begin > time) || (end > 0 && end < time)) {
					entities.remove(index);
				} else {
					index ++;
				}
			}
		}		
		return entities;
	}
	
	/**
	 * 5.1. 查詢明細清單。
	 * @param uuid 識別值
	 * @return 清單	
	 */
	public List<EmailDetail> details(String uuid) {
        return this.detailRepo.findAllByUuid(uuid);
	}
	
	/**
	 * 5.2. 查詢明細清單。(根據時間排序)
	 * @param uuid 識別值
	 * @return 清單	
	 */
	public List<EmailDetail> detailsByUuidOrderByResponeDateAscResponeTimeAsc(String uuid) {
		//return this.detailRepo.findAllByUuidOrderByChangeDateAscChangeTimeAsc(uuid);
		return this.detailRepo.findAllByUuidOrderByResponeDateAscResponeTimeAsc(uuid);
	}
	
	/**
	 * 6. 查詢在綫清單(批次)。
	 * @return 清單
	 */
	public List<EmailMaster> online() {
		return this.masterRepo.findAllByStatusAndOnlineInOrderByChangeDateAscChangeTimeAsc("00", this.onlineList);
	}
	
	/**
	 * 7. 查詢逾時未回覆清單 (三日未回覆重發驗證信用)
	 *    條件：status="00" 且 txStatus IN ("11", "13")
	 * @return 清單
	 */
	//例如: SELECT * FROM EMAILMAS WHERE STATUS = '00'  AND TX_STATUS IN ('11', '12') ORDER BY ID ASC
	public List<EmailMaster> overdueResend() {
		List<String> txList = new ArrayList<String>();
		txList.add("11");  // 11=寄信後
		txList.add("13");  // 13=客戶收到
		return this.masterRepo.findAllByStatusAndTxStatusInOrderById("00", txList);
	}

	/**
	 * 7. 依據UUID讀取實體。
	 * @param uuid 識別值
	 * @return 實體
	 */
	public EmailMaster uuid(String uuid) {
		return this.masterRepo.findOneByUuid(uuid);
	}
	
	/**
	 * 8. 讀取成功的最後1個實體。
	 * @param idNo 身份証號
	 * @return 實體
	 */
	public EmailMaster success(String idNo) {
		return this.masterRepo.findTop1ByIdNoAndStatusOrderByChangeDateDescChangeTimeDesc(idNo, "01");
	}

	/**
	 * 9. 儲存郵件主檔。
	 * @param master 實體
	 * @return 異常類
	 */
	public Exception save(EmailMaster master) {
		
		try {
			this.masterRepo.save(master);
		} catch (Exception ex) {
			log.error(ex.toString());
			return ex;
		}
		return null;
	}
	
	/**
	 * 10. 儲存郵件明細檔。
	 * @param detail 實體
	 * @return 異常類
	 */
	public Exception save(EmailDetail detail) {
		
		try {
			this.detailRepo.save(detail);
		} catch (Exception ex) {
			log.error(ex.toString());
			return ex;
		}
		return null;
	}
	
	/**
	 * 11. 查詢三日未回覆清單(重發驗證信用)。
	 *     條件：status="00" 且 txStatus="13" 且 CHG_DATE < D-3
	 * @return 清單
	 */
	public List<EmailMaster> findOverdue3DaysAiCalling() {
		//return this.masterRepo.findOverdue3DaysAiCalling();
	    
		java.util.Calendar cal = java.util.Calendar.getInstance();
	    cal.add(java.util.Calendar.DAY_OF_MONTH, -3);
	    String threshold = page2020.util.TimeUtil.dateE(cal.getTime());
	    return this.masterRepo.findByStatusAndTxStatusAndChangeDateEqualsOrderByChangeDateAsc("00", "13", threshold);
	}

	//TODO 查詢逾時六回覆清單 (六日未回覆AI外撥) 限制 必須是 星期一執行 範圍是 D-7 ~ D-28 區間 
	/**
	 * 12. 查詢逾時六回覆清單 (六日未回覆AI外撥)
	 *     條件：status="00" 且 txStatus="13" 且 changeDate < D-6 且 flag="1"
	 *     條件時間修改
	 *     條件：status="00" 且 txStatus="13" 且 changeDate 每周一執行一次且範圍是 D-28日 到 D-7日 且 flag="1"
	 * @param MUST_MONDAY true:限定必須是星期一 false:不限定
	 * @return 清單
	 */
	public List<EmailMaster> findOverdue6DaysAiCalling(boolean MUST_MONDAY) {
		//return this.masterRepo.findOverdue6DaysAiCalling();
		
		/*
	    java.util.Calendar cal = java.util.Calendar.getInstance();
	    cal.add(java.util.Calendar.DAY_OF_MONTH, -6);
	    String threshold = page2020.util.TimeUtil.dateE(cal.getTime());
	    return this.masterRepo.findByStatusAndTxStatusAndChangeDateLessThanAndFlagOrderByChangeDateAsc("00", "13", threshold, "1");
	    */
		
		java.util.Calendar cal = java.util.Calendar.getInstance();

	    //取得今天是星期幾
	    int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);

	    if(MUST_MONDAY) {
		    //只在星期一執行
		    if (dayOfWeek != java.util.Calendar.MONDAY) {
		        return java.util.Collections.emptyList();
		    }
	    }

	    //D-28
	    java.util.Calendar calStart = (java.util.Calendar) cal.clone();
	    calStart.add(java.util.Calendar.DAY_OF_MONTH, -28);
	    String startDate = page2020.util.TimeUtil.dateE(calStart.getTime());

	    //D-7
	    java.util.Calendar calEnd = (java.util.Calendar) cal.clone();
	    calEnd.add(java.util.Calendar.DAY_OF_MONTH, -7);
	    String endDate = page2020.util.TimeUtil.dateE(calEnd.getTime());

	    //查詢範圍 between  (含指定起訖日)
	    return this.masterRepo.findByStatusAndTxStatusAndChangeDateBetweenAndFlagOrderByChangeDateAsc("00", "13", startDate, endDate, "1");
	    
	    //查詢範圍 between (不含指定起訖日)
	    //return this.masterRepo.findByStatusAndTxStatusAndChangeDateGreaterThanAndChangeDateLessThanAndFlagOrderByChangeDateAsc"00", "13", startDate, endDate, "1");
	     
	}
	
	/**
	 * 13. 依據身分證字號讀取實體 (取 STATUS=00 且排除CHANNEL=不能是JS 且排除ON_OFF_LINE=Y)
	 * @param idNo 身分證字號
	 * @return 實體
	 */
	public EmailMaster idNo(String idNo) {
		EmailMaster m=null;
		
		List<EmailMaster> list= this.masterRepo.findAllByIdNoAndStatusOrderByChangeDateAscChangeTimeAsc(idNo, "00");
		//List<EmailMaster> list= this.masterRepo.findAllByIdNoOrderByChangeDateAscChangeTimeAsc(idNo);
	
		if(list.size()==0) {
			log.warn("依據身分證字號 "+idNo+" 取 STATUS=00 且排除CHANNEL=不能是JS 且排除ON_OFF_LINE=Y 但卻無資料符合條件");
		}
		else {
			log.info("依據身分證字號 "+idNo+" 取 STATUS=00 且排除CHANNEL=不能是JS 且排除ON_OFF_LINE=Y 找到幾筆="+list.size());
		}
		
		for(EmailMaster em:list) {
			
			//排除 STATUS 不等於 00
			//if(!em.getStatus().equals("00")) {
			//	continue;
			//}
			
			//排除 CHANNEL 等於 JS
			if(em.getChannel().equals("JS")) {
				continue;
			}
			//排除 ON_OFF_LINE 等於 Y
			if(em.getOnline().equals("Y")) {
				continue;
			}
			//排除 修改日 小於等於 20220101
			if(Integer.parseInt(em.getChangeDate())<=20220101) {
				continue;
			}
			
			m=em;
			break;
		}
		
		return m;
	}
}
