# status.ps1 — 一键体检：四个服务是否都活着 + 关键接口是否通
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\status.ps1

. "$PSScriptRoot\env.ps1"

Write-Host "`n========== 智循书堂 · 环境体检 ==========" -ForegroundColor Cyan

# ---------- 1. 端口 ----------
Write-Host "`n[1/3] 端口监听状态" -ForegroundColor Yellow
$ports = @(
    @{ Port = $DB_PORT;      Name = 'MySQL（系统服务）' },
    @{ Port = $BACKEND_PORT; Name = 'SpringBoot 后端'   },
    @{ Port = $AI_PORT;      Name = 'FastAPI AI 服务'   },
    @{ Port = $WEB_PORT;     Name = 'Vue3 前端'         }
)
foreach ($p in $ports) {
    $up = Test-Port $p.Port
    $mark = if ($up) { '✓ 运行中' } else { '✗ 未启动' }
    $color = if ($up) { 'Green' } else { 'Red' }
    Write-Host ("  {0,-20} :{1,-5} {2}" -f $p.Name, $p.Port, $mark) -ForegroundColor $color
}

# ---------- 2. HTTP 接口 ----------
Write-Host "`n[2/3] 接口连通性" -ForegroundColor Yellow

function Try-Get($name, $url) {
    try {
        $r = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 10
        Write-Host ("  {0,-24} HTTP {1}  ✓" -f $name, $r.StatusCode) -ForegroundColor Green
        return $r
    } catch {
        Write-Host ("  {0,-24} 失败：{1}" -f $name, $_.Exception.Message) -ForegroundColor Red
        return $null
    }
}

Try-Get '后端 /api/categories' "http://localhost:$BACKEND_PORT/api/categories" | Out-Null
$aiHealth = Try-Get 'AI /health' "http://localhost:$AI_PORT/health"
if ($aiHealth) {
    $h = $aiHealth.Content | ConvertFrom-Json
    Write-Host ("       └ llm_enabled={0}  providers={1}  books_loaded={2}" -f `
        $h.llm_enabled, ($h.providers -join ','), $h.books_loaded) -ForegroundColor DarkGray
}
Try-Get '前端 首页' "http://localhost:$WEB_PORT/" | Out-Null
# 经前端代理 → 后端（验证 Vite proxy）
Try-Get '前端代理 → 后端' "http://localhost:$WEB_PORT/api/categories" | Out-Null
# 经前端代理 → AI（验证 /ai 前缀剥离）
Try-Get '前端代理 → AI'   "http://localhost:$WEB_PORT/ai/health" | Out-Null

# ---------- 3. 数据库 ----------
Write-Host "`n[3/3] 数据库（127.0.0.1:$DB_PORT）" -ForegroundColor Yellow
if (Test-Port $DB_PORT) {
    try {
        $t = Invoke-Sql -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';" -Database $null
        Write-Host "  $DB_NAME 表数量：$($t[-1])（预期 7）" -ForegroundColor Green
        $b = Invoke-Sql -Sql "SELECT COUNT(*) FROM book;"
        Write-Host "  book 表记录数：$($b[-1])" -ForegroundColor Green
        $u = Invoke-Sql -Sql "SELECT COUNT(*) FROM user;"
        Write-Host "  user 表记录数：$($u[-1])" -ForegroundColor Green
    } catch {
        Write-Host "  查询失败：$_" -ForegroundColor Red
    }
} else {
    Write-Host "  MySQL 未启动（管理员执行 net start MySQL80）" -ForegroundColor Red
}

Write-Host "`n========================================`n" -ForegroundColor Cyan
