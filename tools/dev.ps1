[CmdletBinding()]
param(
    [ValidateSet('Check', 'Client', 'GameTest', 'Export')]
    [string]$Task = 'Check',
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$metadataPath = Join-Path $taskRoot '.tools\development.json'
if (!(Test-Path -LiteralPath $metadataPath)) {
    throw 'Run python tools/bootstrap_development.py once to install the project-local Java and Gradle.'
}
$development = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
$gradleExecutable = Join-Path $development.gradleHome 'bin\gradle.bat'
if (!(Test-Path -LiteralPath $gradleExecutable) -or !(Test-Path -LiteralPath (Join-Path $development.javaHome 'bin\java.exe'))) {
    throw 'Development SDK paths are missing. Run python tools/bootstrap_development.py again in this checkout.'
}
$previousJava = $env:JAVA_HOME
$previousGradle = $env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME = $development.javaHome
    $env:GRADLE_USER_HOME = Join-Path $taskRoot '.gradle-user-home'
    $arguments = @(switch ($Task) {
        'Check' { @('compileJava', 'compileClientJava', 'processResources') }
        'Client' { @('runClient') }
        'GameTest' { @('-I', 'local-tests/testing.init.gradle', 'runClientGameTest') }
        'Export' { @('assemble') }
    })
    if ($Task -eq 'GameTest' -and !(Test-Path -LiteralPath (Join-Path $taskRoot 'local-tests/testing.init.gradle'))) {
        throw 'GameTest requires the private local-tests directory. Formal Check, Client and Export do not.'
    }
    $arguments += @('--console=plain', '--no-daemon')
    if ($Offline) { $arguments += '--offline' }
    Push-Location -LiteralPath $taskRoot
    try {
        & $gradleExecutable @arguments
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
        if ($Task -eq 'Export') {
            $versionLine = Get-Content -LiteralPath (Join-Path $taskRoot 'gradle.properties') | Select-String '^mod_version=(.+)$'
            $artifactPath = Join-Path $taskRoot ('build\libs\YumisSkyIsland-' + $versionLine.Matches[0].Groups[1].Value + '.jar')
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            $archive = [System.IO.Compression.ZipFile]::OpenRead($artifactPath)
            $configurationRoot = Join-Path $taskRoot 'config\yumis_sky_island'
            $matched = 0
            try {
                foreach ($configurationFile in Get-ChildItem -LiteralPath $configurationRoot -Recurse -File -Filter '*.toml') {
                    $relative = $configurationFile.FullName.Substring($configurationRoot.Length).TrimStart('\').Replace('\', '/')
                    $entry = $archive.GetEntry('defaults/yumis_sky_island/' + $relative)
                    if ($null -eq $entry) { throw "Missing JAR default: $relative" }
                    $entryStream = $entry.Open()
                    $hasher = [System.Security.Cryptography.SHA256]::Create()
                    try { $entryHash = [BitConverter]::ToString($hasher.ComputeHash($entryStream)).Replace('-', '') }
                    finally { $entryStream.Dispose(); $hasher.Dispose() }
                    if ($entryHash -ne (Get-FileHash -LiteralPath $configurationFile.FullName -Algorithm SHA256).Hash) {
                        throw "JAR configuration differs from the latest editable source: $relative"
                    }
                    $matched++
                }
                foreach ($requiredEntry in @('fabric.mod.json', 'yumis_sky_island.mixins.json', 'yumis_sky_island.client.mixins.json')) {
                    if ($null -eq $archive.GetEntry($requiredEntry)) { throw "Missing JAR entry: $requiredEntry" }
                }
                $expectedConfigurationCount = @(Get-ChildItem -LiteralPath $configurationRoot -Recurse -File -Filter '*.toml').Count
                if ($matched -ne $expectedConfigurationCount) { throw "Expected $expectedConfigurationCount default TOML files; found $matched" }
                foreach ($entry in $archive.Entries) {
                    if ($entry.FullName -match '(^|/)(local-tests|clienttest|gametest|test)(/|$)|fabric\.mod\.json.*test') { throw "Test content in release: $($entry.FullName)" }
                }
            } finally { $archive.Dispose() }
            $configBundle = Join-Path $taskRoot ('build\libs\YumisSkyIsland-' + $versionLine.Matches[0].Groups[1].Value + '-config.zip')
            $bundleStream = [System.IO.File]::Open($configBundle, [System.IO.FileMode]::Create)
            try {
                $bundle = [System.IO.Compression.ZipArchive]::new($bundleStream, [System.IO.Compression.ZipArchiveMode]::Create)
                try {
                    foreach ($configurationFile in Get-ChildItem -LiteralPath $configurationRoot -Recurse -File -Filter '*.toml') {
                        $relative = $configurationFile.FullName.Substring($configurationRoot.Length).TrimStart('\').Replace('\', '/')
                        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($bundle, $configurationFile.FullName, 'config/yumis_sky_island/' + $relative) | Out-Null
                    }
                } finally { $bundle.Dispose() }
            } finally { $bundleStream.Dispose() }
            Write-Output "JAR: $artifactPath"
            Write-Output "Verified $matched default TOML files against current source; tests excluded."
            Write-Output ('SHA256: ' + (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash)
            Write-Output "Configuration ZIP: $configBundle"
            Write-Output ('Configuration SHA256: ' + (Get-FileHash -LiteralPath $configBundle -Algorithm SHA256).Hash)
        }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:GRADLE_USER_HOME = $previousGradle
}
