# Robot Vocal: build plan

Plan of 18 October 2026. The research behind it is in [docs/research](research/):
engines (01), rap flow (03), beat alignment (04), singing and rap synthesis (05),
robot effects (06), Android (07), LA2L (08), Google Play (09), the home computer
voice (10), the speech chip voice (11) and clear robot voices (12). Report 02
covers neural voices, which this app leaves out.

## Status, 18 October 2026

Steps 1 to 5, 7 and 9 are built and on GitHub; steps 6 and 8 are partly done.

- Built: the front panel look, lyrics to syllables, eleven flows with the placement algorithm, sample-exact timing with the vowel on the beat, 49 voices (five formant characters, five home computer voices, eighteen retro voices ported from whackintalk, Flite kal, SPELLER on an emulated TMS5100, nineteen eSpeak NG voices), 21 robot effects plus movement and space effects (delays on the beat, echo throws, plates, ping-pong, a wide chorus, auto-pan, tape stops, reverse swells), a note and a stutter per syllable, example beats, WAV export in mono or stereo, Send to Live with LA2L, About, Manual, icon, store graphics and a signed release build.
- Changed on the way: the effects are written in Kotlin instead of C++ (offline rendering is fast enough, and every effect is tested on the JVM). The retro voices keep whackintalk's synthesizer, phoneme table and voice settings, under names of our own; tempo and pitch come from the song, so its speaking rates and intonation are left out.
- Version 1.1.0, 20 October 2026: clearer words (report 12). Every voice goes through a vocal chain (high-pass, presence shelf, a fast compressor gated by the phonemes, a de-esser by phoneme); the effects get every voice at one level and compress it in two stages; vocoders and talkboxes switch to noise for unvoiced phonemes; the crushers count their steps from the loudest moment.
- Still open: the ClusterGen voices of Flite (slt, rms, awb need a patch for exact durations), the other candidates of section 3.2 and a chord track.

## 1. What the app does

You type lyrics, pick a tempo and a voice, and Robot Vocal raps them as a robot:
every syllable starts exactly on a step of the beat grid. You check the timing
against an example beat, move syllables around in a step grid like on a drum
machine, and save the vocal as a WAV file or send it straight into Ableton Live
with LA2L. The example beats are for listening only and never end up in a file.

## 2. Decisions so far

| Topic | Decision |
|---|---|
| Name | App label "Robot Vocal"; the intro screen says "Lloyd's Robot Vocal" |
| Package | `nl.lloydstellar.robotvocal`, debug builds `.debug` with the label "Robot Vocal DEBUG" |
| Licence | GPL-3.0-or-later for the whole app, plus a section 7(e) term: no trademark rights to the name "Robot Vocal", the app icon or the name Lloyd Stellar. Generated audio is not covered by the GPL |
| Voices | Only engines and voice data whose terms allow commercial use of the output. Nothing with non-commercial, research-only or missing terms |
| Neural voices | None. Classic and retro speech engines only |
| Language | English lyrics only |
| Look | Its own look, based on vocal hardware of around 1980 (section 14), not the LLOYDOS terminal look |
| Repo | Public, `lloydstellar/robot-vocal-android` |
| Store | Ready for Google Play: listing, icon, screenshots, privacy policy, Data safety |

## 3. Voices

A voice is an engine plus a voice setting (speaker, pitch, speed of articulation,
character). A robot sound is a voice plus an effect chain (section 7). The app
ships named combinations, and the user can combine any voice with any effect.

### 3.1 Engines in version 1

