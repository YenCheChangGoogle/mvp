package com.fubon.mvp.data;

import javax.persistence.Column;
import javax.persistence.Entity;

/**
 * 富邦MVP-主從郵件系統實體
 * @author MILO-GAO(高振銘)@2020
 * @category 實體類
 */
@Entity(name="EMAILHOS")
public class EmailHost extends SidClass {

	// 版本序列號
	private static final long serialVersionUID = 202111L;

	// 1. 電腦地址
	@Column(name="IP", length=15, nullable=false)
	private String address;
	
	// 2. 服務器名
	@Column(name="HOST_NAME", length=30, nullable=false)
	private String hostname;
	
	// 3. 主服務器
	@Column(name="MAIN", nullable=false)
	private Integer main = 0;
	
	// 預設建構子
	public EmailHost() {
		super();
	}
	
	//------------------------------------------------------------------------------
	// 覆寫型
	//------------------------------------------------------------------------------

	@Override
	public String toString() {
		return "EmailHost [address=" + address + ", hostname=" + hostname + ", main=" + main + ", getId()=" + getId()
				+ "]";
	}

	//------------------------------------------------------------------------------
	// 讀寫子
	//------------------------------------------------------------------------------

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public Integer getMain() {
		return main;
	}

	public void setMain(Integer main) {
		this.main = main;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}	
}
