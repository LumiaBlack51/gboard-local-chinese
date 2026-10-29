param(
    [string]$PrivateRoot = (Join-Path $env:USERPROFILE '.config\gboard-phrase-sync'),
    [string]$Rime = (Join-Path $env:APPDATA 'Rime'),
    [string]$Python = (Get-Command python.exe -ErrorAction Stop).Source,
    [switch]$SetDefault
)
$ErrorActionPreference = 'Stop'
$taskName = 'GboardPhraseSync'
$library = Join-Path $PrivateRoot 'lib'
$null = New-Item -ItemType Directory -Force -Path $library
$currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls $PrivateRoot /inheritance:r /grant:r "*${currentSid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not protect the private configuration directory' }
foreach ($name in @('windows.py', 'common.py', 'rime_data.py', 'windows_setup.pyw')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $library $name) -Force
}
$pythonw = Join-Path (Split-Path $Python) 'pythonw.exe'
if (-not (Test-Path -LiteralPath $pythonw)) { throw 'pythonw.exe is required to avoid console windows' }
if (-not (Test-Path -LiteralPath (Join-Path $PrivateRoot 'config.json'))) { throw 'Private pairing config.json is required' }
$resolvedRime = & $Python -c 'import pathlib,sys; print(pathlib.Path(sys.argv[1]).resolve())' $Rime
if ($LASTEXITCODE -ne 0) { throw 'Could not resolve the native Rime data directory' }
$arguments = '-X utf8 "' + (Join-Path $library 'windows.py') + '" --config "' + (Join-Path $PrivateRoot 'config.json') + '" --rime "' + $resolvedRime + '"'
$action = New-ScheduledTaskAction -Execute $pythonw -Argument $arguments -WorkingDirectory $library
$now = Get-Date
$nextHour = $now.Date.AddHours($now.Hour + 1)
$hourly = New-ScheduledTaskTrigger -Once -At $nextHour -RepetitionInterval (New-TimeSpan -Hours 1)
$logon = New-ScheduledTaskTrigger -AtLogOn -User ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name)
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 1) -MultipleInstances IgnoreNew -Hidden
$principal = New-ScheduledTaskPrincipal -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
$setupName = $taskName + '-Setup'
$setupStatus = Join-Path $PrivateRoot ('native-setup-' + [guid]::NewGuid().ToString('N') + '.json')
$setupArguments = '-X utf8 "' + (Join-Path $library 'windows_setup.pyw') + '" --rime "' + $resolvedRime + '" --status "' + $setupStatus + '"'
if ($SetDefault) { $setupArguments += ' --set-default' }
$setupAction = New-ScheduledTaskAction -Execute $pythonw -Argument $setupArguments -WorkingDirectory $library
$setupSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Minutes 1) -MultipleInstances IgnoreNew -Hidden
Register-ScheduledTask -TaskName $setupName -Action $setupAction -Settings $setupSettings -Principal $principal -Force | Out-Null
try {
    Start-ScheduledTask -TaskName $setupName
    for ($attempt = 0; $attempt -lt 25 -and -not (Test-Path -LiteralPath $setupStatus); $attempt++) { Start-Sleep -Seconds 1 }
    if (-not (Test-Path -LiteralPath $setupStatus)) { throw 'Native profile setup did not finish' }
    $nativeStatus = Get-Content -LiteralPath $setupStatus -Raw | ConvertFrom-Json
    if (-not $nativeStatus.success) { throw ('Native profile setup failed: ' + $nativeStatus.error_type) }
} finally {
    Unregister-ScheduledTask -TaskName $setupName -Confirm:$false -ErrorAction SilentlyContinue
}
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger @($hourly, $logon) -Settings $settings -Principal $principal -Description 'Hourly AC-only phrase/selection aggregate sync; no persistent process or IME restart.' -Force | Out-Null
[xml]$taskXml = Export-ScheduledTask -TaskName $taskName
if ($taskXml.Task.Settings.DisallowStartIfOnBatteries -ne 'true' -or $taskXml.Task.Settings.StopIfGoingOnBatteries -ne 'true') {
    Disable-ScheduledTask -TaskName $taskName | Out-Null
    throw 'Task disabled because AC power restrictions were not preserved'
}
Write-Output 'Installed GboardPhraseSync: hourly and logon, AC-only, 60 second limit, interactive user, hidden pythonw.'
