[CmdletBinding()]
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

function Get-RequiredUserValue([string]$name) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($name, 'User')
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing required user environment variable: $name"
    }
    return $value
}

$releaseKeyStore = Get-RequiredUserValue 'JIQU_RELEASE_KEYSTORE'
$releaseKeyAlias = Get-RequiredUserValue 'JIQU_RELEASE_KEY_ALIAS'
$releaseLineage = Get-RequiredUserValue 'JIQU_RELEASE_LINEAGE'
$releaseStorePassword = Get-RequiredUserValue 'JIQU_RELEASE_STORE_PASSWORD'

if (!(Test-Path -LiteralPath $releaseKeyStore) -or !(Test-Path -LiteralPath $releaseLineage)) {
    throw 'Release signing keystore or certificate lineage is unavailable.'
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkDirectory = (Get-Content (Join-Path $projectRoot 'local.properties') |
    Where-Object { $_ -match '^sdk\.dir=' } |
    Select-Object -First 1).Substring(8).Replace('\:', ':').Replace('\\', '\')
$buildToolsDirectory = Get-ChildItem (Join-Path $sdkDirectory 'build-tools') -Directory |
    Sort-Object Name -Descending |
    Select-Object -First 1 -ExpandProperty FullName
$apkSigner = Join-Path $buildToolsDirectory 'apksigner.bat'
$unsignedApk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release-unsigned.apk'
$signedApk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'

if (!$SkipBuild) {
    & (Join-Path $projectRoot 'gradlew.bat') :app:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$env:JIQU_RELEASE_STORE_PASSWORD = $releaseStorePassword
& $apkSigner sign `
    --min-sdk-version 31 `
    --lineage $releaseLineage `
    --rotation-min-sdk-version 31 `
    --v1-signing-enabled false `
    --v2-signing-enabled false `
    --v3-signing-enabled true `
    --ks $releaseKeyStore `
    --ks-key-alias $releaseKeyAlias `
    --ks-pass env:JIQU_RELEASE_STORE_PASSWORD `
    --key-pass env:JIQU_RELEASE_STORE_PASSWORD `
    --out $signedApk `
    $unsignedApk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $apkSigner verify --verbose --print-certs $signedApk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Get-FileHash $signedApk -Algorithm SHA256 | Format-List
