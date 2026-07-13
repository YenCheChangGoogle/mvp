package com.fubon.mvp.data;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.dom4j.Document;

import page2020.util.EmptyUtil;
import page2020.util.TimeUtil;

/**
 * 富邦MVP-郵件主檔實體
 * @author MILO-GAO(高振銘)@2020
 * @category 實體類
 */
@Entity(name="EMAILMAS")
public class EmailMaster extends SidClass {

	// 版本序列號
	private static final long serialVersionUID = 202111L;

	// 1. UUID
	@Column(name="UUID", length=32, nullable=false, unique=true)
	private String uuid;

	// 2. ID(身份証號碼)
	@Column(name="ID", length=24, nullable=false)
	private String idNo;

	// 3. ID_TYPE
	@Column(name="ID_TYPE", length=2, nullable=false)
	private String idType;

	// 4. CH_NAME
	@Column(name="CH_NAME", length=120)
	private String chName;

	// 5. EN_NAME
	@Column(name="EN_NAME", length=120)
	private String enName;

	// 6. BRANCH
	@Column(name="BRANCH", length=5)
	private String branch;

	// 7. TELLER
	@Column(name="TELLER", length=10)
	private String teller;

	// 8. PREV_EMAIL_ADDR
	@Column(name="PREV_EMAIL_ADDR", length=50)
	private String prevEmail;

	// 9. AFTER_EMAIL_ADDR
	@Column(name="AFTER_EMAIL_ADDR", length=50)
	private String afterEmail;

	// 10. REASON
	@Column(name="REASON", length=50)
	private String reason;

	// 11. CHG_DATE
	@Column(name="CHG_DATE", length=8, nullable=false)
	private String changeDate;

	// 12. CHG_TIME
	@Column(name="CHG_TIME", length=8, nullable=false)
	private String changeTime;

	// 13. CHNL
	@Column(name="CHNL", length=2)
	private String channel;

	// 14. SUB_CHNL
	@Column(name="SUB_CHNL", length=2)
	private String subChannel;

	// 15. ON_OFF_LINE
	@Column(name="ON_OFF_LINE", length=1)
	private String online;

	// 16. TRAN_CODE
	@Column(name="TRAN_CODE", length=6)
	private String tranCode;

	// 17. STATUS(00處理中,01成功完成,02失敗,99作廢)
	@Column(name="STATUS", length=2)
	private String status;

	// 18. TX_STATUS(當 STATUS=00/01/02時,此欄為目前交易狀態,01/02時狀態不再變,99時表示當時最後處理的狀況)
	@Column(name="TX_STATUS", length=2)
	private String txStatus;

	// 19. ERR_CODE
	@Column(name="ERR_CODE", length=6)
	private String errorCode;

	// 20. REMAKR
	@Column(name="REMAKR", length=500)
	private String remark;

	// 21. CHECKER
	@Column(name="CHECKER", length=100)
	private String checker;

	// 22. TEL_NO (AI外撥手機號碼)
	@Column(name="TEL_NO", length=20)
	private String telNo;
	
	// 23. FLAG (AI外撥標記: 1=待處理, 2=已獲取, 0=不處理)
	@Column(name="FLAG", length=1)
	private String flag;
	
	// 24. NAME (AI外撥客戶姓名)
	@Column(name="NAME", length=120)
	private String name;
	
	// 25. PHONE (AI外撥客戶電話)
	@Column(name="PHONE", length=20)
	private String phone;
	
	//------------------------------------------------------------------------------
	// 臨時型
	//------------------------------------------------------------------------------
	
	// 1. 查詢UUID
	@Transient
	private String queryUuid;

	// 2. 開始日期
	@Transient
	private String beginDate;

	// 3. 結束日期
	@Transient
	private String endDate;

	// 4. 下個主鍵
	@Transient
	private String nextKey;

	// 預設建構子
	public EmailMaster() {
		super();
	}

