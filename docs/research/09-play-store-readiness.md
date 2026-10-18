# Play Store readiness (October 2026)

App: Robot Vocal, package `nl.lloydstellar.robotvocal`, minSdk 30, targetSdk 36, compileSdk 37, organisation developer account. Checked on 2026-10-18 against the pages under Sources. Points marked "not researched" were not checked.

## 1. Requirements that affect the build

| Topic | What applies | Source |
|---|---|---|
| Target API | Since August 31, 2026, new apps and updates must target Android 16 (API 36); an extension to November 1, 2026 can be requested. targetSdk 36 meets it. No 2027 deadline is announced yet. | [1] |
| Android 16 behaviour (target 36) | No edge-to-edge opt-out; predictive back on by default and `onBackPressed` no longer called; orientation and resizability limits ignored on displays with a smallest width of 600 dp or more, so the UI must work on tablets and foldables. | [11] |
| 16 KB pages | Apps targeting API 35+ must support 16 KB memory pages on 64-bit devices. Google announced this for new apps and updates from November 1, 2025 [3]; the current page (updated 2026-10-16) says updates without it cannot be released from February 1, 2027 [2]. eSpeak NG, Flite, ONNX Runtime and own C/C++ are native code: use NDK r28+, AGP 8.5.1+ and 16 KB aligned prebuilt `.so` files, checked with `check_elf_alignment.sh` and `zipalign -c -P 16 -v 4`. | [2], [3] |
| 64-bit | Native code must include the 64-bit ABI for every 32-bit ABI shipped. | [4] |
| App bundle | AAB required for new apps since August 2021. Compressed download limits: base module 500 MB, feature module 500 MB, asset pack 1.5 GB, install-time total 4 GB, fast-follow plus on-demand 30 GB. Above 200 MB, users on mobile data see a size dialog. | [5], [6] |
| Neural voice models | Small models fit in the base module. Larger ones can go in AI packs (Play for On-device AI): install-time, fast-follow or on-demand, up to 1.5 GB each, AGP 8.8+, models only. AI packs exist only on Play, so builds from the public repo need their own model download. Model sizes: not researched. | [7] |
| Play App Signing | New apps are enrolled automatically with a Google-generated app signing key. Lloyd keeps the upload key (RSA 2048 or larger); a lost upload key can be reset. | [8] |
| Developer verification | In effect since September 30, 2026 for installs in Brazil, Indonesia, Singapore and Thailand; worldwide in 2027. Creating the app in the Play Console registers the package name. Test APKs handed out outside Play are signed with the upload key: register that key's SHA-256 for the package in the Play Console before the worldwide rollout. | [9], [10] |
| Foreground services | Avoid them. Upload with a plain WorkManager request (network constraint, no `setForeground`, no expedited work); that needs no declaration. For user-requested transfers Google points to user-initiated data transfer jobs (Android 14+, `RUN_USER_INITIATED_JOBS`, only for user-started network transfers) rather than `dataSync`. If an FGS is used anyway (`dataSync` for uploads, `mediaProcessing` for long renders), the Play Console asks per type for a description, the impact if deferred or interrupted, and a video; both types are limited to 6 hours per 24 hours on Android 15+. | [12], [13], [14], [15] |
| Notifications | `POST_NOTIFICATIONS` is a runtime permission on Android 13+, needed only if the app posts notifications. No Play declaration (not researched further). | [16] |

## 2. Policy

### Data safety form

Google's definitions [17]: "collect" means transmitting data off the device, also to the developer's own server. "Sharing" is a transfer to a third party; transfers to a service provider and user-initiated transfers the user expects are exempt. Ephemeral processing (in memory only, for the request only) still goes in the form. Encryption in transit can only be declared if it covers all collected data. Data that never leaves the device is not declared.

