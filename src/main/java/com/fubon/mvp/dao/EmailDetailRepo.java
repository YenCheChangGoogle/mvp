package com.fubon.mvp.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fubon.mvp.data.EmailDetail;

/**
 * 富邦MVP-郵件明細檔接口
 * @author MILO-GAO(高振銘)@2020
 * @category 接口類
 */
public interface EmailDetailRepo extends JpaRepository<EmailDetail, Long> {

	//------------------------------------------------------------------------------
	// 查詢類
	//------------------------------------------------------------------------------

	/**
	 * 1. 查詢明細清單。
	 * @param uuid 識別值
	 * @return 清單
	 */
	public List<EmailDetail> findAllByUuid(String uuid);
	
	/**
	 * 2. 查詢明細清單 (依日期時間排序)。
	 * @param uuid 識別值
	 * @return 清單
	 */
	//public List<EmailDetail> findAllByUuidOrderByChangeDateAscChangeTimeAsc(String uuid);
	public List<EmailDetail> findAllByUuidOrderByResponeDateAscResponeTimeAsc(String uuid);
}
