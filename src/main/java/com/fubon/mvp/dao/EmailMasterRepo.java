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

	//------------------------------------------------------------------------------
	// 查詢類
	//------------------------------------------------------------------------------

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
	public List<EmailMaster> findAllByStatusAndTxStatusAndErrorCodeOrderById(String status, String txStatus, String errorCode);
	
	/**
	 * 5. 依據在綫值查詢。
	 * @param status 狀態值
	 * @param onlineList 在綫值清單
	 * @return 清單
	 */
	public List<EmailMaster> findAllByStatusAndOnlineInOrderByChangeDateAscChangeTimeAsc(String status, List<String> onlineList);
	
	//------------------------------------------------------------------------------
	// 讀取類
	//------------------------------------------------------------------------------

	/**
	 * 1. 依據UUID讀取實體。
	 * @param uuid 識別值
	 * @return 實體
	 */
	public EmailMaster findOneByUuid(String uuid);
	
	/**
	 * 2. 依據身份証號與狀態值讀取實體。
	 * @param idNo 身份証號
	 * @param status 狀態值
	 * @return 實體
	 */
	public EmailMaster findTop1ByIdNoAndStatusOrderByChangeDateDescChangeTimeDesc(String idNo, String status);
}
