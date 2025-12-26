package com.fubon.mvp.dao;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.fubon.mvp.data.Channel;

import page2020.util.EmptyUtil;

/**
 * 富邦MVP-交易通道儲存器
 * @author MILO-GAO(高振銘)@2020
 * @category 儲存類
 */
@Repository
@Transactional
public class ChannelDao {

	private static Logger log = LoggerFactory.getLogger(ChannelDao.class);

	@Autowired
	private ChannelRepo repo;
	
	/**
	 * 初始程序。
	 */
	// @PostConstruct
	public void initial() {
		
		if (this.count() == 0) {
			// 初始資料。
			List<Channel> list = new ArrayList<Channel>();
			list.add(new Channel("0Z", "39", "集中登打"));
			list.add(new Channel("0Z", "40", "報表平台"));
			list.add(new Channel("00", "01", "新端末"));
			list.add(new Channel("0U", "01", "信託資產管理"));
			list.add(new Channel("0U", "02", "不動產信託"));
			list.add(new Channel("0Z", "02", "買方額度管理"));
			list.add(new Channel("0U", "03", "員工福利信託"));
			list.add(new Channel("0Z", "03", "印鑑系統"));
			list.add(new Channel("0Z", "04", "待補文件管理"));
			list.add(new Channel("0Z", "05", "總務管理作業"));
			list.add(new Channel("0Z", "06", "信用卡外撥"));
			list.add(new Channel("0Z", "07", "補褶機系統"));
			list.add(new Channel("0Z", "08", "利害關係人"));
			list.add(new Channel("0Z", "09", "BD分行議價"));
			list.add(new Channel("0Z", "10", "分行議價系統"));
			list.add(new Channel("0Z", "11", "繳款系統"));
			list.add(new Channel("0Z", "12", "fubon.com"));
			list.add(new Channel("0H", "01", "財管信託系統"));
			list.add(new Channel("0Z", "14", "行外-個金"));
			list.add(new Channel("0Z", "16", "通路前置處理"));
			list.add(new Channel("0V", "02", "跨行ATM"));
			list.add(new Channel("0M", "01", "FEP  匯款"));
			list.add(new Channel("0Z", "32", "整批代收代發"));
			list.add(new Channel("0F", "01", "關貿EDI"));
			list.add(new Channel("0F", "02", "財金EDI"));
			list.add(new Channel("0L", "01", "富邦商務網 FBO"));
			list.add(new Channel("0Y", "01", "富邦 E 收網 FCO"));
			list.add(new Channel("0Z", "17", "銀行客服 CTI"));
			list.add(new Channel("0I", "01", "銀行語音系統"));
			list.add(new Channel("0N", "01", "網路銀行 網銀"));
			list.add(new Channel("0N", "02", "網路銀行 行銀"));
			list.add(new Channel("0Z", "18", "新個金徵審系"));
			list.add(new Channel("0Z", "19", "就學貸款系統"));
			list.add(new Channel("0Z", "20", "企金新興徵審"));
			list.add(new Channel("0Z", "21", "企金徵審"));
			list.add(new Channel("0J", "01", "企金應收帳款"));
			list.add(new Channel("0Z", "22", "個金催收管理"));
			list.add(new Channel("0J", "02", "企金客票融資"));
			list.add(new Channel("0Z", "23", "消金客服作業"));
			list.add(new Channel("0U", "04", "特金信託基金"));
			list.add(new Channel("08", "01", "發卡系統VISA Deb"));
			list.add(new Channel("0U", "05", "特金信託債券"));
			list.add(new Channel("0U", "06", "特金信託股票"));
			list.add(new Channel("0Z", "31", "個金分行業務"));
			list.add(new Channel("0Z", "25", "組合式商品系"));
			list.add(new Channel("0Z", "26", "SI 組合式存款"));
			list.add(new Channel("0Z", "27", "保管箱系統"));
			list.add(new Channel("0L", "02", "企金傳真銀行"));
			list.add(new Channel("0Z", "33", "國庫收支建置"));
			list.add(new Channel("0Z", "28", "外匯額度查扣"));
			list.add(new Channel("0Z", "29", "跨區Pooling平台"));
			list.add(new Channel("0Z", "30", "集作法扣系統"));
			list.add(new Channel("0Z", "34", "7-11 I-BON平台"));
			list.add(new Channel("0Z", "01", "保管銀行系統"));
			list.add(new Channel("0V", "01", "自行ATM"));
			list.add(new Channel("0T", "", "Trickle Feed"));
			list.add(new Channel("0B", "", "Pure Batch"));
			list.add(new Channel("0H", "02", "證券e01"));
			list.add(new Channel("0H", "03", "證券複委託FSTS"));
			list.add(new Channel("0H", "04", "期貨後台 FSMS"));
			list.add(new Channel("0H", "05", "證券後台 SMS"));
			list.add(new Channel("0Z", "36", "微信跨境支付"));
			list.add(new Channel("0Z", "35", "資金通報"));
			list.add(new Channel("0Z", "37", "Alliance系統"));
			list.add(new Channel("0Z", "38", "協同整合CIP"));
			list.add(new Channel("00", "00", "BaNCSLink"));
			list.add(new Channel("0Z", "41", "Murex 系統"));
			list.add(new Channel("0Z", "42", "集保系統"));
			list.add(new Channel("0Z", "43", "Mail hunter"));
			list.add(new Channel("0Z", "44", "SMS"));
			list.add(new Channel("0Z", "45", "集中支付"));
			list.add(new Channel("0D", "", "DIRECT CREDIT/DEBIT"));
			list.add(new Channel("0Z", "46", "掃描系統"));
			list.add(new Channel("0O", "01", "FBO FXML"));
			list.add(new Channel("0Z", "47", "ESB FOR NBTS"));
			list.add(new Channel("0N", "10", "e家付"));
			list.add(new Channel("0N", "11", "payTaipei"));
			list.add(new Channel("0Z", "48", "中央額度(CRP,')"));
			list.add(new Channel("0L", "03", "富壽快付"));
			list.add(new Channel("0Z", "49", "富邦產險"));
			list.add(new Channel("0Z", "50", "法金行銷"));
			list.add(new Channel("0Z", "52", "EACH"));
			list.add(new Channel("0U", "10", "黃金存摺系統"));
			list.add(new Channel("0N", "03", "M+"));
			list.add(new Channel("0N", "04", "街口支付"));
			list.add(new Channel("0N", "05", "一卡通"));
			list.add(new Channel("0N", "06", "帳聯網"));
			list.add(new Channel("0N", "07", "MOMO"));
			list.add(new Channel("0N", "08", "LuckyPay"));
			list.add(new Channel("0N", "09", "FeBO"));
			list.add(new Channel("08", "03", "發卡授權系統"));
			list.add(new Channel("0Z", "53", "富邦期貨入金"));
			list.add(new Channel("08", "02", "清算系統"));
			list.add(new Channel("0Z", "51", "TIP"));
			list.add(new Channel("0N", "12", "悠遊付"));
			list.add(new Channel("0Z", "54", "BKSETTLER"));
			list.add(new Channel("1N", "02", "奈米投"));
			list.add(new Channel("0Z", "89", "EMAIL驗證平台"));
			// 儲存資料。
			this.save(list);
		}
	}
	