| Engine | What you hear | Timing on the grid | Licence |
|---|---|---|---|
| Flite, voices kal and kal16 | The classic 1990s diphone robot, 8 and 16 kHz | Exact: our own durations and pitch go in through `duration_model_func` and `f0_model_func` (tested in report 01) | BSD-style, voices free for commercial use |
| Flite, CMU ARCTIC voices slt, rms, awb, bdl, clb, jmk, ksp | Seven buzzy statistical voices, male and female, some accented | Exact, same hooks | BSD-style |
| eSpeak NG, a curated set of its 104 variants (robosoft 1 to 8, klatt 1 to 6, whisper, croak and others) | Sharp formant voices, very robotic, very clear | The engine reports phoneme onsets but takes no durations, so each word is rendered and then placed and stretched (section 6.4) | GPL-3.0-or-later |
| Our own formant synth | A Klatt synthesizer with our own phoneme tables, in several characters: clean unit, coarse 1970s allophone steps, sung monotone in the spirit of Kraftwerk's Robovox | Exact: we compute every frame | Ours (GPL); based on klatt-syn (MIT) and the Holmes tables of rsynth (GPL) |
| Home computer voices (our own) | Five voices made the way the software speech of 1982 was made: three oscillators at the formants (two sines and a square), restarted on every pulse, 4-bit levels and output, 1-bit hiss. Mouth and throat knobs as on those programs | Exact: the retro engine's track with our start and end per phoneme; the step clock is set per note, so periods are whole steps and notes stay in tune | Ours. The method follows the public description of S.A.M. (report 10); its tables come from the retro engine, not from S.A.M. |
| whackintalk, ported to Kotlin | Eighteen voices after the talking computers of the mid-1980s: one Klatt synthesizer with the KLGLOTT88 source, from a flat machine to a whisper, seven of them singing a tune | Exact: every phoneme becomes elements with our start and end, with a new frame every millisecond | MIT |
| Speech chip voice (SPELLER) | The sound of the 1978 Speak & Spell: Flite's kal encoded into frames for the TMS5100 (TMC0281) and spoken by a port of MAME's emulation of it, with the chip's tables, integer lattice, chirp, interpolation rules and 8-bit output | Exact: a frame starts at every phoneme and lasts at most 25 ms; the chip's clock is set per note so notes are in tune (report 11) | MAME (BSD-3), our own encoder, kal (BSD-style). No Texas Instruments word data |

That gives 49 base voices before effects.

### 3.2 Candidates for later versions

- rsynth (GPL): the 1990s Unix `say` voice; takes exact durations.
- MBROLA with the en1 voice: the en1 terms say "for any purpose", the engine is AGPL-3.0, which GPLv3 allows. Read the en1 licence text in full before it ships.
- hts_engine with the ARCTIC slt voice: smooth 48 kHz vocoder voice, exact timing; the voice is CC BY 3.0, so it needs a credit in the app.
- Gnuspeech (GPL): a tube model voice, heavier to port.

### 3.3 Left out, and why

| Left out | Reason |
|---|---|
| S.A.M. (sam-js, the C port and every "SAM mode" in other software) | No licence, and SoftVoice, Inc. never released it; the "SAM" modes of other synths carry its tables. The home computer voices follow its publicly described method with tables of our own (report 10) |
| DECtalk | Proprietary licence |
| Original chip ROM data (Votrax, SP0256, TI word lists) | Copyrighted data |
| SVOX Pico | Output length differs per run on 64-bit; DMCA notice on parts of the code |
| MBROLA us1, us2, us3 | No-sale clause |
| Festival, Mimic 1, MaryTTS | Too heavy for a phone, some voices with unclear terms |
| Piper, Kokoro and other neural voices | Lloyd's choice: robot engines only |
| The phone's own system voices | No terms found that cover commercial use of the audio |

## 4. From text to syllables

1. Normalise: numbers to words, symbols (`&`, `%`, `$`), common abbreviations; every line of the lyrics is one phrase; commas, full stops and line ends become candidate rests.
2. Look up every word in CMUdict (BSD-style licence, about 135,000 words with stress marks, packed into a compact binary asset by a script in `tools/`). Report 03 found CMUdict covers 98% of words in rap lyrics.
3. Words that are missing go through Flite's letter-to-sound rules, which return phonemes with stress.
4. Split the phonemes into syllables with the maximal onset rule and a list of legal English onsets. Each syllable keeps its onset consonants, vowel, coda consonants, stress (0, 1, 2), word and position in the word.
5. The user can fix a word by typing a respelling in brackets, for example `[ro-BOT]`, which sets syllables and stress.