	/**
	 * 建構子(1)
	 * @param doc XML文件
	 * @param txNo 交易代號
	 */
	public EmailMaster(Document doc, String txNo) {

		// 電文型
		this.tranCode = txNo;
		try { this.uuid = doc.selectSingleNode("//UUID").getText().trim(); }catch(Exception ex) {}
		try { this.branch = doc.selectSingleNode("//BRANCH").getText().trim(); }catch(Exception ex) {}
		try { this.teller = doc.selectSingleNode("//TELLER_NO").getText().trim(); }catch(Exception ex) {}
		try { this.channel = doc.selectSingleNode("//CHNL").getText().trim(); }catch(Exception ex) {}
		try { this.subChannel = doc.selectSingleNode("//SUB_CHNL").getText().trim(); }catch(Exception ex) {}
		try { this.idNo = doc.selectSingleNode("//CUST_ID").getText().trim(); }catch(Exception ex) {}
		try { this.idType = doc.selectSingleNode("//ID_TYPE").getText().trim(); }catch(Exception ex) {}
		try { this.chName = doc.selectSingleNode("//CUST_NAME").getText().trim(); }catch(Exception ex) {}
		try { this.enName = doc.selectSingleNode("//ENG_NAME").getText().trim(); }catch(Exception ex) {}
		try { this.prevEmail = doc.selectSingleNode("//PREV_EMAIL_ADDR").getText().trim(); }catch(Exception ex) {}
		try { this.afterEmail = doc.selectSingleNode("//AFTER_EMAIL_ADDR").getText().trim(); }catch(Exception ex) {}
		try { this.changeDate = TimeUtil.dateE(new Date()); }catch(Exception ex) {}
		try { this.changeTime = TimeUtil.timeE(new Date()); }catch(Exception ex) {}
		try { this.online = doc.selectSingleNode("//ON_OFF_LINE").getText().trim(); }catch(Exception ex) {}
		try { this.reason = doc.selectSingleNode("//REASON").getText().trim(); }catch(Exception ex) {}
		try { this.remark = doc.selectSingleNode("//REMARK").getText(); }catch(Exception ex) {}
		
		// 延伸型
		// (1) remark 欄位後面20個byte會寫入欄位 checker
		//int len = this.remark.length();
		//if (len == 500) {
		//	this.checker = this.remark.substring(480).trim();
		//	this.remark = this.remark.substring(0, 480).trim();
		//} else {
		//	this.checker = "";
		//	this.remark = this.remark.trim();
		//}
		
		// 臨時型
		try { this.queryUuid = doc.selectSingleNode("//QUERY_UUID").getText().trim(); }catch(Exception ex) {}
		try { this.beginDate = doc.selectSingleNode("//FROM_DATE").getText().trim(); }catch(Exception ex) {}
		try { this.endDate = doc.selectSingleNode("//TO_DATE").getText().trim(); }catch(Exception ex) {}
		try { this.nextKey = doc.selectSingleNode("//NEXT_KEY").getText().trim(); }catch(Exception ex) {}
	}

	//------------------------------------------------------------------------------
	// 公開級
	//------------------------------------------------------------------------------

	/**
	 * 1. 輸入格式檢查(MVC110001/MVC110003)。
	 * @return 布林值
	 */
	public boolean invalid110001() {
		return EmptyUtil.is(this.uuid, this.branch, this.teller, this.idNo, this.idType, this.afterEmail);
	}

	/**
	 * 1.2  輸入格式檢查(MVC110002)。(2024/07/10 11:00)。
	 * @return 布林值
	 */
	public boolean invalid110002() {
		return EmptyUtil.is(this.uuid, this.branch, this.teller, this.idNo, this.idType, this.afterEmail);
	 }

	/**
	 * 2. 輸入格式檢查(MVC310001)。
	 * @return 布林值
	 */
	public boolean invalid310001() {
		return EmptyUtil.is(this.getQueryUuid());
	}

