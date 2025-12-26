package com.fubon.mvp.ctrl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fubon.mvp.serv.Mvc084000Serv;
import com.fubon.mvp.serv.Mvc110001Serv;
import com.fubon.mvp.serv.Mvc110002Serv;
import com.fubon.mvp.serv.Mvc110003Serv;
import com.fubon.mvp.serv.Mvc110006Serv;
import com.fubon.mvp.serv.Mvc110007Serv;
import com.fubon.mvp.serv.Mvc310001Serv;
import com.fubon.mvp.serv.Mvc310002Serv;

import page2020.client.WebProxy;

/**
 * 富邦MVP- MVP 控制器
 * @author MILO-GAO(高振銘)@2020
 * @category 組態類
 */
@RestController
@RequestMapping("/api")
public class MvpApiCtrl {

	@Autowired
	private WebProxy proxy;
	@Autowired
	private Mvc110001Serv mvc110001;
	@Autowired
	private Mvc110002Serv mvc110002;
	@Autowired
	private Mvc110003Serv mvc110003;
	@Autowired
	private Mvc110006Serv mvc110006;
	@Autowired
	private Mvc110007Serv mvc110007;
	@Autowired
	private Mvc310001Serv mvc310001;
	@Autowired
	private Mvc310002Serv mvc310002;
	@Autowired
	private Mvc084000Serv mvc084000;

	/**
	 * 1. MVC110001 - 前臺登錄。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/110001")
	public String mvc110001(@RequestBody String xml) {
		return mvc110001.service(this.proxy.document(xml));
	}

	/**
	 * 2. MVC110002 - 取消申請。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/110002")
	public String mvc110002(@RequestBody String xml) {
		return mvc110002.service(this.proxy.document(xml));
	}
	
	/**
	 * 2. MVC110003 - 前臺人工啟用。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/110003")
	public String mvc110003(@RequestBody String xml) {
		return mvc110003.service(this.proxy.document(xml));
	}

	/**
	 * 3. MVC110006 - 信件狀態。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/110006")
	public String mvc110006(@RequestBody String xml) {
		return mvc110006.service(this.proxy.document(xml));
	}
	
	/**
	 * 4. MVC110007 - 客戶確認信件。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/110007")
	public String mvc110007(@RequestBody String xml) {
		return mvc110007.service(this.proxy.document(xml));
	}

	/**
	 * 5. MVC310001 - 前臺查詢。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/310001")
	public String mvc310001(@RequestBody String xml) {
		return mvc310001.service(this.proxy.document(xml));
	}

	/**
	 * 6. MVC310002 - 整批查詢。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/310002")
	public String mvc310002(@RequestBody String xml) {
		return mvc310002.service(this.proxy.document(xml));
	}
	
	/**
	 * 7. MVC084000 - 下行存活驗證。
	 * @param xml 上行電文
	 * @return 下行電文
	 */
	@PostMapping("/084000")
	public String mvc084000(@RequestBody String xml) {
		return mvc084000.service(this.proxy.document(xml));
	}
}
