package com.fubon.mvp.dao;

import java.util.List;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.fubon.mvp.data.EmailAiOut;

/**
 * 富邦MVP-AI外撥結果檔儲存器
 * @author 張晏哲
 * @category 儲存類
 */
@Repository
@Transactional
public class EmailAiOutDao {

	private static Logger log = LoggerFactory.getLogger(EmailAiOutDao.class);

	@Autowired
	private EmailAiOutRepo repo;

	/**
	 * 1. 依據身份証號查詢外撥紀錄清單(依外撥時間遞減排序)。
	 * @param idNo 身份証號
	 * @return 清單
	 */
	public List<EmailAiOut> findByIdNo(String idNo) {
		return this.repo.findAllByIdNoOrderByDateTimeDesc(idNo);
	}

	/**
	 * 2. 依據UUID查詢外撥紀錄清單。
	 * @param uuid 識別值
	 * @return 清單
	 */
	public List<EmailAiOut> findByUuid(String uuid) {
		return this.repo.findAllByUuid(uuid);
	}

	/**
	 * 3. 依據身份証號讀取最近一筆外撥紀錄。
	 * @param idNo 身份証號
	 * @return 實體
	 */
	public EmailAiOut latest(String idNo) {
		return this.repo.findTop1ByIdNoOrderByDateTimeDesc(idNo);
	}

	/**
	 * 4. 儲存(新增)一筆AI外撥結果紀錄。
	 * @param entity 實體
	 * @return 異常類
	 */
	public Exception save(EmailAiOut entity) {
		try {
			this.repo.save(entity);
		} catch (Exception ex) {
			log.error(ex.toString());
			return ex;
		}
		return null;
	}
}
