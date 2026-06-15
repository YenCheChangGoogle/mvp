package com.fubon.mvp.ctrl;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.dom4j.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fubon.mvp.dao.EmailDao;
import com.fubon.mvp.data.EmailMaster;
import com.fubon.mvp.serv.EmailStatusServ;

import page2020.client.WebProxy;
import page2020.util.TimeUtil;
import reactor.core.publisher.Mono;

/**
 * 富邦MVP-測試連綫控制器
 * @author MILO-GAO(高振銘)@2020
 * @category 組態類
 */
@RestController
@RequestMapping("/api/hello")
public class MvpHelloCtrl {

	private static String txMvc110001 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + 
		"<Tx><TxHead><HMSGID>P</HMSGID><HERRID>0000</HERRID><HSYDAY>1101111</HSYDAY><HSYTIME>122306</HSYTIME><HWSID>ROE_Client</HWSID><HSTANO>7898411</HSTANO><HDTLEN>1059</HDTLEN><HREQQ1/><HREPQ1/><HDRVQ1/><HPVDQ1/><HPVDQ2/><HSYCVD>1101112</HSYCVD><HTLID>2004115</HTLID><HTXTID>MVC110001</HTXTID><HFMTID>0001</HFMTID><HRETRN/><HSLGNF/><HSPSCK>Y8</HSPSCK><HRTNCD/><HSBTRF/><HFILL/></TxHead><TxBody><UUID>{uuid}</UUID><BRANCH>00200</BRANCH><TELLER_NO>89014100</TELLER_NO><TX_CODE>110001</TX_CODE><CHNL>0Z</CHNL><SUB_CHNL>19</SUB_CHNL><CUST_ID>{cust_id}</CUST_ID><ID_TYPE>01</ID_TYPE><CUST_NAME>東方不敗</CUST_NAME><ENG_NAME>will</ENG_NAME><PREV_EMAIL_ADDR>123@gmail.com</PREV_EMAIL_ADDR><AFTER_EMAIL_ADDR>456@yahoo.com.tw</AFTER_EMAIL_ADDR><FROM_DATE/><TO_DATE/><REASON>01</REASON><REMARK/><ON_OFF_LINE>Y</ON_OFF_LINE><QUERY_UUID/><NEXT_KEY/></TxBody></Tx>";
	private static String txMvc310001 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + 
		"<Tx><TxHead><HMSGID>P</HMSGID><HERRID>0000</HERRID><HSYDAY>1101111</HSYDAY><HSYTIME>122306</HSYTIME><HWSID>ROE_Client</HWSID><HSTANO>7898411</HSTANO><HDTLEN>1059</HDTLEN><HREQQ1/><HREPQ1/><HDRVQ1/><HPVDQ1/><HPVDQ2/><HSYCVD>1101112</HSYCVD><HTLID>2004115</HTLID><HTXTID>MVC110001</HTXTID><HFMTID>0001</HFMTID><HRETRN/><HSLGNF/><HSPSCK>Y8</HSPSCK><HRTNCD/><HSBTRF/><HFILL/></TxHead><TxBody><UUID></UUID><BRANCH>00200</BRANCH><TELLER_NO>89014100</TELLER_NO><TX_CODE>110001</TX_CODE><CHNL>0Z</CHNL><SUB_CHNL>19</SUB_CHNL><CUST_ID>{cust_id}</CUST_ID><ID_TYPE>01</ID_TYPE><CUST_NAME>東方不敗</CUST_NAME><ENG_NAME>will</ENG_NAME><PREV_EMAIL_ADDR>123@gmail.com</PREV_EMAIL_ADDR><AFTER_EMAIL_ADDR>456@yahoo.com.tw</AFTER_EMAIL_ADDR><FROM_DATE/><TO_DATE/><REASON>01</REASON><REMARK/><ON_OFF_LINE>Y</ON_OFF_LINE><QUERY_UUID>{uuid}</QUERY_UUID><NEXT_KEY/></TxBody></Tx>";
	private static String txMvc310002 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + 
		"<Tx><TxHead><HMSGID>P</HMSGID><HERRID>0000</HERRID><HSYDAY>1101111</HSYDAY><HSYTIME>122306</HSYTIME><HWSID>ROE_Client</HWSID><HSTANO>7898411</HSTANO><HDTLEN>1059</HDTLEN><HREQQ1/><HREPQ1/><HDRVQ1/><HPVDQ1/><HPVDQ2/><HSYCVD>1101112</HSYCVD><HTLID>2004115</HTLID><HTXTID>MVC110001</HTXTID><HFMTID>0001</HFMTID><HRETRN/><HSLGNF/><HSPSCK>Y8</HSPSCK><HRTNCD/><HSBTRF/><HFILL/></TxHead><TxBody><UUID></UUID><BRANCH>00200</BRANCH><TELLER_NO>89014100</TELLER_NO><TX_CODE>110001</TX_CODE><CHNL>0Z</CHNL><SUB_CHNL>19</SUB_CHNL><CUST_ID>{cust_id}</CUST_ID><ID_TYPE>01</ID_TYPE><CUST_NAME>東方不敗</CUST_NAME><ENG_NAME>will</ENG_NAME><PREV_EMAIL_ADDR>123@gmail.com</PREV_EMAIL_ADDR><AFTER_EMAIL_ADDR>456@yahoo.com.tw</AFTER_EMAIL_ADDR><FROM_DATE>20211101</FROM_DATE><TO_DATE>20220101</TO_DATE><REASON>01</REASON><REMARK/><ON_OFF_LINE>2</ON_OFF_LINE><QUERY_UUID>{uuid}</QUERY_UUID><NEXT_KEY>{next_key}</NEXT_KEY></TxBody></Tx>";

