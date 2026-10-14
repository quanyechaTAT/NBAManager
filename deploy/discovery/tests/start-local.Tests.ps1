param([string]$Launcher=(Join-Path $PSScriptRoot '../start-local.ps1'))
$ErrorActionPreference='Stop'
$environmentNames=@('DISCOVERY_DB_NAME','DISCOVERY_DB_PORT','DISCOVERY_DB_PASSWORD','DISCOVERY_INTERNAL_TOKEN',
    'DISCOVERY_DB_HOST','DISCOVERY_DB_USER','DISCOVERY_REDIS_URL','DISCOVERY_OPENSEARCH_URL','DISCOVERY_KAFKA',
    'DB_USERNAME','DB_PASSWORD','REDIS_HOST','REDIS_PORT','DISCOVERY_RUNTIME','JWT_SECRET','DISCOVERY_EMBEDDING_ENABLED')
$originalEnvironment=@{}
foreach($name in $environmentNames){$originalEnvironment[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$temporaryBase=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
$fixtureRoot=Join-Path $temporaryBase ('discovery-launcher-'+[guid]::NewGuid().ToString('N'))
$fixtureLauncher=Join-Path $fixtureRoot 'deploy/discovery/start-local.ps1'
$capture=[pscustomobject]@{Launches=[Collections.Generic.List[object]]::new();Fail=$false}

# Exercise the real launcher against a temporary layout; no service is started or stopped.
function Start-Process {
    param($FilePath,$ArgumentList,$WorkingDirectory,$WindowStyle,$RedirectStandardOutput,$RedirectStandardError,[switch]$PassThru)
    if($capture.Fail){throw 'Synthetic launch failure'}
    $capture.Launches.Add(@{Arguments=$ArgumentList;Embedding=$env:DISCOVERY_EMBEDDING_ENABLED;WindowStyle=$WindowStyle})
    [pscustomobject]@{Id=123456}
}
function Get-Process {
    [CmdletBinding()]param([int]$Id)
    return $null
}
function Check-Sequence([AllowNull()][string]$Configured) {
    $env:DISCOVERY_EMBEDDING_ENABLED=$Configured
    $capture.Launches.Clear()
    foreach($role in @('recommendations','api','embeddings')) {
        & $fixtureLauncher -Role $role | Out-Null
        if([string]$env:DISCOVERY_EMBEDDING_ENABLED -cne [string]$Configured){throw "Role $role leaked its embedding setting"}
    }
    if($capture.Launches.Count -ne 3 -or $capture.Launches[0].Embedding -cne 'false'){throw 'Recommendation encoder must be disabled'}
    foreach($launch in $capture.Launches[1..2]) {
        if([string]$launch.Embedding -cne [string]$Configured){throw 'Configured encoder setting was not inherited'}
    }
    if($capture.Launches[0].Arguments -notmatch '--port 19002' -or
       $capture.Launches[1].Arguments -notmatch '--port 19001' -or
       $capture.Launches[2].Arguments -notmatch 'worker embeddings'){throw 'Incorrect role routing'}
    if($capture.Launches.Where({$_.WindowStyle -ne 'Hidden'}).Count){throw 'Service windows must remain hidden'}
}

try {
    New-Item -ItemType Directory -Path (Split-Path $fixtureLauncher),
        (Join-Path $fixtureRoot 'backend/scripts/venv312/Scripts') -Force | Out-Null
    Copy-Item -LiteralPath $Launcher -Destination $fixtureLauncher
    New-Item -ItemType File -Path (Join-Path $fixtureRoot 'backend/scripts/venv312/Scripts/python.exe') | Out-Null
    @('DISCOVERY_DB_NAME=nba_discovery_test','DISCOVERY_DB_PORT=13306',
      'DISCOVERY_DB_PASSWORD=fixture-only','DISCOVERY_INTERNAL_TOKEN=fixture-only') |
        Set-Content -LiteralPath (Join-Path (Split-Path $fixtureLauncher) '.env.local')
    Check-Sequence 'true'
    Check-Sequence $null
    Check-Sequence 'false'
    $env:DISCOVERY_EMBEDDING_ENABLED='true'
    $capture.Fail=$true
    try { & $fixtureLauncher -Role recommendations | Out-Null; throw 'Expected launch failure' }
    catch { if($_.Exception.Message -ne 'Synthetic launch failure'){throw} }
    if($env:DISCOVERY_EMBEDDING_ENABLED -cne 'true'){throw 'Failed launch leaked its environment'}
    Write-Output 'PASS: enabled, unset and disabled configurations survive sequential roles; failure also restores the environment.'
} finally {
    foreach($name in $environmentNames){[Environment]::SetEnvironmentVariable($name,$originalEnvironment[$name],'Process')}
    $resolved=[IO.Path]::GetFullPath($fixtureRoot)
    if(!$resolved.StartsWith($temporaryBase+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing cleanup outside temporary directory'
    }
    if(Test-Path -LiteralPath $resolved){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
