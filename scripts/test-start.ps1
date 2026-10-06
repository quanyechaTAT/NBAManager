param(
    [string]$Launcher = (Join-Path (Split-Path $PSScriptRoot -Parent) 'start.bat')
)

$ErrorActionPreference = 'Stop'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('nba-start-test-' + [guid]::NewGuid().ToString('N'))
$fixture = Join-Path $testRoot 'project with spaces'
$savedPath = $env:PATH
$savedFrontendPort = $env:NBA_FRONTEND_PORT
$savedBackendPort = $env:NBA_BACKEND_PORT
$failures = 0

function Write-BatchFile([string]$Path, [string]$Content) {
    [IO.File]::WriteAllText($Path, ($Content -replace '\r?\n', "`r`n"), [Text.Encoding]::ASCII)
}

function Run-Launcher([string]$Action, [int]$TimeoutSeconds = 10) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $env:ComSpec
    $info.Arguments = '/d /s /c "call "' + (Join-Path $fixture 'start.bat') + '" ' + $Action + '"'
    $info.WorkingDirectory = $fixture
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($info)
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $completed = $process.WaitForExit($TimeoutSeconds * 1000)
    if (-not $completed) {
        & taskkill.exe /PID $process.Id /T /F *> $null
        $process.WaitForExit()
    }
    [PSCustomObject]@{
        Completed = $completed
        ExitCode = $process.ExitCode
        Elapsed = $timer.Elapsed.TotalSeconds
        Output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
    }
}

function Assert-Case([string]$Name, [bool]$Condition, [string]$Detail) {
    if ($Condition) { Write-Host "PASS $Name" }
    else {
        Write-Host "FAIL $Name : $Detail"
        $script:failures++
    }
}

function Get-FreePort {
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    $port
}

try {
    foreach ($relative in @('backend/target', 'frontend/node_modules/.bin', 'logs', 'bin', 'scripts')) {
        New-Item -ItemType Directory -Path (Join-Path $fixture $relative) -Force | Out-Null
    }
    Copy-Item -LiteralPath $Launcher -Destination (Join-Path $fixture 'start.bat')
    $sourceRoot = Split-Path $Launcher -Parent
    foreach ($helper in @('start-service.ps1', 'resolve-backend-jar.ps1')) {
        $source = Join-Path $sourceRoot ('scripts/' + $helper)
        if (Test-Path -LiteralPath $source) {
            Copy-Item -LiteralPath $source -Destination (Join-Path $fixture ('scripts/' + $helper))
        }
    }
    [IO.File]::WriteAllText((Join-Path $fixture 'backend/pom.xml'), '<project><artifactId>nba-manager-backend</artifactId><version>4.0.0</version></project>')
    [IO.File]::WriteAllText((Join-Path $fixture 'backend/target/nba-manager-backend-4.0.0.jar'), 'fixture')
    [IO.File]::WriteAllText((Join-Path $fixture 'frontend/node_modules/.bin/vite.cmd'), 'fixture')
    Write-BatchFile (Join-Path $fixture 'backend/mvnw.cmd') @'
@echo off
echo build>>"%~dp0build-count.txt"
exit /b 37
'@
    Write-BatchFile (Join-Path $fixture 'bin/netstat.cmd') @'
@echo off
echo   TCP    127.0.0.1:3306    0.0.0.0:0    LISTENING    100
echo   TCP    127.0.0.1:6379    0.0.0.0:0    LISTENING    101
'@
    Write-BatchFile (Join-Path $fixture 'bin/npm.cmd') @'
@echo off
echo fixture-vite-startup-failed
exit /b 37
'@
    $env:PATH = (Join-Path $fixture 'bin') + ';' + $savedPath
    $env:NBA_BACKEND_PORT = [string](Get-FreePort)
    $env:NBA_FRONTEND_PORT = [string](Get-FreePort)

    $backend = Run-Launcher 'backend'
    Assert-Case 'reuse packaged 4.0.0 JAR without Maven' (-not (Test-Path -LiteralPath (Join-Path $fixture 'backend/build-count.txt'))) 'Maven ran even though the current JAR exists'
    Assert-Case 'launch the JAR named by pom.xml' ($backend.Completed -and $backend.Output.Contains('nba-manager-backend-4.0.0.jar')) 'the selected current JAR was not passed to startup'

    $frontend = Run-Launcher 'frontend' 10
    Assert-Case 'report exited frontend promptly' ($frontend.Completed -and $frontend.ExitCode -ne 0 -and $frontend.Elapsed -lt 8) ('completed={0}, exit={1}, elapsed={2:N2}s' -f $frontend.Completed, $frontend.ExitCode, $frontend.Elapsed)
    Assert-Case 'show frontend startup error' ($frontend.Output.Contains('fixture-vite-startup-failed')) ('startup error was hidden while waiting for HTTP: ' + $frontend.Output)

    $resolver = Join-Path $fixture 'scripts/resolve-backend-jar.ps1'
    if (Test-Path -LiteralPath $resolver) {
        [IO.File]::WriteAllText((Join-Path $fixture 'backend/pom.xml'), '<project><artifactId>nba-manager-backend</artifactId><version>5.0.0</version></project>')
        $resolvedJar = & $resolver -Root $fixture
        Assert-Case 'follow future POM versions' ($resolvedJar.EndsWith('nba-manager-backend-5.0.0.jar')) $resolvedJar
        [IO.File]::WriteAllText((Join-Path $fixture 'backend/pom.xml'), '<project><artifactId>nba-manager-backend</artifactId><version>${revision}</version><properties><revision>5.1.0</revision></properties><build><finalName>${project.artifactId}-${project.version}-app</finalName></build></project>')
        $resolvedJar = & $resolver -Root $fixture
        Assert-Case 'respect Maven finalName and properties' ($resolvedJar.EndsWith('nba-manager-backend-5.1.0-app.jar')) $resolvedJar
        [IO.File]::WriteAllText((Join-Path $fixture 'backend/pom.xml'), '<project><artifactId>nba-manager-backend</artifactId><version>4.0.0</version></project>')
    }

    Write-BatchFile (Join-Path $fixture 'backend/mvnw.cmd') @'
@echo off
echo build>>"%~dp0build-count.txt"
echo fixture>"%~dp0target\nba-manager-backend-4.0.0.jar"
exit /b 0
'@
    Remove-Item -LiteralPath (Join-Path $fixture 'backend/target/nba-manager-backend-4.0.0.jar')
    $missingJar = Run-Launcher 'backend'
    $buildCountFile = Join-Path $fixture 'backend/build-count.txt'
    $buildCount = if (Test-Path -LiteralPath $buildCountFile) { @(Get-Content -LiteralPath $buildCountFile).Count } else { 0 }
    Assert-Case 'build a missing JAR once and resolve the result' ($missingJar.Completed -and $buildCount -eq 1 -and $missingJar.Output.Contains('nba-manager-backend-4.0.0.jar')) ('build count: ' + $buildCount)
}
finally {
    $env:PATH = $savedPath
    $env:NBA_FRONTEND_PORT = $savedFrontendPort
    $env:NBA_BACKEND_PORT = $savedBackendPort
    $resolvedTestRoot = [IO.Path]::GetFullPath($testRoot)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    if (-not $resolvedTestRoot.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing cleanup outside the test temporary directory'
    }
    Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures -gt 0) { exit 1 }
