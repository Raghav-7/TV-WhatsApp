# Builds wa-tile.apk (the "WhatsApp" home-screen tile for the TV).
# Needs Google's build-tools and platform unpacked under $env:TEMP\tvbuild, and a JDK on PATH.
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$bt   = "$env:TEMP\tvbuild\bt\android-15"
$jar  = "$env:TEMP\tvbuild\pl\android-35\android.jar"
$out  = "$env:TEMP\tvbuild\out"

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\classes", "$out\dex" | Out-Null

& "$bt\aapt2.exe" compile --dir "$here\res" -o "$out\res.zip"
& "$bt\aapt2.exe" link -o "$out\base.apk" -I $jar --manifest "$here\AndroidManifest.xml" "$out\res.zip" --java "$out\gen" `
    --min-sdk-version 23 --target-sdk-version 31 --version-code 11 --version-name 2.6
if ($LASTEXITCODE) { throw "aapt2 link failed" }

& javac --release 8 -nowarn -cp $jar -d "$out\classes" (Get-ChildItem "$here\src", "$out\gen" -Recurse -Filter *.java).FullName
if ($LASTEXITCODE) { throw "javac failed" }
& "$bt\d8.bat" --min-api 23 --lib $jar --output "$out\dex" (Get-ChildItem "$out\classes" -Recurse -Filter *.class).FullName
if ($LASTEXITCODE) { throw "d8 failed" }

Push-Location "$out\dex"; & jar -u -M -f "$out\base.apk" classes.dex; Pop-Location
& "$bt\zipalign.exe" -f -p 4 "$out\base.apk" "$out\aligned.apk"

$ks = "$here\wa-tile.keystore"
if (-not (Test-Path $ks)) {
    $ErrorActionPreference = 'Continue'  # keytool reports progress on stderr
    & keytool -genkeypair -keystore $ks -alias watile -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass watile-local -keypass watile-local -dname "CN=Family TV tile" 2>$null
    $ErrorActionPreference = 'Stop'
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:watile-local --out "$here\wa-tile.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw "apksigner failed" }
"Built $here\wa-tile.apk"
