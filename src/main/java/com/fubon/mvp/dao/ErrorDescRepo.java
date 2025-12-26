package com.fubon.mvp.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fubon.mvp.data.ErrorDesc;

/**
 * 富邦MVP-異常代碼檔
 * @author MILO-GAO(高振銘)@2020
 * @category 接口類
 */
public interface ErrorDescRepo extends JpaRepository<ErrorDesc, Long> {

	//------------------------------------------------------------------------------
	// 讀取類
	//------------------------------------------------------------------------------

	/**
	 * 1. 依據異常碼讀取。
	 * @param code 異常碼 
	 * @return 實體
	 */
	public ErrorDesc findOneByCode(String code);
}
