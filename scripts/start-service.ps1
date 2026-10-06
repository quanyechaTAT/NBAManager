param(
    [Parameter(Mandatory = $true)][ValidateSet('Backend', 'Frontend')][string]$Service,
    [Parameter(Mandatory = $true)][string]$Root,
    [Parameter(Mandatory = $true)][ValidateRange(1, 65535)][int]$Port,
    [ValidatePattern('^[A-Za-z0-9_,.-]+$')][string]$SpringProfile = 'local',
    [string]$Jar,
    [ValidateRange(1, 600)][int]$TimeoutSeconds = 150
)

$ErrorActionPreference = 'Stop'
$logFile = Join-Path $Root ('logs/' + $Service.ToLowerInvariant() + '.log')
$process = $null

function Show-StartupLog {
    if (Test-Path -LiteralPath $logFile) {
        Get-Content -LiteralPath $logFile -Tail 30
    }
}

try {
    if ($Service -eq 'Frontend') {
        $null = Get-Command node.exe -CommandType Application -ErrorAction Stop
        $npm = (Get-Command npm.cmd -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
        $workingDirectory = Join-Path $Root 'frontend'
        $command = 'call "' + $npm + '" run dev -- --host 0.0.0.0 --port ' + $Port + ' --strictPort'
        $url = "http://127.0.0.1:$Port/"
    }
    else {
        $java = (Get-Command java.exe -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
        if (-not $Jar -or -not (Test-Path -LiteralPath $Jar -PathType Leaf)) {
            throw "Backend JAR is missing: $Jar"
        }
        $workingDirectory = Join-Path $Root 'backend'
        $command = '"' + $java + '" -jar "' + $Jar + '" --spring.profiles.active=' + $SpringProfile + ' --server.port=' + $Port
        $url = "http://127.0.0.1:$Port/api/teams"
    }
    $command += ' > "' + $logFile + '" 2>&1'
    # /d bypasses cmd AutoRun; /s preserves quoting around paths with spaces.
    $process = Start-Process -FilePath $env:ComSpec -ArgumentList ('/d /s /c "' + $command + '"') -WorkingDirectory $workingDirectory -WindowStyle Hidden -PassThru
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $nextProgress = 5
    while ($timer.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        $process.Refresh()
        if ($process.HasExited) {
            throw "$Service exited before becoming ready (exit code $($process.ExitCode))."
        }
        $response = $null
        try {
            $request = [Net.HttpWebRequest]::Create($url)
            # Local readiness must not depend on HTTP_PROXY or system proxy settings.
            $request.Proxy = $null
            $request.Timeout = [Math]::Max(1, [Math]::Min(2000, [int](($TimeoutSeconds - $timer.Elapsed.TotalSeconds) * 1000)))
            $response = $request.GetResponse()
            if ([int]$response.StatusCode -eq 200) {
                $process.Refresh()
                if ($process.HasExited) { throw "$Service exited during its readiness check." }
                Write-Host ('   [OK] {0} ready (PID {1}, {2:N1}s)' -f $Service, $process.Id, $timer.Elapsed.TotalSeconds)
                exit 0
            }
        }
        catch [Net.WebException] {
            if ($_.Exception.Response) { $_.Exception.Response.Close() }
        }
        finally {
            if ($response) { $response.Close() }
        }
        if ($timer.Elapsed.TotalSeconds -ge $nextProgress) {
            Write-Host ('   [WAIT] {0}: {1} ({2:N0}s)' -f $Service, $url, $timer.Elapsed.TotalSeconds)
            $nextProgress += 5
        }
        Start-Sleep -Milliseconds 300
    }
    throw "$Service was not ready within ${TimeoutSeconds}s: $url"
}
catch {
    Write-Host ('   [ERROR] ' + $_.Exception.Message)
    Show-StartupLog
    if ($process) {
        $process.Refresh()
        if (-not $process.HasExited) {
            # Stop only the process tree launched by this invocation.
            & taskkill.exe /PID $process.Id /T /F *> $null
        }
    }
    exit 1
}
