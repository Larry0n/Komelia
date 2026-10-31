# Bigme hardware E-Ink refresh

Hardware refresh is opt-in in the image reader's image settings. It runs after a
page change, zoom, pan or fling settles. While the backend is available and
enabled, software flash is ignored; the saved flash settings are retained.
Unsupported platforms use a no-op controller. An incompatible vendor API disables
hardware refresh for the lifetime of the process and restores the software-flash
path if it was enabled.

Firmware mode analysis (HD256, Regal, color presets and sequencing limits) is
documented in [Russian](bigme-display-modes-ru.md).

The reader also exposes a persisted count of 1–5 full refreshes, default 1
(migration V15). Repeats use the same submitted frame and are spaced by at least
1000 ms, or the configured settling delay when it is longer. Any new gesture,
content request, menu or disposal cancels the remainder. These are vendor command
submissions; the firmware exposes no physical completion callback.

Android additionally tracks pointer count in `MainActivity.dispatchTouchEvent`,
before Compose consumes or replaces gesture handlers. Releasing one of two
fingers keeps the refresh blocked. The controller checks the current input state
again immediately before invoking the vendor API. This only gates Komelia's
requests, not independent system refreshes.

Reader keyboard focus is requested on the actual controls node after it is
composed, and when returning from an overlay/window focus change. The requester
precedes `focusable`; the removed outer focus target previously competed with the
node containing the page-key handlers. Paged/panel readers consume D-pad key-down
to avoid focus traversal before their key-up page action.

## Firmware evidence and remaining device validation

The local firmware artifacts used for research were available in the adjacent
`BigmeCoverChanger` project:

* `diagnostics/charging-research/XrzEinkManager.java`, lines 283–285: **instance**
  `public void forceGlobalRefresh(int mode)` delegates to a native method. Both
  the no-argument constructor and a `Context` constructor exist.
* `diagnostics/boot-research/framework/sources/xrz/framework/server/DisplayPolicyController.java`,
  lines 80–82: the system `repaint()` calls `forceGlobalRefresh(0)`.
* `diagnostics/decompiled/settings/sources/com/xrz/settings/dialog/EinkCenterDialog.java`,
  lines 93–106: the settings handler calls `forceGlobalRefresh(132)` for one
  message and sends `com.xrz.GlobalRefresh` for another.
* `diagnostics/device.txt`: the examined device identifies as
  `alps/k65v1_64_bsp`, Android 11. Requiring the manufacturer string `Bigme`
  would reject this firmware.

Further device investigation traced the receiver in `android.app.Activity`,
decompiled from `diagnostics/charging-research/framework.jar`: the receiver for
`com.xrz.GlobalRefresh` constructs `XrzEinkManager` and calls
`forceGlobalRefresh(4)`, logging `wj: full rect`. The device's broadcast history
records these events from `com.xrz.settings` to the foreground Komelia activity.
This is the manual-refresh path on the examined firmware. **Manual refresh (4)**
is now available and is the default for new settings records. Existing saved
choices are retained; **0 (system repaint)** and **132 (GU16)** remain available.
No guessed CLEAN/HD/REGAL flags or persistent global display-policy changes are
used. Matching the call does not itself prove identical physical color quality.

The adapter inspects method modifiers at runtime to support both instance and
static firmware variants, validates the public `(int) -> void` signature, and
catches reflection, linkage and runtime failures. Detection does not invoke a
refresh. Device identity is logged; successful vendor capability discovery is
the decisive support check. There is no bundled vendor JAR, hidden-API bypass,
root command, privileged permission or broadcast dependency.

