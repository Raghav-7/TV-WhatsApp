# Downloads Google's official Android tools used by this project into %TEMP% (short paths on purpose:
# Windows tools fail on very long paths). Needs a JDK (javac, jar, keytool) on PATH.
#   %TEMP%\tvadb\adb.exe                     ADB, to talk to the TV over Wi-Fi
#   %TEMP%\tvbuild\bt\android-15\...         build-tools 35.0.1 (aapt2, d8, zipalign, apksigner)
#   %TEMP%\tvbuild\pl\android-35\android.jar Android platform 35
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$repo = "https://dl.google.com/android/repository"

function Get-Zip($name, $dest) {
    $zip = "$env:TEMP\$name"
    Invoke-WebRequest -Uri "$repo/$name" -OutFile $zip -UseBasicParsing
    Expand-Archive $zip -DestinationPath $dest -Force
    Remove-Item $zip
}

if (-not (Test-Path "$env:TEMP\tvadb\adb.exe")) {
    Get-Zip "platform-tools-latest-windows.zip" "$env:TEMP\tvadb-unpack"
    New-Item -ItemType Directory -Force "$env:TEMP\tvadb" | Out-Null
    Copy-Item "$env:TEMP\tvadb-unpack\platform-tools\*" "$env:TEMP\tvadb" -Recurse -Force
    Remove-Item "$env:TEMP\tvadb-unpack" -Recurse -Force
}
if (-not (Test-Path "$env:TEMP\tvbuild\bt\android-15\aapt2.exe")) {
    Get-Zip "build-tools_r35.0.1_windows.zip" "$env:TEMP\tvbuild\bt"
}
if (-not (Test-Path "$env:TEMP\tvbuild\pl\android-35\android.jar")) {
    Get-Zip "platform-35_r02.zip" "$env:TEMP\tvbuild\pl"
}
"Tools ready."
