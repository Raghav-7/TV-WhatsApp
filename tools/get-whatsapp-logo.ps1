# Copies WhatsApp's app icon out of WhatsApp's own app file into both projects (the logo isn't kept in git).
# Usage: .\tools\get-whatsapp-logo.ps1 -Apk C:\path\to\WhatsApp.apk
#   Get the file from https://www.whatsapp.com/android (official) or pull it from the TV:
#   adb shell pm path com.whatsapp   ->   adb pull <path> WhatsApp.apk
param([Parameter(Mandatory)][string]$Apk)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$aapt2 = "$env:TEMP\tvbuild\bt\android-15\aapt2.exe"

# WhatsApp's resource file names are obfuscated, so look up the full-colour launcher icon by its resource name.
$dump = & $aapt2 dump resources $Apk 2>$null
$i = [array]::FindIndex([string[]]$dump, [Predicate[string]] { param($l) $l -match 'mipmap/icon$' })
if ($i -lt 0) { throw "Couldn't find mipmap/icon in $Apk" }
$file = $null
for ($j = $i + 1; $j -lt $i + 8 -and $j -lt $dump.Count; $j++) {
    if ($dump[$j] -match '\((xxxhdpi|xxhdpi)\) \(file\) (res/\S+)') { $file = $Matches[2] }
}
if (-not $file) { throw "Couldn't find a high-resolution icon file" }

$root = Split-Path $PSScriptRoot -Parent
$zip = [System.IO.Compression.ZipFile]::OpenRead($Apk)
try {
    foreach ($project in 'wa-tile', 'wa-shortcut') {
        $dir = "$root\$project\res\drawable-xhdpi"
        New-Item -ItemType Directory -Force $dir | Out-Null
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry($file), "$dir\wa_logo.webp", $true)
    }
} finally {
    $zip.Dispose()
}
"Copied $file to both projects as wa_logo.webp"