Open-source research included [inksdk](https://github.com/imedwei/inksdk), whose
Bigme backend targets the separate `com.xrz.HandwrittenClient` stylus pipeline;
its documented interface does not establish the correct S6 full-refresh
argument. No code from that project was copied.

## Rendering and coordination

`EInkRefreshController` is an `expect/actual` platform factory and common interface.
`BigmeEInkApi` owns the process-wide capability probe and circuit breaker;
`BigmeEInkRefreshController` owns the current Android view and frame submission.
JVM and Wasm return `NoOpEInkRefreshController`.

The reader shares one `EInkRefreshCoordinator` and `EInkRefreshScheduler`:

1. Real transform changes, visible layout changes and newly drawn page painters
   invalidate one pending request. Identical redraws do not invalidate it. The
   existing `pageChangeFlow` stays with software flash: it fires before the
   bitmap is loaded, and continuous mode can emit it for composed prefetches.
2. Raw touch events are observed in the Initial pointer pass. A held finger
   blocks refresh even after another gesture detector cancels or consumes events.
3. Animation counters use `try/finally`, including cancellation. Drag release
   cannot refresh while `performFling` or panel scrolling is still running.
4. The reader verifies that image requests describe the current transform or
   visible list layout. The image pipeline verifies that the latest request has
   finished and its painter has been published. All visible images in a spread
   must be ready and actually drawn. Offscreen prefetches are excluded.
5. A 120 ms quiet window coalesces events from existing 50 ms image and 100 ms
   panel/continuous update throttles. It is not used as a rendering fence.
6. Two Compose frame callbacks are awaited. The first callback can run before
   recomposition/layout/drawing of the changed state; the second crosses that
   frame. On hardware-accelerated Android 29+, a
   [frame commit callback](https://developer.android.com/reference/android/view/ViewTreeObserver#registerFrameCommitCallback(java.lang.Runnable))
   then waits for a rendered buffer to be submitted. An unchanged invalidation
   requests that commit without changing page pixels.
7. An additional, cancellable hardware-settling delay (100–1500 ms in 100 ms
   steps, default 500 ms) follows frame submission. On the tested firmware,
   logcat recorded submission at 19:48:51.253 and the previous normal HWC commit
   at 19:48:51.264; the old refresh at 19:48:51.256 arrived before that commit.
   The delay gives the deferred display pipeline time to settle. It is a
   configurable margin, not a hardware-completion fence.
8. The generation and readiness are checked again before invoking the vendor
   API on the main thread. New input, another painter, disposal or disabling the
   feature cancels the pending frame wait and settling delay.

The Android commit callback is a buffer-submission signal, **not** confirmation
that SurfaceFlinger or the E-Ink waveform has finished. On Android 26–28 and
software rendering, submission uses a best-effort additional animation callback.
A one-second timeout only bounds a missing commit callback; it is not a delay
before refresh. Detached, hidden or unfocused views do not refresh.

Animated images are not considered settled. Continuous-layout size animations
are suppressed only when hardware refresh is active, so a spring cannot keep
moving a page after the refresh. Menus block/discard pending refresh, and a tap
with no page/content change does not create a request.

## Settings and compatibility

`hardware_eink_refresh` defaults to false. Migration V13 retains its original
`SYSTEM` mode default; new settings records use `MANUAL`. Existing mode choices
are preserved. V14 adds `hardware_eink_refresh_delay` with default 500 ms.
V1–V13 are unchanged by the delay update. The serializable settings model provides
defaults for existing Web/IndexedDB records. The legacy `flash_on_page_change`,
`flash_duration`, `flash_every_n_pages`, and `flash_with` fields are unchanged.
Hardware mode refreshes after each settled operation; the every-N-pages setting
continues to apply only to software flash.

## Verification on a Bigme S6 Color+

1. Open a static color comic. In image settings, enable hardware refresh and
   select Manual refresh (4). Keep the device's normal per-app color/display
   settings fixed for the comparison.
2. Watch `adb logcat -s KomeliaEInk`. Support detection logs device identity and
   whether the reflected method is static. Each successful invocation logs the
   argument. This confirms invocation, not the physical waveform or image quality.
3. Turn one page, including an uncached page and a two-page spread. Expect one
   refresh after the final image appears, with no software white/black overlay.
4. Pinch, hold the fingers still for a few seconds, then release. Expect no
   refresh while any finger is down and one after the final transformed image.
5. Pan a zoomed page, then perform a long fling. Expect one refresh at the end
   of motion. Interrupt a fling with another touch and hold it: no intermediate
   refresh should occur.
6. Repeat in continuous reading and panels mode. Try rapid page turns, a slow
   download, cancellation/navigation away, and rotating the device while a
   request is pending.
7. Tap the center/open and close settings without changing page content. Expect
   no hardware invocation for the tap/menu itself. Check that leaving the reader
   or switching the setting off cancels queued work.
8. On the same color page compare ghosting, brightness and colors with the
   device's manual Full Refresh. Repeat the same operations with GU16 (132).
   Choose the mode matching the manual result; retain the diagnostic choice if
   results vary between firmware versions.
9. Test a normal Android device and Desktop/Web: hardware support is unavailable
   and existing software-flash behavior is preserved. To retry an API that failed
   in this process, restart the app.

Physical color quality and equivalence to manual Full Refresh require this
device comparison; screenshots and a successful reflective call cannot prove them.

## Automated checks

`komelia-ui/src/commonTest/.../EInkRefreshSchedulerTest.kt` covers request
coalescing, two final frames, held touch plus inertia, taps, slow bitmaps, new
input during frame wait, new painters, readiness changes, menus and disposal.
Additional cases cover post-submission delay, a new page or held touch during
that delay, and readiness changing before the delayed refresh.
`EInkRefreshCoordinatorTest.kt` covers two-page readiness, actual painter drawing,
offscreen prefetch and repeated identical redraws.

Run:

```text
gradlew :komelia-ui:jvmTest :komelia-ui:compileKotlinJvm :komelia-ui:compileKotlinWasmJs :komelia-ui:compileAndroidMain :komelia-infra:database:sqlite:compileKotlinJvm
```

App packaging additionally requires the repository's native JNI and EPUB build
steps documented in the root README.

## Android packaging and the blank startup screen

The first debug APK was incomplete: it contained neither `libsqlitejdbc.so` nor
Komelia's image decoder/ONNX native libraries. On the S6 Color+ (Android 11,
arm64-v8a), ADB reproduced `UnsatisfiedLinkError: dlopen failed: library
"libsqlitejdbc.so" not found` in `KomeliaDatabase` before dependency initialization
could display the login screen. The old crash handler exited while the splash
window was still visible. This happened before any E-Ink controller was used.

SQLite JNI libraries are now generated from the exact JDBC version on the Gradle
classpath and connected to Android's generated JNI sources. `assembleDebug`
therefore includes them without a separate manual extraction task. The old
architecture-specific extraction task names remain as aliases. `preBuild` also
checks the image/ONNX libraries and EPUB readers for every packaged ABI. Startup
initialization failures are displayed in the existing error view without exiting
the process and leaving the splash window behind.

For Windows builds, native runtime files can be prepared with:

```powershell
./scripts/Prepare-AndroidRuntime.ps1
./gradlew.bat :komelia-app:androidApp:assembleDebug "-Pkomelia.android.abis=arm64-v8a"
```

The preparation script downloads the official upstream `0.19.0` APK, verifies
SHA-256 `f3fe2b2ad9c53f63a852fc54c9808e93a1f3afe58320724e387128f3c8d8f4a3`,
and extracts only its native image/ONNX runtime and the two EPUB HTML bundles.
Native bindings and EPUB reader sources are unchanged between upstream `0.19.0`
and this checkout (`3c5f501e`, version 0.19.3); see the
[upstream comparison](https://github.com/Snd-R/Komelia/compare/0.19.0...3c5f501e).
SQLite and AndroidX libraries always come from current Gradle dependencies.
Reassess this runtime pin if native bindings change. Compiling the runtime from
source via the root README remains supported.

The ABI property defaults to `arm64-v8a,armeabi-v7a,x86_64`; the command above
produces a smaller APK specifically for the Bigme's arm64 architecture. The
generated runtime files are ignored by Git and can be recreated by the script.

Device verification on 2026-09-25: the corrected arm64 debug APK was installed
over the previous build using `adb install -r`, without clearing app data.
The S6 Color+ successfully opened both SQLite databases, applied app migrations
through V13, loaded libvips, and displayed `Komga Login`, `Server URL`, `Username`,
`Password`, and `Login` (confirmed by screenshot and UI hierarchy). A negative
Gradle check with the unprepared x86 ABI failed at `verifyAndroidRuntime` as
intended. The APK signature and presence of all 38 arm64 native libraries,
both EPUB bundles, and migration V13 were also verified. Login and startup checks
do not establish the E-Ink waveform quality; that still requires the reader tests
above with an actual book.

## Follow-up reader verification (2026-09-25)

The installed reader had hardware refresh enabled with GU16 (132). A diagnostic
build confirmed successful buffer submission and vendor calls; the previous
absence of debug-level entries was not evidence that refresh was disabled.
Firmware tracing then identified argument 4 for the manual system gesture.

The updated arm64 APK adds Manual refresh (4) and a persistent delay slider in
the same image-settings section: 100–1500 ms, step 100 ms, default 500 ms.
The device accepted an in-place update and opened the existing library and comic,
including migration V14. Selecting 1500 ms through the slider produced a
`Final frame submitted=true` entry at 20:01:43.602 and exactly one
`Hardware refresh submitted: MANUAL (4)` at 20:01:45.105. HWC committed mode 4
at 20:01:45.268, after the earlier page commit at 20:01:43.604.
After force-stop/relaunch, a new process loaded `mode=MANUAL delay=1500ms`,
confirming persistence without reading private application database contents.
The slider's 100 ms endpoint was also selected through accessibility: final frame
at 20:11:44.384, manual refresh at 20:11:44.488 (104 ms). The previous 700 ms
selection was restored after testing. Because accessibility repeatedly lost
reader focus, the final restoration used an on-device, conditional update of
only `hardware_eink_refresh_delay` for the `DEFAULT` row, followed by relaunch;
no database file or credential fields were exported.

All 16 scheduler/coordinator tests passed. The Android APK, JVM UI tests,
Wasm UI compilation and JVM SQLite compilation succeeded. The APK's v2 signature
was verified. Color and ghosting comparisons still require viewing the physical
E-Ink panel; framebuffer captures only verify application pixels.

## Input and repeated-refresh verification (2026-09-26)

All 21 scheduler/coordinator tests passed (18 scheduler, 3 coordinator), including
five new cases for repeat count, spacing, touch cancellation, a new page during
the series and disposal. JVM UI tests, Wasm UI compilation, JVM SQLite compilation
and the arm64 Android debug build succeeded. A synthetic SQLite V15 migration
check preserved existing 700/1500 ms delays, assigned count 1 to both old rows
and accepted count 5 for a book-specific row.

Artifact: `output/Komelia-Bigme-0.19.3-input-refresh.apk`.
SHA-256: `ab4d0ad90dc194b1db386f419dbc42f826954987d8bd67245fed17980cfcbec1`.
The v2 APK signature verifies and uses the same certificate as the previously
installed refresh-delay build. Packaging contains 38 arm64 native libraries,
both EPUB bundles, V15 and the new Android pointer tracker.

The first two ADB connections to `192.168.50.124:42219` timed out. Once the user
provided port 38261, `adb install -r` succeeded without clearing data. A cold
launch opened the existing library and its current comic. The new process loaded
`mode=MANUAL delay=600ms count=1`, and the committed reader frame was followed
by `Hardware refresh submitted: MANUAL (4)` after 609 ms (device timestamps
02:27:06.148 and 02:27:06.757). This verifies startup, the migrated count field
and preservation of the user's current delay. No display policy was changed.

The reader's accessibility hierarchy now shows a focused controls node on entry
without rotation. However, neither shell input nor UiAutomation key injection
produced a confirmed page change, even though injection returned success; shell
taps also failed on the library, where accessibility clicks worked. Physical
D-pad navigation remains unverified. Two-finger/one-finger holds, repeat-count
selection/persistence and physical repeat/colour behavior also remain device
checks; startup and vendor-call logs do not establish those results.