	/**
	 * 3. 輸入格式檢查(MVC310002)。
	 * @return 布林值
	 */
	public boolean invalid310002() {

		if ((EmptyUtil.is(this.idNo)) ||
			(EmptyUtil.not(this.beginDate) && this.beginDate.length() != 8) ||
			(EmptyUtil.not(this.endDate) && this.endDate.length() != 8)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 4. 必填欄位 驗證是否合法
	 * @return 布林值
	 */
	public boolean invalid310003() {
		
		if(this.queryUuid!=null && this.queryUuid.trim().length()>0) return false;
		if(this.idNo!=null && this.idNo.trim().length()>0) return false;
		//if(this.uuid!=null && this.uuid.trim().length()>0) return false;
		return true;
		
		//return EmptyUtil.is(this.uuid, this.branch, this.teller, this.idNo, this.idType, this.queryUuid);
	}

	/**
	 * 4. 是作廢條件? (2022/01/24 12:00)
	 * @return 布林值
	 */
	public boolean isCancel() {

		if ((! EmptyUtil.is(this.idNo)) &&
			("Y".equals(this.online) || "0".equals(this.online)) &&
			EmptyUtil.is(this.prevEmail) &&
			EmptyUtil.is(this.afterEmail)) {
			// 非空值 = uuid, idType
			if (EmptyUtil.is(this.uuid)) {
				this.uuid = "0";
			}
			if (EmptyUtil.is(this.idType)) {
				this.idType = "0";
			}
			return true;
		} else {
			return false;
		}
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

	public String getIdNo() {
		return idNo;
	}

	public void setIdNo(String idNo) {
		this.idNo = idNo;
	}

	public String getIdType() {
		return idType;
	}

	public void setIdType(String idType) {
		this.idType = idType;
	}

	public String getChName() {
		return chName;
	}

	public void setChName(String chName) {
		this.chName = chName;
	}

	public String getEnName() {
		return enName;
	}

	public void setEnName(String enName) {
		this.enName = enName;
	}

	public String getBranch() {
		return branch;
	}

	public void setBranch(String branch) {
		this.branch = branch;
	}

	public String getTeller() {
		return teller;
	}

	public void setTeller(String teller) {
		this.teller = teller;
	}

	public String getPrevEmail() {
		return prevEmail;
	}

	public void setPrevEmail(String prevEmail) {
		this.prevEmail = prevEmail;
	}

	public String getAfterEmail() {
		return afterEmail;
	}

	public void setAfterEmail(String afterEmail) {
		this.afterEmail = afterEmail;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
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

	public String getOnline() {
		return online;
	}

	public void setOnline(String online) {
		this.online = online;
	}

	public String getTranCode() {
		return tranCode;
	}

	public void setTranCode(String tranCode) {
		this.tranCode = tranCode;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
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

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public String getQueryUuid() {
		return queryUuid;
	}

	public void setQueryUuid(String queryUuid) {
		this.queryUuid = queryUuid;
	}

	public String getBeginDate() {
		return beginDate;
	}

	public void setBeginDate(String beginDate) {
		this.beginDate = beginDate;
	}

	public String getEndDate() {
		return endDate;
	}

	public void setEndDate(String endDate) {
		this.endDate = endDate;
	}

	public String getNextKey() {
		return nextKey;
	}

	public void setNextKey(String nextKey) {
		this.nextKey = nextKey;
	}

	public String getChecker() {
		return checker;
	}

	public void setChecker(String checker) {
		this.checker = checker;
	}

	public String getTelNo() {
		return telNo;
	}

	public void setTelNo(String telNo) {
		this.telNo = telNo;
	}

	public String getFlag() {
		return flag;
	}

	public void setFlag(String flag) {
		this.flag = flag;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	@Override
	public String toString() {
		return "EmailMaster [uuid=" + uuid + ", idNo=" + idNo + ", idType=" + idType + ", chName=" + chName
				+ ", enName=" + enName + ", branch=" + branch + ", teller=" + teller + ", prevEmail=" + prevEmail
				+ ", afterEmail=" + afterEmail + ", reason=" + reason + ", changeDate=" + changeDate + ", changeTime="
				+ changeTime + ", channel=" + channel + ", subChannel=" + subChannel + ", online=" + online
				+ ", tranCode=" + tranCode + ", status=" + status + ", txStatus=" + txStatus + ", errorCode="
				+ errorCode + ", remark=" + remark + ", checker=" + checker + ", telNo=" + telNo + ", flag=" + flag
				+ ", name=" + name + ", phone=" + phone + ", queryUuid=" + queryUuid + ", beginDate=" + beginDate
				+ ", endDate=" + endDate + ", nextKey=" + nextKey + ", invalid110001()=" + invalid110001()
				+ ", invalid110002()=" + invalid110002() + ", invalid310001()=" + invalid310001() + ", invalid310002()="
				+ invalid310002() + ", isCancel()=" + isCancel() + ", getUuid()=" + getUuid() + ", getIdNo()="
				+ getIdNo() + ", getIdType()=" + getIdType() + ", getChName()=" + getChName() + ", getEnName()="
				+ getEnName() + ", getBranch()=" + getBranch() + ", getTeller()=" + getTeller() + ", getPrevEmail()="
				+ getPrevEmail() + ", getAfterEmail()=" + getAfterEmail() + ", getReason()=" + getReason()
				+ ", getChangeDate()=" + getChangeDate() + ", getChangeTime()=" + getChangeTime() + ", getChannel()="
				+ getChannel() + ", getSubChannel()=" + getSubChannel() + ", getOnline()=" + getOnline()
				+ ", getTranCode()=" + getTranCode() + ", getStatus()=" + getStatus() + ", getTxStatus()="
				+ getTxStatus() + ", getErrorCode()=" + getErrorCode() + ", getRemark()=" + getRemark()
				+ ", getQueryUuid()=" + getQueryUuid() + ", getBeginDate()=" + getBeginDate() + ", getEndDate()="
				+ getEndDate() + ", getNextKey()=" + getNextKey() + ", getChecker()=" + getChecker() + ", getTelNo()="
				+ getTelNo() + ", getFlag()=" + getFlag() + ", getName()=" + getName() + ", getPhone()=" + getPhone()
				+ "]";
	}
}
