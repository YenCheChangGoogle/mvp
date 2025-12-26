package com.fubon.mvp.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fubon.mvp.data.EmailImage;

/**
 * 富邦MVP-存活驗證檔接口
 * @author MILO-GAO(高振銘)@2020
 * @category 接口類
 */
public interface EmailImageRepo extends JpaRepository<EmailImage, Long> {

}
