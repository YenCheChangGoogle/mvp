//
// 此檔案是由 JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.3.0 所產生 
// 請參閱 <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// 一旦重新編譯來源綱要, 對此檔案所做的任何修改都將會遺失. 
// 產生時間: 2021.12.24 於 10:55:16 AM CST 
//


package com.fubon.mvp.email;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>anonymous complex type 的 Java 類別.
 * 
 * <p>下列綱要片段會指定此類別中包含的預期內容.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="SendToOneUIDResult" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "sendToOneUIDResult"
})
@XmlRootElement(name = "SendToOneUIDResponse")
public class SendToOneUIDResponse {

    @XmlElement(name = "SendToOneUIDResult")
    protected int sendToOneUIDResult;
    
    @Override
	public String toString() {
		return "SendToOneUIDResponse [sendToOneUIDResult=" + sendToOneUIDResult + "]";
	}

	/**
     * 取得 sendToOneUIDResult 特性的值.
     * 
     */
    public int getSendToOneUIDResult() {
        return sendToOneUIDResult;
    }

    /**
     * 設定 sendToOneUIDResult 特性的值.
     * 
     */
    public void setSendToOneUIDResult(int value) {
        this.sendToOneUIDResult = value;
    }

}