What LA2L sends, per its docs [18]: when pairing, the code, `app_name` and an optional `app_id` (install UUID); per upload the bearer `app_token`, a manifest (`source.app`, app version, platform, `created_at`, package `title`, per sound a `name`, colour, BPM) and the WAV files. Files are deleted right after import and at the latest 24 hours after upload, uncommitted uploads after 1 hour; package status is kept 7 days; unpaired apps are removed 30 days after unpairing. The server keeps a keyed hash of the IP address until a package's files are deleted, with the key only in memory. HTTPS only.

| Question | Answer |
|---|---|
| Collects or shares required data types? | Yes, only through LA2L |
| All data encrypted in transit? | Yes |
| Account creation | None. Pairing is not an account, so no account deletion URL [19] |
| Users can request deletion? | Yes: Unpair and delete a waiting package in the app, plus the contact form; files are gone within 24 hours anyway |
| Audio > Other audio files (the WAVs) | Collected, not shared, not ephemeral (kept until import), optional, App functionality |
| Device or other IDs (pairing token, `app_id` if sent) | Collected, not shared, not ephemeral, optional, App functionality. Conservative choice, since the token identifies one install. Leave out `app_id`. |
| Files and docs | Only if users type the take or scene names sent as `title` and `name`; then collected, optional, App functionality. Generated names ("Vocal 1") avoid this entry. |
| Shared | Nothing. Lloyd's server is first party, the host is a service provider, the user's own computer is not a third party. |
| IP address | No entry: the form has no IP type and IPs are not used for location [17]. Mention it in the privacy policy. |
| App name, version, platform; lyrics and projects | No entry: not user data, or never leaves the phone. |

### Privacy policy

Every app needs a privacy policy link in the Play Console and inside the app, on a public, non-geofenced URL (no PDF), clearly labelled. It must name the developer and a privacy contact, the data types, who receives data, how it is protected, and retention and deletion; data travels over modern cryptography such as HTTPS [20]. The Spin Doctor page says "no internet", so Robot Vocal needs its own page with the LA2L facts above. Prominent disclosure is only required where data use may fall outside the user's reasonable expectation [20]; an upload started with a Send button does not, but one sentence on the pairing screen about what is sent and when it is deleted helps.

### AI-Generated Content policy

It covers "content that is created by generative AI models based on user prompts", with chatbots and image or video generators as examples; in-scope apps need in-app reporting of offensive output "without needing to exit the app" [21]. eSpeak NG, Flite and a formant synth are rule-based, not generative models. Neural TTS reading the user's own words is not named, so a reviewer's reading is uncertain. If Kokoro or Piper ship, a small in-app "Report this voice" dialog (sent over HTTPS and declared in Data safety) removes the doubt. Describe the voices as speech synthesis in the listing, not as "AI voices".

### User-generated content

UGC is content "visible to or accessible by at least a subset of the app's users" [22]. Lyrics are never shown to other users, so the UGC rules do not apply.

### Content rating and target audience

The IARC questionnaire is required, misrepresentation can lead to removal, and unrated apps are not allowed [23], [24]. Expected answers: no violence, sexual content, profanity, drugs or gambling in the app's own content (keep built-in example lyrics clean), no user interaction or content exchange, no location sharing, no purchases. Questionnaire categories and any generative AI question: not researched.

Target audience 13 to 15, 16 to 17 and 18+. Under 13 brings in the Families policy, and listings with "youthful animation or young characters in the graphic assets" can be rejected when children are not the audience [25]. Another reason to avoid a cute robot mascot.

### Metadata and trademarks

Title at most 30 characters; no emoji or repeated special characters; ALL CAPS only for a brand; no ranking, price or Play program terms ("#1", "free for limited time", "New") in title, icon or developer name; no repetitive or unrelated keywords; no unattributed testimonials; "Don't use a celebrity's name or a brand's logo without permission" [26].

- "Lloyd's" (own name) and "robot" (generic word) are fine.
- Daft Punk is a band name and Speak & Spell another company's product: leave both out.
- Ableton Live may be named as compatibility ("for use with Ableton Live"), spelled exactly, less prominent than the app name, never part of it, no Ableton logo, no hint of endorsement, plus "Ableton and Live are trademarks of Ableton AG" [27].

