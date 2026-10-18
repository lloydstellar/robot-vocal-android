# LA2L integration for Robot Vocal

Checked on 2026-10-18 against the LA2L repository `github.com/lloydstellar/Lloyds-app2live` (commit `aefe1f2`), la2l-server 0.12.0, the Android SDK `nl.lloydstellar:la2l` and `la2l-ktx` 0.1.0, and `@lloydstellar/la2l` 0.1.0 on npm. The LA2L device (0.2.2) was studied from its sources; Ableton Live itself was not used. Tests ran on Linux with Node.js 26.8.1 and on a Pixel 8 with Android 17 (API 37) over USB. Points marked "not researched" are open.

## Recommendation

- Use route A: the Android SDK over HTTPS (`la2l-ktx`), with pairing inside Robot Vocal. Route B (a share to LA2L Sender) gives the app no feedback and needs an extra web app.
- Send every render as `Kind.LOOP` with `Role.VOCAL`, the exact render tempo as package `bpm`, and `lengthBeats` = whole bars × 4. Send no `warp_mode`, `color` or `gain_db` by default; Live then warps the vocal in Complex Pro.
- The WAV is exactly `round(sampleRate × 60 × beats / bpm)` frames long, starts on the downbeat of bar 1 and holds only the vocal. The example drum beats never go into a package, neither as a sound nor mixed into the vocal.

## 1. Adding the SDK

```kotlin
// settings.gradle.kts, in dependencyResolutionManagement.repositories
maven("https://lloydstellar.nl/la2l/maven/")

// app/build.gradle.kts
implementation("nl.lloydstellar:la2l-ktx:0.1.0")
```

- Served: `maven-metadata.xml` of both artifacts lists 0.1.0 as latest, release and only version (lastUpdated `20261018114134`). Each version has `.pom`, `.aar`, `.module` and `-sources.jar`, each with an `.asc` signature, plus checksums.
- `la2l-ktx` pulls in `la2l` 0.1.0, `kotlinx-coroutines-core` 1.10.2 and `kotlin-stdlib` 2.1.0 (Kotlin 2.1 or newer).
- minSdk 24, so the app's 30 is fine. The AAR adds only `android.permission.INTERNET` and a non-exported provider `nl.lloydstellar.la2l.La2lFileProvider` (authority `${applicationId}.la2l.files`), used only by `La2lShare`.
- R8/ProGuard: the AAR's `proguard.txt` says `# la2l uses no reflection; nothing to keep.` The app needs no rules. A minified release build: not researched.
- Token: `Storage.privateFile(context)` keeps `token`, `deviceName` and `appId` in `la2l.properties` in `Context.getNoBackupFilesDir()`, written atomically, not encrypted, outside Auto Backup and device transfer. The token goes only in the `Authorization: Bearer` header; on a 401 the SDK deletes it and throws `NOT_PAIRED`. One storage holds one token, so the app is paired with one computer at a time. The constructor reads (and the first time writes) this file: build `La2l` off the main thread. The README's Pairing section mentions SharedPreferences; the code does not use them.
- Signatures, checked with a throwaway GnuPG home:

```bash
curl -sSO https://lloydstellar.nl/la2l/maven/signing-key.asc && gpg --import signing-key.asc
gpg --verify la2l-ktx-0.1.0.aar.asc la2l-ktx-0.1.0.aar   # and the same for .pom, .module, -sources.jar of both
```

  All eight files gave "Good signature" from "Lloyd's App2Live (Android release signing)", fingerprint `D38E 63C1 D254 89FF 5AAB 9442 D117 2ECD 7AAE 8584`, as in `sdk/android/README.md`. The key file equals `sdk/android/signing-key.asc` on GitHub. It is on no public key server, so Gradle dependency verification would need it in `gradle/verification-keyring.keys` (not researched).

## 2. Texts and rules LA2L prescribes

Pairing: one code field with exactly this line above it: "In Live, click Pair app in LA2L and type the code it shows." Hint "K7P 42Q". A code has six characters without I, O, 0 or 1; case, spaces and dashes do not matter; it works once, for 10 minutes. Live then shows "Paired with Robot Vocal". Pair once; ask again only after `NOT_PAIRED`.

Status: "Sending... 45%", "Waiting for Import in Live", "In Live", and for a partial import "In Live: 1 of 2 sounds".

Brand: call it "Send to Live" with the LA2L logo, never the Ableton logo, and nothing that looks like an Ableton feature ("Ableton and Live are trademarks of Ableton AG, and LA2L is not made by Ableton."). Suggested wording: "Sends your sounds to Ableton Live with LA2L."

Errors: a `La2lException` has a `code`, a short English `message` to show as it is, and `retryAfter`.

