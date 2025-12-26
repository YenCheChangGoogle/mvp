package com.fubon.mvp;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import page2020.Ctrl;
import page2020.core.App;
import page2020.flow.LayoutFlow;
import page2020.flow.PagePersonal;
import page2020.view.css3.C3color.Grey;
import page2020.view.css3.C3color.White;

/**
 * 富邦MVP-入口控制器
 * @author MILO-GAO(高振銘)@2020
 * @category 控制器
 */
// @Controller
// @RequestMapping("/mvp")
public class MvpCtrl extends Ctrl {

	// 網頁模板
	@Autowired
	private PagePersonal personal;
	
	// 初始程序
	@PostConstruct
	@Override
	public void initial() {
		// bar, background, color, active
		
		// 1. 註冊系統碼網頁。
		App.sysPage("mvp")
			.personal(this.personal)
			.icon("mvp-32x32.jpg", "mvp-192x192.jpg", "mvp-180x180.jpg")
			.logo("mvp-logo-sm.jpg", "mvp-logo-md.jpg", "mvp-logo-lg.jfif")
			.color(Grey.Silver , Grey.Gainsboro , White.White, Grey.DimGray)
			.header(Grey.DimGray)
			.body(Grey.Black)
			.font("Helvetica", "Arial")
			.zoom(0.7)
			.langs("en", "zh_TW");
		
		// 2. 控制器初始化。
		super.initial("/mvp",
			new LayoutFlow().view(this.personal.home("mvp"))
		);		
	}
	
	// 入口網頁
	@GetMapping
	public String page(Model model, HttpSession session) {
		return super.view(model, session);
	}
}
