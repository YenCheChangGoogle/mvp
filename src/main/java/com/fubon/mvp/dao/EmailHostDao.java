package com.fubon.mvp.dao;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import com.fubon.mvp.data.EmailHost;

import page2020.util.AddrUtil;

/**
 * 富邦MVP-主從郵件系統儲存
 * @author MILO-GAO(高振銘)@2020
 * @category 儲存類
 */
@Repository
@Transactional
public class EmailHostDao {
		
	private static Logger log = LoggerFactory.getLogger(EmailHostDao.class);
	
	// 啓動JOB設定值。
	@Value("${default.job.1}")
	private String defaultJob1;
	// 啓動JOB開關。
	private boolean job;

	// 資料實體。
	private EmailHost entity;
		
	@Autowired 
	private EmailHostRepo repo;

	// 初始程序
	@PostConstruct
	public void initial() {

		// 啓動JOB開關。
		this.job = "1".equals(this.defaultJob1);
		
		// 查詢資料庫。
		String address = AddrUtil.address();
		String hostname = AddrUtil.hostname();
		this.entity = this.repo.findOneByAddressAndHostname(address, hostname);
		
        log.info("█ 目前執行的主機="+address+" "+hostname+" "+entity+" 若要啟用請於資料庫設定啟用判定");
        
		if (this.entity == null) {
			
			// 創建資料實體。
			this.entity = new EmailHost();
			this.entity.setAddress(address);
			this.entity.setHostname(hostname);
			// 新增資料實體。
			this.repo.save(entity);
		}
	}
	
	// 定時程序
	@Scheduled(fixedRate=3000)
	public void schedule() {

		// 啓動定時JOB程序 ?
		if (! this.job) {
			return;
		}
		
		// 定時檢查資料庫。
		EmailHost host = this.repo.findOneByAddressAndHostname(this.entity.getAddress(), this.entity.getHostname());
		if (host != null) {
			this.entity.setMain(host.getMain());
		}
	}
		
	//------------------------------------------------------------------------------
	// 公開級
	//------------------------------------------------------------------------------

	/**
	 * 1. 是否是主服務器。
	 * @return 布林值
	 */
	public boolean isMain() {
		return this.entity.getMain().intValue() == 1 ? true : false;
	}
	
	//------------------------------------------------------------------------------
	// 異動類
	//------------------------------------------------------------------------------

	/**
	 * 1. 儲存實體。
	 * @param entity 實體
	 * @return 布林值
	 */
	public boolean save(EmailHost entity) {
		
		boolean success = false;
		try {
			this.repo.save(entity);
			success = true;
		} catch (Exception ex) {
			log.error(ex.toString());
		}
		return success;
	}
}
