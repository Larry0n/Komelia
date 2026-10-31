# Bigme Android releases

This fork publishes stable arm64 APKs to [Larry0n/Komelia Releases](https://github.com/Larry0n/Komelia/releases). The first fork-channel version is **1.0.0**, based on upstream **0.20.0**. Install it manually over the earlier Bigme APK once. Older builds still check upstream. Subsequent updates are offered by Komelia when startup update checks are enabled (normally once per 24 hours), or through Settings → Updates. Android asks for installation confirmation.

Updating main does not publish a release. The upstream 0.20.0 source tree contains no GitHub Actions release workflow; any automation outside that tree cannot be inferred from the repository. Publishing this fork is an explicit maintainer action:

1. Update the fork and merge the desired upstream release. If native code or EPUB bundles change, update the pinned official APK URL and SHA-256 in `scripts/Prepare-AndroidRuntime.ps1`.
2. Increment both `bigmeVersion` (three numeric components) and `bigmeVersionCode` (strictly increasing) in `gradle.properties`; commit and push main. The Android package name stays unchanged. The original version in the catalog records the upstream base.
3. On Windows use JDK 17, Android SDK 37 with build tools 36.0.0, and the same private signing keystore as the installed Bigme APK. Keep a private backup outside Git. A new key will not update the installed application. This release deliberately retains the existing development signing identity for compatibility.
4. Run `./scripts/Release-Bigme.ps1` to build and verify locally. To publish, sign in to GitHub CLI with write access to the fork and run `./scripts/Release-Bigme.ps1 -Publish -NotesFile docs/releases/1.0.0.md` (use the corresponding notes file for later versions). The script runs the UI tests, builds a standalone Android release APK, checks the signer, creates a draft at the pushed commit, uploads the APK, and publishes the draft. A partial draft should be inspected before retrying. Never publish a release without its APK.

The tag is exactly the fork version (for example `1.0.0`) and its asset must be named `Komelia-Bigme-1.0.0-arm64-v8a.apk`. The Android updater filters out prereleases and looks for the matching asset. ONNX runtime downloads remain separate.

## Merge notes for upstream 0.20.0

Upstream's MangaBaka migration occupied V13, already applied on Bigme devices for E-Ink settings. The fork retains the original V13–V15 migration files and checksums, and applies the upstream SQL unmodified as V16. Both the new MangaBaka setting and existing delay/count settings are preserved. Existing official 0.20.0 installations are not a supported in-place path; their signing identity and migration history differ. Reader series metadata changes, strings and build dependencies were merged alongside the Bigme controls.
