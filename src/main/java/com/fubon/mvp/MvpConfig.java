package com.fubon.mvp;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import secure2020.data.SecureUser;

/**
 * 富邦MVP-應用組態類
 * @author MILO-GAO(高振銘)@2020
 * @category 組態類
 */
@Configuration
@ComponentScan({ "page2020" })
@EnableScheduling
@EnableAsync
public class MvpConfig {

	// 1. 安全用戶
	@Bean
	@SessionScope
	public SecureUser user() {
		return new SecureUser();
	}

	// 2. 驗證密碼元件
	@Bean
	public BCryptPasswordEncoder encoder() {
		return new BCryptPasswordEncoder();
	}

	// 3. 語系資源
	@Bean
	public MessageSource messageSource() {
	    
		ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
	    messageSource.setBasename("classpath:messages");
	    messageSource.setDefaultEncoding("UTF-8");
	    return messageSource;
	}

	// 4. 本地語系驗證器
	@Bean
	public LocalValidatorFactoryBean getValidator() {
		
	    LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
	    bean.setValidationMessageSource(messageSource());
	    return bean;
	}
	
	// 5. 語言環境解析器
	@Bean
	public LocaleResolver localeResolver() {
		
		SessionLocaleResolver resolver = new SessionLocaleResolver();
		resolver.setDefaultLocale(Locale.TAIWAN);
		return resolver;
	}
}
