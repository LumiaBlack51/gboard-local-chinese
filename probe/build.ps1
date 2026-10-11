$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskSdk = $env:ANDROID_SDK_ROOT
if (-not $taskSdk) { $taskSdk = $env:ANDROID_HOME }
if (-not $taskSdk) { throw 'Set ANDROID_SDK_ROOT' }
$taskTools = Join-Path $taskSdk 'build-tools\36.1.0'
$taskAndroid = Join-Path $taskSdk 'platforms\android-36\android.jar'
$taskJava = Join-Path $env:JAVA_HOME 'bin'
$taskOut = Join-Path $taskRoot 'out'
New-Item -ItemType Directory -Force -Path $taskOut,(Join-Path $taskOut 'classes'),(Join-Path $taskOut 'dex') | Out-Null

$taskSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src\local\gboard\probe') -Filter '*.java' | ForEach-Object { $_.FullName })
& (Join-Path $taskJava 'javac.exe') -encoding UTF-8 -source 8 -target 8 -classpath $taskAndroid -d (Join-Path $taskOut 'classes') $taskSources
if ($LASTEXITCODE) { throw 'javac failed' }
& (Join-Path $taskJava 'jar.exe') cf (Join-Path $taskOut 'classes.jar') -C (Join-Path $taskOut 'classes') .
if ($LASTEXITCODE) { throw 'jar failed' }
& (Join-Path $taskTools 'd8.bat') --min-api 26 --lib $taskAndroid --output (Join-Path $taskOut 'dex') (Join-Path $taskOut 'classes.jar')
if ($LASTEXITCODE) { throw 'd8 failed' }
& (Join-Path $taskTools 'aapt.exe') package -f -M (Join-Path $taskRoot 'AndroidManifest.xml') -S (Join-Path $taskRoot 'res') -I $taskAndroid -F (Join-Path $taskOut 'unsigned.apk')
if ($LASTEXITCODE) { throw 'aapt failed' }
Push-Location (Join-Path $taskOut 'dex')
try { & (Join-Path $taskTools 'aapt.exe') add (Join-Path $taskOut 'unsigned.apk') classes.dex; if ($LASTEXITCODE) { throw 'aapt add failed' } }
finally { Pop-Location }
& (Join-Path $taskTools 'zipalign.exe') -f -p 4 (Join-Path $taskOut 'unsigned.apk') (Join-Path $taskOut 'aligned.apk')
if ($LASTEXITCODE) { throw 'zipalign failed' }
$taskKey = $env:GBOARD_KEYSTORE
if (-not $taskKey -or -not $env:GBOARD_KEYSTORE_PASS) { throw 'Set GBOARD_KEYSTORE and GBOARD_KEYSTORE_PASS' }
$taskAlias = $env:GBOARD_KEY_ALIAS
if (-not $taskAlias) { $taskAlias = 'probe' }
& (Join-Path $taskTools 'apksigner.bat') sign --ks $taskKey --ks-key-alias $taskAlias --ks-pass env:GBOARD_KEYSTORE_PASS --key-pass env:GBOARD_KEYSTORE_PASS --out (Join-Path $taskOut 'gboard-local-probe.apk') (Join-Path $taskOut 'aligned.apk')
if ($LASTEXITCODE) { throw 'signing failed' }
& (Join-Path $taskTools 'apksigner.bat') verify (Join-Path $taskOut 'gboard-local-probe.apk')
if ($LASTEXITCODE) { throw 'signature verification failed' }
& (Join-Path $taskTools 'aapt.exe') dump permissions (Join-Path $taskOut 'gboard-local-probe.apk')
if ($LASTEXITCODE) { throw 'permission verification failed' }
Write-Output (Join-Path $taskOut 'gboard-local-probe.apk')
