param([Parameter(Mandatory=$true)][string]$Serial,[string]$ApkDirectory='.')
$ErrorActionPreference='Stop'
$adb=(Get-Command adb -ErrorAction Stop).Source
function Invoke-Adb([string[]]$Arguments) {
    & $adb -s $Serial @Arguments
    if ($LASTEXITCODE) { throw ('ADB failed: '+($Arguments -join ' ')) }
}
$root=(Resolve-Path -LiteralPath $ApkDirectory).Path
$files=@('base.apk','split_brella_feature_split.apk','split_config.xxxhdpi.apk','split_tenoranimation_feature_split.apk','voice-helper.apk')
foreach($name in $files) { if(-not(Test-Path -LiteralPath (Join-Path $root $name))) { throw ('Missing '+$name) } }
$manifest=Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'release-manifest.json') | ConvertFrom-Json
foreach($entry in $manifest.assets) {
    $hash=(Get-FileHash -LiteralPath (Join-Path $root $entry.name) -Algorithm SHA256).Hash
    if($hash -ne $entry.sha256) { throw ('SHA256 mismatch: '+$entry.name) }
}
Invoke-Adb -Arguments @('install','-r',(Join-Path $root 'voice-helper.apk'))
Invoke-Adb -Arguments @('install-multiple','-r',(Join-Path $root 'base.apk'),(Join-Path $root 'split_brella_feature_split.apk'),(Join-Path $root 'split_config.xxxhdpi.apk'),(Join-Path $root 'split_tenoranimation_feature_split.apk'))
Invoke-Adb -Arguments @('shell','ime','enable','com.localx.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME')
Invoke-Adb -Arguments @('shell','ime','set','com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME')
Invoke-Adb -Arguments @('shell','ime','set','com.localx.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME')
