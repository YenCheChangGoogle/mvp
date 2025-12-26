package com.fubon.mvp.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fubon.mvp.data.EmailHost;

/**
 * 富邦MVP-主從郵件系統接口
 * @author MILO-GAO(高振銘)@2020
 * @category 接口類
 */
public interface EmailHostRepo extends JpaRepository<EmailHost, Long> {

	//------------------------------------------------------------------------------
	// 讀取類
	//------------------------------------------------------------------------------

	/**
	 * 1. 查詢實體。
	 * @param address 電腦地址
	 * @param hostanme 服務器名
	 * @return 實體
	 */
	public EmailHost findOneByAddressAndHostname(String address, String hostanme);
}