All engines get the same phonemes, so a syllable is the same syllable for every voice. Flite takes them directly; eSpeak NG gets them through its phoneme input (`[[ ]]`) with a mapping table from ARPAbet to eSpeak phonemes; the formant and LPC voices work in ARPAbet.

## 5. Flow: putting syllables in slots

Time is counted in ticks, 480 per quarter note, so straight 16ths (120 ticks) and triplets (160 or 80 ticks) both fit.

**Flow presets** (report 03, section 5). The pattern letters: `X` stressed syllable preferred, `x` allowed, `R` rhyme target, `.` rest or hold, `p` pickup.

| Preset | Pattern per bar | Syllables per bar | BPM | Echoes |
|---|---|---|---|---|
| Robot 8ths | `X.x.X.x.X.x.R.x.` | 4 to 8 | 110 to 130 | "The Robots", "Pocket Calculator" |
| Word per beat | `X.x.X.x.X.x.R.x.`, each word on a beat | 4 to 8 | 118 to 130 | "Numbers", "Technologic" |
| Call and response | A `X.x.X.x.X.x.R...`, B `....X.x.X.x.R...` | 4 to 7 | 115 to 130 | "Planet Rock" |
| Electro 3+3+2 | `X.xX.xX.X.xX.xR.` | 6 to 10 | 105 to 130 | Egyptian Lover, Cybotron |
| Old-school couplet | `X.x.X.x.X.xxR...` | 6 to 9 | 95 to 115 | Kurtis Blow |
| Boom bap laid back | `XxXxXxXxXxXRRxXx`, 54 to 58% swing, 15 ms late | 8 to 13 | 84 to 100 | MCFlow norms |
| Pickup flow | `X.xxX.x.XxxxR.pp` | 8 to 12 | 85 to 100 | "99 Problems" |
| 16th rapid fire | `XxxxXxxxXxxxRxxx` | 12 to 16 | 80 to 100 | Blackalicious |
| Triplet flow | 12 slots `XxxXxxXxxRxx` | 9 to 12 | 120 to 150 | Migos |
| Half-time trap | `X.x.x.xxX.x.x.xx` / `X.x.x.xxR...x.xx` | 6 to 10 | 130 to 160 | trap, UK drill |
| Stutter snap | `Xx..Xx..Xx..Rx..` | 6 to 8 | 130 to 150 | Scotch snap |

**Placement** is dynamic programming over syllables and slots (report 03, section 4). Costs: a stressed syllable on a weak slot, an unstressed syllable on beat 1, a syllable shorter than about 120 ms at the chosen tempo (rising gradually below that, from 95 ms for unstressed syllables, so flows keep their 16ths up to about 140 BPM), a gap inside a word, an empty beat in the middle of a phrase. Rewards: a rest at punctuation, the last stressed syllable of the line on the preset's rhyme slot. The algorithm tries 1 to 4 bars per line and keeps the cheapest. Syllables the user has locked stay where they are and the rest flows around them. The weights start at the values in report 03 and get tuned by ear.

**The step grid** shows each bar as 16 step keys (12 in a triplet bar). Edits: move a syllable, stretch it over more steps, insert a rest, lock, repeat a syllable as a stutter, mark an accent, shift a whole line by a step, and switch a bar between 16 and 12 steps. Warnings sit in the key itself: a stressed syllable on an off step, a syllable too short to say.

## 6. Timing: sample-exact placement

### 6.1 Grid maths

All audio is rendered at 48 kHz. The frame of a tick is computed from its absolute position, never by adding up beat lengths:

```
frame(tick) = round(tick * 60 * 48000 / (bpm * 480))
```

At 127 BPM a beat is 22,677.165 frames; adding rounded beat lengths drifts 4.4 ms after 1,270 beats, the formula above does not drift (report 04). Swing moves every second 16th later by `(swing - 0.5) * 2 * 120` ticks.

### 6.2 What lands on the beat

The onset of the vowel, not the start of the consonant before it, is what a listener hears as "on the beat" (P-centre research, and the same choice in UTAU, OpenUtau, VOCALOID and Sinsy; report 04 and 05). So for each syllable:

```
anchor = vowel onset - min(feel * onset consonant length, 60 ms)
```

and the anchor sits exactly on the slot's frame. `feel` started at 0.25, the compromise of the literature, but in Live next to tight drums that put robot vowels about 20 ms late: their synthetic consonants are soft, so the vowel itself is heard as the beat (which matches Rathcke et al. 2024 in report 04). It is now 0, the vowel exactly on the slot; a FEEL setting (0 to 0.4) can still push it later.

### 6.3 Engines that take durations (Flite, formant, LPC)

The timing planner turns the placed syllables into a list of phonemes with a start frame, a length and pitch targets:

1. Onset consonants get their natural length from the engine's duration model and end at the vowel onset. If the run-up is longer than half of the previous syllable's slot, they are compressed, down to Klatt's minimum durations (the OpenUtau auto-fit rule).
2. The vowel runs from its onset to the end of the slot, minus the coda and minus the gap that the GATE setting asks for (robot staccato at 0.6 to 0.7, legato at 1.0).
3. Coda consonants keep their natural length when they fit, and are compressed when they do not.

The engine then renders exactly that plan. Vowel onsets land on the target frame.

### 6.4 eSpeak NG

eSpeak NG renders each word from our phonemes and reports where every phoneme starts. The renderer then shifts the word so the anchor sits on the slot, and changes only the middle of each vowel to fit its slot (time-stretch with Signalsmith Stretch, MIT, or our own WSOLA; consonants stay untouched). Expected error: a few milliseconds, checked by a test that finds the vowel onsets in the rendered audio.

### 6.5 Loops and tails

A loop is exactly `round(48000 * 60 * beats / bpm)` frames, the same formula LA2L uses, with the downbeat at frame 0. A tail that rings past the end (reverb, a long last vowel) is added to the start; a consonant that starts before beat 1 is added to the end. Looped, the vocal then runs on without a gap and stays on the grid. For a one-off phrase there is a second export form with one count-in bar before it, so the clip is still a whole number of bars.

### 6.6 Same sound in preview and export

Every noise source has a fixed seed, every syllable render is cached by its inputs, and the whole vocal is rendered once. The preview plays that buffer and the export writes that buffer, so what you hear is what you save.

## 7. Robot effects

The effect engine is C++ through the NDK, 48 kHz float, blocks of 256 frames, with parameters that can follow the grid (per bar or per step). Building blocks (report 06):

- Channel vocoder, 8 to 32 bands, the Brazen Head design as a starting point (paired band-pass filters, envelope followers, a separate path for consonants so s and t stay audible), carriers saw, square, pulse, supersaw, noise and chords.
- Talkbox (mda TalkBox, MIT or GPL), LPC re-synthesis (the chip voice of section 3), ring modulation, comb and tight-delay resonators, phaser, flanger, bitcrusher and sample-rate reduction, hard tune and monotone (WORLD, BSD-3), Codec2 at 700 and 1300 bit/s (LGPL-2.1), formant shift, stutter and gate locked to 16ths, drive, and a clean-up chain before every effect (EQ, compressor, de-esser). As built (report 12): a vocal chain on every voice (high-pass, presence shelf, a compressor gated by the phonemes, a de-esser that knows where every s is), then before every effect one working level, a fast and a slow compressor.

Twenty starting presets: Brazen Head I, II and III, Flat Twenty, Sixteen Channel, Vocoder Plus, Supersaw Choir, Golden Throat, NuVo Talker, Hard Tune, Robovox Singer, Monotone Unit, Speak & Spell, Codec 700, Exterminator, Centurion, Phase Robot, Whisper Unit, Tin Can, Tight Delay Electro, Stutter MC, Bass Droid. Names that point to products or films get renamed before release.

## 8. Pitch and carrier notes

