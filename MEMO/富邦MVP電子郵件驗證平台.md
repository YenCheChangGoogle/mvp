
## 📊 專案總體分析 — 富邦MVP電子郵件驗證平台

共 **48 個 Java 檔案**，分為 **6 個套件**：

---

### 🔧 `com.fubon.mvp` — 根套件（5 檔）

| 檔案 | 說明 |
|------|------|
| `MvpApplication.java` | Spring Boot 主程式入口，啟用 Scheduling + JPA Repo，排除 DataJPAAutoConfiguration |
| `MvpConfig.java` | DataSource（SQL Server）+ EntityManagerFactory + TransactionManager Bean 配置 |
| `MvpConn.java` | ESB 連線設定：App（本地）、ESB（Fep102fep47）、MailHunter（10.152.18.33）等 URL |
| `MvpCtrl.java` | 健康檢查 + 存活驗證接口（GET `/mvp/mvc084000`，POST `/mvp/{txnId}`）|
| `MvpSecurity.java` | Spring Security 配置：所有 API 放行，CORS 允許來源 `https://fubon.com, https://sso.fubon.com` |

---

### 🎮 `com.fubon.mvp.ctrl` — 控制器（2 檔）

| 檔案 | 說明 |
|------|------|
| `MvpApiCtrl.java` | **核心 API 路由器**：POST `/mvp/{txnId}` + GET `/api/mvc{code}`，依交易編號分派到對應 Service，含 `setFlag`、`downloadAiResult`、`processAiResult` 等管理接口 |
| `MvpHelloCtrl.java` | **測試控制器**：包含 18+ 個模擬端點（mvp003001~mvp110008），可模擬各交易場景的上行電文，含速率限制（3秒冷卻）|

---

### 💾 `com.fubon.mvp.dao` — 資料存取層（12 檔）

#### Repository 接口（6 檔）
| 檔案 | 對應資料表 | 說明 |
|------|-----------|------|
| `ChannelRepo.java` | `CHNL` | 通路查詢 |
| `EmailDetailRepo.java` | `EMAILDTL` | UUID 查詢 |
| `EmailHostRepo.java` | `EMAILHOS` | IP+Host 查詢 |
| `EmailImageRepo.java` | `EMAILIMG` | 基礎 CRUD |
| `EmailMasterRepo.java` | `EMAILMAS` | **最複雜**—含身分證查詢、狀態篩選、逾期3日/6日查詢等 14+ 個方法 |
| `ErrorDescRepo.java` | `ERRDESC` | 錯誤碼查詢 |

#### DAO 實作（6 檔）
| 檔案 | 說明 |
|------|------|
| `ChannelDao.java` | 通路管理，初始時寫入 80+ 組通路資料 |
| `EmailDao.java` | **核心 DAO**：含逾期3日/6日查詢、按條件過濾、AI外撥名單查詢 |
| `EmailHostDao.java` | 主從節點判定（每3秒輪詢），決定哪台機器執行定時 JOB |
| `EmailImageDao.java` | 存活驗證影像記錄 |
| `ErrorDescDao.java` | 錯誤碼池管理（0000/9999/E001~E012/E100~E110）|
| `EmailMasterRepo.java` | 已在上面說明 |

---

### 🗂️ `com.fubon.mvp.data` — JPA 實體（6 檔）

| 實體 | 資料表 | 核心欄位 |
|------|--------|---------|
| `SidClass.java` | — | 抽象基類，自增主鍵 `SID` |
| `Channel.java` | `CHNL` | CHNL, SUB_CHNL, CHNL_NAME, RESPONSE |
| `EmailMaster.java` | `EMAILMAS` | **30+欄位**：UUID, ID, CUST_NAME, Email, STATUS(00/01/02/99), TX_STATUS(00~99), FLAG, TEL_NO, PHONE, NAME 等，Transient 欄位含 QUERY_UUID, BEGIN_DATE, END_DATE |
| `EmailDetail.java` | `EMAILDTL` | UUID, CHG_DATE/TIME, RESP_DATE/TIME, TX_STATUS, ERR_CODE, FLAG |
| `EmailImage.java` | `EMAILIMG` | CHNL, SUB_CHNL, UUID, TRAN_CODE, ERR_CODE, IP |
| `EmailHost.java` | `EMAILHOS` | IP, HOST_NAME, MAIN(0/1) |
| `ErrorDesc.java` | `ERRDESC` | ERR_CODE, ERR_DESC |

