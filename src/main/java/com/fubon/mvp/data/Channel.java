package com.fubon.mvp.data;

import javax.persistence.Column;
import javax.persistence.Entity;

/**
 * 富邦MVP-交易通路實體
 * @author MILO-GAO(高振銘)@2020
 * @category 實體類
 */
@Entity(name="CHNL")
public class Channel extends SidClass {

	// 版本序列號
	private static final long serialVersionUID = 202111L;

	// 1. CHNL
	@Column(name="CHNL", length=2, nullable=false)
	private String channel;
	
	// 2. SUB_CHNL
	@Column(name="SUB_CHNL", length=2)
	private String subChannel;
	
	// 3. CHNL_NAME
	@Column(name="CHNL_NAME", length=20)
	private String name;
	
	// 4. RESPONSE
	@Column(name="RESPONSE", length=20)
	private String response;

	// 預設建構子
	public Channel() {
		super();
	}
	
	/**
	 * 建構子(1)
	 * @param channel 通路
	 * @param subChannel 次通路
	 * @param name 通路名
	 * @param response 響應字串
	 */
	public Channel(String channel, String subChannel, String name) {
		
		this.channel = channel;
		this.subChannel = subChannel;
		this.name = name;
		this.response = "";
	}

	//------------------------------------------------------------------------------
	// 覆寫型
	//------------------------------------------------------------------------------

	@Override
	public String toString() {
		return "Channel [channel=" + channel + ", subChannel=" + subChannel + ", name=" + name + ", response="
				+ response + ", getId()=" + getId() + "]";
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getResponse() {
		return response;
	}

	public void setResponse(String response) {
		this.response = response;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}
}