Each syllable has a pitch mode: monotone on a chosen note (the default, the classic robot), a note of its own (the step grid gets a note per step, in a key and scale), or speech contour (the engine's own intonation). For the vocoder the carrier follows the same notes, or a chord per bar from a small chord track. Engines with pitch input (Flite, formant, LPC) render the notes directly; eSpeak NG goes through hard tune.

## 9. Example beats, preview only

The drums are synthesized in code, 808 style (kick, snare, clap, closed and open hat, cowbell, rim, tom), so no sample files and no sample licences. Patterns: electro in the "Planet Rock" mould, electro funk, Miami bass, boom bap with swing, half-time trap, house, techno, and a plain click. The beat follows the song's tempo and swing, has its own volume, and can be muted.

The beats live in the preview code only. The export and LA2L code take vocal stems as input and have no way to reach the drum code, so a beat cannot end up in a file by mistake. A test checks this.

## 10. Preview playback

One `AudioTrack` stream at 48 kHz float. A small mixer adds the rendered vocal and the beat, loops over the chosen bars, and reports the playhead from the audio clock (`getPlaybackHeadPosition`), so the step lights follow what you actually hear. Playback stops when the app goes to the background (Android 17 silences background audio without a foreground service anyway). Audio focus as in Spin Doctor.

## 11. Export: WAV

- 48 kHz, 24-bit PCM by default, 32-bit float as an option; mono, stereo when an effect makes stereo (chorus, phaser).
- Loop (exact whole bars, tails folded) or phrase (count-in bar plus the phrase, padded to whole bars).
- Optional dry stem next to the robot stem, same length, so both line up in a DAW.
- An `acid` chunk with tempo and beats (REAPER reads it; other DAWs ignore it) and a LIST INFO chunk with title and "Robot Vocal" as software.
- Saved through MediaStore in `Music/Robot Vocal`, named `<song> <bpm>bpm.wav`. Plus share, and "Save as" through the system file picker.

## 12. Send to Ableton Live (LA2L)

From report 08, tested against the LA2L test receiver:

- SDK `nl.lloydstellar:la2l-ktx:0.1.0` from `https://lloydstellar.nl/la2l/maven/` (repository line in `settings.gradle.kts`). It adds only the INTERNET permission. App name for LA2L: "Robot Vocal" (an apostrophe turns into a space in Live).
- Pairing once, with exactly the line LA2L prescribes above the code field: "In Live, click Pair app in LA2L and type the code it shows." Ask again only after `NOT_PAIRED`.
- Each send: kind loop, role vocal, the exact BPM, `lengthBeats` = bars x 4, name at most 40 characters (the song title plus the line, cut cleanly). Optional: one-shot, warp mode (Complex Pro by default, Beats, Tones, Re-Pitch), the dry stem in the same package.
- Status on screen: sending, "Waiting for Import in Live", "In Live". Every error shows LA2L's own message.
- Debug builds only: a server address and a separate token for the local test receiver through `adb reverse`, with plain http allowed for 127.0.0.1 in the debug network security config.

The app goes online for this and nothing else. The privacy text says so plainly.

## 13. Screens

| Screen | Contents |
|---|---|
| Intro | "LLOYD'S" above "ROBOT VOCAL". The robot says "Lloyd's Robot Vocal" in the default voice while the step keys light up on its syllables at 120 BPM: the app's main trick in two seconds. Tap to skip; can be switched off |
| Songs | Saved songs with title, tempo, voice and length; NEW SONG; three example songs to start from |
| Studio | The workplace. Display window with the line being played; transport (play, stop, loop, BPM with tap tempo, bar and beat, beat pattern and beat level); five sections: WORDS (lyrics), STEPS (grid), VOICE, EFFECT, OUT (WAV and Live) |
| Voices | Every voice with a short spoken sample, filter by engine and character |
| Send to Live | Pairing, package contents, status |
| Manual | How flow, steps, feel and gate work, with real examples |
| Settings | Sample format, feel, gate default, intro on or off, haptics |
| About | Who made it, privacy, credits, open source licences (viewer as in Override) |

## 14. The look

Robot Vocal gets its own look: the front panel of vocal hardware from around 1980 (the Sennheiser VSM 201 with its 20 LED columns, the Roland VP-330 and SVC-350, the EMS Vocoder 2000, the Speak & Spell with its display), printed flat on the phone. Rules:

- **Panel, not screen.** A near-black panel with printed legends in warm off-white, sections separated by printed lines with the section name in the line, the way hardware labels its groups. No glow, no gradients, no scanlines, no star fields, no blur.
- **Real displays only where hardware has them.** Tempo and bar/beat on 7-segment LED digits (DSEG7 font, OFL), the line being spoken on a 14-segment display window (DSEG14, OFL). Display colours only inside display windows.
- **Step keys.** The step grid uses the four colour groups of the classic drum machine step row (red, orange, yellow, off-white for beats 1 to 4), with a lamp per key that chases the playhead.
- **Live meters with real data.** The vocoder's band levels from the actual render, not decoration.
- **Type.** One condensed grotesk for panel legends (upper case, little tracking), one readable sans for sentences and lyrics, the two segment fonts for displays. All OFL. To be chosen with a test render in step 1.
- **Colour roles.** Panel black, legend off-white, LED red for playing and record states, LED green for done, amber `#FFB000` for the one main action on a screen (the thread to Lloyd's other apps), display teal only behind display glass.
- **Words.** Every label is a real unit or a real control: BPM, BAR 2 BEAT 3, 16 STEPS, FEEL 25 %, GATE 70 %, -6 dB. No invented codes, no status chatter, no clock. Microcopy short and plain; no emoji, no exclamation marks.
- **Motion.** Mechanical: lamps switch on and off, keys move one step. No fades for show.
- **Sound.** The UI makes no sound; buttons give haptic feedback.

Step 1 delivers one finished Studio screen as a static mock-up for Lloyd to judge before the other screens follow the same rules.

## 15. Architecture

Same habits as Lloyd's other apps where they fit: one activity, a `Screen` enum with `AnimatedContent` and `BackHandler`, an `object App` for singletons, Compose state in plain classes, SharedPreferences for settings, JSON files for songs, no navigation library, no dependency injection.

```
nl.lloydstellar.robotvocal
  App.kt, MainActivity.kt
  text/     Normalizer, Lexicon (CMUdict asset), LetterToSound (Flite), Syllabifier, Respelling
  score/    Song, Line, Syllable, Placement, Ticks (grid maths), FlowPresets, FlowPlacer (DP)
  voice/    VoiceEngine interface, FliteEngine, EspeakEngine, FormantEngine, LpcChipEngine, Voices (catalogue)
  render/   TimingPlanner, Renderer, Warp (vowel stretch), LoopFold, RenderCache
  dsp/      Native bridge, EffectChain, Presets
  beats/    DrumSynth, Patterns, BeatPlayer (preview only)
  audio/    Mixer, PcmPlayer (AudioTrack), Haptics
  export/   WavWriter, Gallery (MediaStore), Share, La2lSender
  data/     Store (settings), SongStore (JSON)
  ui/       Theme, Panel components, one file per screen
app/src/main/cpp/
  flite/, espeak-ng/, dsp/ (vocoder, talkbox, lpc, ...), world/, codec2/, jni/
```

`text/`, `score/`, `render/` timing and `export/WavWriter` are plain Kotlin without Android classes, so they run in fast JVM unit tests.

## 16. Build and toolchain

- AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37, targetSdk 36 (required by Play since 31 August 2026), minSdk 30. The same versions as Lloyd's other apps.
- NDK r30 (`30.0.16248370`) and CMake 3.22.1 still need to be installed in `~/Android/Sdk`. ABIs arm64-v8a and x86_64 (emulator).
- 16 KB page size: on by default with NDK r28 and newer; checked on every release build.
- R8 on, release signing from `signing/keystore.properties` (git-ignored, as in the other apps).
- Third-party C code is vendored in `app/src/main/cpp` with its licence files; the licences also go into `assets/licenses` for the in-app viewer.

## 17. Tests

- Grid maths: frames at fractional tempos, no drift over 1,000 bars, swing, loop lengths equal to LA2L's formula.
- Text: syllable counts and stress on a fixed word list, numbers, respellings.
- Flow: each preset on known lines gives the expected slots; locked syllables never move.
- Timing: for every engine, find the vowel onsets in the rendered audio and compare with the grid (exact engines within 1 ms, eSpeak NG within 5 ms).
- Determinism: two renders of the same song are identical bit for bit.
- Export: WAV headers, exact frame counts, fold of tails and pickups, `acid` chunk; the beat never in an export.
- Audio sanity: no NaN, no clipping, not silent, for every voice and preset (rendered to `tools/audio/` to check by ear, as Override does).
- On the phone: `android run`, `android screen` and `android layout` for every step; Lloyd listens.

## 18. Google Play

From report 09:

- Listing draft: title "Robot Vocal: Text to Speech" (27 of 30), short description "Type lyrics, hear robot vocals locked to your BPM. Save WAVs for your DAW." (74 of 80). Category Music & Audio. Ableton Live may be named as compatible, with its trademark line; no artist or product names such as Daft Punk or Speak & Spell in the listing.
- Data safety: data is collected only when the user sends to Live: audio files (Audio, other audio files) and the pairing token (Device or other IDs), optional, for app functionality, encrypted in transit, deleted after import or within 24 hours; nothing shared. No account.
- The AI-generated content policy does not apply: no generative models. The user-generated content policy does not apply: nothing is shared between users.
- Privacy policy page on lloydstellar.nl, written for this app (it goes online for LA2L, unlike the other apps).
- Assets: icon 512 x 512, feature graphic 1024 x 500, at least four 1080 x 1920 screenshots with the real interface filling most of the frame, and a 20 to 30 second video, because a listing cannot play sound otherwise.
- App icon: after the sleeve of Computer World (1981): a computer terminal of that time on yellow, seen from above and a little from the side, with a robot on its screen in the style of the mannequins of early electro (slicked hair, a blank face, a red shirt and a black tie). The terminal is a small 3D model projected in perspective and drawn in flat shades by `tools/make_icon.py`. It replaced two earlier icons: two lamps over band levels, which looked like a voice assistant, and a robot head in profile.
- The store package as for Spin Doctor: `tools/store/listing.md`, `privacy.html`, `LEESMIJ.txt` with the Play Console steps, graphics, screenshots, signed AAB.

## 19. Steps

Each step ends with a commit, a build on the Pixel 8 and something Lloyd can hear or see.

1. **Skeleton and look.** Gradle setup, licence files, theme tokens, fonts, the panel components, a static Studio mock-up for approval, intro and songs screens as shells.
2. **Text and flow.** Normaliser, CMUdict asset, syllables and stress, score model, grid maths, flow presets and the placement algorithm, all with unit tests.
3. **First robot on the beat.** The formant engine in Kotlin, the timing planner, drum synth and example beats, preview player with the step lights, WAV export. Type a line, hear it on the beat, save it.
4. **Native voices.** NDK and CMake set up; Flite with all voices and exact timing; eSpeak NG with word renders and vowel stretch; the voice browser.
5. **Robot effects.** The C++ effect engine, the twenty presets, pitch modes, chord track.
6. **Step grid editing.** Move, stretch, rest, lock, stutter, accent, notes per step, feel and gate.
7. **Ableton Live.** LA2L pairing and sending, tested with the test receiver and in Live.
8. **More voices.** Home computer voices and the speech chip voice (done), then candidates from 3.2 after their licence check.
9. **Release.** Icon drafts, manual, about and licences, store graphics and screenshots, privacy policy, signed AAB, 16 KB check, internal test track.

## 20. Risks and open points

- eSpeak NG's phoneme onset times and the vowel stretch need to be measured; if they are not tight enough, eSpeak NG voices render per syllable instead.
- The flow weights come from literature and need tuning by ear. Feel was tuned by ear in Live: 0, the vowel on the beat.
- narrator-ts is a new single-author project; review before use. whackintalk was reviewed before the port: it implements published work (Klatt 1980, KLGLOTT88, Peterson and Barney) and holds no code or data from Apple; only its voice names were Apple's, and the app uses its own.
- Default warp mode in Live (Complex Pro or Beats) is a listening test in Live.
- Speed and memory on the Pixel 8 are not measured yet; rendering is offline, so slow means waiting, not glitches.
