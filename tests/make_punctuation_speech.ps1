# Synthetic acoustic playback for the disposable probe, never the release APKs.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Speech
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskOutput=Join-Path $taskRoot 'probe\assets\speech-punctuation.wav'
$taskSynth=New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
  $taskSynth.SelectVoice('Microsoft Huihui Desktop')
  $taskFormat=New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000,[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,[System.Speech.AudioFormat.AudioChannel]::Mono)
  $taskSynth.SetOutputToWaveFile($taskOutput,$taskFormat)
  $taskSynth.Speak('我买了苹果香蕉和橘子然后回家做饭你今天晚上有时间吗')
} finally {$taskSynth.Dispose()}
Write-Output $taskOutput
