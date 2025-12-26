package com.fubon.mvp.data;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;

import page2020.util.TimeUtil;

/**
 * 富邦MVP-郵件明細檔實體
 * @author MILO-GAO(高振銘)@2020
 * @category 實體類
 */
@Entity(name="EMAILDTL")
public class EmailDetail extends SidClass {

	// 版本序列號
	private static final long serialVersionUID = 202111L;

	// 1. UUID
	@Column(name="UUID", length=32, nullable=false)
	private String uuid;
	
	// 2. CHG_DATE
	@Column(name="CHG_DATE", length=8, nullable=false)
	private String changeDate;
	
	// 3. CHG_TIME
	@Column(name="CHG_TIME", length=8, nullable=false)
	private String changeTime;
	
	// 4. RESP_DATE
	@Column(name="RESP_DATE", length=8, nullable=false)
	private String responeDate;
	
	// 5. RESP_TIME
	@Column(name="RESP_TIME", length=8, nullable=false)
	private String responeTime;
	
	// 6. TX_STATUS
	@Column(name="TX_STATUS", length=2)
	private String txStatus;
	
	// 7. ERR_CODE
	@Column(name="ERR_CODE", length=6)
	private String errorCode;

	// 預設建構子
	public EmailDetail() {
		super();
	}
	
	/**
	 * 建構子(1)
	 * @param master 主檔實體
	 */
	public EmailDetail(EmailMaster master) {
		
		this.uuid = master.getUuid();
		this.changeDate = master.getChangeDate();
		this.changeTime = master.getChangeTime();
		this.responeDate = TimeUtil.dateE(new Date());
		this.responeTime = TimeUtil.timeE(new Date());
		this.txStatus = master.getTxStatus();
		this.errorCode = master.getErrorCode();
	}
	
	/**
	 * 建構子(2)
	 * @param master 主檔實體
	 * @param txStatus 交易狀態
	 */
	public EmailDetail(EmailMaster master, String txStatus) {
		
		this(master);
		this.txStatus = txStatus;
	}

	//------------------------------------------------------------------------------
	// 讀寫子
	//------------------------------------------------------------------------------

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getChangeDate() {
		return changeDate;
	}

	public void setChangeDate(String changeDate) {
		this.changeDate = changeDate;
	}

	public String getChangeTime() {
		return changeTime;
	}

	public void setChangeTime(String changeTime) {
		this.changeTime = changeTime;
	}

	public String getResponeDate() {
		return responeDate;
	}

	public void setResponeDate(String responeDate) {
		this.responeDate = responeDate;
	}

	public String getResponeTime() {
		return responeTime;
	}

	public void setResponeTime(String responeTime) {
		this.responeTime = responeTime;
	}

	public String getTxStatus() {
		return txStatus;
	}

	public void setTxStatus(String txStatus) {
		this.txStatus = txStatus;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}	
}
