param([string]$ApkPath)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$cache = Join-Path $repo 'build/android-runtime'
New-Item -ItemType Directory -Force -Path $cache | Out-Null

# This fork's native bindings and EPUB readers are unchanged since upstream 0.19.0.
# Use that release's runtime only; SQLite comes from the current Gradle dependency.
$url = 'https://github.com/Snd-R/Komelia/releases/download/0.19.0/Komelia-0.19.0-android-universal.apk'
$expectedHash = 'F3FE2B2AD9C53F63A852FC54C9808E93A1F3AFE58320724E387128F3C8D8F4A3'
if (-not $ApkPath) {
    $ApkPath = Join-Path $cache 'Komelia-0.19.0-android-universal.apk'
    if (-not (Test-Path -LiteralPath $ApkPath)) {
        & curl.exe --fail --location --output $ApkPath $url
        if ($LASTEXITCODE -ne 0) { throw 'Could not download the upstream Android runtime.' }
    }
}
if ((Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'Upstream APK checksum mismatch; runtime files were not extracted.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ApkPath).Path)
$count = 0
try {
    foreach ($entry in $zip.Entries) {
        $target = $null
        if ($entry.FullName -match '^lib/(arm64-v8a|armeabi-v7a|x86_64)/(lib[a-zA-Z0-9_.-]+\.so)$') {
            $abi = $Matches[1]
            $lib = $Matches[2]
            # These are supplied by current Maven dependencies, not the old APK.
            if ($lib -in @('libsqlitejdbc.so', 'libandroidx.graphics.path.so', 'libdatastore_shared_counter.so')) { continue }
            $target = Join-Path $repo "komelia-infra/jni/src/androidMain/jniLibs/$abi/$lib"
        } elseif ($entry.FullName -match '^assets/composeResources/io\.github\.snd_r\.komelia\.ui\.komelia_ui\.generated\.resources/files/(komga|ttsu)\.html$') {
            $target = Join-Path $repo "komelia-ui/src/commonMain/composeResources/files/$($Matches[1]).html"
        }
        if ($target) {
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
            $count++
        }
    }
} finally {
    $zip.Dispose()
}
Write-Output "Prepared $count runtime files from upstream Komelia 0.19.0 (SHA-256 verified)."
