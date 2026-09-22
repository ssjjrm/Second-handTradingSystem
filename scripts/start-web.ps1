# start-web.ps1 — 启动 Vue3 前端（5173）
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\start-web.ps1
# 参数：-Dir 指定 frontend 目录。默认启动【参考项目】；以后我自己的项目传：-Dir .\frontend

param(
    [string]$Dir = ''
)

. "$PSScriptRoot\env.ps1"

if (-not $Dir) { $Dir = Join-Path $REF_DIR 'frontend' }
if (-not (Test-Path $Dir)) { throw "frontend 目录不存在：$Dir" }

if (-not (Test-Path (Join-Path $Dir 'node_modules'))) {
    Write-Host "[web] 未装依赖，先执行 npm install ..." -ForegroundColor Yellow
    Push-Location $Dir
    npm install
    Pop-Location
}

Write-Host "[web] 目录：$Dir" -ForegroundColor Cyan
Write-Host "[web] 打开 http://localhost:$WEB_PORT/" -ForegroundColor Green

Set-Location $Dir
npm run dev
