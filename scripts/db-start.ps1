# db-start.ps1 — 启动本项目专用 MySQL 实例（端口 3307）
#
# 背景：本机自带的 MySQL80 服务（端口 3306）root 密码未知，且重置它需要管理员权限。
#       所以本项目用一个「完全归你所有」的独立实例：数据目录在
#       D:\code-javaweb\.mysql-book\data，监听 3307，不动系统那个。
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\db-start.ps1

. "$PSScriptRoot\env.ps1"

$myIni = Join-Path $MYSQL_BASE 'my.ini'
$mysqld = Join-Path $MYSQL_BIN 'mysqld.exe'

if (-not (Test-Path $mysqld)) { throw "找不到 mysqld.exe：$mysqld" }
if (-not (Test-Path $myIni))   { throw "找不到配置文件：$myIni（实例未初始化？）" }

if (Test-Port $DB_PORT) {
    Write-Host "[db] 已在运行（端口 $DB_PORT），跳过启动" -ForegroundColor Yellow
} else {
    Write-Host "[db] 启动 MySQL 实例（端口 $DB_PORT）..." -ForegroundColor Cyan
    Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=$myIni" -WindowStyle Hidden
    # 等待就绪（最多 30 秒）
    $ok = $false
    foreach ($i in 1..30) {
        Start-Sleep -Seconds 1
        if (Test-Port $DB_PORT) { $ok = $true; break }
    }
    if (-not $ok) { throw "MySQL 启动超时，请看 $MYSQL_BASE\logs\ 下的错误日志" }
    Write-Host "[db] 启动成功" -ForegroundColor Green
}

# 自检：能连上吗？表在吗？
try {
    $r = Invoke-Sql -Sql "SELECT COUNT(*) AS tables_cnt FROM information_schema.tables WHERE table_schema='$DB_NAME';" -Database $null
    Write-Host "[db] 连接自检通过，$DB_NAME 库共有表：$($r[-1])" -ForegroundColor Green
} catch {
    Write-Host "[db] 自检失败：$_" -ForegroundColor Red
    throw
}
