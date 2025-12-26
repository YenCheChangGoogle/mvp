package com.fubon.mvp.data;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import page2020.anno.Col2020;
import page2020.anno.Ctrl2020;
import page2020.core.Bean;
import page2020.view.html5.H5input.Type;

/**
 * 富邦MVP-資料包-主鍵類
 * @author MILO-GAO(高振銘)@2020
 * @category 實體類
 * @param <T> 真實類
 */
@MappedSuperclass
public class SidClass extends Bean<SidClass> implements Serializable {

	// 版本序列號
	private static final long serialVersionUID = 202111L;
	
	// 識別值
	@Ctrl2020(type=Type.hidden)
	@Col2020(hidden=true)
	@Id 
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="SID")
	private Long id;
		
	//------------------------------------------------------------------------------
	// 讀寫子
	//------------------------------------------------------------------------------

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}
}
