#!/bin/bash

# ============================================================
# 富邦MVP - AI外撥結果報表導入腳本 (ImportAiResultServ.sh)
# ============================================================
# 功能：每日從 FTP 下載 AI 結果報表 CallList_YYYYMMDD.xlsx，
# 透過 REST API 呼叫 MvpApiCtrl.aiResultImport() 解析 Excel
# 並更新 EMAILMAS / EMAILDTL / EMAILIMG 資料表
#
# 邏輯：
# - 客戶選擇 = "1" → TX_STATUS 設成 "01"(收到申請)，重新觸發流程
# - 客戶選擇 = "2" → TX_STATUS 設成 "00"(全部完成)，註記客戶表示無須變更Email
# - 客戶選擇 = 其他 → 不處理
#
# 資料流程：
#   FTP 伺服器 (/download/CallList_YYYYMMDD.xlsx)
#     ↓ FTP 下載
#   /home/mvpadm/download/CallList_YYYYMMDD.xlsx
#     ↓ Excel 解析 + DB 更新 (Java REST API)
#   /home/mvpadm/processed/YYYYMM/CallList_YYYYMMDD.xlsx
#
# 執行方式：sh ImportAiResultServ.sh
# 建議 crontab：每天凌晨 2:00 執行 (0 2 * * *)
# ============================================================

# --------------------------------------- 第 1 段：環境初始化
. ~/.bash_profile
date
pgm=`basename $0`
echo ${pgm}

# 日期設定
#   file_date    → 昨日的日期（YYYYMMDD），用於命名檔案
#   currentmonth → 當月（YYYYMM），用於備份目錄分層
file_date=`date -d "1 day ago" "+%Y%m%d"`
currentmonth=`date "+%Y%m"`

# ====== 檔案名稱設定 ======
ai_filename="CallList_${file_date}.xlsx"
download_dir="/home/mvpadm/download"
processed_dir="/home/mvpadm/processed"
local_file="${download_dir}/${ai_filename}"
log_file="${logs}/AI_RESULT_${file_date}.log"

echo "Target file: ${ai_filename}"
echo "Local path: ${local_file}"

# 寫入日誌標頭
cat >> ${log_file} << EOF
========================================
AI Result Import Script
Target file (本次處置的目標檔案): ${ai_filename}
Local path (下載後暫存路徑): ${local_file}
========================================
EOF

# --------------------------------------- 第 2 段：解密資料庫連線設定
openssl rsautl -decrypt -inkey ${home}/rsa.key -in ${home}/mvpsqlserver.conf.enc -out ${home}/mvpsqlserver.conf

ip=`cat ${home}/mvpsqlserver.conf | grep ip | awk -F"=" '{print $2}'`
port=`cat ${home}/mvpsqlserver.conf | grep port | awk -F"=" '{print $2}'`
database=`cat ${home}/mvpsqlserver.conf | grep database | awk -F"=" '{print $2}'`
separator=`cat ${home}/mvpsqlserver.conf | grep sep | awk -F"=" '{print $2}'`
user=`cat ${home}/mvpsqlserver.conf | grep user | awk -F"=" '{print $2}'`
password=`cat ${home}/mvpsqlserver.conf | grep password | awk -F"=" '{print $2}'`

# --------------------------------------- 第 3 段：確認主節點（防止多機重複執行）
master=`sqlcmd -S ${ip},${port} -h -1 -W -j -s ${separator} -d ${database} -U ${user} -P ${password} -I -Q "set nocount on;select host_name from emailhos where main='1';"`