	//------------------------------------------------------------------------------
	// 查詢類
	//------------------------------------------------------------------------------
	
	/**
	 * 1. 資料筆數。
	 * @return 數字
	 */
	public int count() {
		return (int) this.repo.count();
	}

	//------------------------------------------------------------------------------
	// 讀取類
	//------------------------------------------------------------------------------

	/**
	 * 1. 依據通路與次通路編號讀取。
	 * @param channel 通路
	 * @param subChannel 次通路
	 * @return 實體
	 */
	public Channel channel(String channel, String subChannel) {
		return this.repo.findOneByChannelAndSubChannel(channel, subChannel);
	}
	
	/**
	 * 2. 讀取響應字串。
	 * @param channel 通路
	 * @param subChannel 次通路
	 * @return 字串
	 */
	public String response(String channel, String subChannel) {
		
		Channel entity = this.channel(channel, subChannel);
		if (entity != null) {
			return EmptyUtil.orEmpty(entity.getResponse());
		}
		return "";
	}
	
	//------------------------------------------------------------------------------
	// 操作類
	//------------------------------------------------------------------------------

	/**
	 * 1. 儲存清單。
	 * @param channels 清單
	 * @return 數字
	 */
	private int save(List<Channel> channels) {
		
		try {
			for (Channel channel : channels) {
				this.repo.save(channel);
			}
		} catch (Exception ex) {
			log.error(ex.toString());
			return 0;
		}
		return channels.size();
	}
}
