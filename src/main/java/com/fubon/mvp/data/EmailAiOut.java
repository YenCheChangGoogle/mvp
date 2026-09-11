package com.fubon.mvp.data;

import java.io.Serializable;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * 富邦MVP-AI外撥結果檔實體
 * 對應資料表: [dbo].[EMAILAIOUT] (由 SSMS 手動建表, 非由 Hibernate 產生)
 * 主鍵: DATETIME (外撥廠商回傳資料中的外撥時間戳記, 非系統自動產生的識別值)
 * @author 張晏哲
 * @category 實體類
 */
@Entity(name="EMAILAIOUT")
public class EmailAiOut implements Serializable {

	// 版本序列號
	private static final long serialVersionUID = 202609L;

	// 1. CHNL (通路)
	@Column(name="CHNL", length=5, nullable=false)
	private String channel;

	// 2. ID (客戶身分証號碼)
	@Column(name="ID", length=24, nullable=false)
	private String idNo;

	// 3. NAME (客戶姓名)
	@Column(name="NAME", length=120, nullable=false)
	private String name;

	// 4. PURPOSE (本次外撥目的)
	@Column(name="PURPOSE", length=256, nullable=false)
	private String purpose;

	// 5. PHONE (外撥號碼)
	@Column(name="PHONE", length=20, nullable=false)
	private String phone;

	// 6. STATUS (外撥結果)
	@Column(name="STATUS", length=256)
	private String status;

	// 7. RETRY (外撥次數)
	@Column(name="RETRY", length=3)
	private String retry;

	// 8. DATETIME (外撥時間, 主鍵)
	@Id
	@Column(name="DATETIME", nullable=false)
	private LocalDateTime dateTime;

	// 9. INTENT (客戶意圖)
	@Column(name="INTENT", length=256)
	private String intent;

	// 10. HANGUP (掛斷節點)
	@Column(name="HANGUP", length=256)
	private String hangup;

	// 11. CHOICE (客戶選擇)
	@Column(name="CHOICE", length=3)
	private String choice;

	// 12. UUID
	@Column(name="UUID", length=32)
	private String uuid;

	// 13. TTS1
	@Column(name="TTS1", length=256)
	private String tts1;

	// 14. VAR1
	@Column(name="VAR1", length=2)
	private String var1;

	// 15. TTS2
	@Column(name="TTS2", length=2)
	private String tts2;

	// 16. VAR2
	@Column(name="VAR2", length=2)
	private String var2;

	// 17. TTS3
	@Column(name="TTS3", length=2)
	private String tts3;

	// 18. VAR3
	@Column(name="VAR3", length=2)
	private String var3;

	// 19. TTS4
	@Column(name="TTS4", length=2)
	private String tts4;

	// 20. SMS1
	@Column(name="SMS1", length=2)
	private String sms1;

	// 21. SMS2
	@Column(name="SMS2", length=2)
	private String sms2;

	// 22. SMS3
	@Column(name="SMS3", length=2)
	private String sms3;

	// 23. SMS4
	@Column(name="SMS4", length=2)
	private String sms4;

	// 24. SMS5
	@Column(name="SMS5", length=2)
	private String sms5;

	// 25. SMSDefault
	@Column(name="SMSDefault", length=2)
	private String smsDefault;

	// 預設建構子
	public EmailAiOut() {
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

	public String getIdNo() {
		return idNo;
	}

	public void setIdNo(String idNo) {
		this.idNo = idNo;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPurpose() {
		return purpose;
	}

	public void setPurpose(String purpose) {
		this.purpose = purpose;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getRetry() {
		return retry;
	}

	public void setRetry(String retry) {
		this.retry = retry;
	}

	public LocalDateTime getDateTime() {
		return dateTime;
	}

	public void setDateTime(LocalDateTime dateTime) {
		this.dateTime = dateTime;
	}

	public String getIntent() {
		return intent;
	}

	public void setIntent(String intent) {
		this.intent = intent;
	}

	public String getHangup() {
		return hangup;
	}

	public void setHangup(String hangup) {
		this.hangup = hangup;
	}

	public String getChoice() {
		return choice;
	}

	public void setChoice(String choice) {
		this.choice = choice;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getTts1() {
		return tts1;
	}

	public void setTts1(String tts1) {
		this.tts1 = tts1;
	}

	public String getVar1() {
		return var1;
	}

	public void setVar1(String var1) {
		this.var1 = var1;
	}

	public String getTts2() {
		return tts2;
	}

	public void setTts2(String tts2) {
		this.tts2 = tts2;
	}

	public String getVar2() {
		return var2;
	}

	public void setVar2(String var2) {
		this.var2 = var2;
	}

	public String getTts3() {
		return tts3;
	}

	public void setTts3(String tts3) {
		this.tts3 = tts3;
	}

	public String getVar3() {
		return var3;
	}

	public void setVar3(String var3) {
		this.var3 = var3;
	}

	public String getTts4() {
		return tts4;
	}

	public void setTts4(String tts4) {
		this.tts4 = tts4;
	}

	public String getSms1() {
		return sms1;
	}

	public void setSms1(String sms1) {
		this.sms1 = sms1;
	}

	public String getSms2() {
		return sms2;
	}

	public void setSms2(String sms2) {
		this.sms2 = sms2;
	}

	public String getSms3() {
		return sms3;
	}

	public void setSms3(String sms3) {
		this.sms3 = sms3;
	}

	public String getSms4() {
		return sms4;
	}

	public void setSms4(String sms4) {
		this.sms4 = sms4;
	}

	public String getSms5() {
		return sms5;
	}

	public void setSms5(String sms5) {
		this.sms5 = sms5;
	}

	public String getSmsDefault() {
		return smsDefault;
	}

	public void setSmsDefault(String smsDefault) {
		this.smsDefault = smsDefault;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	@Override
	public String toString() {
		return "EmailAiOut [channel=" + channel + ", idNo=" + idNo + ", name=" + name + ", purpose=" + purpose
				+ ", phone=" + phone + ", status=" + status + ", retry=" + retry + ", dateTime=" + dateTime
				+ ", intent=" + intent + ", hangup=" + hangup + ", choice=" + choice + ", uuid=" + uuid
				+ ", tts1=" + tts1 + ", var1=" + var1 + ", tts2=" + tts2 + ", var2=" + var2 + ", tts3=" + tts3
				+ ", var3=" + var3 + ", tts4=" + tts4 + ", sms1=" + sms1 + ", sms2=" + sms2 + ", sms3=" + sms3
				+ ", sms4=" + sms4 + ", sms5=" + sms5 + ", smsDefault=" + smsDefault + "]";
	}
}