# 若首次查詢返回空值，等待 10 秒後重試
if [ ${#master} -eq 0 ] ; then
   echo "Master not found, sleeping 10s..."
   echo "Master not found, sleeping 10s..." >> ${log_file}
   sleep 10
   master=`sqlcmd -S ${ip},${port} -h -1 -W -j -s ${separator} -d ${database} -U ${user} -P ${password} -I -Q "set nocount on;select host_name from emailhos where main='1';"`
fi

# 取得當前機器的主機名稱並比較
run_machine=`hostname`
if [ "${master}" != "${run_machine}" ] ; then
   echo -e "=== The Master Is ${master} ===\n"
   echo " "
   echo -e "=== Running Machine Is ${run_machine} ===\n"
   echo "Not master node, exiting..." >> ${log_file}
   rm -f ${home}/mvpsqlserver.conf
   exit 8
else
   echo "=== The Master Is ${master} ==="
   echo " "
   echo "=== Running Machine Is ${run_machine} ==="
   echo "Master node confirmed, proceeding..." >> ${log_file}
fi

# --------------------------------------- 第 4 段：從 FTP 下載 Excel 檔案

# 讀取 FTP 帳號密碼 (與 GenAiCallingRptServ 相同做法)
CNT=0
FUSER=""
PASSWD=""
cat /home/mvpadm/sh/ftp.ini | while read LINE
do
   # 跳過空白列
   if [ -z "$(echo ${LINE} | tr -d '[:space:]')" ]; then
      continue
   fi

   OUTPUT="$(sh /home/mvpadm/sh/decode.sh ${LINE} 2>&1)"
   if [ $CNT -eq 0 ]
   then
      FUSER=${OUTPUT}
   else
      PASSWD=${OUTPUT}
   fi

   ((CNT+=1))

   # FTP 下載 (只讀取前2行：帳號+密碼)
   if [ $CNT -eq 2 ]
   then
      echo "Connecting to FTP server..."
      echo "Connecting to FTP server..." >> ${log_file}
      echo "Downloading ${ai_filename}..." >> ${log_file}

      # 建立下載目錄 (若不存在)
      mkdir -p ${download_dir}

      # FTP 下載檔案
      # 注意：FTP 路徑 /download 為 AI 外撥平台放置結果報表的位置
      ftp -p -n ${FTP_IP} << END_SCRIPT >> ${log_file} 2>&1
quote USER ${FUSER}
quote PASS ${PASSWD}

# 遠端目錄路徑 (AI 外撥平台放置結果報表的位置)
cd /download

# 本地目錄路徑
lcd ${download_dir}

# 下載檔案
get ${ai_filename}

quit
END_SCRIPT

      ftp_exit_code=$?
      echo "FTP download result: ${ftp_exit_code}"
      echo "FTP download result: ${ftp_exit_code}" >> ${log_file}

      # 檢查 FTP 是否成功，失敗則提前結束
      if [ ${ftp_exit_code} -ne 0 ] ; then
         echo "ERROR: FTP download failed with exit code (FTP下載檔案異常) ${ftp_exit_code}"
         echo "ERROR: FTP download failed with exit code (FTP下載檔案異常) ${ftp_exit_code}" >> ${log_file}
         rm -f ${home}/mvpsqlserver.conf
         exit 9
      fi
   fi
done

# --------------------------------------- 第 5 段：檢查檔案是否存在
if [ ! -f "${local_file}" ]; then
   echo "ERROR: File ${local_file} not found!"
   echo "ERROR: File ${local_file} not found!" >> ${log_file}
   echo "Possible reasons:" >> ${log_file}
   echo " 1. FTP download failed" >> ${log_file}
   echo " 2. File name mismatch (check date)" >> ${log_file}
   echo " 3. File not yet uploaded by AI platform" >> ${log_file}

   # 檢查是否有其他日期的檔案 (協助除錯)
   echo "Available files in ${download_dir} (列出下載目錄中所有 CallList_*.xlsx 檔案) :" >> ${log_file}
   ls -la ${download_dir}/CallList_*.xlsx >> ${log_file} 2>&1

   rm -f ${home}/mvpsqlserver.conf
   exit 9
fi

# --------------------------------------- 第 6 段：檢查檔案修改時間 (避免處理舊檔)
# Java 邏輯：檔案需小於 12 小時內修改 (12 * 3600 = 43200 秒)
file_age_seconds=$(( $(date +%s) - $(stat -c %Y "${local_file}") ))
if [ ${file_age_seconds} -gt 43200 ]; then
   warning_msg="WARNING: File ${local_file} is too old (modified ${file_age_seconds} seconds ago)."
   echo ${warning_msg}
   echo ${warning_msg} >> ${log_file}
   echo "Skipping to avoid processing stale data." >> ${log_file}
   rm -f ${home}/mvpsqlserver.conf
   exit 8
fi

echo "File found: ${local_file} (modified ${file_age_seconds} seconds ago)"
echo "File found: ${local_file} (modified ${file_age_seconds} seconds ago)" >> ${log_file}

# --------------------------------------- 第 7 段：呼叫 REST API 解析 Excel 並更新資料庫
# 端點：MvpApiCtrl.java @PostMapping("/api/airesult/import")
# 功能：ImportAiResultToProcessServ.processAiResultReport()
#       解析 Excel → 逐筆比對 EMAILMAS.UUID
#       客戶選擇=1 → TX_STATUS="01" (重新觸發寄驗證信)
#       客戶選擇=2 → TX_STATUS="00" (全部完成)

echo "Processing Excel file directly..." >> ${log_file}
API_URL="http://localhost:8080/mvp/api/airesult/import"

# 呼叫 API (傳遞 Excel 檔案路徑)
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
   -X POST ${API_URL} \
   -H "Content-Type: text/plain" \
   -d "${local_file}")

echo "API call status: ${HTTP_STATUS}" >> ${log_file}

if [ "${HTTP_STATUS}" = "200" ]; then
   echo "SUCCESS: AI result import completed" >> ${log_file}
else
   echo "ERROR: Failed to process Excel - API returned status ${HTTP_STATUS}" >> ${log_file}
   rm -f ${home}/mvpsqlserver.conf
   exit 11
fi

# --------------------------------------- 第 8 段：備份已處理的檔案並清理
# 將原始檔案移至 /processed/YYYYMM/ 目錄 (同 filesystem 下為 rename 操作)
# 若目標檔案已存在則覆蓋 (REPLACE_EXISTING)
# 範例: /home/mvpadm/download/CallList_20260618.xlsx
#       → /home/mvpadm/processed/202606/CallList_20260618.xlsx

backup_dir="${processed_dir}/${currentmonth}"
mkdir -p ${backup_dir}

# mv 覆蓋目標 (若已存在)
mv -f ${local_file} ${backup_dir}/${ai_filename} 2>/dev/null

echo "File backed up to ${backup_dir}/${ai_filename}" >> ${log_file}

# 記錄處理結果摘要
cat >> ${log_file} << EOF
========================================
Processing completed at $(date "+%Y-%m-%d %H:%M:%S")
========================================
EOF

# 刪除明文化連線設定檔，避免密碼遺留
rm -f ${home}/mvpsqlserver.conf

echo "Done."
exit 0