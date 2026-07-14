package com.fubon.mvp.serv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.soap.SoapMessage;

import com.fubon.mvp.data.EmailMaster;
import com.fubon.mvp.email.SendToOneUIDResponse;
import com.fubon.mvp.email.SendToOneUID;

/**
 * 富邦MVP-郵件服務的WS用戶端
 * @author MILO-GAO(高振銘)@2020
 * @category 網絡SOAP用戶端
 */
public class EmailWsClient extends WebServiceGatewaySupport {

	private static Logger log = LoggerFactory.getLogger(EmailWsClient.class);

	@Autowired
	private EmailContent content;
	
	// 設定 SOAP-Action 的類。
	private WebServiceMessageCallback soapAction = new WebServiceMessageCallback() {
	    public void doWithMessage(WebServiceMessage message) {
	        ((SoapMessage) message).setSoapAction("http://tempuri.org/SendToOneUID");
	    }
	};
	
	/**
	 * 1. 寄出郵件。
	 * @param master 郵件主檔
	 * @return 回復狀態。
	 */
	public int sendMail(EmailMaster master) {
		
		// 1. 創建請求對象。
		SendToOneUID request = new SendToOneUID();
		// (1) project_category_code	專案類別代號
		request.setProjectCategoryCode("Mvp02");
		// (2) toname	收件者姓名 (新郵箱帶遮罩)
		// 產生新郵箱的遮罩字串。
		request.setToname(master.getAfterEmail());
		//request.setToname(this.maskToname(master.getAfterEmail()));
		// (3) toemail	收件者Email
		request.setToemail(master.getAfterEmail());
		// (4) fromname	寄件者姓名
		request.setFromname("台北富邦銀行");
		// (5) fromemail (2021/12/23 10:45)
		request.setFromemail("service");
		// (6) subject	郵件主旨
		request.setSubject("台北富邦銀行電子郵件確認通知/Notice of E-mail Address Confirmation");
		// (7) content	郵件內容
		request.setContent(this.content.email(master));
		log.info("################# 信件內容字數="+request.getContent().length());
		// (8) uid UUID (2021/12/21 16:55)
		request.setUID(master.getUuid());
		log.info("request: " + request.toString());
		
		// 2. 創建響應對象。
		SendToOneUIDResponse response = null;
		try {
			response = (SendToOneUIDResponse) super.getWebServiceTemplate()
				.marshalSendAndReceive(request, this.soapAction);
		} catch (Exception ex) {
			ex.printStackTrace();
			log.error(ex.toString());
			return -1;
		}
		log.info("response: " + response.toString());
		
		// 3. 返回狀態值。
		return response.getSendToOneUIDResult();
	}
	
	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------

	/**
	 * 1. 中文遮罩姓名。
	 * @param fullname 中文全名
	 * @return 字串
	private String maskChName(String fullname) {
		
		// 1. 缺省姓名。
		if (EmptyUtil.is(fullname)) {
			return "xxx";
		}
		
		// 2. 依據字串長度判讀。
		String maskChName = null;
		int len = fullname.length();
		String first = fullname.substring(0, 1);
		if (len == 1) {
			maskChName = first;
		} else if (len == 2) {
			maskChName = first + "*";
		} else if (len == 3) {
			maskChName = first + "*" + fullname.substring(2, 3);
		} else {
			maskChName = first + "**" + fullname.substring(3);
		}
		return maskChName;
	}
	*/
	
	/**
	 * 2. 用戶名遮罩。
	 * @param email 原始的新郵箱。
	 * @return 字串
	 */
	private String maskToname(String email) {

		int pos = email.indexOf("@");
		if (pos > 0) {
			email = email.substring(0, pos);
		}
		return email;
		
		/*
		String original = email;
		int len = email.length();
		if (len <= 1) {
			email = "*";
		} else {
			String firstChar = email.substring(0, 1);
			if (len == 2) {
				email = firstChar + "*";
			} else if (len == 3) {
				email = firstChar + "**";
			} else if (len == 4) {
				email = firstChar + "***";
			} else {
				email = firstChar + "***" + email.substring(4);
			}
		}
		if (pos > 0) {
			email += original.substring(pos);
		}
		return email;
		*/
	}
}
