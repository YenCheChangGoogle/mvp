package com.fubon.mvp.data;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;

import org.dom4j.Document;
import org.dom4j.Node;

import page2020.util.EmptyUtil;
import page2020.util.TimeUtil;

/**
 * 富邦MVP-存活驗證實體
 * @author MILO-GAO(高振銘)@2020
 * @category 實體類
 */
@Entity(name="EMAILIMG")
public class EmailImage extends SidClass {

	// 版本序列號
	private static final long serialVersionUID = 202111L;

	// 1. CHNL
	@Column(name="CHNL", length=2, nullable=false)
	private String channel;
	
	// 2. SUB_CHNL
	@Column(name="SUB_CHNL", length=2, nullable=false)
	private String subChannel;
	
	// 3. CHG_DATE
	@Column(name="CHG_DATE", length=8, nullable=false)
	private String changeDate;
	
	// 4. CHG_TIME
	@Column(name="CHG_TIME", length=6, nullable=false)
	private String changeTime;
	
	// 5. UUID
	@Column(name="UUID", length=32, nullable=false)
	private String uuid;
	
	// 6. TRAN_CODE
	@Column(name="TRAN_CODE", length=10, nullable=false)
	private String tranCode;
	
	// 7. ERR_CODE
	@Column(name="ERR_CODE", length=6)
	private String errorCode;

	// 8. IP
	@Column(name="IP", length=15)
	private String address;
	
	// 預設建構子
	public EmailImage() {
		super();
	}
	
	/**
	 * 建構子(1)
	 * @param uuid 交易識別值
	 * @param tranCode 交易代碼
	 */
	public EmailImage(String uuid, String tranCode) {
		
		this.channel = "0Z";
		this.subChannel = "89";
		this.changeDate = TimeUtil.dateE(new Date());
		this.changeTime = TimeUtil.timeE(new Date());
		this.uuid = uuid;
		this.tranCode = tranCode;
		this.errorCode = "";
	}

	/**
	 * 建構子(2)
	 * @param master 郵件主檔
	 */
	public EmailImage(EmailMaster master) {
		
		this.channel = master.getChannel();
		this.subChannel = master.getSubChannel();
		this.changeDate = master.getChangeDate();
		this.changeTime = master.getChangeTime();
		this.uuid = master.getUuid();
		if (EmptyUtil.is(this.uuid)) {
			this.uuid = master.getQueryUuid();
		}
		this.tranCode = master.getTranCode();
		this.errorCode = master.getErrorCode();
	}

	/**
	 * 建構子(3)
	 * @param master 郵件主檔
	 * @param txStatus 交易狀態
	 */
	public EmailImage(EmailMaster master, String txStatus) {
		
		this(master);
		this.tranCode = txStatus;
	}

	/**
	 * 建構子(4)
	 * @param doc 上行電文
	 * @param tranCode 交易代碼
	 */
	public EmailImage(Document doc, String tranCode) {

		// 1. CHNL
		Node chnl = doc.selectSingleNode("//CHNL");
		this.channel = (chnl != null && EmptyUtil.not(chnl.getText())) ? chnl.getText() : "**";
		// 2. SUB_CHNL
		Node subchnl = doc.selectSingleNode("//SUB_CHNL");
		this.subChannel = (subchnl != null && EmptyUtil.not(subchnl.getText())) ? subchnl.getText() : "**";
		// 3. CHG_DATE
		this.changeDate = TimeUtil.dateE(new Date());
		// 4. CHG_TIME
		this.changeTime = TimeUtil.timeE(new Date());
		// 5. UUID
		Node uuid = doc.selectSingleNode("//UUID");
		this.uuid = (uuid != null && EmptyUtil.not(uuid.getText())) ? uuid.getText() : "*************************";
		// 6. TRAN_CODE
		this.tranCode = tranCode;
		// 7. ERR_CODE
		this.errorCode = "";
	}
	
	//------------------------------------------------------------------------------
	// 覆寫型
	//------------------------------------------------------------------------------

	@Override
	public String toString() {
		return "EmailImage [channel=" + channel + ", subChannel=" + subChannel + ", changeDate=" + changeDate
				+ ", changeTime=" + changeTime + ", uuid=" + uuid + ", tranCode=" + tranCode + ", errorCode="
				+ errorCode + ", address=" + address + ", getId()=" + getId() + "]";
	}
	
	//------------------------------------------------------------------------------
	// 讀寫子
	//------------------------------------------------------------------------------

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public String getSubChannel() {
		return subChannel;
	}

	public void setSubChannel(String subChannel) {
		this.subChannel = subChannel;
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

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getTranCode() {
		return tranCode;
	}

	public void setTranCode(String tranCode) {
		this.tranCode = tranCode;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}	
}
