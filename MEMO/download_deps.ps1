###############################################################################
# 是根據 專案目錄下的 pom.xml 來決定要哪些依賴 
# 令會讀取專案的 pom.xml，解析出所有依賴（包含直接依賴與傳遞依賴），並輸出完整的依賴樹
# 腳本再用正則表達式去抓取其中的 groupId、artifactId、version，整理成清單，最後到本機的 .m2/repository 找出對應的 .jar 和 .pom 檔案來複製
#
# 必須在專案目錄下有 有效的 pom.xml，否則 mvn dependency:tree 會失敗
# 系統必須安裝好 Maven，並且能在 PowerShell 中直接執行 mvn
# 腳本裡的路徑（$projectDir, $m2Repo, $outDir）要確認正確，否則會找不到檔案
#
# 執行範例 powershell -ExecutionPolicy Bypass -File .\download_deps.ps1
###############################################################################

$projectDir = "D:\GIT\github\mvp"
$m2Repo     = "C:\Users\張晏哲\.m2\repository"
$outDir     = "$projectDir\MEMO\OUT\repository"

Set-Location $projectDir

Write-Host "--- 正在解析 Maven 依賴樹 ... ---" -ForegroundColor Cyan
$treeOutput = (mvn dependency:tree 2>$null) -join "`n"

# 用 regex 匹配 groupId:artifactId:jar:version:scope
$matches_list = [regex]::Matches($treeOutput, '(?<g>[^\s:]+):(?<a>[^\s:]+):jar:(?<v>[^\s:]+):\s*(?:compile|runtime|test|provided)')

$deps = [System.Collections.Generic.List[string]]::new()
foreach ($m in $matches_list) {
    $key = "$($m.Groups['g'].Value)`:$($m.Groups['a'].Value)`:$($m.Groups['v'].Value)"
    if (-not $deps.Contains($key)) {
        $deps.Add($key) | Out-Null
    }
}
Write-Host "共找到 $($deps.Count) 個 jar 依賴（去重後）" -ForegroundColor Green

# 同時也要取得所有 parent POM（如 spring-boot-starter 的 .pom）
$parentPomMatches = [regex]::Matches($treeOutput, '(?<g>[^\s:]+):(?<a>[^\s:]+):pom:(?<v>[^\s:]+):\s*(?:compile|runtime|test|provided)')
foreach ($m in $parentPomMatches) {
    $key = "$($m.Groups['g'].Value)`:$($m.Groups['a'].Value)`:$($m.Groups['v'].Value)"
    if (-not $deps.Contains($key)) {
        $deps.Add($key) | Out-Null
    }
}

$copied = 0
$skipped = 0
foreach ($dep in $deps) {
    $parts = $dep -split ':'
    $groupId   = $parts[0].Replace('.', '\')
    $artifactId = $parts[1]
    $version   = $parts[2]

    $relDir = "$groupId\$artifactId\$version"
    $srcDir = Join-Path $m2Repo $relDir

    if (-not (Test-Path $srcDir)) {
        $skipped++
        Write-Host "  [!] (skip) $dep" -ForegroundColor Yellow
        continue
    }

    # 只取主 .jar 和 .pom（排除 -sources.jar 等）
    $filesFound = Get-ChildItem -Path $srcDir -File | Where-Object { 
        ($_.Extension -in '.jar', '.pom') -and 
        ($_.BaseName -match "$($artifactId)-$($version)$") 
    }

    if (-not $filesFound) {
        $skipped++
        Write-Host "  [!] (skip) $dep" -ForegroundColor Yellow
        continue
    }

    $destDir = Join-Path $outDir $relDir
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null

    foreach ($f in $filesFound) {
        Copy-Item $f.FullName "$destDir\$($f.Name)" -Force
        $copied++
    }
    Write-Host "  [+] $dep" -ForegroundColor Green
}

Write-Host "`n=== 完成! 共複製 $copied 個檔案, 跳過 $skipped 筆 ===" -ForegroundColor Cyan
