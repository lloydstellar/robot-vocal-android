# 07 Android architecture (October 2026)

Researched on 2026-10-18. Versions come from the repositories themselves (`maven-metadata.xml` on Google Maven and Maven Central, services.gradle.org, the SDK manifest [repository2-3.xml](https://dl.google.com/android/repository/repository2-3.xml), GitHub releases). Prebuilt AARs were downloaded and measured. "Unverified" means a source was not found; "not researched" means it was out of time. Engines are covered in `01-retro-speech-engines.md`, DSP in `06-robot-voice-dsp.md`, LA2L in `08-la2l-integration.md`.

## 1. Project setup

| Item | Recommendation | Source |
|---|---|---|
| AGP | 9.4.1 (newest preview 9.5.0-alpha10). AGP 9.4 supports API 37, needs Gradle 9.6.0+ and JDK 17+, defaults to build tools 36.0.0 and NDK 28.2.13676358 | [metadata](https://dl.google.com/android/maven2/com/android/tools/build/gradle/maven-metadata.xml), [AGP 9.4 notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes) |
| Gradle | 9.8.1 (2026-10-07) | [services.gradle.org](https://services.gradle.org/versions/current) |
| Kotlin | 2.4.21 through AGP built-in Kotlin plus the Compose compiler plugin; no `kotlin-android` plugin | [KGP metadata](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml), [AGP 9.0 notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) |
| Compose BOM | 2026.09.00 (ui, foundation, runtime 1.12.1; material3 1.4.0) | [BOM POM](https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom) |
| JDK | Temurin 21 runs Gradle; Java target 17 as in Lloyd's other apps; keep `systemProp.java.net.preferIPv4Stack=true` | AGP 9.4 notes |
| SDK levels | compileSdk 37, targetSdk 36, minSdk 30 | below |

Play requires new apps and updates to target API 36 since 2026-08-31, with an extension to 2026-11-01 on request ([target-sdk](https://developer.android.com/google/play/requirements/target-sdk)). Android 17 (API 37) has been stable since 2026-06-16 ([blog](https://developer.android.com/blog/posts/android-17-is-here)), but Play does not require it yet. Targeting 37 would block local-network access without `ACCESS_LOCAL_NETWORK`, which matters if LA2L talks to a computer on the LAN ([local network](https://developer.android.com/privacy-and-security/local-network-permission)). It would also require a while-in-use foreground service for background audio ([bg-audio](https://developer.android.com/about/versions/17/changes/bg-audio)), remove the large-screen orientation opt-out, and require files passed to `System.load()` to be read-only ([behaviour changes 17](https://developer.android.com/about/versions/17/behavior-changes-17)).

minSdk 30 covers everything used here: float `AudioTrack` (API 21), AAudio (API 26) and permission-free MediaStore writes (Android 10). The `mediaProcessing` foreground service type needs API 35, so older devices fall back to `dataSync`, as Spin Doctor already does. AGP 9 sets `targetSdk` to `compileSdk` when it is unset, so set it explicitly.

### Build machine

`~/Android/Sdk` has `platforms;android-37.0` (rev 2), build-tools 36.0.0 and 37.0.0, platform-tools 37.0.1 and cmdline-tools 19.0. It has no NDK and no CMake. Install these (not run as part of this research):

```
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager "ndk;30.0.16248370" "cmake;3.22.1"
```

- **NDK:** r30 (`30.0.16248370`) is the "Latest LTS Version" ([NDK downloads](https://developer.android.com/ndk/downloads); Linux zip 738,633,529 bytes). Pin it with `ndkVersion`.
- **CMake:** 3.22.1 is AGP's default (`CMakeVersion.DEFAULT` in the [AGP source](https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/cxx/configure/CmakeLocator.kt)). The SDK also offers 3.30.x, 3.31.6, 4.0.x and 4.1.2.

### 16 KB page sizes

The [page-sizes guide](https://developer.android.com/guide/practices/page-sizes) says apps targeting 35+ "must support 16 KB memory page sizes on 64-bit devices on Google Play" and "Starting February 1, 2027, if your app updates don't support 16 KB memory page sizes, you won't be able to release these updates". The [original announcement](https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html) set the date at 2025-11-01. Treat 16 KB support as a release blocker.

NDK r28 and later align ELF segments to 16 KB by default, and AGP 8.5.1 and later zip-align uncompressed libraries to 16 KB. Leave `useLegacyPackaging` off. Check every release:

```
~/Android/Sdk/build-tools/37.0.0/zipalign -v -c -P 16 4 app-release.apk   # "Verification successful"
llvm-objdump -p lib/arm64-v8a/libX.so | grep LOAD                         # every LOAD: align 2**14
bundletool dump config --bundle=app-release.aab | grep alignment          # PAGE_ALIGNMENT_16K
```

Measured AARs (arm64-v8a and x86_64):

- **ONNX Runtime 1.31.0:** all LOAD segments align at 2**14. The RELRO end of `libonnxruntime.so` is only 4 KB aligned, but the next writable segment starts on the next 16 KB page. That comes from reading the program headers, not from a runtime test.
- **sherpa-onnx 1.13.8 (shared and static) and LiteRT 2.3.0:** all LOAD segments align at 2**14.

For a runtime test, use a 16 KB emulator image. The Pixel 8 developer option "Boot with 16KB page size" may require OEM unlocking and a data wipe ([AOSP](https://source.android.com/docs/core/architecture/16kb-page-size/16kb-developer-option)), so don't use it on a personal phone. Also scan the C engines for hard-coded `PAGE_SIZE` or 4096.

## 2. Native code (C engines)

- **One module:** an Android library `:engines` holds `src/main/cpp/CMakeLists.txt`, with eSpeak NG and Flite as pinned git submodules plus the own SAM-like synth.
  - Each engine is a `STATIC` target, linked into one `SHARED` `librobotvoice.so`.
  - The JNI glue is written in C, so no C++ runtime is linked.
  - The NDK allows a static C++ runtime only with "one and only one shared library", and that rule includes third-party libraries ([C++ support](https://developer.android.com/ndk/guides/cpp-support)).
  - Oboe would force `-DANDROID_STL=c++_shared` ([Oboe setup](https://developer.android.com/games/sdk/oboe/update-build-settings)).
- **eSpeak NG data:**
  - `espeak_Initialize()` takes "the directory which contains the espeak-ng-data directory" ([speak_lib.h](https://github.com/espeak-ng/espeak-ng/blob/master/src/include/espeak-ng/speak_lib.h)).
  - Compile the data on the build host, ship it in `assets/`, and copy it once to `noBackupFilesDir`.
  - Use `AUDIO_OUTPUT_SYNCHRONOUS` with phoneme events (options bit 0) to get timing.
  - A synth callback that returns 1 aborts synthesis.
- **JNI** ([JNI tips](https://developer.android.com/ndk/guides/jni-tips)):
  - Call `RegisterNatives()` in `JNI_OnLoad`, and export only `JNI_OnLoad` through a version script.
  - Keep calls coarse: one call per syllable or phrase returns samples plus a timing table.
  - Copy with `Get/Set<Type>ArrayRegion`.
  - Never share a `JNIEnv` between threads; return error codes and throw from Kotlin.
- **Threading:** give each engine its own single-thread dispatcher, because engine thread safety was not researched. Call `ensureActive()` between syllables so renders cancel quickly.
- **ABIs and symbols:** in the app module, set `ndk.abiFilters += listOf("arm64-v8a", "x86_64")`. This also strips armeabi-v7a and x86 from the AARs, so 32-bit-only devices are excluded. Set release `ndk.debugSymbolLevel = "SYMBOL_TABLE"` ([native symbols](https://developer.android.com/build/include-native-symbols)).
- **Prefab** (`buildFeatures { prefab = true }`, [docs](https://developer.android.com/build/native-dependencies)) is only needed for Oboe. The ORT AAR ships headers but no prefab package.

| Native library, arm64-v8a (measured) | Uncompressed size |
|---|---|
| ORT 1.31.0 `libonnxruntime.so` | 34.2 MB (gzip 12.9 MB) |
| sherpa-onnx static 1.13.8 `libsherpa-onnx-jni.so` (contains ORT 1.28.2) | 24.2 MB (gzip 9.0 MB) |
| LiteRT 2.3.0 `libLiteRt.so` | 5.6 MB |
| eSpeak NG, Flite, SAM-like and eSpeak data | not researched, measure in the first spike |

## 3. ONNX models on Android

| | sherpa-onnx, static-link AAR | ONNX Runtime Android | LiteRT |
|---|---|---|---|
| Version | 1.13.8 (2026-09-10), [release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8) | 1.31.0 (2026-10-09), [Maven Central](https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/maven-metadata.xml) | 2.3.0, [Google Maven](https://dl.google.com/android/maven2/com/google/ai/edge/litert/litert/maven-metadata.xml) |
| Distribution | GitHub AAR, also JitPack; not on Maven Central | Maven Central | Google Maven |
| Licence | Apache-2.0, but contains eSpeak NG (GPL-3.0-or-later) | MIT | Apache-2.0 |
| TTS | Kotlin API for VITS/Piper, Matcha, Kokoro, Kitten and others | none built in | needs `.tflite` conversion |
| Manifest | nothing added | adds `INTERNET`, `ACCESS_NETWORK_STATE` and a telemetry provider (since 1.29.0) | nothing added |

- **ORT telemetry:** "Telemetry is turned **ON** by default in the official builds", and on Android it keeps platform device IDs ([Privacy.md](https://github.com/microsoft/onnxruntime/blob/main/docs/Privacy.md)). It can be switched off with `ORT_DISABLE_TELEMETRY=1` or by building with `--no_telemetry`. That clashes with Lloyd's no-tracking line, so avoid the official AAR.
- **Execution providers:**
  - NNAPI is deprecated since Android 15 ([migration guide](https://developer.android.com/ndk/guides/neuralnetworks/migration-guide)).
  - XNNPACK must be registered explicitly ([XNNPACK EP](https://onnxruntime.ai/docs/execution-providers/Xnnpack-ExecutionProvider.html)).
  - QNN is Qualcomm-only, so it does not apply to the Pixel 8.
- **ORT threading:** spinning is on by default and "consumes more CPU cycles, resources, and power" ([threading](https://onnxruntime.ai/docs/performance/tune-performance/threading.html)). Use one session per model on one dispatcher.
- **sherpa-onnx conflicts:** the shared variant and the official ORT both ship `libonnxruntime.so`, so pick one. The static AAR has an empty consumer `proguard.txt`, so add a keep rule for `com.k2fsa.sherpa.onnx.**` and test it in a minified build.
- **LiteRT:** convert models with `litert_torch` ([conversion](https://ai.google.dev/edge/litert/conversion/pytorch/overview)). A community Kokoro conversion exists ([litert-community/Kokoro-82M](https://huggingface.co/litert-community/Kokoro-82M), untested). No Piper `.tflite` was found.

**Recommendation:** launch with the C engines. Put neural voices in an optional `:neural` module on the sherpa-onnx static AAR (SHA-256 `b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471`), fetched by a script into a project-local Maven folder. Do this only after the eSpeak NG licence decision (section 13).

## 4. Large assets

| Play limit, compressed ([size limits](https://support.google.com/googleplay/android-developer/answer/9859372)) | Value |
|---|---|
| Base module | 500 MB |
| Each asset pack | 1.5 GB |
| Modules plus install-time packs | 4 GB |
| Fast-follow plus on-demand packs | 30 GB |
| Asset packs per bundle | 100 |
| Legacy APK | 100 MB |

Installs over 200 MB on mobile data show a dialog.

Model archive sizes ([tts-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)):

| Model | fp32 | int8 |
|---|---|---|
| Piper lessac medium | 67.2 MB | 21.0 MB |
| Kokoro en v0_19 | 319.6 MB | 103.2 MB |
| Kokoro multi-lang v1_1 | 364.8 MB | 147.0 MB |
| Kitten nano v0_8 | 63.8 MB | 31.2 MB |

**Recommendation:** keep models out of the base app. Ship each voice as an on-demand Play Asset Delivery pack, using `com.google.android.play:asset-delivery-ktx:2.3.0` and the `com.android.asset-pack` plugin ([PAD](https://developer.android.com/guide/playcore/asset-delivery)). Test locally with `bundletool build-apks --local-testing` (bundletool 1.18.3). Add an HTTPS download path with SHA-256 checks for test APKs shared outside Play; whether universal APKs include asset packs is unverified.

Play forbids downloading executable code "such as dex, JAR, .so files" ([policy](https://support.google.com/googleplay/android-developer/answer/9888379)), so the runtime itself ships in the APK. The `INTERNET` permission is needed for LA2L. The README must drop the "no internet permission" line that the other apps use.

From February 2027, Play also sets memory thresholds: 2 GB foreground on 4 GB devices ([Play Help](https://support.google.com/googleplay/android-developer/answer/17492799)). Android 17 adds per-app memory limits ([blog](https://developer.android.com/blog/posts/preparing-your-app-for-broader-memory-limits)). Measure Kokoro against both.

## 5. Preview playback

Use `AudioTrack` with `ENCODING_PCM_FLOAT`, stereo, at the device rate (`PROPERTY_OUTPUT_SAMPLE_RATE`, normally 44.1 or 48 kHz per the [latency guide](https://developer.android.com/ndk/guides/audio/audio-latency)), in `MODE_STREAM`. A Kotlin mixer thread writes chunks of about 10 ms.

- **Loop:** the loop is computed per frame from the loop length, so it is sample-accurate. Edits swap in immutable buffers between chunks.
- **Why not static mode:** `MODE_STATIC` plus `setLoopPoints()` needs the track stopped to change the loop points ([AudioTrack](https://developer.android.com/reference/android/media/AudioTrack)). Keep it for short note auditions.
- **Drums:** store the example beats as 16-step patterns plus one-shot samples, rendered at the project BPM. That avoids time-stretch libraries: SoundTouch is LGPL-2.1, Rubber Band is GPL or commercial. Drums exist only in `PreviewMixer`, so the exporter cannot include them.
- **Playhead:**
  - `getPlaybackHeadPosition()` is an unsigned 32-bit frame count, reset by `stop()` and `flush()`.
  - Use `getTimestamp()` until it advances, then poll it "approximately once every 10 seconds to once per minute" and extrapolate.
  - The mixer keeps output-frame to project-frame anchors at each loop wrap.
  - `getWrittenFramesCount()` is API 37 only.
- **Focus and lifecycle:**
  - Request focus right before play, stop on loss, and stop in `onStop`.
  - Targeting 35+, only the top app or a foreground service may request focus ([audio focus](https://developer.android.com/media/optimize/audio-focus)). Android 17 enforces this for playback too.
  - Pause on `ACTION_AUDIO_BECOMING_NOISY` ([output](https://developer.android.com/media/platform/output)).
- **No MediaSession:** Media3 suggests just a `Player` when "no external control or background playback is needed" ([media sessions](https://developer.android.com/media/media3/session/control-playback)).
- **Oboe 1.11.0 or AAudio:** only if live low-latency play over the loop is added.

## 6. WAV export

- **Write once, then send out:** render the vocal only into `filesDir/exports/<id>.wav`. From there:
  - **Save:** MediaStore `Music/Robot Vocal`. No permission is needed for the app's own files on Android 10+, using `IS_PENDING` ([media](https://developer.android.com/training/data-storage/shared/media)).
  - **Save as:** `ActivityResultContracts.CreateDocument`, which cannot overwrite ([SAF](https://developer.android.com/training/data-storage/shared/documents-files)).
  - **Share:** FileProvider with `FLAG_GRANT_READ_URI_PERMISSION` and ClipData ([share](https://developer.android.com/develop/ui/compose/sharing/send)).
  - **Send to Live:** LA2L.
- **MIME type:** `audio/x-wav` is the AOSP mapping for `.wav` ([mime.types](https://android.googlesource.com/platform/external/mime-support/+/refs/heads/main/mime.types)).
- **Name clashes:** MediaProvider appends " (1)" and so on, up to 31 times ([FileUtils.java](https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/main/src/com/android/providers/media/util/FileUtils.java)).
- **Reinstall:** after a reinstall the app no longer owns its old exports, so store the export history in the project instead.
- **Format:** 24-bit PCM at 48 kHz by default, with a 32-bit float option. Microsoft says formats above 16 bits need `WAVE_FORMAT_EXTENSIBLE` ([MS](https://learn.microsoft.com/en-us/windows-hardware/drivers/audio/extensible-wave-format-descriptors)).
- **Ableton:** Live loads "samples of any length, sample rate, or bit depth" ([Live 12, 5.1](https://www.ableton.com/en/live-manual/12/managing-files-and-sets/)).
- **Loop length:** Live "assumes that it contains a well-cut musical loop of 1, 2, 4, 8, or 16 bars" ([Live 12, 9.2.4](https://www.ableton.com/en/live-manual/12/audio-clips-tempo-and-warping/)). So export exactly N bars, `frames = round(N * 4 * 60 * rate / bpm)`, with effect tails wrapped into the loop.
- **File names:** `Night Drive 124bpm 4bars.wav`, with characters that file systems reject stripped out.

## 7. Background work

- **Rendering:**
  - Run it in coroutines in the editor ViewModel, collecting with `collectLatest`, and cache results per syllable keyed by a content hash.
  - Compute every frame position from the absolute step index, so timing never drifts.
  - Export takes seconds and needs no foreground service. Batch exports would use `mediaProcessing` (API 35+, falling back to `dataSync`), both limited to 6 h per 24 h ([timeout](https://developer.android.com/develop/background-work/services/fgs/timeout)).
- **LA2L uploads (WorkManager):**
  - Use `androidx.work:work-runtime:2.12.0`; the `-ktx` artifact is empty and `CoroutineWorker` lives in `work-runtime`.
  - Run as unique work with a `CONNECTED` constraint, expedited with `RUN_AS_NON_EXPEDITED_WORK_REQUEST`.
  - Long uploads switch to `setForeground` with type `dataSync` ([long-running](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)).
  - Android 16 applies job quotas even while the app is on top ([FGS changes](https://developer.android.com/develop/background-work/services/fgs/changes)).
  - User-initiated data transfer jobs are not available in WorkManager ([options](https://developer.android.com/develop/background-work/background-tasks/data-transfer-options)).
  - Progress notifications need `POST_NOTIFICATIONS` (API 33+).

## 8. Compose UI

- **Navigation:** follow the pattern of the other apps (enum screens, `AnimatedContent`, `BackHandler`). Navigation 3 1.2.0 and `navigationevent-compose` 1.2.0 are optional. Targeting 36, `onBackPressed` is no longer called and edge-to-edge cannot be opted out of ([16 changes](https://developer.android.com/about/versions/16/behavior-changes-16)), so call `enableEdgeToEdge()` and handle insets for the IME and the keyboard area.
- **Orientation:** do not lock portrait. Targeting 36, screens 600 dp and wider ignore the lock anyway.
- **Step grid:**
  - Draw the grid lines in one Canvas with `drawWithCache` ([docs](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)).
  - Make the syllable chips small composables placed with `Modifier.offset { }`, dragged with `detectDragGesturesAfterLongPress` and snapped on release. Each completed drag is one undo step.
  - Compose drag-and-drop is ClipData transfer, which the docs keep separate from "pick up and move" ([DnD](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/drag-and-drop)).
  - Set `testTagsAsResourceId = true` at the root ([interop](https://developer.android.com/develop/ui/compose/testing/interoperability)).
- **Waveform:**
  - Compute peaks off the main thread, and cache the path and beat lines in `drawWithCache`.
  - Read the playhead only inside a draw lambda, updated from `withFrameNanos` ("defer reads", [best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)).
  - Strong skipping is the default since Kotlin 2.0.20.
- **Lyrics editor:**
  - Use `TextField` with `TextFieldState`, which is the recommended state-based API ([text input](https://developer.android.com/develop/ui/compose/text/user-input)).
  - Highlight the sung syllable with an `OutputTransformation` and `TextFieldBuffer.addStyle`.
  - `TextFieldState.undoState` is still `@ExperimentalFoundationApi` in foundation 1.12.1.
- **Knobs, piano and sliders:**
  - Build knobs and the piano as Canvas plus `pointerInput`. The piano tracks pointer ids for multi-touch.
  - Give knobs semantics with range info.
  - Use Material3 `Slider` for linear values.
- **Screen on:** `Modifier.keepScreenOn()`, which is present in ui 1.12.1 ([reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/keepScreenOn.modifier)). Apply it only while playing.

## 9. Persistence and undo

- **Projects:** one JSON file each (`filesDir/projects/<uuid>/project.json`, kotlinx-serialization 1.11.0), with a `schemaVersion` and written through `AtomicFile`.
- **Settings:** Preferences DataStore 1.2.1. DataStore "is ideal for small datasets and does not support partial updates or referential integrity" ([DataStore](https://developer.android.com/topic/libraries/architecture/datastore)).
- **No database:** Room 2.8.5, or Room 3.0.3 in the new `androidx.room3` package ([Room 3](https://developer.android.com/training/data-storage/room/migration-2-to-3)), is not needed for a few hundred projects.
- **Other storage:** caches go in `cacheDir`, models and eSpeak data in `noBackupFilesDir`.
- **Undo:**
  - Keep the project as immutable data; each action is a `Project -> Project` function.
  - Keep bounded past and future stacks of snapshots, and commit gestures when they end.
  - While the lyrics field has focus, undo goes to `undoState`. When focus leaves, commit the text as one step and clear the field's history.

## 10. Testing

- **JVM** (`:core`, JUnit 4.13.2, coroutines-test 1.11.0):
  - Step-to-frame maths and exact loop lengths.
  - DSP impulse and sine tests with tolerances, plus NaN and clipping checks.
  - WAV header golden bytes.
  - JSON migrations and undo.
- **Device** (androidx.test runner 1.7.0, ext-junit 1.3.0):
  - Engine renders compared by duration and RMS. Exact output across ABIs was not researched.
  - The player: the timestamp advances and `getUnderrunCount()` stays at 0.
  - The full export to MediaStore, as in Spin Doctor's `ExportPipelineTest` and its `perf` build type.
- **Screenshots:**
  - Google's Compose Preview Screenshot Testing is 0.0.1-alpha16, and its standalone plugin is deprecated from AGP 9.5.0-alpha03 in favour of test suites ([docs](https://developer.android.com/studio/preview/compose-screenshot-testing)).
  - Roborazzi 1.76.0 with Robolectric 4.17 is the alternative; its compatibility with AGP 9.4 is not researched.
  - Paparazzi 1.3.5 predates AGP 9.
- **USB phone** (`android` CLI 1.0.16500706; the phone must be awake and past the lock screen):

```bash
./gradlew :app:assembleDebug
android install --device="$SERIAL" --apks=app/build/outputs/apk/debug/app-debug.apk
adb -s "$SERIAL" shell am start -W -n nl.lloydstellar.robotvocal.debug/nl.lloydstellar.robotvocal.MainActivity
android layout --device="$SERIAL" --pretty -o build/smoke/intro.json && grep -q "Robot Vocal" build/smoke/intro.json
android screen capture --device="$SERIAL" -o build/smoke/intro.png
```

`android run --apks=... --device=...` installs and launches in one step. Tap coordinates come from the `center` fields of `android layout`, sent with `adb shell input tap`.

## 11. Modules and packages

```
app/        com.android.application   nl.lloydstellar.robotvocal
            ui.intro ui.projects ui.editor ui.export ui.components (StepGrid, Waveform, Knob, PianoKeyboard)
            playback (PreviewPlayer, PreviewMixer, DrumKit)  export (Exporter, MediaStoreSaver, Share)
            la2l (client wrapper, UploadWorker)  data (ProjectStore, SettingsStore)
core/       kotlin("jvm"), no Android: model, timing, lyrics, render (VoiceEngine, RenderCache), dsp, wav, undo
engines/    com.android.library + CMake: EspeakEngine, FliteEngine, SamEngine, NativeVoice (JNI)
neural/     com.android.library, optional: SherpaEngine, ModelStore, PlayAssetPackSource, HttpsModelSource
voicepacks/ com.android.asset-pack modules, one per voice (on-demand)
tools/      smoke.sh, check-16kb.sh, fetch-sherpa.sh
```

Dependencies point one way: `app` uses `core`, `engines` and `neural`, and both engine modules use `core` only.

## 12. Dependencies (verified 2026-10-18 on Google Maven or Maven Central unless noted)

| Artifact | Version |
|---|---|
| AGP plugins (`application`, `library`, `asset-pack`) | 9.4.1 |
| Kotlin plugins (compose, serialization, jvm) | 2.4.21 |
| `androidx.compose:compose-bom` | 2026.09.00 |
| `androidx.core:core-ktx` / `core-splashscreen` | 1.19.1 / 1.2.0 |
| `androidx.activity:activity-compose` | 1.13.0 |
| `androidx.lifecycle:lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | 2.11.0 |
| `androidx.work:work-runtime` | 2.12.0 |
| `androidx.datastore:datastore-preferences` | 1.2.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` / `kotlinx-serialization-json` | 1.11.0 / 1.11.0 |
| `com.google.android.play:asset-delivery-ktx` | 2.3.0 |
| sherpa-onnx static-link AAR (GitHub release) | 1.13.8 |
| Optional: `navigation3-*` / `navigationevent-compose` / `com.google.oboe:oboe` | 1.2.0 / 1.2.0 / 1.11.0 |
| Not recommended now: `onnxruntime-android` / `litert` / `media3` / `room` | 1.31.0 / 2.3.0 / 1.11.1 / 2.8.5 |
| Tests: `junit` / `kotlinx-coroutines-test` / `androidx.test:runner` / `androidx.test.ext:junit` | 4.13.2 / 1.11.0 / 1.7.0 / 1.3.0 |
| Tests: `robolectric` / `roborazzi` / `benchmark-junit4` / compose screenshot plugin | 4.17 / 1.76.0 / 1.5.0 / 0.0.1-alpha16 |
| Tooling: Gradle / NDK / CMake / bundletool | 9.8.1 / 30.0.16248370 / 3.22.1 / 1.18.3 |
| LA2L Android SDK | see `08-la2l-integration.md` |

## 13. Open questions and risks

1. **eSpeak NG licence:** eSpeak NG is GPL-3.0-or-later, both directly and inside sherpa-onnx ([README](https://github.com/espeak-ng/espeak-ng/blob/master/README.md)). The Apache-2.0 apps need a decision: ship under GPL terms, or leave eSpeak NG out (Flite is BSD-style, [COPYING](https://github.com/festvox/flite/blob/master/COPYING)). This needs legal advice; this document is not legal advice.
2. **SAM licence:** the licence status of existing SAM ports was not researched. Write the SAM-like synth from scratch.
3. **ORT telemetry:** this applies if the official ONNX Runtime AAR is ever added.
4. **16 KB runtime test:** a test on a 16 KB system image is still pending.
5. **Memory:** neural model memory on the Pixel 8 against the Android 17 limits and Play's thresholds from 2027 was not measured.
6. **Not researched:**
   - Universal APKs with asset packs.
   - Screenshot tooling on AGP 9.4.
   - C engine binary and data sizes.
   - Engine thread safety.
   - Whether Live accepts `WAVE_FORMAT_EXTENSIBLE` (test it once in Live).
   - The Android 17 local network rule for LA2L, if it ever uses the LAN.
