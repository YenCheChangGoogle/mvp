package com.fubon.mvp.serv;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.transport.WebServiceMessageSender;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

/**
 * 富邦MVP-郵件服務的WS組態
 * @author MILO-GAO(高振銘)@2020
 * @category 網絡SOAP組態
 */
@Configuration
public class EmailWsConfig {

	// POJO路徑。
	@Value("${default.pojo.1}")
	private String pojo;
	
	// 網絡服務路徑。
	@Value("${default.url.1}")
	private String url;

	// Email用戶端
	@Bean
	public EmailWsClient emailWsClient() {
		
		EmailWsClient client = new EmailWsClient();
		client.setDefaultUri(this.url);
		client.setMarshaller(this.mashaller());
		client.setUnmarshaller(this.mashaller());
		client.setMessageSender(this.sender());
		return client;
	}

	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------

	// 1. 設定POJO路徑。
	private Jaxb2Marshaller mashaller() {
		
		Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
		marshaller.setContextPath(this.pojo);
		return marshaller;
	}
	
	// 2. 設定連綫參數。
	private WebServiceMessageSender sender() {
		
		HttpComponentsMessageSender sender = new HttpComponentsMessageSender();
		sender.setConnectionTimeout(15000);
		sender.setReadTimeout(15000);
		return sender;
	}
}
