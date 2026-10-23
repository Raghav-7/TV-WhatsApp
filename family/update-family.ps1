# Copies family.txt and the photos in this folder to the TV's WhatsApp Beta screen.
# Needs ADB debugging on the TV. Usage: .\update-family.ps1 -Tv <TV IP address>
param([Parameter(Mandatory)][string]$Tv)
$ErrorActionPreference = 'Stop'
$adb = "$env:TEMP\tvadb\adb.exe"
$dest = "/sdcard/Android/data/com.family.watile/files/family"

& $adb connect "${Tv}:5555" | Out-Null
& $adb -s "${Tv}:5555" shell "mkdir -p $dest"
Get-ChildItem $PSScriptRoot -File | Where-Object { $_.Extension -in '.txt', '.csv', '.jpg', '.jpeg', '.png', '.webp' } | ForEach-Object {
    & $adb -s "${Tv}:5555" push $_.FullName "$dest/$($_.Name)" | Out-Null
    "Copied $($_.Name)"
}
"Done. Reopen WhatsApp Beta on the TV to see the changes."
