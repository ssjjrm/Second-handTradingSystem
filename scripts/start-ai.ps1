# start-ai.ps1 — 启动 AI 微服务 FastAPI（8000）
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\start-ai.ps1
# 参数：-Dir 指定 ai-service 目录（默认原始参考项目）

param(
    [string]$Dir = "$ORIGIN_DIR\ai-service"
)

. "$PSScriptRoot\env.ps1"

$py = Join-Path $Dir '.venv\Scripts\python.exe'
if (-not (Test-Path $py)) {
    throw "找不到虚拟环境：$py`n请先执行：cd $Dir; python -m venv .venv; .\.venv\Scripts\pip install -r requirements.txt"
}

Write-Host "[ai] 目录：$Dir" -ForegroundColor Cyan
Write-Host "[ai] 等待出现 'Loaded XX books from MySQL' 即成功" -ForegroundColor Yellow
Write-Host "[ai] 无 API Key 时会降级为纯 BM25 检索模式，服务照常启动" -ForegroundColor DarkGray

Set-Location $Dir
& $py -m uvicorn main:app --host 0.0.0.0 --port $AI_PORT --reload
