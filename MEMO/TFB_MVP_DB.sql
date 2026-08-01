select * from EMAILMAS where ID='F125688926' and UUID='0010027aab9918100890028000000000'

select * from ERRDESC

select emailmaste0_.SID as sid1_4_, emailmaste0_.AFTER_EMAIL_ADDR as after_em2_4_, emailmaste0_.BRANCH as branch3_4_, emailmaste0_.CH_NAME as ch_name4_4_, emailmaste0_.CHG_DATE as chg_date5_4_, 
emailmaste0_.CHG_TIME as chg_time6_4_, emailmaste0_.CHNL as chnl7_4_, emailmaste0_.CHECKER as checker8_4_, emailmaste0_.EN_NAME as en_name9_4_, emailmaste0_.ERR_CODE as err_cod10_4_, 
emailmaste0_.FLAG as flag11_4_, emailmaste0_.ID as id12_4_, emailmaste0_.ID_TYPE as id_type13_4_, emailmaste0_.NAME as name14_4_, emailmaste0_.ON_OFF_LINE as on_off_15_4_, emailmaste0_.PHONE as phone16_4_, 
emailmaste0_.PREV_EMAIL_ADDR as prev_em17_4_, emailmaste0_.REASON as reason18_4_, emailmaste0_.REMAKR as remakr19_4_, emailmaste0_.STATUS as status20_4_, emailmaste0_.SUB_CHNL as sub_chn21_4_, 
emailmaste0_.TEL_NO as tel_no22_4_, emailmaste0_.TELLER as teller23_4_, emailmaste0_.TRAN_CODE as tran_co24_4_, emailmaste0_.TX_STATUS as tx_stat25_4_, emailmaste0_.UUID as uuid26_4_ 
from EMAILMAS emailmaste0_ where emailmaste0_.STATUS='02' and emailmaste0_.TX_STATUS='31' and emailmaste0_.ERR_CODE='E010' order by emailmaste0_.SID asc

SELECT * FROM EMAILMAS 
WHERE STATUS = '00' 
  AND TX_STATUS = '13' 
  AND CHG_DATE < DATE_SUB(NOW(), INTERVAL 6 DAY) 
  AND FLAG = '1' 
ORDER BY ID
-- 測試六日逾期
update EMAILMAS set STATUS='00', TX_STATUS='13', FLAG='1', CHG_DATE='20260603' where ID='F125688926' and UUID='0010027aab9918100890028000000000'


select T.STATUS, T.TX_STATUS, T.CHG_DATE, T.* from EMAILMAS T where T.ID='F125688926' and T.UUID='0010027aab9918100890028000000000'
-- 測試三日逾期
update EMAILMAS set STATUS='00', TX_STATUS='13', FLAG=null where ID='F125688926' and UUID='0010027aab9918100890028000000000'


select m.SID as sid1_4_, m.AFTER_EMAIL_ADDR as after_em2_4_, m.BRANCH as branch3_4_, m.CH_NAME as ch_name4_4_, m.CHG_DATE as chg_date5_4_, m.CHG_TIME as chg_time6_4_, m.CHNL as chnl7_4_, m.CHECKER as checker8_4_, m.EN_NAME as en_name9_4_, m.ERR_CODE as err_cod10_4_, m.FLAG as flag11_4_, m.ID as id12_4_, m.ID_TYPE as id_type13_4_, m.NAME as name14_4_, m.ON_OFF_LINE as on_off_15_4_, m.PHONE as phone16_4_, m.PREV_EMAIL_ADDR as prev_em17_4_, m.REASON as reason18_4_, m.REMAKR as remakr19_4_, m.STATUS as status20_4_, m.SUB_CHNL as sub_chn21_4_, m.TEL_NO as tel_no22_4_, m.TELLER as teller23_4_, m.TRAN_CODE as tran_co24_4_, m.TX_STATUS as tx_stat25_4_, m.UUID as uuid26_4_ 
from EMAILMAS m 
where m.STATUS='00' and m.TX_STATUS='13' 
and m.CHG_DATE<'20260608'
order by m.CHG_DATE asc

select * from EMAILMAS where UUID='0Z89110001061715515200004'
select * from EMAILDTL where UUID='0Z89110001061715515200004'
select * from EMAILIMG where UUID='0Z89110001061715515200004'

select * from EMAILMAS where UUID='0010027aab9918100890028000000000'
select * from EMAILDTL where UUID='0010027aab9918100890028000000000'
select * from EMAILIMG where UUID='0010027aab9918100890028000000000'



--EMAILDTL
select * from EMAILDTL where UUID='0010027aab9918100890028000000000' 
--and CHG_DATE='20260605' and CHG_TIME='095704' and RESP_DATE='20260611' and RESP_TIME='001850'
order by CHG_DATE, CHG_TIME

select count(uuid), uuid from EMAILDTL group by uuid
select * from EMAILDTL where UUID='0010027aab9918100890028000000000'
select * from EMAILIMG where UUID='0010027aab9918100890028000000000'






--查詢全部 AI 外撥相關紀錄
SELECT * FROM EMAILMAS 
WHERE UUID = '0010027aab9918100890028000000000' AND STATUS != '99'

--查出所有 STATUS='00' 且 TX_STATUS IN ('11','13') 的記錄
SELECT * FROM EMAILMAS 
WHERE 1=1
--AND STATUS = '00' 
--AND TX_STATUS IN ('11', '13')
-- ############### 時間區間 使用範例 ############### 
--AND CHG_DATE >= '20260529' AND CHG_DATE <= '20260729' --時間區間
--AND CONVERT(DATE, CHG_DATE)  BETWEEN '2026-05-29' AND '2026-07-29' --MSSERVER 時間區間
AND STR_TO_DATE(CHG_DATE, '%Y%m%d')  BETWEEN '2026-05-29' AND '2026-07-29' --MYSQL 時間區間
-- ############### 時間區間 使用範例 ############### 
ORDER BY ID ASC
