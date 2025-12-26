package com.fubon.mvp;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariDataSource;

/**
 * 富邦MVP-數據連接組態
 * @author MILO-GAO(高振銘)@2020
 * @category 組態類
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
	basePackages={"com.fubon.mvp.dao"},
	entityManagerFactoryRef="mvpEntityManagerFactory",
	transactionManagerRef="mvpTransactionManager")
public class MvpConn {

	// 資料庫方言
	@Value("${mvp.dialect}")
	private String dialect;
	
	/**
	 * 1. 生成資料來源屬性的元件。
	 * @return 元件
	 */
	@Bean(name="mvpDataSourceProperties")
	@ConfigurationProperties("mvp.datasource")
	public DataSourceProperties dataSourceProperties() {
		return new DataSourceProperties();
	}
	
	/**
	 * 2. 生成資料來源的元件。
	 * @param properties 資料來源屬性的元件
	 * @return 元件
	 */
	@Bean(name="mvpDataSource")
	public DataSource dataSource(@Qualifier("mvpDataSourceProperties") DataSourceProperties properties) {
		try {
			return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
		} catch (Exception ex) {
			return new JndiDataSourceLookup().getDataSource("java/mvp");
		}
	}
	
	/**
	 * 3. 生成實體管理工廠的元件。
	 * @param builder 工廠產生器
	 * @param dataSource 資料來源的元件
	 * @return 元件
	 */
	@Bean(name="mvpEntityManagerFactory")
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(
			EntityManagerFactoryBuilder builder,
			@Qualifier("mvpDataSource") DataSource dataSource) {
		
		LocalContainerEntityManagerFactoryBean manager = builder.dataSource(dataSource)
			.packages("com.fubon.mvp.data")
			.persistenceUnit("mvpFactory").build();
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("hibernate.hbm2ddl.auto", "update");
		properties.put("hibernate.dialect", this.dialect);
		manager.setJpaPropertyMap(properties);
		return manager;
	}

	/**
	 * 4. 生成交易管理的元件。
	 * @param entityManager 實體管理工廠的元件
	 * @return 元件
	 */
	@Bean(name="mvpTransactionManager")
	public PlatformTransactionManager transactionManager(
			@Qualifier("mvpEntityManagerFactory") EntityManagerFactory entityManager) {
		return new JpaTransactionManager(entityManager);
	}
	
	/**
	 * 5. 生成資料庫模板的元件。
	 * @param mvpDataSource 資料來源的元件 
	 * @return 元件
	 */
	@Bean(name="mvpJdbc")
	public JdbcTemplate mvpJdbcTemplate(
			@Qualifier("mvpDataSource") DataSource mvpDataSource) {
		return new JdbcTemplate(mvpDataSource);
	}
}
