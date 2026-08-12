<#
.SYNOPSIS
    Build JRXML Compiler as a dependency-separated distribution and zip it.

.DESCRIPTION
    Output layout (dist/jrxml-compiler-<version>/):
      lib/         project jar + all third-party dependency jars
      config/      external config files (override jar-internal resources at runtime)
      log/         log output directory (empty placeholder, written at runtime)
      jrxml-compiler.jar   project main jar (thin jar, classes only)
      start.bat    Windows launcher
    Finally compress the whole directory into dist/jrxml-compiler-<version>.zip.
#>

$ErrorActionPreference = 'Stop'

# ---------- 1. Locate JDK / Maven ----------
if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    $candidateJdk = 'D:\APP\jdk_1.8'
    if (Test-Path $candidateJdk) { $env:JAVA_HOME = $candidateJdk }
}
Write-Host "[1/7] JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Cyan

$mvn = $null
if (Test-Path .\mvnw.cmd) { $mvn = '.\mvnw.cmd' }
elseif (Get-Command mvn -ErrorAction SilentlyContinue) { $mvn = 'mvn' }
else {
    $guess = @(
        'D:\APP\apache-maven-3.3.9\bin\mvn.cmd'
        "$env:USERPROFILE\apache-maven-3.3.9\bin\mvn.cmd"
        'C:\apache-maven-3.3.9\bin\mvn.cmd'
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($guess) { $mvn = $guess }
}
if (-not $mvn) { throw 'Maven not found (mvnw / PATH / common paths all missed). Install Maven or set PATH.' }
Write-Host "[1/7] Maven   = $mvn" -ForegroundColor Cyan

# ---------- 2. Read version from pom ----------
[xml]$pom = Get-Content pom.xml
$version = $pom.project.version
if (-not $version) { $version = '6.20.0' }
$artifact = $pom.project.artifactId
$mainJar  = "$artifact.jar"
$distName = "$artifact-$version"
$distRoot = "dist\$distName"
Write-Host "[2/7] version = $version  dist = $distRoot" -ForegroundColor Cyan

# ---------- 3. Maven build (skip shade -> thin jar) ----------
Write-Host '[3/7] compile and build thin jar (mvn clean package -Dshade.skip=true -DskipTests)...' -ForegroundColor Cyan
& $mvn clean package '-Dshade.skip=true' '-DskipTests'
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }

$builtJar = "target\$artifact-$version.jar"
if (-not (Test-Path $builtJar)) { throw "Missing build artifact: $builtJar" }

# ---------- 4. Assemble distribution directory ----------
Write-Host '[4/7] assemble distribution directory...' -ForegroundColor Cyan
if (Test-Path $distRoot) { Remove-Item -Recurse -Force $distRoot }
New-Item -ItemType Directory -Force -Path "$distRoot\lib"    | Out-Null
New-Item -ItemType Directory -Force -Path "$distRoot\config" | Out-Null
New-Item -ItemType Directory -Force -Path "$distRoot\log"    | Out-Null

# 4.1 third-party runtime dependencies -> lib/
& $mvn dependency:copy-dependencies '-DincludeScope=runtime' "-DoutputDirectory=$distRoot\lib"
if ($LASTEXITCODE -ne 0) { throw 'Copy dependencies failed.' }

# 4.2 project main jar -> both lib/ and root
Copy-Item $builtJar "$distRoot\lib\$mainJar" -Force
Copy-Item $builtJar "$distRoot\$mainJar"      -Force

# 4.3 config files -> config/ (exclude web frontend resources, kept inside jar)
$resDir = 'src\main\resources'
if (Test-Path $resDir) {
    $resRoot = (Resolve-Path $resDir).Path
    Get-ChildItem $resDir -Recurse | Where-Object {
        -not $_.PSIsContainer -and $_.FullName -notmatch '\\web(\\|$)'
    } | ForEach-Object {
        $rel  = $_.FullName.Substring($resRoot.Length + 1)
        $dest = Join-Path "$distRoot\config" $rel
        New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
        Copy-Item $_.FullName $dest -Force
    }
}

# ---------- 5. Generate start.bat ----------
Write-Host '[5/7] generate start.bat...' -ForegroundColor Cyan
$startBat = @"
@echo off
REM JRXML Compiler launcher (dependency-separated edition)
REM config/ takes priority over jar-internal resources; lib/* are runtime dependencies.
setlocal
if not defined JAVA_HOME set "JAVA_HOME=$env:JAVA_HOME"
if not exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_HOME="
    for %%i in (java) do set "JAVA_BIN=%%~`$PATH:i"
) else (
    set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
)

set "APP_HOME=%~dp0"
set "MAIN_JAR=%APP_HOME%$mainJar"
set "CLASSPATH=%APP_HOME%config;%APP_HOME%lib\*"

"%JAVA_BIN%" -cp "%CLASSPATH%" com.example.jasper.JrxmlCompiler %*
endlocal
"@
Set-Content -Path "$distRoot\start.bat" -Value $startBat -Encoding ASCII

# 5.1 double-click launcher for Web UI (no args needed)
$startWeb = @"
@echo off
REM Double-click to launch the Web UI (dependency-separated edition).
REM Equivalent to: start.bat --server 8080 .
setlocal
call "%~dp0start.bat" --server 8080 .
endlocal
"@
Set-Content -Path "$distRoot\start-web.bat" -Value $startWeb -Encoding ASCII

# ---------- 6. Generate README.txt ----------
Write-Host '[6/7] generate README.txt...' -ForegroundColor Cyan
$readme = @"
JRXML Compiler $version  (dependency-separated distribution)
================================================

Layout
  lib/      project jar and all third-party dependency jars
  config/   external config (overrides jar-internal resources at runtime)
  log/      log output directory (written at runtime)
  $mainJar   project main jar (thin jar)
  start.bat launcher
  start-web.bat double-click launcher for the Web UI

Usage
  start-web.bat                      start Web UI (port 8080, double-click)
  start.bat input.jrxml              compile JRXML -> JASPER
  start.bat --preview in.jasper      export JASPER -> PDF
  start.bat --server 8080 .          web server on port 8080, work dir .

Notes
  - Dependencies load from lib/*; upgrade by replacing jars in lib/.
  - Files in config/ override same-named resources inside the jar.
"@
Set-Content -Path "$distRoot\README.txt" -Value $readme -Encoding ASCII

# ---------- 7. Compress to zip ----------
Write-Host '[7/7] compress to zip...' -ForegroundColor Cyan
$zip = "dist\$distName.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path $distRoot -DestinationPath $zip -Force

Write-Host ''
Write-Host "Done! Distribution: $zip" -ForegroundColor Green
Write-Host "Unpacked at: $distRoot" -ForegroundColor Green
Get-ChildItem $distRoot | ForEach-Object { Write-Host "  $($_.Name)" }
