param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference='Stop'
adb -s $Serial shell ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME
if($LASTEXITCODE) { throw 'Failed to select original Gboard' }
