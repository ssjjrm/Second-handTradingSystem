# stop-all.ps1 — 停止本项目启动的四个服务
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1
# 注意：MySQL 只停「我们自己的 3307 实例」，绝不碰系统自带的 MySQL80（3306）。

. "$PSScriptRoot\env.ps1"

Write-Host "`n停止服务..." -ForegroundColor Cyan

# ---------- 1. 前端 / 后端 / AI：按端口找进程并结束 ----------
foreach ($port in @($WEB_PORT, $BACKEND_PORT, $AI_PORT)) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conns) {
        foreach ($pid_ in ($conns.OwningProcess | Sort-Object -Unique)) {
            try {
                $p = Get-Process -Id $pid_ -ErrorAction Stop
                Write-Host ("  停止 {0} (PID {1}) 占用端口 {2}" -f $p.ProcessName, $pid_, $port) -ForegroundColor Yellow
                Stop-Process -Id $pid_ -Force
            } catch {
                Write-Host ("  端口 {0} 的进程 {1} 已退出" -f $port, $pid_) -ForegroundColor DarkGray
            }
        }
    } else {
        Write-Host ("  端口 {0} 本就空闲" -f $port) -ForegroundColor DarkGray
    }
}

# ---------- 2. MySQL：优雅关闭我们自己的实例 ----------
if (Test-Port $DB_PORT) {
    Write-Host "  关闭专用 MySQL 实例（3307）..." -ForegroundColor Yellow
    $mysqladmin = Join-Path $MYSQL_BIN 'mysqladmin.exe'
    & $mysqladmin --no-defaults -h127.0.0.1 "-P$DB_PORT" "-u$DB_USER" "-p$DB_PASS" shutdown 2>&1 |
        Where-Object { $_ -notmatch 'Using a password' }
    Start-Sleep -Seconds 2
    if (Test-Port $DB_PORT) {
        Write-Host "  优雅关闭未生效，强制结束 mysqld 进程" -ForegroundColor Yellow
        Get-Process mysqld -ErrorAction SilentlyContinue | Stop-Process -Force
    }
    Write-Host "  MySQL 已停止" -ForegroundColor Green
} else {
    Write-Host "  MySQL(3307) 本就未运行" -ForegroundColor DarkGray
}

Write-Host "`n全部停止完成。系统自带的 MySQL80(3306) 未受影响。`n" -ForegroundColor Cyan