| Code | Action |
|---|---|
| `NOT_PAIRED` | Show the code field again (the user unpaired in Live; the token is already gone). Keep the render. |
| `BAD_CODE` | Show the message ("This code does not work. Check the code in Live.") and ask again. |
| `RATE_LIMITED` | Show the message and block Send for `retryAfter` seconds (up to an hour for the package limit). No retry loop. |
| `SERVER_FULL` | Show the message, often "Click Import in Live first". |
| `BAD_WAV`, `BAD_PACKAGE` | Show the message; it names the sound. For Robot Vocal this is a render bug. |
| `NETWORK`, `SERVER` | Try again later. |
| `IMPORT_FAILED` | Show the message; do not send again. The package stays and the next Import in Live retries it. |
| `EXPIRED` | Not imported within 24 hours: offer to send again. |
| `ABORTED` | The app cancelled; show nothing. |

Token and privacy: never put the token in a URL, log, analytics event or crash report; the app's public repository gets no tokens or real logs. LA2L's server keeps the files until Import (at most 24 hours), then the title, sound names and result for 7 days; database backups (no audio) 30 days; a call log with IP addresses. Robot Vocal's privacy text should say that Send to Live uploads the vocal, title and names to lloydstellar.nl and link https://lloydstellar.nl/la2l/developers/privacy. Data safety answers: not researched.

Naming and tone: Live adds the app name to every track ("Treinbeat (Robot Vocal)"), so sound names never contain it. Names are cut at 40 characters (title at 60) without a warning; only letters, digits, space and `_ - ( ) . ' & + # !` survive, anything else becomes a space: "Lloyd’s" with a typographic apostrophe becomes "Lloyd s", and "127,5 BPM" becomes "127 5 BPM". Suggested app name: "Robot Vocal". LA2L's own texts are plain English, short, without em dashes.

## 3. Route A or route B

LA2L Sender is a web app (PWA) at https://lloydstellar.nl/la2l/, not a store app. Installed from Chrome (any Android phone) or Samsung Internet (Samsung) via "Add to home screen" and "Install", and opened once, it is a share target for audio, WAV and ZIP files. It has its own pairing with Live and shows the sounds before the user taps Send. Firefox and iOS are not supported.

| | A: SDK over HTTPS | B: `La2lShare` to LA2L Sender |
|---|---|---|
| User setup | Type a code once | Install the PWA, pair it |
| Taps per send | 1 | 3 or more |
| Feedback in the app | Progress, waiting, "In Live", per-sound result | None |
| Track names | "(Robot Vocal)" | "(LA2L Sender)": `La2lShare.text()` 0.1.0 writes no `source.app` |
| Permission | INTERNET | Still INTERNET when the SDK is used |

Route B also passes URIs only in `EXTRA_STREAM` without `ClipData`; Android 17's notes say Android 18 stops granting such URIs automatically. Whether `La2lShare` is affected: not researched.

## 4. Metadata for robot vocals

| Field | Default | User choice |
|---|---|---|
| `kind` | `loop` | "one-shot" for a word that should not follow the tempo (then send no `bpm`) |
| `role` | `vocal` | none |
| `warp_mode` | not sent (Complex Pro) | advanced: Beats, Tones, Re-Pitch, Complex |
| `bpm` | exact render tempo, on the package | the app's BPM, best equal to the Live Set |
| `length_beats` | whole bars × 4 | number of bars |
| `name` / `title` | song name, cleaned, max 40 / 60 | editable |
| `color`, `gain_db` | not sent (one colour per package) | optional fixed colour |
| format | 48 kHz, 32-bit float, mono dry vocal | 16-bit for smaller uploads |

Warp mode: LA2L sets the tempo with two warp markers (sample 0 at beat 0, and one near the end at the declared tempo), so syllable onsets stay on the grid in every mode. The tightest result is no stretching: let the app BPM match the Set, or use LA2L's "Use N BPM" button, which only appears for a package-level `bpm`. When tempos differ, Beats should keep staccato attacks tightest, Complex Pro (LA2L's choice for vocals) stays smoother on held vowels, Tones is Ableton's suggestion for pitched vocals, and Re-Pitch shifts the pitch (120 to 124 BPM is +0.57 semitones). Which sounds best: not researched. Test by ear with one package holding the same render four times, each with another `warpMode`.

Length: LA2L loops from beat 0 to `length_beats`; audio past it is not heard. Without `length_beats`, the length is seconds × bpm / 60, rounded only within 0.1 beat. So: 8 beats at 120 BPM and 48 kHz is 192,000 frames; 16 beats at 123 BPM is 374,634 frames. Place each syllable at `round(step × framesPerStep)` with a `Double` step; adding up rounded steps drifts (7 ms after 64 bars at 123 BPM). Extend tails to the next whole bar, or fold them into the start for a repeating loop.

Packages: one tap is one package. Default one sound. Stems (dry plus FX) as two sounds give two aligned tracks. Takes become tracks that all play at once. Sections must not be separate sounds: every sound starts at 1.1.1 in the Arrangement. Limits: 32 sounds, 200 MB (209,715,200 bytes), 30 packages an hour per app (every attempt counts), 600 calls a minute; upload within an hour; a package waits 24 hours. Size: 11 MB per minute for mono float at 48 kHz, 22 MB for stereo float.

## 5. Testing without Ableton

