param([Parameter(Mandatory = $true)][string]$Root)

$ErrorActionPreference = 'Stop'
try {
    [xml]$pom = Get-Content -LiteralPath (Join-Path $Root 'backend/pom.xml') -Raw
    $artifactId = [string]$pom.project.artifactId
    $version = [string]$pom.project.version
    if (-not $version) { $version = [string]$pom.project.parent.version }
    $properties = @{}
    if ($pom.project.properties) {
        foreach ($property in $pom.project.properties.ChildNodes) {
            if ($property.NodeType -eq [Xml.XmlNodeType]::Element) {
                $properties[$property.Name] = $property.InnerText
            }
        }
    }
    $properties['project.artifactId'] = $artifactId
    $properties['project.version'] = $version
    $finalName = [string]$pom.project.build.finalName
    if (-not $finalName) { $finalName = "$artifactId-$version" }
    for ($i = 0; $i -lt 10 -and $finalName.Contains('${'); $i++) {
        $finalName = [regex]::Replace($finalName, '\$\{([^}]+)\}', {
            param($match)
            $key = $match.Groups[1].Value
            if (-not $properties.ContainsKey($key)) { throw "Unknown Maven property: $key" }
            [string]$properties[$key]
        })
    }
    if (-not $artifactId -or -not $version -or $finalName.Contains('${') -or $finalName.IndexOfAny([IO.Path]::GetInvalidFileNameChars()) -ge 0) {
        throw 'Cannot resolve a valid backend JAR name from pom.xml'
    }
    Join-Path $Root ("backend/target/$finalName.jar")
}
catch {
    [Console]::Error.WriteLine('[ERROR] ' + $_.Exception.Message)
    exit 1
}
