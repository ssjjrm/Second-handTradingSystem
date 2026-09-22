# env.ps1 — 环境探测与统一配置（其它脚本都 dot-source 这个文件）
# 用途：把"JDK 在哪、Maven 在哪、MySQL 在哪、密码是什么"这些本机事实集中到一处，
#       避免每个脚本各写一遍路径。
# 用法：. "$PSScriptRoot\env.ps1"

$ErrorActionPreference = 'Stop'

# ---------- 1. 项目根目录 ----------
$Global:PROJ_ROOT = Split-Path -Parent $PSScriptRoot
$Global:ORIGIN_DIR = 'D:\code-javaweb\book'   # 原始参考项目（克隆来的）
$Global:MYSQL_BASE = 'D:\code-javaweb\.mysql-book'  # 本项目专用 MySQL 实例
$Global:MYSQL_BIN = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'

# ---------- 2. 端口约定 ----------
$Global:DB_PORT   = 3307   # 专用实例（避开系统自带的 3306）
$Global:BACKEND_PORT = 8080
$Global:AI_PORT   = 8000
$Global:WEB_PORT  = 5173

# ---------- 3. 数据库账号 ----------
$Global:DB_USER = 'root'
$Global:DB_PASS = '123456'
$Global:DB_NAME = 'book_cycle'

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
# 本机没有把 maven 加进 PATH，但用户目录下已有 maven wrapper 缓存，
# 这里自动找到它，避免要求用户手工配 PATH。
function Find-Maven {
    # 5.1 先看 PATH
    $onPath = Get-Command mvn -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    # 5.2 再看 maven wrapper 缓存目录
    $dists = Join-Path $env:USERPROFILE '.m2\wrapper\dists'
    if (Test-Path $dists) {
        $found = Get-ChildItem $dists -Recurse -Filter 'mvn.cmd' -ErrorAction SilentlyContinue |
                 Sort-Object FullName -Descending | Select-Object -First 1
        if ($found) { return $found.FullName }
    }

    # 5.3 最后看常见安装位置
    foreach ($p in @('C:\Program Files\apache-maven\bin\mvn.cmd',
                     'C:\apache-maven\bin\mvn.cmd')) {
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

# 调用本项目的 mysql 客户端（自动带 --no-defaults，避免读到系统 my.ini）
#
# 两个坑，都在这里一次性处理掉：
# 1) mysql.exe 会把 "Using a password on the command line" 警告写到 stderr，
#    而 $ErrorActionPreference='Stop' 会把原生命令的 stderr 当成终止错误 —— 所以临时降级为 Continue。
# 2) 警告文本混在返回结果里会污染取值（比如 $r[-1] 拿到的是警告而不是行数）—— 所以过滤掉。
function Invoke-Sql {
    param(
        [Parameter(Mandatory)][string]$Sql,
        [string]$Database = $Global:DB_NAME
    )
    $mysqlArgs = @('--no-defaults', '-h127.0.0.1', "-P$($Global:DB_PORT)",
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

Write-Host "[env] JDK    : $Global:JAVA_HOME_RESOLVED" -ForegroundColor DarkGray
Write-Host "[env] Maven  : $Global:MVN" -ForegroundColor DarkGray
