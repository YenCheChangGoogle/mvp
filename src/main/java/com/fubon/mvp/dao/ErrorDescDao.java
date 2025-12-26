package com.fubon.mvp.dao;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.fubon.mvp.data.ErrorDesc;

import page2020.util.EmptyUtil;

/**
 * 富邦MVP-郵件儲存器
 * @author MILO-GAO(高振銘)@2020
 * @category 儲存類
 */
@Repository
@Transactional
public class ErrorDescDao {

	private static Logger log = LoggerFactory.getLogger(ErrorDescDao.class);
	
	@Autowired
	private ErrorDescRepo repo;
	
	// 異常碼
	public static final String success = "0000";
	public static final String invalid = "9999";
	public static final String database = "E001";
	public static final String notfound = "E002";
	public static final String unique = "E003";
	public static final String notesb = "E010";
	public static final String uuid = "E011";
	public static final String notuuid = "E012";
	public static final String mail = "E1";
	public static final String terminate = "99999999999999999999999999999999999999999999999999";
	
	// 異常代碼池
	private Map<String, String> pool = Collections.synchronizedMap(new LinkedHashMap<String, String>());
	
	/**
	 * 1. 初始程序
	 */
	@PostConstruct
	public void initial() {
		
		// 1. 本系統異常碼。
		// (1) 標準類。
		// 0000 = 交易成功
		this.pool.put(success, "交易成功");
		// 9999 = 輸入格式有誤
		this.pool.put(invalid, "輸入格式有誤");
		// (2) 常見類。
		// E001 = 資料庫異常
		this.pool.put(database, "資料庫異常");
		// E002 = 資料不存在
		this.pool.put(notfound, "資料不存在");
		// E003 = 唯一值重複
		this.pool.put(unique, "唯一值重複");
		// (3) 業務類。
		// E010 = ESB服務器無法訪問
		this.pool.put(notesb, "ESB服務器無法訪問");
		// E011 = UUID已存在
		this.pool.put(uuid, "UUID已存在");
		// E012 = UUID不存在
		this.pool.put(notuuid, "UUID不存在");
		// (4) 郵件匝道類。
		this.pool.put(mail + "00", "郵件服務器(Mail Hunter)無法訪問");
		this.pool.put(mail + "01", "呼叫成功");
		this.pool.put(mail + "02", "呼叫失敗，郵件內容有機敏資料的疑慮");
		this.pool.put(mail + "03", "必填(*)欄位不全");
		this.pool.put(mail + "04", "有附件檔，但未傳入對應的附件檔檔名");
		this.pool.put(mail + "05", "只允許上傳副檔名為{ att_filename }的檔案");
		this.pool.put(mail + "06", "總夾檔檔案大小限制在{ att_filesize }M內");
		this.pool.put(mail + "07", "電子郵件為黑名單");
		this.pool.put(mail + "08", "此IP不允許使用");
		this.pool.put(mail + "09", "專案類別預設值不存在");
		this.pool.put(mail + "10", "其它錯誤，例如DB連線問題");		
		
		// 2. 更新資料庫。
		for (String key : this.pool.keySet()) {
			if (this.code(key) == null) {
				this.save(new ErrorDesc(key, this.pool.get(key)));
			}
		}
		
		// 3. 讀取完整資料庫。
		this.pool.clear();
		List<ErrorDesc> entities = this.repo.findAll();
		for (ErrorDesc entity : entities) {
			this.pool.put(entity.getCode(), entity.getDesc());
		}
	}
	
	/**
	 * 2. 交易成功。
	 * @return 字串數組
	 */
	public String[] success() {
		return new String[] { success, this.pool.get(success) };
	}

	/**
	 * 3. 輸入格式有誤。
	 * @return 字串數組
	 */
	public String[] invalid() {
		return new String[] { invalid, this.pool.get(invalid) };
	}

	/**
	 * 4. 資料庫異常。
	 * @return 字串數組
	 */
	public String[] database() {
		return new String[] { database, this.pool.get(database) };
	}
	
	/**
	 * 5. 唯一值重複。
	 * @return 字串數組
	 */
	public String[] unique() {
		return new String[] { unique, this.pool.get(unique) };
	}

	/**
	 * 6. 資料不存在。
	 * @return 字串數組
	 */
	public String[] notfound() {
		return new String[] { notfound, this.pool.get(notfound) };
	}
	
	/**
	 * 7. 交易已終止。
	 * @return 字串數組
	 */
	public String[] terminate() {
		return new String[] { terminate, "交易已終止！" };
	}

	/**
	 * 8. 通用陳述。
	 * @return 字串數組
	 */
	public String[] error(String code) {
		
		if (EmptyUtil.not(code) && this.pool.containsKey(code)) {
			return new String[] { code, this.pool.get(code) };
		} else {
			return new String[] { "", "" };
		}
	}
	
	/**
	 * 9. ESB服務器無法訪問。
	 * @return 字串數組
	 */
	public String[] notEsb() {
		return new String[] { notesb, this.pool.get(notesb) };
	}
	
	/**
	 * 10. 郵件閘道。
	 * @param errorCode 閘道錯誤碼 
	 * @return 字串數組
	 */
	public String[] mail(int errorCode) {
		
		String code = String.format("%s%02d", mail, errorCode);
		return new String[] { code, this.pool.get(code) };
	}
		
	//------------------------------------------------------------------------------
	// 查詢類
	//------------------------------------------------------------------------------

	/**
	 * 1. 查詢整個清單。
	 * @return 清單
	 */
	public List<ErrorDesc> all() {
		return this.repo.findAll();
	}
	
	//------------------------------------------------------------------------------
	// 讀取類
	//------------------------------------------------------------------------------

	/**
	 * 1. 依據異常碼讀取。
	 * @param code 異常碼 
	 * @return 實體
	 */
	public ErrorDesc code(String code) {
		return this.repo.findOneByCode(code);
	}

	//------------------------------------------------------------------------------
	// 操作類
	//------------------------------------------------------------------------------

	/**
	 * 1. 儲存資料。
	 * @param entity 實體
	 * @return 布林值
	 */
	public boolean save(ErrorDesc entity) {
		
		try {
			this.repo.save(entity);
		} catch (Exception ex) {
			log.error(ex.toString());
			return false;
		}
		return true;
	}	
}
