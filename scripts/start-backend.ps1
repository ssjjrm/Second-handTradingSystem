# start-backend.ps1 — 启动 Spring Boot 后端（8080）
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\start-backend.ps1
# 参数：-Dir 指定后端目录。默认启动【参考项目】；以后我自己的项目传：-Dir .\backend
#
# 说明：param() 的默认值在 dot-source env.ps1 之前就求值了，
#       所以这里不能直接写 $REF_DIR，改用"先留空、dot-source 之后再算"的写法。

param(
    [string]$Dir = ''
)

. "$PSScriptRoot\env.ps1"

if (-not $Dir) { $Dir = Join-Path $REF_DIR 'backend' }
if (-not (Test-Path $Dir)) { throw "后端目录不存在：$Dir" }

$env:JAVA_HOME = $JAVA_HOME_RESOLVED

# 数据库连接（application.yml 里已有同样默认值，这里再设一遍是双保险，
# 也方便你临时切库：改这里或改 yml 都可以）
$env:BOOK_DB_URL      = "jdbc:mysql://127.0.0.1:$DB_PORT/$DB_NAME?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:BOOK_DB_USER     = $DB_USER
$env:BOOK_DB_PASSWORD = $DB_PASS

Write-Host "[backend] 目录：$Dir" -ForegroundColor Cyan
Write-Host "[backend] DB  ：127.0.0.1:$DB_PORT/$DB_NAME" -ForegroundColor DarkGray
Write-Host "[backend] 等待控制台出现 'Started BackendApplication' 即成功" -ForegroundColor Yellow

Set-Location $Dir
& $MVN -B spring-boot:run