Commands as run (pairing codes masked; `LA2L_CONFIG_DIR` kept the CLI's token file in a temporary folder):

```bash
timeout 900 npx --yes @lloydstellar/la2l@0.1.0 test-receiver --local --port 41234 > receiver.log 2>&1 &
npx --yes @lloydstellar/la2l@0.1.0 pair "XXX XXX" --server http://127.0.0.1:41234/api --app-name "Robot Vocal"
npx --yes @lloydstellar/la2l@0.1.0 send robot-2bars-120.wav --loop --bpm 120 --role vocal \
  --title "Robot test" --name "Robot hallo" --wait --server http://127.0.0.1:41234/api --app-name "Robot Vocal"
npx --yes @lloydstellar/la2l@0.1.0 unpair --server http://127.0.0.1:41234/api --app-name "Robot Vocal"
kill -INT <pid on port 41234>
```

`robot-2bars-120.wav`: 2 bars at 120 BPM, 48 kHz, mono 16-bit, 192,000 frames, made by a small Node script with built-ins only. The CLI answered "In Live: 1 of 1." Receiver report:

```text
Robot test (Robot Vocal): imported 1 of 1 sound
  Robot hallo (Robot Vocal): loop, warped in Complex Pro mode at 120 BPM, 8 beats. 48 kHz, 16-bit, mono, 4 s
Tail test (Robot Vocal): imported 1 of 1 sound
  Robot tail (Robot Vocal): loop, warped in Complex Pro mode at 120 BPM, 9.5 beats. 48 kHz, 16-bit, mono, 4.75 s
Robot test stems (Robot Vocal): imported 2 of 2 sounds
  Robot dry (Robot Vocal): loop, warped in Beats mode at 120 BPM, 8 beats. 48 kHz, 16-bit, mono, 4 s
  Robot FX (Robot Vocal): loop, warped in Complex Pro mode at 120 BPM, 8 beats. 48 kHz, 32-bit float, stereo, 4 s
```

The tail test (0.75 s of echo added) shows the length mistake. The stems package, sent with `curl` over the REST API, confirmed that `warp_mode: "beats"` overrides the role. A one-shot with a BPM got the warning "tempo.bpm only applies to loops, and this package has none". A 44-character name was cut to 40 without a warning. After restarting the receiver, the old token got 401 "Unknown token. Pair this app again.", an easy test for `NOT_PAIRED`. The receiver was stopped afterwards; nothing was left on port 41234.

Phone over USB: the receiver listens on 127.0.0.1 only, and the SDK allows plain http only to localhost, 127.0.0.1 and ::1. Verified on the Pixel 8:

```bash
adb reverse tcp:41234 tcp:41234
adb shell '(printf "GET /api/health HTTP/1.0\r\n\r\n"; sleep 2) | nc 127.0.0.1 41234'   # {"ok":true,"name":"la2l test receiver",...}
adb reverse --remove tcp:41234
```

For the app, in debug builds only (the app itself was not tested; not researched):

- `src/debug/res/xml/network_security_config.xml` with `<domain-config cleartextTrafficPermitted="true">` for `127.0.0.1` and `localhost`, and a main version without it; the manifest references `@xml/network_security_config`.
- `buildConfigField("String", "LA2L_SERVER", ...)`: empty in `defaultConfig` (the real server), and in `debug` from a Gradle property such as `la2lServer=http://127.0.0.1:41234/api`. Use `La2l(La2l.Options("Robot Vocal").server(BuildConfig.LA2L_SERVER).storage(...))` when it is set.
- A separate storage for test servers (for example a `Storage` that prefixes keys), so testing does not replace the real pairing.

Without a local server, https://lloydstellar.nl/la2l/receiver does the same in a browser against the real server; pairing with it replaces the app's pairing with Live.

## 6. Pitfalls

- A loop that is not a whole number of beats: tails, count-ins, effect latency, rounding.
- Sending the BPM field instead of the tempo the file was rendered at.
- The drum preview leaking into the vocal render or the package.
- 24-bit: LA2L accepts it, but the SDK's `Wav` writes only 16-bit PCM and 32-bit float. Refused: compressed WAV, RF64, 12-bit, more than two channels.
- Frame math must use the file's own sample rate (8 to 192 kHz accepted).
- `Wav.fromFloat` without a `File` builds the whole WAV in memory.
- Automatic resends: 30 packages an hour, and every send makes new tracks.
- `awaitImport()` polls every 3 seconds for up to 24 hours; tie it to the screen and keep the package id.
- Every send lands at 1.1.1 in the Arrangement, on new tracks.
- The LA2L device has not been tested on a Mac.

## 7. Open points (not researched)

- App name: "Robot Vocal" or "Lloyd's Robot Vocal" (straight apostrophe).
- The app's language, and whether to translate LA2L's English messages.
- The current WAV export: sample rate, bit depth, channels, tails, count-in.
- INTERNET permission, privacy policy and Play Data safety today.
- Whether the app is public, and where its users would get the LA2L device.
- Lloyd's Live setup (Windows or Mac, Live 12.4 or newer).
- The default warp mode, after an A/B test in Live.
- Background uploads for long stereo renders (WorkManager, or a user-initiated data transfer job).
- Whether route B is wanted; if so, `source.app` and `ClipData` fixes in `La2lShare`.
