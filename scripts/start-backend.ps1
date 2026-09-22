# start-backend.ps1 — 启动 Spring Boot 后端（8080）
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\start-backend.ps1
# 参数：-Dir 指定后端目录（默认原始参考项目），复刻出新项目后传 -Dir .\backend

param(
    [string]$Dir = "$ORIGIN_DIR\backend"
)

. "$PSScriptRoot\env.ps1"

$env:JAVA_HOME = $JAVA_HOME_RESOLVED

# 数据库连接：指向本项目专用实例（3307）。
# 注意用 / 而不是 \，PowerShell 会吞掉 \ 并在后面接 & 时断句。
$env:BOOK_DB_URL      = "jdbc:mysql://127.0.0.1:$DB_PORT/$DB_NAME?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:BOOK_DB_USER     = $DB_USER
$env:BOOK_DB_PASSWORD = $DB_PASS
$env:JWT_SECRET       = 'bookcycle2024secretkeyforjwttoken12345678901234567890'

if (-not (Test-Path $Dir)) { throw "后端目录不存在：$Dir" }

Write-Host "[backend] 目录：$Dir" -ForegroundColor Cyan
Write-Host "[backend] JDK ：$JAVA_HOME_RESOLVED" -ForegroundColor DarkGray
Write-Host "[backend] DB  ：127.0.0.1:$DB_PORT/$DB_NAME" -ForegroundColor DarkGray
Write-Host "[backend] 等待控制台出现 'Started BackendApplication' 即成功" -ForegroundColor Yellow

Set-Location $Dir
& $MVN -B spring-boot:run