---

### 📧 `com.fubon.mvp.email` — SOAP JAXB 類別（3 檔）

| 檔案 | 說明 |
|------|------|
| `ObjectFactory.java` | JAXB 自動生成 |
| `SendToOneUID.java` | Mail Hunter SOAP 請求（19個欄位：專案代碼、收寄件人、主旨、內文、5組附件）|
| `SendToOneUIDResponse.java` | Mail Hunter SOAP 回應（int 結果碼）|

---

### ⚙️ `com.fubon.mvp.serv` — 服務層（16 檔，**業務核心**）

#### 即時交易服務（8 檔）

| Service | 交易代號 | 功能 |
|---------|---------|------|
| `Mvc084000Serv` | 084000 | 下行存活驗證 |
| `Mvc110001Serv` | 110001 | **登錄申請**—收到變更Email申請，寫入DB + 定時寄信 |
| `Mvc110002Serv` | 110002 | **取消申請** |
| `Mvc110003Serv` | 110003 | **人工啟用**（Pamela-Lin @2024）|
| `Mvc110006Serv` | 110006 | **收件狀態回傳**（txStatus 01→13）|
| `Mvc110007Serv` | 110007 | **客戶確認信件**（txStatus →15）|
| `Mvc310001Serv` | 310001 | **登錄查詢**（單筆UUID查詢）|
| `Mvc310002Serv` | 310002 | **前台查詢-多筆**（分頁，每頁30筆）|

#### 擴增查詢服務（3 檔）

| Service | 交易代號 | 功能 |
|---------|---------|------|
| `Mvc310003Serv` | 310003 | **查主檔**（完整欄位回傳）|
| `Mvc310004Serv` | 310004 | **EMAILDTL明細查詢** |
| `Mvc310005Serv` | 310005 | **AI外撥情形報表**（含FLAG欄位，分頁）|

#### 定時 JOB 服務（6 檔）

| Service | 交易代號 | 排程 | 功能 |
|---------|---------|------|------|
| `Mvp110005Serv` | 110005 | fixedDelay=3s | **JOB2-寄發驗證信**（呼叫 Mail Hunter SOAP）|
| `Mvp067000Serv` | 067000 | fixedDelay=3s | **JOB4-通知ESB更新核心**（txStatus=15→發核心）|
| `Mvp084023Serv` | 084023 | fixedDelay=1s | **異常代碼查詢**（x105→查核心是否已生效→0188→重試）|
| `Mvp310051Serv` | 310051 | fixedDelay=3s | **JOB5-狀態回送前台**（txStatus=31）|
| `Mvp084000Serv` | 084000 | fixedRate=60s | **上行存活驗證**（開關可控）|
| `Mvp110007Serv` | 067050 | cron 可配 | **六日未回覆AI外撥**（每周一，呼叫核心取姓名+手機）|
| `Mvp110008Serv` | 110008 | cron 可配 | **三日未回覆重發驗證信**（txStatus=13→推回01）|

#### AI 外撥整合服務（3 檔）

| Service | 排程 | 功能 |
|---------|------|------|
| `GenAiCallingRptServ` | 01:00 | **AI外撥名單導出**（RSA解密→查DB FLAG=2→CSV→FTP上傳）|
| `ImportAiResultServ` | 02:00 | **AI結果下載**（RSA解密→FTP下載 CallList.xlsx→解析→移備份）|
| `ImportAiResultToProcessServ` | — | **Excel解析+DB更新**（依客戶選擇=1重發 / =2結束流程）|

---

### 🔄 核心狀態機

```
01(收到申請) → 10(寄信前) → 11(寄信中) → 13(客戶收到) 
  ├→ 15(客戶確認) → 20(送ESB前) → 21(送核心) → 00(完成)
  ├→ [三日未回覆] → 重發驗證信 (Mvp110008)
  └→ [六日未回覆] → AI外撥索取資料 (Mvp110007) → 17(AI外撥中) → FTP導出名單
      ↓ 合作廠商執行AI外撥
      ↓ FTP下載 CallList.xlsx (ImportAiResultServ)
      ↓ 依客戶選擇更新狀態 (ImportAiResultToProcessServ)
```

**STATUS:** `00`(處理中) / `01`(成功) / `02`(失敗) / `99`(作廢)