	private int count = 0;
	private String uuid = null;
	private String custId = null;
	private Map<String, Date> times = new HashMap<String, Date>();
	
	@Autowired
	private EmailStatusServ service;
	@Autowired
	private WebProxy proxy;
	@Autowired
	private EmailDao dao;
	
	/**
	 * 初始程序。
	 */
	@PostConstruct
	public void initial() {
		
		this.tranx("110001", true, true);
		this.times.put("110001", new Date());
		this.times.put("110002", new Date());
		this.times.put("110003", new Date());
		this.times.put("110006", new Date());
		this.times.put("110007", new Date());
		this.times.put("310001", new Date());
		this.times.put("310002", new Date());
		this.times.put("084000", new Date());
		this.times.put("084023", new Date());
		this.times.put("mvp110007", new Date());
		this.times.put("mvp110008", new Date());
		this.times.put("310003", new Date());
	}
			
	/**
	 * 1. 測試 MVC110001 連綫。
	 * @return 電文或日期。
	 */
	@GetMapping("/110001")
	public String hello110001() {

		if (this.after3seconds("110001")) {
			String xml = this.proxy.api("app", "/api/110001")
				.body(Mono.just(this.tranx("110001", true, true)), String.class).retrieve()
				.bodyToMono(String.class).block();
			return xml;			
		}
		return new Date().toString();
	}

	/**
	 * (SIT) 測試 MVC110001 連綫(創建N筆測試資料)。
	 * @param id 身份証號
	 * @param online 在綫狀態
	 * @param amount 資料行數
	 * @return 電文或日期。
	 */
	@GetMapping("/110001c/{id}/{online}/{amount}")
	public String hello110001c(@PathVariable("id") String id, @PathVariable("online") String online, 
			@PathVariable("amount") String amount) {

		if (this.after3seconds("110001")) {
			// 1. 創建N筆資料。
			this.custId = id;
			int count = Integer.valueOf(amount).intValue();
			for (int i=0; i < count; i++) {
				String xml = this.tranx("110001", true, false);
				Document doc = this.proxy.document(xml);
				EmailMaster master = new EmailMaster(doc, "110001");	// 交易代號：110001。
				master.setOnline(online);
				master.setStatus("00");
				master.setTxStatus("01");
				master.setChangeTime(this.before(count - i + 1));
				this.dao.save(master);			
			}
			// 2. 執行"110001"交易。
			String xml = this.proxy.api("app", "/api/110001")
				.body(Mono.just(this.tranx("110001", true, false)), String.class).retrieve()
				.bodyToMono(String.class).block();
			return xml;				
		}
		return new Date().toString();
	}
	
