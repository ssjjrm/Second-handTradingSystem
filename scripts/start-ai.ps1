# start-ai.ps1 — 启动 AI 微服务 FastAPI（8000）
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\start-ai.ps1
# 参数：-Dir 指定 ai-service 目录。默认启动【参考项目】；以后我自己的项目传：-Dir .\ai-service

param(
    [string]$Dir = ''
)

. "$PSScriptRoot\env.ps1"

if (-not $Dir) { $Dir = Join-Path $REF_DIR 'ai-service' }
if (-not (Test-Path $Dir)) { throw "ai-service 目录不存在：$Dir" }

$py = Join-Path $Dir '.venv\Scripts\python.exe'
if (-not (Test-Path $py)) {
    throw "找不到虚拟环境：$py`n请先在 $Dir 下执行：`n  python -m venv .venv`n  .\.venv\Scripts\pip install -r requirements.txt"
}

Write-Host "[ai] 目录：$Dir" -ForegroundColor Cyan
Write-Host "[ai] 等待出现 'Loaded XX books from MySQL' 即成功" -ForegroundColor Yellow
Write-Host "[ai] 未配置 API Key 时降级为纯 BM25 检索，服务照常启动" -ForegroundColor DarkGray

Set-Location $Dir
& $py -m uvicorn main:app --host 0.0.0.0 --port $AI_PORT --reload
