# env.ps1 — 环境探测与统一配置（其它脚本都 dot-source 这个文件）
# 用途：把"JDK 在哪、Maven 在哪、数据库连哪里"这些本机事实集中到一处，避免每个脚本各写一遍。
# 用法：. "$PSScriptRoot\env.ps1"

$ErrorActionPreference = 'Stop'

# ---------- 1. 目录约定 ----------
$Global:PROJ_ROOT = Split-Path -Parent $PSScriptRoot      # 我的项目（当前工作目录）
$Global:REF_DIR   = 'D:\code-javaweb\book'                 # 参考项目（原始开源项目）
$Global:MYSQL_BIN = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'

# ---------- 2. 端口约定 ----------
# 参考项目用它的默认端口；我的项目会另分配一套（见 PLAN【四】），避免两者冲突
$Global:DB_PORT      = 3306   # 系统自带的 MySQL80 服务，随 Windows 开机自启
$Global:BACKEND_PORT = 8080   # 参考项目后端
$Global:AI_PORT      = 8000   # 参考项目 AI 服务
$Global:WEB_PORT     = 5173   # 参考项目前端

# ---------- 3. 数据库账号 ----------
$Global:DB_USER = 'root'
$Global:DB_PASS = '123456'       # 实测确认：系统 MySQL80 的 root 密码
$Global:DB_NAME = 'book_cycle'   # 参考项目的库（7 张表已导入）

# ---------- 4. JDK 探测 ----------
function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += @('D:\jdk', 'C:\Program Files\Java\jdk-17', 'C:\Program Files\Java\jdk-21')
    foreach ($c in $candidates) {
        if ($c -and (Test-Path (Join-Path $c 'bin\javac.exe'))) { return $c }
    }
    throw "找不到 JDK（需要 javac）。请装 JDK 17+ 或设置 JAVA_HOME。"
}

# ---------- 5. Maven 探测 ----------
# 本机没有把 maven 加进 PATH，但用户目录下已有 maven wrapper 缓存，这里自动找到它。
function Find-Maven {
    $onPath = Get-Command mvn -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $dists = Join-Path $env:USERPROFILE '.m2\wrapper\dists'
    if (Test-Path $dists) {
        $found = Get-ChildItem $dists -Recurse -Filter 'mvn.cmd' -ErrorAction SilentlyContinue |
                 Sort-Object FullName -Descending | Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    foreach ($p in @('C:\Program Files\apache-maven\bin\mvn.cmd', 'C:\apache-maven\bin\mvn.cmd')) {
        if (Test-Path $p) { return $p }
    }
    throw "找不到 Maven。请装 Maven 3.8+ 并加入 PATH。"
}

$Global:JAVA_HOME_RESOLVED = Find-Jdk
$Global:MVN = Find-Maven

# ---------- 6. 常用函数 ----------

# 测试端口是否在监听
function Test-Port([int]$Port) {
    $c = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    return [bool]$c
}

# 调用 mysql 客户端（带 --no-defaults，避免读到系统 my.ini 里意外的配置）
#
# 两个坑一次处理掉：
# 1) mysql.exe 把 "Using a password on the command line" 警告写到 stderr，
#    而 $ErrorActionPreference='Stop' 会把原生命令的 stderr 当成终止错误 —— 临时降级为 Continue。
# 2) 该警告混在结果里会污染取值（$r[-1] 可能拿到警告而非数据）—— 过滤掉。
function Invoke-Sql {
    param(
        [Parameter(Mandatory)][string]$Sql,
        [string]$Database = $Global:DB_NAME,
        [int]$Port = $Global:DB_PORT
    )
    $mysqlArgs = @('--no-defaults', '-h127.0.0.1', "-P$Port",
                   "-u$($Global:DB_USER)", "-p$($Global:DB_PASS)",
                   '--default-character-set=utf8mb4')
    if ($Database) { $mysqlArgs += $Database }
    $mysqlArgs += @('-e', $Sql)

    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & (Join-Path $Global:MYSQL_BIN 'mysql.exe') @mysqlArgs 2>&1 |
            Where-Object { "$_" -notmatch 'Using a password on the command line' }
    } finally {
        $ErrorActionPreference = $prev
    }
}

Write-Host "[env] JDK   : $Global:JAVA_HOME_RESOLVED" -ForegroundColor DarkGray
Write-Host "[env] Maven : $Global:MVN" -ForegroundColor DarkGray
Write-Host "[env] MySQL : 127.0.0.1:$Global:DB_PORT / $Global:DB_USER / $Global:DB_NAME" -ForegroundColor DarkGray
