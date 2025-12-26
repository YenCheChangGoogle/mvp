package com.fubon.mvp.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fubon.mvp.data.Channel;

/**
 * 富邦MVP-交易通道接口
 * @author MILO-GAO(高振銘)@2020
 * @category 接口類
 */
public interface ChannelRepo extends JpaRepository<Channel, Long> {
	
	//------------------------------------------------------------------------------
	// 讀取類
	//------------------------------------------------------------------------------

	/**
	 * 1. 依據通路與次通路編號讀取。
	 * @param channel 通路
	 * @param subChannel 次通路
	 * @return 實體
	 */
	public Channel findOneByChannelAndSubChannel(String channel, String subChannel);
}
