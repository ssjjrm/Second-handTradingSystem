# db-init.ps1 — 初始化数据库（从零建库 + 导入建表 SQL）
#
# 用法：
#   # 重建【参考项目】的库（book_cycle，7 张表）
#   powershell -ExecutionPolicy Bypass -File scripts\db-init.ps1
#
#   # 建【我的项目】的库（库名自定，SQL 目录自定）
#   powershell -ExecutionPolicy Bypass -File scripts\db-init.ps1 -DbName secondhand_shop -SqlDir .\database
#
# 参数：
#   -DbName    要初始化的库名（默认 book_cycle）
#   -SqlDir    存放 *.sql 的目录（默认参考项目的 database 目录）
#   -Force     库已存在且非空时，先删库重建（危险！会丢数据，必须显式传）

param(
    [string]$DbName = '',
    [string]$SqlDir = '',
    [switch]$Force
)

. "$PSScriptRoot\env.ps1"

if (-not $DbName) { $DbName = $DB_NAME }
if (-not $SqlDir) { $SqlDir = Join-Path $REF_DIR 'database' }

if (-not (Test-Path $SqlDir)) { throw "SQL 目录不存在：$SqlDir" }

Write-Host "`n===== 初始化数据库：$DbName =====" -ForegroundColor Cyan
Write-Host "  实例：127.0.0.1:$DB_PORT" -ForegroundColor DarkGray
Write-Host "  SQL ：$SqlDir" -ForegroundColor DarkGray

# ---------- 1. 检查库是否已存在 ----------
$exists = Invoke-Sql -Sql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$DbName';" -Database $null
$existsCount = [int]($exists[-1])

if ($existsCount -gt 0) {
    $tblCount = Invoke-Sql -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DbName';" -Database $null
    $tblCount = [int]($tblCount[-1])
    if ($tblCount -gt 0) {
        if (-not $Force) {
            Write-Host "  库已存在且有 $tblCount 张表，未做任何改动。" -ForegroundColor Yellow
            Write-Host "  如确认要清空重建，请加 -Force：" -ForegroundColor Yellow
            Write-Host "    powershell -ExecutionPolicy Bypass -File scripts\db-init.ps1 -DbName $DbName -Force" -ForegroundColor DarkGray
            return
        }
        Write-Host "  -Force 已指定，删除旧库 $DbName ..." -ForegroundColor Red
        Invoke-Sql -Sql "DROP DATABASE ``$DbName``;" -Database $null | Out-Null
        $existsCount = 0
    }
}

# ---------- 2. 建库 ----------
if ($existsCount -eq 0) {
    Write-Host "  建库 $DbName （utf8mb4）..." -ForegroundColor Green
    Invoke-Sql -Sql "CREATE DATABASE ``$DbName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" -Database $null | Out-Null
}

# ---------- 3. 导入 SQL ----------
# 备选顺序：优先按"表之间的外键依赖"排，其次按文件名排
$priority = @('user','category','book','product','address','cart','order')
$files = Get-ChildItem $SqlDir -Filter '*.sql' -File | Sort-Object @{
    Expression = {
        $n = $_.Name.ToLower()
        $idx = 0
        for ($i = 0; $i -lt $priority.Count; $i++) {
            if ($n -match $priority[$i]) { $idx = $i; break }
        }
        $idx
    }
}, Name

if (-not $files) { throw "在 $SqlDir 下没找到任何 .sql 文件" }

$ok = 0
foreach ($f in $files) {
    # 用 "source" 而不是管道，避免 PowerShel 重定向破坏编码
    $out = Invoke-Sql -Sql "source $($f.FullName -replace '\\','/')" -Database $DbName
    $err = ($out | Where-Object { "$_" -match 'ERROR' })
    if ($err) {
        Write-Host "  ✗ $($f.Name)" -ForegroundColor Red
        $err | ForEach-Object { Write-Host "      $_" -ForegroundColor Red }
    } else {
        Write-Host "  ✓ $($f.Name)" -ForegroundColor Green
        $ok++
    }
}

# ---------- 4. 结果核对 ----------
Write-Host "`n  --- 导入结果 ---" -ForegroundColor Cyan
Write-Host "  成功 $ok / $($files.Count) 个文件" -ForegroundColor $(if ($ok -eq $files.Count) { 'Green' } else { 'Yellow' })

$tables = Invoke-Sql -Sql "SHOW TABLES;" -Database $DbName | Where-Object { "$_".Trim() -and "$_" -notmatch 'Tables_in' }
Write-Host "  $DbName 现有表（$($tables.Count) 张）：" -ForegroundColor Cyan
$tables | ForEach-Object { Write-Host "    - $_" -ForegroundColor DarkGray }

Write-Host "`n===== 完成 =====`n" -ForegroundColor Cyan