### Licence notices

The whole app is GPL-3.0-or-later, with a section 7(e) term declining trademark rights to the name "Robot Vocal", the app icon and the name Lloyd Stellar [28]. Bundled parts keep their own licences: eSpeak NG GPL-3.0-or-later [29]; Flite under the CMU BSD-style licence (keep the notice, mark modifications, do not use the authors' names to promote the app; its COPYING also holds file-specific licences) [30]; Apache-2.0 and MIT libraries such as ONNX Runtime (MIT) [31]; Kokoro weights, if shipped, Apache-2.0 with CC BY credits for part of the training audio [32]; Orbitron and Share Tech Mono under OFL 1.1 [33].

- Show "Appropriate Legal Notices": copyright, no warranty, that the app may be passed on under the GPL, and how to view the licence. A prominent menu item counts (GPLv3 sections 0 and 5(d)) [28].
- Corresponding Source: tag the public repo per release and put "Source code: github.com/lloydstellar/robot-vocal-android" in About and in the Play description, so directions sit next to the download (section 6(d)) [28]. List exact versions of third-party native libraries.
- Reuse the Override pattern: About screen, an OPEN SOURCE LICENCES button, texts in `assets/licenses/` (GPL-3.0, eSpeak NG COPYING, the complete Flite COPYING, MIT and Apache texts, OFL texts, model credits).

## 3. Store listing assets

| Asset | Spec | Source |
|---|---|---|
| App icon | 512 x 512, 32-bit PNG with alpha, sRGB, max 1024 KB, full square. Play adds the mask (corner radius 30% of icon size) and shadow. No badges or ranking text. | [34], [35] |
| Feature graphic | Required. 1024 x 500, JPEG or 24-bit PNG without alpha; main elements near the centre, out of the cut-off zones; little text | [34], [36] |
| Phone screenshots | 2 to 8, JPEG or 24-bit PNG without alpha, 320 to 3840 px, long side at most twice the short side. For promotion and recommendations: at least 4 at 1080 px minimum, 9:16 (1080 x 1920) or 16:9 | [34] |
| Tablet, Chromebook | Optional; at least 4, 1080 to 7680 px, 16:9 or 9:16 | [34] |
| Video | Optional. YouTube URL without playlist or timecode, public or unlisted, ads off, not age-restricted, embeddable; the first 30 seconds autoplay | [34] |
| Alt text | Per graphic, 140 characters or less | [34] |
| Texts | Title 30, short description 80, full description 4000 characters; don't repeat the short description in the full one | [26], [36] |

Google's screenshot rules [34], [36]: UI first in the first three; taglines only when needed and under 20% of the image; no device imagery or fingers; no "Best", "#1", "Top", "New", "Download now", store badges or third-party logos; localise screenshots that carry text. Lloyd's `make_store_graphics.py` layout (a real phone screen under an Orbitron caption, 1080 x 1920, a thin frame instead of a phone) fits; keep captions to two lines.

Order: 1. lyrics on the beat grid with syllables placed, 2. voice and effect choice, 3. export and Send to Live, then BPM with preview beats, takes, About. A listing can't play sound, so a 20 to 30 second screen recording with the app's own audio is the only way people hear the voices before installing.

## 4. App icon

Adaptive icons [37]: foreground and background layers of 108 x 108 dp; logo at least 48 dp and at most 66 dp (safe zone); the outer 18 dp per side is for masks and motion, so 72 dp shows; a monochrome layer for themed icons (Android 13+; Android 16 QPR2 auto-themes icons without one).

Lloyd's method: Spin Doctor's `make_icon.py` writes flat geometry as vector paths (background, foreground, monochrome, 24 dp notification icon) plus `icon.svg`, with the motif 1.3x larger on a full-bleed navy square, rendered by `rsvg-convert`. Override's `make_app_icon.py` draws with Pillow at 16x, crops the middle 72 dp for the store icon and renders a test sheet at 192 to 48 px in circle and squircle masks on dark and light wallpaper. Use the vector method plus that test sheet.

Colours: navy `#05122E`, black `#06080C`, steel `#151B24`, cyan `#4DE0FF`, ice `#CDF4FF`, amber `#FFB000`.

1. **Band face.** Five vertical bars (6 dp wide, 3 dp gaps) with the heights of a spoken word, like a vocoder band display, form a mouth; two amber 7 dp squares above are eyes. Navy, cyan, amber. At 48 dp the bars stay 4 dp wide, and two dots over a row reads as a face.
2. **Grille mouth.** A steel disc (60 dp) with four horizontal slots like an intercom grille; the middle slot is longer and amber, a lit mouth. Navy, steel, amber. Closest to Spin Doctor; one circle and one bright bar survive every mask.
3. **Speech chip.** A 1980s speech chip from above: black body 34 x 46 dp with a pin-1 notch, five steel legs per side (fits the 66 dp circle), an amber three-peak waveform across it, no type number. Navy, black, amber. The chip silhouette is distinct at 48 dp.
4. **Talkbox.** A cyan tube (7 dp) rising from the bottom edge in one S-curve, ending in a round amber mouthpiece with two short ice arcs. Navy, cyan, amber. Weakest at 48 dp: the curve can read as a hook; test it first.

Recommendation: concept 1. It says robot voice and vocoder at once and is built from rectangles.

## 5. Category and discoverability

Category: Music & Audio [38]; up to five tags (tag names: not researched). Similar apps: klattsch, a retro vocal synth (Music & Audio, 1K+ installs, formant speech that can sing), Voloco (Music & Audio, 50M+), VoxTune: Vocal Tuner & Vocoder (50K+), Rapchat (5M+). Voice changers sit in Entertainment. A Play search for "robot voice" returns mostly "AI voice generator" and voice changer titles [39]. Search volumes: not researched.

Drafts:

- Title (27): `Robot Vocal: Text to Speech`. Alternative (29): `Robot Vocal: Speech Synth Rap`.
- Short description (74): `Type lyrics, hear robot vocals locked to your BPM. Save WAVs for your DAW.`
- Full description: first line says what it does; Ableton Live once, as compatibility, with the trademark line; end with no ads, no account, no tracking, the source link and "Made by Lloyd Stellar, an electro producer from Hilversum."

## 6. Release checklist

```
ROBOT VOCAL 1.0.0, everything for the Play Store
Package: nl.lloydstellar.robotvocal

BEFORE UPLOADING
- Release AAB built with NDK r28+ and AGP 8.5.1+; zipalign -c -P 16 -v 4
  and check_elf_alignment.sh show every .so aligned.
- Merged manifest: INTERNET, ACCESS_NETWORK_STATE, no FOREGROUND_SERVICE_*.
- Source tagged v1.0.0 on GitHub; About shows the licence notices and source link.

STEPS IN THE PLAY CONSOLE
1. Put privacy.html online, for example https://lloydstellar.nl/robotvocal-privacy.html
2. Create the app: title from listing.md, default language English (US),
   app (not a game), free. The package name is registered automatically.
3. Internal testing: upload app-release.aab (Play App Signing is on by
   default), add testers, install from Play, test pairing, upload, import in Live.
4. Store listing: texts from listing.md, icon, feature graphic, screenshots
   in order with alt text, optional YouTube video.
   Category Music & Audio, up to five tags, contact email and website.
5. App content:
   - Privacy policy: the link from step 1
   - Ads: no
   - App access: everything works without login; note that Send to Live
     needs the free LA2L Max for Live device and a pairing code
   - Content rating: questionnaire, no to everything
   - Target audience: 13-15, 16-17, 18+, not aimed at children
   - Data safety: as in section 2 of this report
   - Advertising ID: no. News, health, financial, government: no
   - Foreground services: none, if the merged manifest has none
6. Production: promote the tested release, add release notes, send for review.

NOTE
- The upload key stays in signing/, outside the repository. Keep a backup.
- Test APKs outside Play: register the upload key's SHA-256 for the package.
- The 12-tester closed test is for personal accounts; this is an organisation account.
- Every update: raise versionCode, tag the source, check 16 KB again.
```

The 12-tester rule applies to personal accounts created after November 13, 2023 [40]; internal testing allows 100 testers and releases arrive within minutes [41].

## 7. Open points (not researched)

- IARC questionnaire categories, and whether it asks about generative AI.
- Play tag names for music apps; search volumes.
- Sizes of the neural models, so whether AI packs are needed.
- Where lloydstellar.nl is hosted and how long its web server keeps access logs with IP addresses; both belong in the privacy policy.
- Whether a library adds a foreground service (Play Asset Delivery may; the policy exempts `dataSync` used by Play Asset Delivery [15]).
- Android 17 (API 37) changes for the next target bump.
- Exact Play Console menu names in 2026.

## Sources

1. https://support.google.com/googleplay/android-developer/answer/11926878
2. https://developer.android.com/guide/practices/page-sizes
3. https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html
4. https://developer.android.com/google/play/requirements/64-bit
5. https://developer.android.com/guide/app-bundle
6. https://support.google.com/googleplay/android-developer/answer/9859372
7. https://developer.android.com/google/play/on-device-ai
8. https://support.google.com/googleplay/android-developer/answer/9842756
9. https://developer.android.com/developer-verification/guides/google-play-console
10. https://developer.android.com/distribute/play-policies
11. https://developer.android.com/about/versions/16/behavior-changes-16
12. https://support.google.com/googleplay/android-developer/answer/13392821
13. https://developer.android.com/develop/background-work/background-tasks/data-transfer-options
14. https://developer.android.com/develop/background-work/services/fgs/timeout
15. https://support.google.com/googleplay/android-developer/answer/9888379
16. https://developer.android.com/develop/ui/views/notifications/notification-permission
17. https://support.google.com/googleplay/android-developer/answer/10787469
18. https://github.com/lloydstellar/Lloyds-app2live (docs/server-api.md, docs/manifest.md)
19. https://support.google.com/googleplay/android-developer/answer/13327111
20. https://support.google.com/googleplay/android-developer/answer/10144311
21. https://support.google.com/googleplay/android-developer/answer/13985936
22. https://support.google.com/googleplay/android-developer/answer/9876937
23. https://support.google.com/googleplay/android-developer/answer/9859655
24. https://support.google.com/googleplay/android-developer/answer/17134731
25. https://support.google.com/googleplay/android-developer/answer/9867159
26. https://support.google.com/googleplay/android-developer/answer/9898842
27. https://www.ableton.com/en/legal/branding-trademark-guidelines/
28. https://www.gnu.org/licenses/gpl-3.0.html
29. https://github.com/espeak-ng/espeak-ng
30. https://github.com/festvox/flite/blob/master/COPYING
31. https://github.com/microsoft/onnxruntime/blob/main/LICENSE
32. https://huggingface.co/hexgrad/Kokoro-82M
33. https://openfontlicense.org/open-font-license-official-text/
34. https://support.google.com/googleplay/android-developer/answer/9866151
35. https://developer.android.com/distribute/google-play/resources/icon-design-specifications
36. https://support.google.com/googleplay/android-developer/answer/13393723
37. https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
38. https://support.google.com/googleplay/android-developer/answer/9859673
39. https://play.google.com/store/search?q=robot%20voice&c=apps, listings of com.crashunited.klattsch, com.jazarimusic.voloco, de.voxtune, me.rapchat.rapchat
40. https://support.google.com/googleplay/android-developer/answer/14151465
41. https://support.google.com/googleplay/android-developer/answer/9845334
