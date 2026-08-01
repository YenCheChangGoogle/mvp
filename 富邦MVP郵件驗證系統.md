## 📋 專案概覽 — 富邦MVP郵件驗證系統

## 📋 專案總覽

| 項目 | 內容 |
|------|------|
| **專案名稱** | 富邦MVP郵件驗證系統 (Fubon MVP) |
| **技術棧** | Spring Boot 2.6.15 + Java 8 + Maven |
| **打包方式** | WAR |
| **資料庫** | SQL Server (主生產)、MySQL (開發測試) |
| **ORM** | Spring Data JPA + Spring Data JDBC |
| **安全框架** | Spring Security、OWASP ESAPI、Jasypt 加密 |
| **通訊協定** | REST API (XML) + SOAP Web Service |

---

## 🏗️ 架構層級

```
com.fubon.mvp/
├── MvpApplication.java      ← Spring Boot 入口（WAR部署，繼承SpringBootServletInitializer）
├── MvpConfig.java           ← 組態：MessageSource(i18n)、Validator、BCrypt、LocaleResolver(TW)
├── MvpConn.java             ← 雙資料來源配置（HikariCP + JNDi fallback）、JPA/Hibernate
├── MvpCtrl.java             ← 入口Controller，整合 page2020 自訂框架
├── MvpSecurity.java         ← Security 全放行（antMatchers "/**"）
│
├── ctrl/                    ← REST Controllers
│   ├── MvpApiCtrl.java      ← 主要API端點 /api/{交易代號}
│   └── MvpHelloCtrl.java    ← 測試用Controller /api/hello/*
│
├── dao/                     ← 資料存取層
│   ├── EmailDao.java        ← 核心DAO，包裝 Repository + JdbcTemplate
│   ├── EmailMasterRepo.java ← JPA Repository（EmailMaster 實體）
│   ├── EmailDetailRepo.java ← JPA Repository（EmailDetail 實體）
│   ├── EmailImageRepo.java  ← JPA Repository（EmailImage 實體）
│   ├── ChannelRepo.java     ← JPA Repository（Channel 實體）
│   ├── EmailHostDao/Repo    ← 郵件主機判斷
│   └── ErrorDescDao/Repo    ← 錯誤碼描述
│
├── data/                    ← JPA Entities
│   ├── SidClass.java        ← 抽象父類（@Id、自增主鍵 SID）
│   ├── EmailMaster.java     ← EMAILMAS 表（核心實體，含XML建構子）
│   ├── EmailDetail.java     ← EMAILDTL 表（交易明細/日誌）
│   ├── EmailImage.java      ← EMAILIMG 表（影像紀錄）
│   ├── Channel.java         ← CHNL 表（通路資訊）
│   └── ErrorDesc.java       ← 錯誤碼描述表
│
├── serv/                    ← 服務層
│   ├── Mvc110001Serv.java   ← 登錄用戶（含定時批次處理，fixedDelay=3s）
│   ├── Mvc110002Serv.java   ← 取消申請
│   ├── Mvc110003Serv.java   ← 前臺人工啟用
│   ├── Mvc110006Serv.java   ← 信件狀態（Mail Hunter回傳）
│   ├── Mvc110007Serv.java   ← 客戶確認信件
│   ├── Mvc310001Serv.java   ← 單筆查詢
│   ├── Mvc310002Serv.java   ← 整批查詢（分頁，NEXT_KEY）
│   ├── Mvc310003Serv.java   ← 明細查詢
│   ├── Mvc310004Serv.java   ← EMAILDTL明細查詢
│   ├── Mvc310005Serv.java   ← AI外撥情形報表
│   ├── Mvc084000Serv.java   ← 下行存活驗證（Heartbeat）
│   ├── Mvp110007Serv.java   ← ⭐六日未回覆AI外撥（每日00:30，呼叫ESB取客戶資料）
│   ├── Mvp110008Serv.java   ← ⭐三日未回覆重發驗證信（每日00:30）
│   ├── ImportAiResultServ.java    ← AI外撥回饋檔案處理
│   ├── ImportAiResultToProcessServ.java ← 導入AI結果到流程
│   ├── GenAiCallingRptServ.java   ← 產生外撥名單並FTP上傳
│   ├── EmailStatusServ.java       ← 統一下行電文生成 + UUID生成 + 池管理
│   ├── EmailWsClient.java         ← SOAP WS客戶端（呼叫Mail Hunter寄信）
│   └── EmailContent.java          ← 郵件內容模板
│
└── email/                   ← SOAP Marshal 物件
    ├── SendToOneUID.java
    ├── SendToOneUIDResponse.java
    ├── ObjectFactory.java
    └── package-info.java

page2020/                    ← 自訂Web框架（高振銘@2020）
├── core/   ← App、Page、Flow、Bean、Form、Table、Result等核心抽象
├── view/   ← HTML5/CSS3/Bootstrap4 元件生成器
├── anno/   ← 自訂註解（@Ctrl2020、@Data2020、@Table2020等）
├── client/ ← RestClient、WebProxy（ESB/API呼叫封裝）
├── flow/   ← LayoutFlow、Personal 模板流程
├── unit/   ← UI單元元件
└── util/   ← TimeUtil、EmptyUtil、NumberUtil、ParamUtil

secure2020/
└── data/SecureUser.java     ← 安全用戶實體
```

