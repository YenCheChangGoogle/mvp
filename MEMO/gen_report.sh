#!/bin/bash
. ~/.bash_profile

# ============================================================
# 先觸發 Mvp110008Serv：三日未回覆重發驗證信
# ============================================================
echo "========== 呼叫 /mvp/api/110008 執行三日未回覆重發驗證信 =========="
API_URL="http://localhost:8080/mvp/api/110008"
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST ${API_URL})
echo "API call status: ${HTTP_STATUS}"

if [ "${HTTP_STATUS}" = "200" ]; then
  echo "SUCCESS: /mvp/api/110008 執行成功"
else
  echo "ERROR: /mvp/api/110008 回傳狀態 ${HTTP_STATUS}"
  # rm ${home}/mvpsqlserver.conf
  exit 11
fi
echo "========== /mvp/api/110008 執行完畢 =========="
echo ""

date
pgm=`basename $0`
echo ${pgm}
rundate=`date "+%Y%m%d"`
openssl rsautl -decrypt -inkey rsa.key -in ${home}/mvpsqlserver.conf.enc -out ${home}/mvpsqlserver.conf
ip=`cat ${home}/mvpsqlserver.conf | grep ip | awk -F"=" '{print $2}'`
port=`cat ${home}/mvpsqlserver.conf | grep port | awk -F"=" '{print $2}'`
database=`cat ${home}/mvpsqlserver.conf | grep database | awk -F"=" '{print $2}'`
separator=`cat ${home}/mvpsqlserver.conf | grep sep | awk -F"=" '{print $2}'`
user=`cat ${home}/mvpsqlserver.conf | grep user | awk -F"=" '{print $2}'` 
password=`cat ${home}/mvpsqlserver.conf | grep password | awk -F"=" '{print $2}'`
master=`sqlcmd -S ${ip},${port} -h -1 -W -j -s ${separator} -d ${database} -U ${user} -P ${password} -I -Q "set nocount on;select host_name from emailhos where main='1';"`
if [ ${#master} -eq 0 ] ; then
   sleep 10
   master=`sqlcmd -S ${ip},${port} -h -1 -W -j -s ${separator} -d ${database} -U ${user} -P ${password} -I -Q "set nocount on;select host_name from emailhos where main='1';"`
fi
run_machine=`hostname`
if [ "${master}" != "${run_machine}" ] ; then
   echo -e "=== The Master Is ${master} ===\n"
   echo " "
   echo -e "=== Running Machine Is ${run_machine} ===\n"
   rm ${home}/mvpsqlserver.conf
   exit 8
else
   echo -e "=== The Master Is ${master} ===\n"
   echo " "
   echo -e "=== Running Machine Is ${run_machine} ===\n"
fi
table=$1
table2=$2
file_date=`sqlcmd -S ${ip},${port} -h -1 -W -j -s ${separator} -d ${database} -U ${user} -P ${password} -I -Q "set nocount on;select convert(varchar,DATEADD(day,-1,'${rundate}'),112);"`

sqlcmd -S ${ip},${port} -h -1 -W -j -s ${separator} -d ${database} -U ${user} -P ${password} -o $reports/REPORT1 -I -Q "set nocount on;
select E.BRANCH,E.ID,E.AFTER_EMAIL_ADDR,E.TELLER,CASE WHEN E.CHECKER IS NULL THEN ' ' ELSE E.CHECKER END,
CASE E.REASON WHEN '其他(請說明)' THEN '05' when '擔任負責人之企業/組織' then '04' when '子女' then '03' when '配偶' then '02' when '父母' then '01' else E.REASON END 'REASON',E.REMAKR,substring(A.TMSTAMP,1,8) CHG_DATE,substring(A.TMSTAMP,9,6) CHG_TIME from ${table} E 
inner join ( select UUID,max(resp_date+resp_time) TMSTAMP from EMAILDTL where TX_STATUS='00' AND resp_date='${file_date}' group by UUID ) A
on E.UUID = A.UUID;"

cat $reports/REPORT1 | sed 's/ *//g' > $reports/REPORT1_USE${file_date} 

# # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # 
# ftp data transfer for REPORT1_USE${file_date} # # # # # # # #
HOST='xxx.xxx.xxx.xxx'
CNT=0
cat /home/mvpadm/sh/ftp.ini | while read LINE
do
  OUTPUT="$(sh /home/mvpadm/sh/decode.sh ${LINE} 2>1&)"
  if [ $CNT -eq 0 ]
  then	  
    FUSER=$OUTPUT
#	echo "User =" $FUSER
  else
    PASSWD=$OUTPUT
#	echo "Passwd =" $PASSWD
  fi

((CNT+=1))

if [ $CNT -eq 2 ]
then  
#echo "User =" $FUSER
#echo "Passwd =" $PASSWD

ftp -p -n xxx.xxx.xxx.xxx << END_SCRIPT
quote USER $FUSER 
quote PASS $PASSWD
lcd /home/mvpadm/reports
cd /upload/A0001527/MVP_2_RPT
put REPORT1_USE${file_date}
quit
END_SCRIPT
fi
done

# # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # 
# ftp data transfer for REPORT2_USE${file_date} # # # # # # # #

sqlcmd -S ${ip},${port} -h -1 -W -j -s ${separator} -d ${database} -U ${user} -P ${password} -o $reports/REPORT2 -I -Q "set nocount on;
select  
e.ID,E.CH_NAME,e.AFTER_EMAIL_ADDR,e.CHG_DATE,e.CHG_TIME,e.TELLER,e.BRANCH,
e.STATUS,e.TX_STATUS,E.ERR_CODE,case when (c.ERR_DESC is null and e.STATUS <>'02') then '未回覆' else c.ERR_DESC end,

# ============================================================
# 三日未回覆重發驗證信 旗標
# ============================================================
case
  when e.FLAG in ('1', '2') then 'Y'
  else 'N'
end as MAIL_RETRY
  
from ${table} e
left join ${table2} C
on e.ERR_CODE = C.ERR_CODE
where (e.STATUS='02' or (e.STATUS ='00' and e.TX_STATUS < 15))
AND E.CHG_DATE = convert(varchar,DATEADD(day,-3,'${rundate}'),112);"

cat $reports/REPORT2 | sed 's/ *//g' > $reports/REPORT2_USE${file_date} 

HOST='xxx.xxx.xxx.xxx'
CNT=0
cat /home/mvpadm/sh/ftp.ini | while read LINE
do
  OUTPUT="$(sh /home/mvpadm/sh/decode.sh ${LINE} 2>1&)"
  if [ $CNT -eq 0 ]
  then	  
    FUSER=$OUTPUT
  else
    PASSWD=$OUTPUT
  fi

((CNT+=1))

if [ $CNT -eq 2 ]
then  

ftp -p -n xxx.xxx.xxx.xxx << END_SCRIPT
quote USER $FUSER 
quote PASS $PASSWD
lcd /home/mvpadm/reports
cd /upload/A0001527/MVP_2_RPT
put REPORT2_USE${file_date}
cd /upload/A0001527/MVP_2_AUC
put REPORT2_USE${file_date}
quit
END_SCRIPT
fi
done

# # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # 
# ftp data transfer for REPORT3_USE${file_date} # # # # # # # #

sqlcmd -S ${ip},${port} -h -1 -W -j -s ${separator} -d ${database} -U ${user} -P ${password} -o $reports/REPORT3 -I -Q "set nocount on;
;WITH CTE1 AS (
select m.*,ISNULL(c.ERR_DESC,'') ERR_DESC from EMAILMAS m left join ERRDESC c on m.ERR_CODE = c.ERR_CODE 
),
CTE2 AS(
SELECT ROW_NUMBER() OVER (PARTITION BY ID ORDER BY convert(varchar,chg_date+chg_time,120) DESC ) AS ROW,* from CTE1
),
CTE3 AS (
select * from CTE2 where ROW=1 and ((STATUS='02' and TX_STATUS = '21') or (STATUS ='00' and TX_STATUS = '13')) and CHNL <> 'JS'
),
CTE4 AS (
select e.UUID,ID,CHG_DATE,CHG_TIME,AFTER_EMAIL_ADDR,CHNL,STATUS,BRANCH,TELLER,substring(d.DTL,1,8) ReceiveDate,substring(d.DTL,9,6) ReceiveTime,ERR_DESC 
from CTE3 e
inner join ( select UUID,max(resp_date+resp_time) DTL from EMAILDTL where resp_date < convert(varchar,DATEADD(DAY,-1,'${rundate}'),112 ) group by UUID) d 
on e.UUID = d.UUID 
)
select ID,CHG_DATE,CHG_TIME,AFTER_EMAIL_ADDR,CASE STATUS WHEN '00' THEN '處理中' when '02' then '失敗' END 'STATUS',BRANCH,TELLER,ReceiveDate,ReceiveTime,ERR_DESC from CTE4
order by convert(varchar,CHG_DATE+CHG_TIME,120)"

cat $reports/REPORT3 | sed 's/ *//g' > $reports/REPORT3_USE${file_date} 

HOST='xxx.xxx.xxx.xxx'
CNT=0
cat /home/mvpadm/sh/ftp.ini | while read LINE
do
  OUTPUT="$(sh /home/mvpadm/sh/decode.sh ${LINE} 2>1&)"
  if [ $CNT -eq 0 ]
  then	  
    FUSER=$OUTPUT
  else
    PASSWD=$OUTPUT
  fi

((CNT+=1))

if [ $CNT -eq 2 ]
then  

ftp -p -n xxx.xxx.xxx.xxx << END_SCRIPT
quote USER $FUSER 
quote PASS $PASSWD
lcd /home/mvpadm/reports
cd /upload/A0001527/MVP_2_RPT
put REPORT3_USE${file_date}
quit
END_SCRIPT
fi
done