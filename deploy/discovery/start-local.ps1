param(
    [ValidateSet('bootstrap','java','api','recommendations','cdc','jobs','embeddings','frontend')][string]$Role,
    [string]$EnvironmentFile = "$PSScriptRoot/.env.local",
    [ValidateRange(1,4)][int]$ApiWorkers = 1
)
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$backendRoot=Join-Path $repoRoot 'backend'
$scriptRoot=Join-Path $backendRoot 'scripts'
$runtimeRoot=Join-Path $scriptRoot 'runtime/discovery'
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
$rolePidFile=Join-Path $runtimeRoot "$Role.pid"
if(Test-Path -LiteralPath $rolePidFile) {
    $existingRolePid=[int](Get-Content -LiteralPath $rolePidFile)
    if(Get-Process -Id $existingRolePid -ErrorAction SilentlyContinue) { throw "$Role already has a running process (PID $existingRolePid). Stop that instance before restarting." }
}
foreach($line in Get-Content -LiteralPath $EnvironmentFile) {
    if($line -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2].Trim('"').Trim("'"),'Process') }
}
# This launcher is intentionally restricted to the isolated development database.
if($env:DISCOVERY_DB_NAME -ne 'nba_discovery_test' -or $env:DISCOVERY_DB_PORT -ne '13306') {
    throw 'Use nba_discovery_test on port 13306 for this isolated launcher.'
}
$env:DISCOVERY_DB_HOST='127.0.0.1'
$env:DISCOVERY_DB_USER='root'
$env:DISCOVERY_REDIS_URL='redis://127.0.0.1:16379/2'
$env:DISCOVERY_OPENSEARCH_URL='http://127.0.0.1:19200'
$env:DISCOVERY_KAFKA='127.0.0.1:19092'
$env:DB_USERNAME='root'
$env:DB_PASSWORD=$env:DISCOVERY_DB_PASSWORD
$env:REDIS_HOST='127.0.0.1'
$env:REDIS_PORT='16379'
$env:DISCOVERY_RUNTIME=$runtimeRoot
$env:JWT_SECRET=$env:DISCOVERY_INTERNAL_TOKEN
$stdout=Join-Path $runtimeRoot "$Role.stdout.log"
$stderr=Join-Path $runtimeRoot "$Role.stderr.log"
if($Role -in @('bootstrap','java')) {
    $classpathFile=Join-Path $runtimeRoot 'classpath.txt'
    if(!(Test-Path -LiteralPath $classpathFile)) { throw 'Run mvnw dependency:build-classpath -Dmdep.outputFile=scripts/runtime/discovery/classpath.txt first.' }
    $classpath=((Join-Path $backendRoot 'target/classes')+';'+(Get-Content -Raw -LiteralPath $classpathFile).Trim()).Replace('\','/')
    $argsFile=Join-Path $runtimeRoot "$Role.args"
    $javaArgs=@('-Xmx768m','-cp',('"'+$classpath+'"'),'com.nbamanager.NbaManagerApplication',
        '--server.port=18080','--server.address=127.0.0.1','--app.startup.enabled=false','--app.scheduling.enabled=false',
        '--spring.datasource.url=jdbc:mysql://127.0.0.1:13306/nba_discovery_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8',
        '--app.discovery.server-url=http://127.0.0.1:19001')
    if($Role -eq 'bootstrap') { $javaArgs+='--spring.flyway.enabled=false' }
    [IO.File]::WriteAllLines($argsFile,$javaArgs,[Text.UTF8Encoding]::new($false))
    $process=Start-Process java -ArgumentList ('@"'+$argsFile+'"') -WorkingDirectory $backendRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
} elseif($Role -eq 'frontend') {
    $env:VITE_BACKEND_URL='http://127.0.0.1:18080'
    $process=Start-Process node -ArgumentList 'node_modules/vite/bin/vite.js --host 127.0.0.1 --port 15173 --strictPort' -WorkingDirectory (Join-Path $repoRoot 'frontend') -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
} else {
    $python=Join-Path $scriptRoot 'venv312/Scripts/python.exe'
    if(!(Test-Path -LiteralPath $python)) { throw 'Create the Python venv312 environment and install requirements-discovery.txt first.' }
    if($Role -eq 'recommendations') { $env:DISCOVERY_EMBEDDING_ENABLED='false' }
    $pythonArgs=if($Role -in @('api','recommendations')) {
        $apiPort=if($Role -eq 'api'){19001}else{19002}
        "-m uvicorn discovery_app.api:app --host 127.0.0.1 --port $apiPort --workers $ApiWorkers"
    } else { '-m discovery_app.worker '+$Role }
    $process=Start-Process $python -ArgumentList $pythonArgs -WorkingDirectory $scriptRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
}
$process.Id | Set-Content -LiteralPath (Join-Path $runtimeRoot "$Role.pid")
Write-Output "$Role started (PID $($process.Id)); logs are in backend/scripts/runtime/discovery."
