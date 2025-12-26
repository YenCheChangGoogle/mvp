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
 *         &lt;element name="project_category_code" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="toname" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="toemail" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="fromname" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="fromemail" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="subject" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="content" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="Attachment1" type="{http://www.w3.org/2001/XMLSchema}base64Binary" minOccurs="0"/&gt;
 *         &lt;element name="attachment_filename1" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="Attachment2" type="{http://www.w3.org/2001/XMLSchema}base64Binary" minOccurs="0"/&gt;
 *         &lt;element name="attachment_filename2" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="Attachment3" type="{http://www.w3.org/2001/XMLSchema}base64Binary" minOccurs="0"/&gt;
 *         &lt;element name="attachment_filename3" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="Attachment4" type="{http://www.w3.org/2001/XMLSchema}base64Binary" minOccurs="0"/&gt;
 *         &lt;element name="attachment_filename4" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="Attachment5" type="{http://www.w3.org/2001/XMLSchema}base64Binary" minOccurs="0"/&gt;
 *         &lt;element name="attachment_filename5" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="UID" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
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
    "projectCategoryCode",
    "toname",
    "toemail",
    "fromname",
    "fromemail",
    "subject",
    "content",
    "attachment1",
    "attachmentFilename1",
    "attachment2",
    "attachmentFilename2",
    "attachment3",
    "attachmentFilename3",
    "attachment4",
    "attachmentFilename4",
    "attachment5",
    "attachmentFilename5",
    "uid"
})
@XmlRootElement(name = "SendToOneUID")
public class SendToOneUID {

    @XmlElement(name = "project_category_code")
    protected String projectCategoryCode;
    protected String toname;
    protected String toemail;
    protected String fromname;
    protected String fromemail;
    protected String subject;
    protected String content;
    @XmlElement(name = "Attachment1")
    protected byte[] attachment1;
    @XmlElement(name = "attachment_filename1")
    protected String attachmentFilename1;
    @XmlElement(name = "Attachment2")
    protected byte[] attachment2;
    @XmlElement(name = "attachment_filename2")
    protected String attachmentFilename2;
    @XmlElement(name = "Attachment3")
    protected byte[] attachment3;
    @XmlElement(name = "attachment_filename3")
    protected String attachmentFilename3;
    @XmlElement(name = "Attachment4")
    protected byte[] attachment4;
    @XmlElement(name = "attachment_filename4")
    protected String attachmentFilename4;
    @XmlElement(name = "Attachment5")
    protected byte[] attachment5;
    @XmlElement(name = "attachment_filename5")
    protected String attachmentFilename5;
    @XmlElement(name = "UID")
    protected String uid;
    
    @Override
	public String toString() {
		return "SendToOneUID [projectCategoryCode=" + projectCategoryCode + ", toname=" + toname + ", toemail="
				+ toemail + ", fromname=" + fromname + ", fromemail=" + fromemail + ", subject=" + subject + ", uid="
				+ uid + "]";
	}

	/**
     * 取得 projectCategoryCode 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProjectCategoryCode() {
        return projectCategoryCode;
    }

    /**
     * 設定 projectCategoryCode 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProjectCategoryCode(String value) {
        this.projectCategoryCode = value;
    }

    /**
     * 取得 toname 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getToname() {
        return toname;
    }

    /**
     * 設定 toname 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setToname(String value) {
        this.toname = value;
    }

    /**
     * 取得 toemail 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getToemail() {
        return toemail;
    }

    /**
     * 設定 toemail 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setToemail(String value) {
        this.toemail = value;
    }

    /**
     * 取得 fromname 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFromname() {
        return fromname;
    }

    /**
     * 設定 fromname 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFromname(String value) {
        this.fromname = value;
    }

    /**
     * 取得 fromemail 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFromemail() {
        return fromemail;
    }

    /**
     * 設定 fromemail 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFromemail(String value) {
        this.fromemail = value;
    }

    /**
     * 取得 subject 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSubject() {
        return subject;
    }

    /**
     * 設定 subject 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSubject(String value) {
        this.subject = value;
    }

    /**
     * 取得 content 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getContent() {
        return content;
    }

    /**
     * 設定 content 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setContent(String value) {
        this.content = value;
    }

    /**
     * 取得 attachment1 特性的值.
     * 
     * @return
     *     possible object is
     *     byte[]
     */
    public byte[] getAttachment1() {
        return attachment1;
    }

    /**
     * 設定 attachment1 特性的值.
     * 
     * @param value
     *     allowed object is
     *     byte[]
     */
    public void setAttachment1(byte[] value) {
        this.attachment1 = value;
    }

    /**
     * 取得 attachmentFilename1 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAttachmentFilename1() {
        return attachmentFilename1;
    }

    /**
     * 設定 attachmentFilename1 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAttachmentFilename1(String value) {
        this.attachmentFilename1 = value;
    }

    /**
     * 取得 attachment2 特性的值.
     * 
     * @return
     *     possible object is
     *     byte[]
     */
    public byte[] getAttachment2() {
        return attachment2;
    }

    /**
     * 設定 attachment2 特性的值.
     * 
     * @param value
     *     allowed object is
     *     byte[]
     */
    public void setAttachment2(byte[] value) {
        this.attachment2 = value;
    }

    /**
     * 取得 attachmentFilename2 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAttachmentFilename2() {
        return attachmentFilename2;
    }

    /**
     * 設定 attachmentFilename2 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAttachmentFilename2(String value) {
        this.attachmentFilename2 = value;
    }

    /**
     * 取得 attachment3 特性的值.
     * 
     * @return
     *     possible object is
     *     byte[]
     */
    public byte[] getAttachment3() {
        return attachment3;
    }

    /**
     * 設定 attachment3 特性的值.
     * 
     * @param value
     *     allowed object is
     *     byte[]
     */
    public void setAttachment3(byte[] value) {
        this.attachment3 = value;
    }

    /**
     * 取得 attachmentFilename3 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAttachmentFilename3() {
        return attachmentFilename3;
    }

    /**
     * 設定 attachmentFilename3 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAttachmentFilename3(String value) {
        this.attachmentFilename3 = value;
    }

    /**
     * 取得 attachment4 特性的值.
     * 
     * @return
     *     possible object is
     *     byte[]
     */
    public byte[] getAttachment4() {
        return attachment4;
    }

    /**
     * 設定 attachment4 特性的值.
     * 
     * @param value
     *     allowed object is
     *     byte[]
     */
    public void setAttachment4(byte[] value) {
        this.attachment4 = value;
    }

    /**
     * 取得 attachmentFilename4 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAttachmentFilename4() {
        return attachmentFilename4;
    }

    /**
     * 設定 attachmentFilename4 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAttachmentFilename4(String value) {
        this.attachmentFilename4 = value;
    }

    /**
     * 取得 attachment5 特性的值.
     * 
     * @return
     *     possible object is
     *     byte[]
     */
    public byte[] getAttachment5() {
        return attachment5;
    }

    /**
     * 設定 attachment5 特性的值.
     * 
     * @param value
     *     allowed object is
     *     byte[]
     */
    public void setAttachment5(byte[] value) {
        this.attachment5 = value;
    }

    /**
     * 取得 attachmentFilename5 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAttachmentFilename5() {
        return attachmentFilename5;
    }

    /**
     * 設定 attachmentFilename5 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAttachmentFilename5(String value) {
        this.attachmentFilename5 = value;
    }

    /**
     * 取得 uid 特性的值.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUID() {
        return uid;
    }

    /**
     * 設定 uid 特性的值.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUID(String value) {
        this.uid = value;
    }

}
