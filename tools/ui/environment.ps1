# Dot-source this file: . ./tools/ui/environment.ps1
# Changes this terminal only; existing user/system PATH and SDK settings are preserved.
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$env:MAESTRO_CLI_NO_ANALYTICS = '1'
$env:MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED = 'true'
$toolingRepo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$toolingMaestroBin = Join-Path (Split-Path $toolingRepo -Parent) '.local-tools/maestro-2.11.0/maestro/bin'
if (-not (Test-Path (Join-Path $toolingMaestroBin 'maestro.bat'))) {
    throw 'Expected official Maestro 2.11.0 installation is missing. See docs/ui-tooling-setup.md.'
}
$env:PATH = "$toolingMaestroBin;$env:JAVA_HOME/bin;$env:ANDROID_HOME/platform-tools;$env:PATH"