---

## 🔑 核心業務流程

**郵件驗證申請流程：**
1. **110001 登錄** → 前台來電文 → 寫EMAILMAS + EMAILDTL → 線上(Y/0)即時完成，離線則進入批次佇列
2. **110002 取消** → 作廢狀態(99+99)
3. **110006 寄信回傳** → Mail Hunter SOAP回傳 → txStatus改為"13"（已讀）
4. **110007 客戶確認** → 客戶點擊驗證連結完成

**逾時處理機制：**
- **三日未回覆（Mvp110008）**：每天00:30執行，status=00 + txStatus=13 + D-3 → 推回txStatus=01重發
- **六日未回覆AI外撥（Mvp110007）**：每周一00:30執行，D-28到D-7範圍內，呼叫ESB(MVP067050)取客戶姓名/手機，產生外撥名單

**資料表：** EMAILMAS（主檔）、EMAILDTL（明細/日誌）、EMAILIMG（影像）、CHNL（通路）

---

### 主要套件結構 (`com.fubon.mvp`)
| 套件 | 說明 |
|------|------|
| `ctrl/` | REST API 控制器（MvpApiCtrl、MvpHelloCtrl） |
| `dao/` | 資料存取層（DAO + JPA Repository） |
| `data/` | 實體模型（Entity / JPA） |
| `email/` | SOAP Web Service 請求/回應物件 |
| `serv/` | 業務邏輯服務層 |

### 核心實體 (Data Model)
| Entity | 對應表格 | 說明 |
|--------|---------|------|
| `EmailMaster` | EMAILMAS | 郵件主檔（申請單核心） |
| `EmailDetail` | EMAILDTL | 郵件明細檔（狀態追蹤） |
| `Channel` | CHNL | 交易通路定義 |
| `EmailImage` | (影像檔) | 郵件影像紀錄 |
| `ErrorDesc` | 錯誤碼描述 | 系統訊息管理 |

### 交易 API (13個端點)

**申請類：**
| 代號 | 功能 |
|------|------|
| 110001 | 前臺登錄（Email變更申請） |
| 110002 | 取消申請 |
| 110003 | 前臺人工啟用 |

**狀態類：**
| 代號 | 功能 |
|------|------|
| 110006 | 信件狀態查詢 |
| 110007 | 客戶確認信件 |
| 084000 | 下行存活驗證 (Heartbeat) |

**查詢類：**
| 代號 | 功能 |
|------|------|
| 310001 | 前臺查詢 |
| 310002 | 整批查詢（分頁） |
| 310003 | 查詢明細 |
| 310004 | EMAILDTL明細查詢 |
| 310005 | AI外撥情形報表 |

**批次作業：**
| 端點 | 功能 |
|------|------|
| MVP110007 | 六日未回覆 → AI外撥處理（每周一執行，查詢D-28~D-7範圍） |
| MVP110008 | 三日未回覆 → 重發驗證信 |
| airesult/import | AI結果報表導入（Excel解析） |

### 狀態碼定義
| STATUS | TX_STATUS | 說明 |
|--------|-----------|------|
| 00 | 01 | 處理中 / 收到申請 |
| 00 | 11 | 寄信後 |
| 00 | 13 | 客戶收到（已讀但尚未確認） |
| 01 | 00 | 成功完成 |
| 02 | - | 失敗 |
| 99 | 99 | 作廢 |

### 外部服務整合
- **Mail Hunter** (SOAP WS) → 發送驗證郵件
- **FTP伺服器** → AI外撥名單檔案上傳 / AI回饋檔案下載
- **ESB** (http://172.19.241.10:9201) → 企業服務匯流排轉發

### 密碼配置加密
- 使用 **Jasypt** 加密資料庫帳號密碼，密碼 `fubon`

### 安全防護
- OWASP ESAPI（輸入驗證）
- jsoup（HTML清洗）
- AntiSamy（XSS防護）
- BouncyCastle (AES解密)
- Log4j2（日誌 - 已升級至 2.21.1 修補 Log4Shell）

### 自訂框架 (`page2020`)
- 自研的 MVC 頁面生成框架，支援 Bootstrap 4、HTML5、CSS3 元件化渲染

