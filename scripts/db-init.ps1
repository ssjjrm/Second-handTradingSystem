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
# 3.1 先排除"重置类"脚本。
#     这类脚本内容是 DROP DATABASE（见 database\99_reset.sql），
#     如果被自动导入，会把刚建好的库删掉 —— 必须跳过。
$allFiles = Get-ChildItem $SqlDir -Filter '*.sql' -File
$skipped  = @($allFiles | Where-Object { $_.Name -match '^\d*9\d_' -or $_.Name -match 'reset' })
$files    = @($allFiles | Where-Object { $skipped.FullName -notcontains $_.FullName })

if ($skipped.Count -gt 0) {
    Write-Host "  跳过 $($skipped.Count) 个重置类脚本（含 DROP DATABASE，需手动执行）：" -ForegroundColor DarkYellow
    $skipped | ForEach-Object { Write-Host "    ⊘ $($_.Name)" -ForegroundColor DarkYellow }
}

if (-not $files -or $files.Count -eq 0) { throw "在 $SqlDir 下没找到可导入的 .sql 文件" }

# 3.2 决定导入顺序，支持两种命名约定：
#     约定 A（本项目）：文件名统一带数字前缀 01_/02_/03_ → 直接按【前缀数字】升序
#     约定 B（参考项目）：文件名无前缀               → 按【外键依赖】排，其次按文件名
$numbered = @($files | Where-Object { $_.Name -match '^\d+_' })
if ($numbered.Count -eq $files.Count) {
    $files = $files | Sort-Object @{ Expression = { [int]($_.Name -replace '^(\d+).*$', '$1') } }, Name
    Write-Host "  导入顺序：按文件名数字前缀升序" -ForegroundColor DarkGray
} else {
    $priority = @('user','category','book','product','address','cart','order')
    $files = $files | Sort-Object @{
        Expression = {
            $n = $_.Name.ToLower()
            $idx = 0
            for ($i = 0; $i -lt $priority.Count; $i++) {
                if ($n -match $priority[$i]) { $idx = $i; break }
            }
            $idx
        }
    }, Name
    Write-Host "  导入顺序：按外键依赖（user→category→product→...）" -ForegroundColor DarkGray
}
$files | ForEach-Object { Write-Host "    → $($_.Name)" -ForegroundColor DarkGray }

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
