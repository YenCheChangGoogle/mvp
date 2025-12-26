package com.fubon.mvp.data;

import javax.persistence.Column;
import javax.persistence.Entity;

/**
 * 富邦MVP-異常碼實體
 * @author MILO-GAO(高振銘)@2020
 * @category 實體類
 */
@Entity(name="ERRDESC")
public class ErrorDesc extends SidClass {

	// 版本序列號
	private static final long serialVersionUID = 202111L;

	// 1. ERR_CODE	CHAR(6)
	@Column(name="ERR_CODE", length=6, nullable=false, unique=true)
	private String code;
	
	// 2. ERR_DESC	CHAR(120)
	@Column(name="ERR_DESC", length=120)
	private String desc;
	
	// 預設建構子
	public ErrorDesc() {
		super();
	}
	
	/**
	 * 建構子(1)
	 * @param code 異常碼
	 * @param desc 陳述
	 */
	public ErrorDesc(String code, String desc) {
		
		this.code = code;
		this.desc = desc;
	}

	//------------------------------------------------------------------------------
	// 讀寫子
	//------------------------------------------------------------------------------

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}	
}
