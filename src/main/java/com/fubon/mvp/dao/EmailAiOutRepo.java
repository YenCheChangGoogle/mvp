package com.fubon.mvp.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fubon.mvp.data.EmailAiOut;

/**
 * 富邦MVP-AI外撥結果檔接口
 * @author 張晏哲
 * @category 接口類
 */
public interface EmailAiOutRepo extends JpaRepository<EmailAiOut, LocalDateTime> {

	/**
	 * 1. 依據身份証號查詢(依外撥時間遞減排序)。
	 * @param idNo 身份証號
	 * @return 清單
	 */
	public List<EmailAiOut> findAllByIdNoOrderByDateTimeDesc(String idNo);

	/**
	 * 2. 依據UUID查詢。
	 * @param uuid 識別值
	 * @return 清單
	 */
	public List<EmailAiOut> findAllByUuid(String uuid);

	/**
	 * 3. 依據身份証號讀取最近一筆外撥紀錄。
	 * @param idNo 身份証號
	 * @return 實體
	 */
	public EmailAiOut findTop1ByIdNoOrderByDateTimeDesc(String idNo);

	/**
	 * 4. 依據客戶選擇查詢。
	 * @param choice 客戶選擇
	 * @return 清單
	 */
	public List<EmailAiOut> findAllByChoiceOrderByDateTimeDesc(String choice);
}
