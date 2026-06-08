#!/bin/bash

# ============================================================
# 富邦MVP - AI外撥報表導出腳本 (GenAiCallingRptServ.sh)
# ============================================================
# 功能：每日產出 AI外撥報表 AI_CALLING_(YYYYMMDD).csv
#       透過 SQL 查詢 EMAILMAS 資料表提取需要外撥的客戶資料
#
# 注意:
#   因應 Mvp110007Serv 排程 將需要外撥的名單資料更新(含姓名與電話)
#   同時FLAG會設定為2
#   且 系統設定中 default.job.4=1 是設定 Mvp110007Serv 是否作業
#
# 邏輯：
#   FLAG='2' 且 PHONE不為空值
#
# 執行方式：sh GenAiCallingRptServ.sh
# 建議 crontab：每天凌晨 1:00 執行 (0 1 * * *)
#
# 【CSV 欄位結構】
#   手機號碼, 客戶ID, 客戶姓名, 本次外撥目的, TTS1, 變數1, 變數2, 變數3,
#   TTS2, TTS3, TTS4, SMS1, SMS2, SMS3, SMS4, SMS5, SMSDefault
#
# 【與 ImportAiResultServ 的關聯】
#   GenAiCallingRptServ   (01:00) → 導出外撥名單 → 上傳 FTP
#                          ↓ 合作廠商執行 AI 外撥
#   ImportAiResultServ    (02:00) → 下載外撥結果 → 更新 DB
# ============================================================

. ~/.bash_profile
date
pgm=`basename $0`
echo ${pgm}
rundate=`date "+%Y%m%d"`

# 可設定參數 (對應 Java @Value("${GenAiCallingRptServ.FTP_IP}"))
#FTP_IP="192.168.1.200"

# 1. Decrypt and read SQL Server configuration
openssl rsautl -decrypt -inkey ${home}/rsa.key -in ${home}/mvpsqlserver.conf.enc -out ${home}/mvpsqlserver.conf
ip=`cat ${home}/mvpsqlserver.conf | grep ip | awk -F"=" '{print $2}'`
port=`cat ${home}/mvpsqlserver.conf | grep port | awk -F"=" '{print $2}'`
database=`cat ${home}/mvpsqlserver.conf | grep database | awk -F"=" '{print $2}'`
separator=`cat ${home}/mvpsqlserver.conf | grep sep | awk -F"=" '{print $2}'`
user=`cat ${home}/mvpsqlserver.conf | grep user | awk -F"=" '{print $2}'`
password=`cat ${home}/mvpsqlserver.conf | grep password | awk -F"=" '{print $2}'`

# 2. Check if this machine is the master
master=`sqlcmd -S ${ip},${port} -h -1 -W -j -s ${separator} -d ${database} -U ${user} -P ${password} -I -Q "set nocount on;select host_name from emailhos where main='1';"`

# 若首次查詢返回空值，等待 10 秒後重試
if [ ${#master} -eq 0 ] ; then
   echo "Master not found, sleeping 10s..."
   sleep 10
   master=`sqlcmd -S ${ip},${port} -h -1 -W -j -s ${separator} -d ${database} -U ${user} -P ${password} -I -Q "set nocount on;select host_name from emailhos where main='1';"`
fi

# 取得當前機器的主機名稱並比較
run_machine=`hostname`
if [ "${master}" != "${run_machine}" ] ; then
   echo -e "=== The Master Is ${master} ===\n"
   echo " "
   echo -e "=== Running Machine Is ${run_machine} ===\n"
   rm -f ${home}/mvpsqlserver.conf
   exit 8
else
   echo -e "=== The Master Is ${master} ===\n"
   echo " "
   echo -e "=== Running Machine Is ${run_machine} ===\n"
fi

# 3. Extract AI calling records where PHONE is not null
# Filename: AI_CALLING_(YYYYMMDD).csv
# 報表檔案名稱 AI_CALLING_{YYYYMMDD}.csv 例如: AI_CALLING_20260618.CSV
report_file="AI_CALLING_${rundate}.csv"

# 先寫入 CSV 標頭
printf '手機號碼,客戶ID,客戶姓名,本次外撥目的,TTS1,變數1,變數2,變數3,TTS2,TTS3,TTS4,SMS1,SMS2,SMS3,SMS4,SMS5,SMSDefault\n' > $reports/${report_file}

sqlcmd -S ${ip},${port} -h -1 -W -j -s ',' -d ${database} -U ${user} -P ${password} -I -Q "set nocount on;
select RTRIM(PHONE) as 手機號碼,
       RTRIM(ID) as 客戶ID,
       RTRIM(NAME) as 客戶姓名,
       '6日未回覆' as 本次外撥目的,
       '' as TTS1,
       'NA' as 變數1,
       'NA' as 變數2,
       'NA' as 變數3,
       'NA' as TTS2,
       'NA' as TTS3,
       'NA' as TTS4,
       'NA' as SMS1,
       'NA' as SMS2,
       'NA' as SMS3,
       'NA' as SMS4,
       'NA' as SMS5,
       'NA' as SMSDefault
from EMAILMAS
where FLAG='2' AND PHONE IS NOT NULL AND PHONE <> '';" >> $reports/${report_file}

# Remove extra spaces (as per gen_report.sh pattern)
cat $reports/${report_file} | sed 's/ *//g' > $reports/${report_file}_CLEAN
mv $reports/${report_file}_CLEAN $reports/${report_file}

# 4. FTP upload using decode.sh for credentials
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
ftp -p -n ${FTP_IP} << END_SCRIPT
quote USER $FUSER
quote PASS $PASSWD

# 本地目錄路徑
lcd /home/mvpadm/reports

# 輸出目錄路徑
cd /upload/A0001527/MVP_2_AUC

put ${report_file}
quit
END_SCRIPT
fi
done

# Cleanup config
rm -f ${home}/mvpsqlserver.conf