	/**
	 * 2. 測試 MVC110002 連綫。
	 * @return 電文或日期。
	 */
	@GetMapping("/110002")
	public String hello110002() {

		if (this.after3seconds("110002")) {
			String xml = this.proxy.api("app", "/api/110002")
				.body(Mono.just(this.tranx("110002", true, false)), String.class).retrieve()
				.bodyToMono(String.class).block();
			return xml;			
		}
		return new Date().toString();
	}
	
	/**
	 * 2. 測試 MVC110003 連綫。
	 * @return 電文或日期。
	 */
	@GetMapping("/110003")
	public String hello110003() {

		if (this.after3seconds("110003")) {
			String xml = this.proxy.api("app", "/api/110003")
				.body(Mono.just(this.tranx("110003", true, false)), String.class).retrieve()
				.bodyToMono(String.class).block();
			return xml;			
		}
		return new Date().toString();
	}

	/**
	 * 3. 測試 MVC110006 連綫。
	 * 説明：上行電文與110001共用。
	 * @return 電文或日期。
	 */
	@GetMapping("/110006")
	public String hello110006() {

		if (this.after3seconds("110006")) {
			String xml = this.proxy.api("app", "/api/110006")
				.body(Mono.just(this.tranx("110006")), String.class).retrieve()
				.bodyToMono(String.class).block();
			return xml;
		}
		return new Date().toString();
	}

	/**
	 * 4. 測試 MVC110007 連綫。
	 * 説明：上行電文與110001共用。
	 * @return 電文或日期。
	 */
	@GetMapping("/110007")
	public String hello110007() {
		
		if (this.after3seconds("110007")) {
			String xml = this.proxy.api("app", "/api/110007")
				.body(Mono.just(this.tranx("110007")), String.class).retrieve()
				.bodyToMono(String.class).block();
			return xml;
		}
		return new Date().toString();
	}

	/**
	 * 5. 測試 MVC310001 連綫。
	 * @return 電文或日期。
	 */
	@GetMapping("/310001")
	public String hello310001() {
		
		if (this.after3seconds("310001")) {
			String xml = this.proxy.api("app", "/api/310001")
				.body(Mono.just(this.tranx("310001")), String.class).retrieve()
				.bodyToMono(String.class).block();
			return xml;
		}
		return new Date().toString();
	}
	
	/**
	 * 6. 測試 MVC310002 連綫。
	 * @return 電文或日期。
	 */
	@GetMapping("/310002")
	public String hello310002() {
		
		if (this.after3seconds("310002")) {
			String xml = this.proxy.api("app", "/api/310002")
				.body(Mono.just(this.tranx("310002")), String.class).retrieve()
				.bodyToMono(String.class).block();
			return xml;
		}
		return new Date().toString();
	}
	
	/**
	 * 7. (SIT) 測試 MVC310002 連綫(NEXT_KEY)。
	 * @return 電文或日期。
	 */
	@GetMapping("/nextkey310002/{id}/{nextkey}")
	public String nextkey310002(@PathVariable("id") String id, @PathVariable("nextkey") String nextKey) {
	
		if (this.after3seconds("310002")) {
			this.custId = id;
			String inbound = this.tranx("310002").replace("{next_key}", nextKey);
			String xml = this.proxy.api("app", "/api/310002")
				.body(Mono.just(inbound), String.class).retrieve()
				.bodyToMono(String.class).block();
			return xml;
		}
		return new Date().toString();
	}

	/**
	 * 8. 測試 MVC084000 連綫。
	 * @return 電文或日期。
	 */
	@GetMapping("/084000")
	public String hello084000() {

		if (this.after3seconds("084000")) {
			String xml = this.proxy.api("app", "/api/084000")
				.body(Mono.just(this.tranx("084000")), String.class).retrieve()
				.bodyToMono(String.class).block();
			return xml;
		}
		return new Date().toString();
	}

	/**
	 * 9. 測試 MVP084023 連綫。
	 * @return 電文或日期。
	 */
	//http://localhost:8080/mvp/api/hello/084023
	@GetMapping("/084023")
	public String hello084023() {

		if (this.after3seconds("084023")) {
			this.service.addToPool("084023", this.dao.uuid(this.uuid));
		}
		return new Date().toString();
	}
	
