package com.fubon.mvp.serv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import javax.annotation.PostConstruct;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.fubon.mvp.data.EmailMaster;

import page2020.util.EmptyUtil;

/**
 * 富邦MVP-信件内容元件
 * @author MILO-GAO(高振銘)@2020
 * @category 元件類
 */
@Component
public class EmailContent {
	
	// 外部鏈接。
	private static final String portal_url = "https://www.fubon.com/banking/event/hot/mvp/index.html";
	
	// 文件的關鍵字
	// 1. 身份證號
	private static final String keyCustId = "{MVP:CUST_ID}";
	// 2. 中文時間
	private static final String keyCnDate = "{MVP:CN_DATE}";
	// 3. 英文時間
	private static final String keyEnDate = "{MVP:EN_DATE}";	
	// 4. 確認網址
	private static final String keyEmail = "{MVP:EMAIL}";
	
	// 信件内容。
	private String emailContent;
	
	/**
	 * 1. 初始程序。
	 */
	@PostConstruct
	public void initial() {
		
		// 1. 讀取文字檔。
		StringBuilder sb1 = new StringBuilder();
		try {
			Resource resource = new ClassPathResource("paper/email");
			File file = resource.getFile();
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				sb1.append(line);
			}
			reader.close();
		} catch (Exception ex) {}

		// 2. 保存在變數。
		this.emailContent = sb1.toString();
	}
	
	/**
	 * 2. 讀取郵件内文。
	 * @param master 郵件主檔
	 * @return 字串
	 */
	public String email(EmailMaster master) {
			
		String email = this.emailContent;
		email = email.replace(keyCustId, this.custId(master));
		email = email.replace(keyCnDate, this.chineseDate(master));
		email = email.replace(keyEnDate, this.englishDate(master));
		email = email.replace(keyEmail, portal_url);
		return email;
	}
	
	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------
	
	/**
	 * 1. 身份証號。
	 * @param master 郵件主檔
	 * @return 字串
	 */
	private String custId(EmailMaster master) {

		String custId = null;
		String id = master.getIdNo();
		if (EmptyUtil.is(id) || id.length() < 8) {
			custId = "*******";
		} else if (id.length() == 8) {
			custId = id.substring(0, 2) + "****" + id.substring(6);
		} else {
			custId = id.substring(0, 3) + "****" + id.substring(7);
		}
		return custId; 
	}

	/**
	 * 2. 中文日期。
	 * @param master 郵件主檔
	 * @return 字串
	 */
	private String chineseDate(EmailMaster master) {
		
		String date = master.getChangeDate();
		return String.format("%s年%s月%s日", 
			date.substring(0, 4), date.substring(4, 6), date.substring(6, 8));
	}
	
	/**
	 * 3. 英文日期。
	 * @param master 郵件主檔
	 * @return 字串
	 */
	private String englishDate(EmailMaster master) {
		
		String date = master.getChangeDate();
		return String.format("%s/%s/%s", 
			date.substring(0, 4), date.substring(4, 6), date.substring(6, 8));		
	}
}
