param(
    [switch]$Publish,
    [string]$Gh = 'gh',
    [string]$NotesFile,
    [string]$Keystore
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
function Check($step) { if ($LASTEXITCODE -ne 0) { throw "$step failed ($LASTEXITCODE)" } }
if (!$env:ANDROID_HOME) { $env:ANDROID_HOME = "$PWD\.android-sdk" }
if (!$env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME = "$PWD\.android-local" }
if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = "$PWD\.gradle-local" }
if (!$Keystore) { $Keystore = Join-Path $env:ANDROID_USER_HOME 'debug.keystore' }
if (!(Test-Path -LiteralPath $Keystore)) { throw 'Existing Bigme signing key required. Do not generate a new key.' }
$version = (Get-Content gradle.properties -Raw | ConvertFrom-StringData).bigmeVersion
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid bigmeVersion' }
if ($Publish) {
    if (!$NotesFile -or !(Test-Path -LiteralPath $NotesFile)) { throw 'Supply -NotesFile when publishing' }
    $revision = git rev-parse HEAD
    $remoteMain = & $Gh api repos/Larry0n/Komelia/git/ref/heads/main --jq '.object.sha'
    Check 'GitHub main lookup'
    if ($revision -ne $remoteMain) { throw 'Push the release commit to fork main first' }
    $changed = git diff HEAD --numstat --ignore-cr-at-eol --ignore-submodules=all
    Check 'Source check'
    if ($changed -or (git ls-files --others --exclude-standard)) { throw 'Commit source changes before publishing' }
}
& "$PSScriptRoot/Prepare-AndroidRuntime.ps1"
$env:CI='true'
$bt = Join-Path $env:ANDROID_HOME 'build-tools/36.0.0'
& ./gradlew.bat :komelia-ui:jvmTest :komelia-app:androidApp:assembleRelease '-Pkomelia.android.abis=arm64-v8a' "-Pandroid.aapt2FromMavenOverride=$bt/aapt2.exe" '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8' --max-workers=2 --no-configuration-cache --console=plain
Check 'Gradle'
$apk = "output/Komelia-Bigme-$version-arm64-v8a.apk"
New-Item -ItemType Directory -Force output | Out-Null
$unsigned = 'komelia-app/androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk'
& "$bt/apksigner.bat" sign --ks $Keystore --ks-key-alias androiddebugkey --ks-pass pass:android --out $apk $unsigned
Check 'APK signing'
$verification = & "$bt/apksigner.bat" verify --print-certs $apk
Check 'Signature verification'
if (!($verification -match 'certificate SHA-256 digest: 3b063cb7409f821cb0d49f0a5ae750f5959a512e6e77728e99b226c9f76e83f4')) { throw 'Signing identity differs from installed Bigme APK' }
Write-Output (Get-FileHash $apk)
if ($Publish) {
    & $Gh release create $version $apk --repo Larry0n/Komelia --target $revision --title "Komelia Bigme $version" --notes-file $NotesFile --draft
    Check 'Draft release creation and APK upload'
    & $Gh release edit $version --repo Larry0n/Komelia --draft=false --latest
    Check 'Release publication'
}
