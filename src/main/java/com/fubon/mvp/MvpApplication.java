package com.fubon.mvp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * 富邦MVP-應用程式類
 * @author MILO-GAO(高振銘)@2020
 * @category 入口類
 */
@SpringBootApplication
public class MvpApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(MvpApplication.class, args);
	}
	
	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(MvpApplication.class);
	}
}