	/**
	 * 10. 測試 MVC310003 連綫。
	 * @return 電文或日期。
	 */
	//http://localhost:8080/mvp/api/310003
	@GetMapping("/310003")
	public String hello310003() {
		
		if (this.after3seconds("310003")) {
			String xml = this.proxy.api("app", "/api/310003")
				.body(Mono.just(this.tranx("310003")), String.class).retrieve()
				.bodyToMono(String.class).block();
			return xml;
		}
		return new Date().toString();
	}

	//------------------------------------------------------------------------------
	// 私有級
	//------------------------------------------------------------------------------
	
	/**
	 * 1. 3秒判斷。
	 * @param txNo 交易代號。
	 * @return 布林值
	 */
	private boolean after3seconds(String txNo) {
		
		if ((new Date().getTime() - this.times.get(txNo).getTime()) >= 3000) {
			this.times.put(txNo, new Date());
			return true;
		}
		return false;
	}

	/**
	 * 2. 讀取上行電文。
	 * @param txNo 電文編號
	 * @return 字串
	 */
	private String tranx(String txNo) {
		return this.tranx(txNo, false, false);
	}

	/**
	 * 3. 讀取上行電文。
	 * @param txNo 電文編號
	 * @param bUuid 重設UUID
	 * @param bCustId 重設身份証號
	 * @return 字串
	 */
	private String tranx(String txNo, boolean bUuid, boolean bCustId) {
		
		if (bUuid) {
			this.uuid = this.service.uuid(txNo);
		}
		if (bCustId) {
			this.custId = String.format("%s%s%03d", "A", TimeUtil.timeE(new Date()), (++ this.count));
		}
		switch (txNo) {
		case "110001":
		case "110002":
		case "110003":
		case "110006":
		case "110007":
		case "084000":
		case "084023":
			return this.replace(txMvc110001);
		case "310001":
			return this.replace(txMvc310001);
		case "310002":
			return this.replace(txMvc310002);
		}
		return null;
	}	

	/**
	 * 4. 替換關鍵值。
	 * @param source 原始來源
	 * @return 字串
	 */
	private String replace(String source) {
		return source
			.replace("{uuid}", this.uuid)
			.replace("{cust_id}", this.custId);
	}
	
	/**
	 * 5. 之前幾秒。
	 * @param seconds 秒數
	 * @return 字串
	 */
	private String before(int seconds) {
		
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.SECOND, (-1) * seconds);
		return String.format("%02d%02d%02d", calendar.get(Calendar.HOUR_OF_DAY), 
			calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND));
	}
	
	//6. mvp110007 http://localhost:8080/mvp/api/hello/mvp110007
	@GetMapping(value = "/mvp110007", produces = MediaType.APPLICATION_XML_VALUE)
	public String mvp110007() {
	    if (this.after3seconds("mvp110007")) {
	        String xml = this.proxy.api("app", "/api/mvp110007")
	            .body(Mono.just(this.tranx("110007", true, true)), String.class) //加上 body，保持一致
	            .retrieve()
	            .bodyToMono(String.class)
	            .block();

	        //如果下游 API 有回傳 XML，就直接傳回；否則給預設 XML
	        return xml != null ? xml : "<response><status>success</status><message>OK</message></response>";
	    }

	    //如果 after3seconds() 不成立，回傳 skip XML
	    return "<response><status>skip</status><message>" + new Date().toString()  + "</message></response>";
	}
	
	//7. mvp110008 http://localhost:8080/mvp/api/hello/mvp110008
	@GetMapping(value = "/mvp110008", produces = MediaType.APPLICATION_XML_VALUE)
	public String mvp110008() {
	    if (this.after3seconds("mvp110008")) {
	        String xml = this.proxy.api("app", "/api/mvp110008")
	            .body(Mono.just(this.tranx("110008", true, true)), String.class) //加上 body，保持一致
	            .retrieve()
	            .bodyToMono(String.class)
	            .block();

	        //如果下游 API 有回傳 XML，就直接傳回；否則給預設 XML
	        return xml != null ? xml : "<response><status>success</status><message>OK</message></response>";
	    }

	    //如果 after3seconds() 不成立，回傳 skip XML
	    return "<response><status>skip</status><message>" + new Date().toString() + "</message></response>";
	}
	
}
