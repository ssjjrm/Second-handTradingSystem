# stop-all.ps1 — 停止后端 / AI / 前端三个应用进程
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1
#
# 重要：本脚本**不动 MySQL**。
#   3306 是系统自带的 MySQL80 服务，随 Windows 开机自启，属于基础设施，不该被脚本随手关掉。
#   真要停它需要管理员权限，请手动执行：
#       （管理员 PowerShell）net stop MySQL80
#   要停也请在确认没有别的项目在用这个数据库之后再停。

. "$PSScriptRoot\env.ps1"

Write-Host "`n停止应用进程..." -ForegroundColor Cyan

foreach ($port in @($WEB_PORT, $BACKEND_PORT, $AI_PORT)) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conns) {
        foreach ($pid_ in ($conns.OwningProcess | Sort-Object -Unique)) {
            try {
                $p = Get-Process -Id $pid_ -ErrorAction Stop
                Write-Host ("  停止 {0} (PID {1})，它占用了端口 {2}" -f $p.ProcessName, $pid_, $port) -ForegroundColor Yellow
                Stop-Process -Id $pid_ -Force
            } catch {
                Write-Host ("  端口 {0} 上的进程 {1} 已退出" -f $port, $pid_) -ForegroundColor DarkGray
            }
        }
    } else {
        Write-Host ("  端口 {0} 本就空闲" -f $port) -ForegroundColor DarkGray
    }
}

# mvn spring-boot:run 会挂一个 java 子进程；杀掉 8080 的监听者后，
# 父进程可能还在，这里顺手清掉残留的 java 进程（它们只可能是本项目的后端）。
$leftover = Get-Process java -ErrorAction SilentlyContinue
if ($leftover) {
    foreach ($j in $leftover) {
        Write-Host ("  清理残留 java 进程 (PID {0})" -f $j.Id) -ForegroundColor DarkGray
        Stop-Process -Id $j.Id -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "`n应用已停止。MySQL(3306) 仍在运行（这是对的，它随开机自启）。`n" -ForegroundColor Cyan
