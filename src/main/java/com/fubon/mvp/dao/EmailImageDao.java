package com.fubon.mvp.dao;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.fubon.mvp.data.EmailImage;

import page2020.util.AddrUtil;

/**
 * 富邦MVP-存活驗證檔接口
 * @author MILO-GAO(高振銘)@2020
 * @category 接口類
 */
@Repository
@Transactional
public class EmailImageDao {

	private static Logger log = LoggerFactory.getLogger(EmailImageDao.class);
	
	@Autowired
	private EmailImageRepo repo;
	
	// 本地IP。
	private String address = AddrUtil.address();
		
	//------------------------------------------------------------------------------
	// 操作類
	//------------------------------------------------------------------------------

	/**
	 * 1. 保存實體。
	 * @param image 實體
	 * @return 布林值
	 */
	public boolean save(EmailImage image) {
		
		try {
			image.setAddress(this.address);
			this.repo.save(image);
		} catch (Exception ex) {
			log.error(ex.toString());
			return false;
		}
		return true;
	}
}
