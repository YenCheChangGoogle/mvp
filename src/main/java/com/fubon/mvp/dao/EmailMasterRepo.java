package com.fubon.mvp.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fubon.mvp.data.EmailMaster;

/**
 * 富邦MVP-郵件主檔接口
 * @author MILO-GAO(高振銘)@2020
 * @category 接口類
 */
public interface EmailMasterRepo extends JpaRepository<EmailMaster, Long> {

	/**
	 * 1. 依據身份証號查詢(一般查詢用)。
	 * @param idNo 身份証號 
	 * @return 清單
	 */
	public List<EmailMaster> findAllByIdNoOrderByChangeDateDescChangeTimeDesc(String idNo);

	/**
	 * 2. 依據身份証號與狀態查詢(取消申請用)。
	 * @param idNo 身份証號 
	 * @param status 狀態值
	 * @return 清單
	 */
	public List<EmailMaster> findAllByIdNoAndStatusOrderByChangeDateAscChangeTimeAsc(String idNo, String status);

	/**
	 * 3. 依據交易狀態查詢。
	 * @param status 主狀態
	 * @param txStatus 交易狀態
	 * @return 清單
	 */
	public List<EmailMaster> findAllByStatusAndTxStatusOrderById(String status, String txStatus);
	
	/**
	 * 4. 依據主狀態與交易狀態查詢。
	 * @param status 主狀態
	 * @param txStatus 交易狀態
	 * @param errorCode 錯誤碼
	 * @return 清單
	 */
	//例如: SELECT * FROM EMAILMAS WHERE STATUS = ? AND TX_STATUS = ? AND ERR_CODE = ? ORDER BY ID ASC
	public List<EmailMaster> findAllByStatusAndTxStatusAndErrorCodeOrderById(String status, String txStatus, String errorCode);
	
	/**
	 * 5 查詢逾時未回覆之清單 (三日未回覆重發驗證信用)。
	 *     條件：status="00" (處理中) 且 txStatus IN ("11", "13")
	 * @param status 主狀態
	 * @return 清單
	 */
	//例如: SELECT * FROM EMAILMAS WHERE STATUS = ?  AND TX_STATUS IN (?, ?, ...) ORDER BY ID ASC
	public List<EmailMaster> findAllByStatusAndTxStatusInOrderById(String status, List<String> txStatusList);
	
	/**
	 * 6. 依據在綫值查詢。
	 * @param status 狀態值
	 * @param onlineList 在綫值清單
	 * @return 清單
	 */
	//例如: SELECT * FROM EMAILMAS WHERE STATUS = ? AND ON_OFF_LINE IN (?, ?, ...) ORDER BY CHG_DATE ASC, CHG_TIME ASC
	public List<EmailMaster> findAllByStatusAndOnlineInOrderByChangeDateAscChangeTimeAsc(String status, List<String> onlineList);
	
	/**
	 * 7. 讀取最近一次成功之實體。
	 *    條件：依 idNo + status 篩選，按 changeDate/ChangeTime 遞減排序，取第一筆
	 * @param idNo 身份証號
	 * @param status 狀態值
	 * @return 實體
	 */
	public EmailMaster findTop1ByIdNoAndStatusOrderByChangeDateDescChangeTimeDesc(String idNo, String status);
    
	/**
	 * 7. 依據UUID讀取實體。
	 * @param uuid 識別值
	 * @return 實體
	 */
	public EmailMaster findOneByUuid(String uuid);
	
	/**
	 * 8. 依據主狀態、交易狀態與日期查詢
	 *    條件：status="00"(處理中) 且 txStatus IN ("11", "13") 且 changeDate < 指定日期
	 * @param status 主狀態
	 * @param txStatusList 交易狀態清單
	 * @param changeDate 日期
	 * @return 清單
	 */
	public List<EmailMaster> findAllByStatusAndTxStatusInAndChangeDateLessThanOrderById(String status, List<String> txStatusList, String changeDate);

	/**
	 * 9.1. 查詢逾時未回覆清單 (三日未回覆)。
	 *      條件：status="00" 且 txStatus="13" 且 changeDate < D-3
	 * @return 清單
	 */
	//SQL Server
	//@org.springframework.data.jpa.repository.Query(value="SELECT * FROM EMAILMAS WHERE STATUS = '00' AND TX_STATUS = '13' AND CHG_DATE < CONVERT(varchar(8), DATEADD(day, -3, GETDATE()), 112) ORDER BY ID", nativeQuery=true)
	//MySQL
	//@org.springframework.data.jpa.repository.Query(value="SELECT * FROM EMAILMAS WHERE STATUS = '00' AND TX_STATUS = '13' " + "  AND CHG_DATE < DATE_SUB(NOW(), INTERVAL 3 DAY) ORDER BY ID", nativeQuery = true)
	//public List<EmailMaster> findOverdue3DaysAiCalling();
	
    /**
     * 9.2. 查詢逾時未回覆清單 (三日未回覆)。
     *      條件：status="00" 且 txStatus="13" 且 changeDate < 判定日期
     *      使用範例:
     *      java.util.Calendar cal = java.util.Calendar.getInstance();
     *      cal.add(java.util.Calendar.DAY_OF_MONTH, -3);
     *      String threshold = page2020.util.TimeUtil.dateE(cal.getTime());
     *      return this.masterRepo.findByStatusAndTxStatusAndChangeDateLessThanOrderByChangeDateAsc("00", "13", threshold);
     *    
     */
	public List<EmailMaster> findByStatusAndTxStatusAndChangeDateLessThanOrderByChangeDateAsc(String status, String txStatus, String changeDate);
	
	/**
	 * 10.1. 查詢逾時未回覆清單 (六日未回覆AI外撥)。
	 *       條件：status="00" 且 txStatus="13" 且 changeDate < D-6 且 flag="1"
	 * @return 清單
	 */
	//SQL Server
	//@org.springframework.data.jpa.repository.Query(value="SELECT * FROM EMAILMAS WHERE STATUS = '00' AND TX_STATUS = '13' AND CHG_DATE < CONVERT(varchar(8), DATEADD(day, -6, GETDATE()), 112) AND FLAG = '1' ORDER BY ID", nativeQuery=true)
	//MySQL
	//@org.springframework.data.jpa.repository.Query(value="SELECT * FROM EMAILMAS WHERE STATUS = '00' AND TX_STATUS = '13' AND CHG_DATE < DATE_SUB(NOW(), INTERVAL 6 DAY) AND FLAG = '1' ORDER BY ID", nativeQuery = true)
	//public List<EmailMaster> findOverdue6DaysAiCalling();
	
    /**
     * 10.2. 查詢逾時未回覆清單 (六日未回覆AI外撥)。
     *       條件：status="00" 且 txStatus="13" 且 changeDate < 判定日期 且 flag="1"
     *       使用範例:
     *       java.util.Calendar cal = java.util.Calendar.getInstance();
     *       cal.add(java.util.Calendar.DAY_OF_MONTH, -6);
     *       String threshold = page2020.util.TimeUtil.dateE(cal.getTime());
     *       return this.masterRepo.findByStatusAndTxStatusAndChangeDateLessThanAndFlagOrderByChangeDateAsc("00", "13", threshold, "1");
     *       
     */
    public List<EmailMaster> findByStatusAndTxStatusAndChangeDateLessThanAndFlagOrderByChangeDateAsc(String status, String txStatus, String changeDate, String flag);
    
}
