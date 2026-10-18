# 06. Robot voice DSP

Research for Robot Vocal ("Lloyd's Robot Vocal"): an Android app (Kotlin, Jetpack Compose) that renders English lyrics with on-device TTS, turns them into robot vocals, places syllables on a BPM grid and exports WAV for Ableton Live. Researched 2026-10-18.

Licence rule from Lloyd: the app is open source, GPL and LGPL code is allowed, nothing with non-commercial terms, dual licences only if the open side is GPL-compatible.

Marks: "(computed)" derived here from a formula; "(measured)" run on the research machine; "(rec.)" a recommendation, not a sourced fact; "not researched" is an open point.

## 1. Key findings

- The Brazen Head vocoder ports directly. Its two Q 6.5 band-passes in series leave a -9.3 dB gap between neighbouring bands (computed). That gap is its ringing colour; a flat 20-band bank needs Q 3.0 for series pairs.
- Every classic vocoder had an unvoiced path: a noise carrier or a high-passed copy of the voice. Without one, TTS consonants disappear.
- Kraftwerk's Robovox was a Votrax SC-02 phoneme chip played over MIDI, not a vocoder ([EP0396141A2](https://patents.google.com/patent/EP0396141A2/en)). That is close to this app's own design.
- Monotone and hard-tune are best done at synthesis time where the engine allows it (eSpeak NG `espeakRANGE` 0, MBROLA `.pho` pitch points); for neural voices use WORLD or Signalsmith Stretch.
- Speak & Spell can be rebuilt from BSD-3 MAME tables plus the MIT `python_wizard` encoder.
- eSpeak NG seeds its RNG from the clock, Codec2 keeps a global static PRNG and Piper adds sampling noise. Cache TTS output, seed every noise source, render once and play that buffer.
- CPU is not a constraint for offline rendering (section 7).
- GPL-3.0-or-later for the app accepts every library recommended here.

## 2. Brazen Head reference

From [`vocoder.js?v=20261007bk`](https://lloydstellar.nl/wunderkammer/brazen-head/js/vocoder.js?v=20261007bk); filter formulas from the [Web Audio spec](https://www.w3.org/TR/webaudio-1.1/#BiquadFilterNode).

| Part | Setting |
|---|---|
| Modulator | S.A.M., pitch 64, speed 72, 22,050 Hz |
| Bands | 20, log-spaced 130 Hz to 7.4 kHz (0.307 octave apart) |
| Filters | two RBJ band-pass biquads in series, Q 6.5, on analysis and synthesis |
| Detector | `abs(x)`, then a 2nd-order low-pass at 32 Hz. Web Audio reads low-pass Q in dB, so "0.5" is a linear Q of 1.06 (computed) |
| Consonants | modulator high-passed at 5.2 kHz, gain 0.5, added to the output |
| Carriers | I: saw, plus 22% dry, then `round(x*24)/24` (49 levels, about 5.6 bits). II: minor triad, two saws per note at -7/+7 cents, 2.6 kHz low-pass. III: square and saw an octave down, sine two octaves down, 1.5 kHz low-pass |
| Output | compressor at -18 dB, 4:1, 3 ms/150 ms, knee 30 dB; 44.1 kHz, 24-bit WAV, peak 0.89 |

The 32 Hz detector lets about 21 dB-down pitch ripple through at 110 Hz (computed), which adds buzz. Keep this graph as a "Retro" preset and add a flat-bank variant.

## 3. Preparing the dry vocal

Engine bandwidth sets what the effects can work with:

| Engine | Rate |
|---|---|
| eSpeak NG | 22,050 Hz ([speech.c](https://github.com/espeak-ng/espeak-ng/blob/master/src/libespeak-ng/speech.c)) |
| Piper | low 16 kHz, medium and high 22,050 Hz ([TRAINING.md](https://github.com/rhasspy/piper/blob/master/TRAINING.md)) |
| Kokoro | 24 kHz ([model card](https://huggingface.co/hexgrad/Kokoro-82M)) |
| Flite `kal` | 8 kHz ([README](https://github.com/festvox/flite)) |

For voices at 16 kHz or less, keep vocoder bands and the unvoiced detector below their Nyquist frequency, or synthesise "air" (noise high-passed at 5 kHz, gated by the 2.5 to 4 kHz envelope).

The pre-chain (rec.):

1. Resample once to 48 kHz: Oboe's resampler, libsamplerate (BSD-2) or r8brain (MIT).
2. High-pass at 80 Hz.
3. Normalise each syllable's RMS. A vocoder multiplies levels band by band, so keep input "as steady as possible" ([SOS](https://www.soundonsound.com/techniques/power-vocoding)).
4. Compress gently: 3:1, 3 ms/60 ms.
5. Add +2 to +4 dB around 3 kHz, where the consonants sit ([DPA](https://www.dpamicrophones.com/mic-university/facts-about-speech-intelligibility)).
6. De-ess at 5 to 8 kHz before pitch effects only. Do not de-ess a vocoder or LPC analysis input: sibilance drives the unvoiced path. Tame it on that path instead.

Use bright carriers (saw, plus 5 to 15% noise), as both [Ableton](https://www.ableton.com/en/manual/live-audio-effect-reference/) and [Reason](https://docs.propellerheads.se/reason9/BV512Vocoder.46.7.html) recommend.

## 4. Effects

| Effect | How it works, key numbers | Speech setting (rec.) | Code and licence |
|---|---|---|---|
| Channel vocoder | matched band-pass banks plus envelope followers. Flat crossover Q = `sqrt(r)/(r-1)`, times 0.642 for series pairs: 20 bands over 100 Hz to 8 kHz gives Q 4.33 single, 2.78 series (computed) | 20 to 24 bands, 100 Hz to 8 kHz, 4th order; attack 3 ms, release 30 ms; V/UV crossfade to noise high-passed at 4.5 kHz; voice above 5 kHz mixed in at -10 dB; saw carrier at 80 to 220 Hz | own code; Faust `ve.vocoder` (LGPL plus exception); mda Vocoder: 15 bands from 95 Hz to 5 kHz plus a high band (GPL-2+ or MIT); Surge (GPL-3+) |
| LPC talkbox | all-pole filter from voice frames, carrier through it. mda TalkBox: half-rate processing, 32.7 ms Hann at 50% overlap, order `(0.0001+0.0004*Q)*fs` = 24 at 48 kHz, 6 dB/oct pre-emphasis | order 24, saw lead on the melody lane, 40 ms glide, 5 to 10% noise for consonants | mda TalkBox ([mda-lv2](https://gitlab.com/drobilla/mda-lv2)) |
| Speak & Spell | TMS5100: 8 kHz, 25 ms frames, 10-pole lattice, chirp or LFSR excitation, 32 pitch steps (52 to 195 Hz), 8-bit clipped output ([MAME](https://github.com/mamedev/mame/blob/master/src/devices/sound/tms5110.cpp), [US4209836](https://patents.google.com/patent/US4209836)) | analyse at 8 kHz, quantise to the tables, take pitch from the grid | MAME tables (BSD-3); [python_wizard](https://github.com/ptwz/python_wizard) (MIT) |
| LPC-10 | 8 kHz, 22.5 ms frames, 54 bits, 2400 bit/s, order 10 ([Crypto Museum](https://cryptomuseum.com/ref/codec/lpc10)) | pulse/noise excitation, 300 Hz to 3.4 kHz | own code |
| Phase-vocoder robot | zero phase per frame; pitch = fs/hop (hop 436 = 110.1 Hz at 48 kHz, computed) | FFT 1024, Hann, hop taken from the grid note | [Audio-Effects](https://github.com/juandagilc/Audio-Effects) (GPL-3+) |
| Whisper | random phase per bin | FFT 512, hop 64, seeded PRNG | same |
| Ring mod | `x * sin(2 pi fc t)`. The Dalek used a 30 Hz sine on a tape loop whose speed wavered ([BBC R&D](https://github.com/bbc/webaudio.prototyping.bbc.co.uk/blob/master/src/ring-modulator.coffee)) | 30 Hz plus 1 Hz wow, 100% wet; or 80 to 300 Hz at 60% for metal | own code |
| Comb, resonators | `y = x + g y[n-M]`; peak gain 1/(1-g): +20 dB at g 0.9 (computed) | 3 combs on chord tones, g 0.85, loop low-pass at 5 kHz, fractional delay | own code |
| Bitcrush, rate reduction | sample-and-hold plus quantiser; about 6 dB per bit | 8 bits at 8 to 11 kHz, no pre-filter | own code (DaisySP Decimator, MIT) |
| Hard tune, monotone | detect (YIN, pYIN, BACF), snap to target with zero retune time ([SOS](https://www.soundonsound.com/techniques/pitch-correction-plug-ins)) | target = grid note; 5 to 10 ms glide on short syllables | WORLD (BSD-3), Signalsmith Stretch (MIT), Q (MIT) |
| Formant shift | warp the spectral envelope (WORLD), Rubber Band R3 `setFormantScale`, or offset the vocoder bank (3.7 semitones per band at 20 bands) | ratio 0.8 to 0.9 for a bigger robot, 1.1 to 1.25 for a smaller one | WORLD, Signalsmith |
| Harmonizer | 2 to 4 formant-preserving shifted copies | -12, +7, +12; detune 5 to 10 cents; 0 to 15 ms offsets | Signalsmith; or a vocoder chord carrier |
| Flanger, phaser | flanger: 1 to 20 ms swept delay. Phaser: 4 or more swept allpasses; feedback 0.5 to 0.7 | tempo-synced, 1 bar | own code (DaisySP, Faust `pf.*`) |
| Codec2 | 8 kHz. 3200 and 2400 use 20 ms frames, 1600 to 700C use 40 ms; 700C has 28 bits per frame. Only 3200 is actively maintained ([repo](https://github.com/drowe67/codec2)) | 700C or 1300, seeded bit errors at 0.1 to 2% | Codec2 (LGPL-2.1) |
| Granular, stutter | grains of 10 to 30 ms ([SFU](https://www.sfu.ca/sonic-studio-webdav/cmns/Handbook5/handbook/Gran.html)); Beat Repeat parameter model (Interval, Offset, Grid, Gate, Chance) | 1/32 slice, 2 to 4 repeats, 3 ms fades | own code |
| BPM gates | 16th = 60/BPM/4 s (125 ms = 6000 samples at 120 BPM and 48 kHz); swing 50 to 70%, 54% loosens ([Roger Linn](https://attackmagazine.com/?p=10694)) | 16 steps with length, level and ratchet; 1 to 5 ms ramps | own code |
| Distortion | tanh, clip, fold; ADAA ([DAFx-16](https://dafx.de/paper-archive/details/vem_XXF5qBbfiWOH2RVVAA)) or oversampling | 6 to 18 dB into tanh, 2x oversampling plus ADAA1 | Faust `aanl.lib` (MIT) |
| Carrier sequences | chord lane per bar, voiced at 100 to 500 Hz, optional 1/16 arpeggio; start notes 10 to 30 ms early, as on "Believe" ([SOS](https://www.soundonsound.com/techniques/recording-cher-believe)) | change chords at a bar or a syllable onset | own code |

### 4.1 Hardware and famous chains

| Source | What made the sound |
|---|---|
| Sennheiser VSM 201 (1978) | 20 channels, 100 Hz to 8 kHz; voiced/unvoiced replacement ([radiomuseum](https://www.radiomuseum.org/r/sennheiser_vocoder_vsm201vsm_20.html)) |
| EMS Vocoder 2000 | 16 filters (14 band-pass, 6th order); V/UV detector gating noise and a pulse; freeze ([VSE](https://www.vintagesynth.com/electronic-music-studios-ems/vocoder-2000)). Used for the 1978 Cylon voices ([Wikipedia](https://en.wikipedia.org/wiki/Vocoder)) |
| EMS 5000 | 22 bands plus a frequency shifter ([gearnews](https://www.gearnews.com/the-history-of-ems-synth/)) |
| Roland VP-330 | 10 bands from 200 Hz to 6 kHz, blue-noise sibilants, BBD ensemble ([Zoe Blade](https://notebook.zoeblade.com/VP-330.html)) |
| Roland SVC-350 | 11 sliders (10 bands plus a consonant channel), hold, chorus ([muzines](https://www.muzines.co.uk/articles/how-about/6570)) |
| Korg VC-10 | 20 bands, one oscillator per key, ensemble ([Wikipedia](https://en.wikipedia.org/wiki/Korg_VC-10)) |
| Moog 16-channel (Bode) | 16 bands from 50 Hz to 5080 Hz, 6 ms response, sample-and-hold, HF bypass ([VSE](https://www.vintagesynth.com/moog/vocoder)) |
| DigiTech Talker | 10 Hz to 14.5 kHz; NuVo mixes in the high-passed voice ([SOS](https://www.soundonsound.com/reviews/digitech-talker)) |
| Daft Punk | "the old Roland one [SVC-350], Auto-Tune, and a DigiTech Vocalist" (Remix, 2001, quoted by [bjango](https://bjango.com/articles/daftpunkvocaleffects/)) |
| Zapp & Roger | EHX Golden Throat talkbox with a Minimoog, later a DX100 ([Wikipedia](https://en.wikipedia.org/wiki/Roger_Troutman)) |
| "Planet Rock" | vocoder through a "really, really tight delay" from a Lexicon PCM41 ([SOS](https://www.soundonsound.com/node/4909791)) |
| Cybotron "Clear" | C64 speech software plus "slap-back delay with a comb" ([SPIN](https://www.spinmagazine.com/2013/10/cybotron-clear-reissue-dave-tompkins-sci-fi-techno/)) |

## 5. Libraries and licences

Ranked for a GPL-3.0 app with a C++ core.

| Rank | Library | Licence | Use | Last release |
|---|---|---|---|---|
| 1 | own C++ modules | GPL-3.0+ | vocoder, ring mod, comb, crush, gates, drive, flanger, phaser | |
| 2 | [mda plugins](https://gitlab.com/drobilla/mda-lv2) | GPL-2.0+ or MIT | TalkBox, Vocoder, RingMod, Degrade, DeEss | mda-lv2 1.2.12, 2026-06-11 |
| 3 | [WORLD](https://github.com/mmorise/World) | BSD-3 | F0 replacement, formant warp, whisper | v1.0.1, 2026-02-18 |
| 4 | [Signalsmith Stretch](https://github.com/Signalsmith-Audio/signalsmith-stretch) | MIT | pitch and formant shift | 1.4.0, 2026-09-25 |
| 5 | [PFFFT](https://github.com/marton78/pffft) or [KISS FFT](https://github.com/mborgerding/kissfft) | BSD | FFT | v1.1.0 / 131.2.0 |
| 6 | [Q](https://github.com/cycfi/q) | MIT | BACF pitch detection | v1.5.4, 2026-10-08 |
| 7 | [Codec2](https://github.com/drowe67/codec2) | LGPL-2.1 | codec artefacts (patch its PRNG) | 1.2.0, 2023-07-24 |
| 8 | MAME [tms5110](https://github.com/mamedev/mame/blob/master/src/devices/sound/tms5110r.hxx) | BSD-3 | Speak & Spell | rolling |
| 9 | [Faust](https://github.com/grame-cncm/faust) + libraries | compiler LGPL-2.1+; generated code under the licence of its inputs ([FAQ](https://faustdoc.grame.fr/manual/faq/)); avoid the GPL-3.0-only and AGPL-only functions | prototyping | 2.88.0, 2026-09-09 |
| 10 | [DaisySP](https://github.com/daisyaudio/DaisySP) | MIT (plus an LGPL part) | decimator, wavefolder, formant oscillator | V1.0.0, 2024-01-08 |
| 11 | [Oboe](https://github.com/google/oboe) | Apache-2.0 | playback, resampler | 1.11.0, 2026-09-15 |
| 12 | [Rubber Band](https://breakfastquay.com/rubberband/) | GPL-2.0+ or commercial | best formant-preserving shift (R3), but heavy | v4.0, 2024-10-25 |
| 13 | [KFR](https://github.com/kfrlib/kfr) | GPL-2.0+ or commercial | NEON DSP | 7.2.0, 2026-10-12 |

Reference only: Soundpipe (MIT, archived; bundled OpenLPC demands a displayed notice), sndkit, STK, Gamma, Surge (GPL-3+).

Not recommended:
- SoundTouch (LGPL-2.1+): no formant control.
- Maximilian: stale.
- Csound and libpd: heavy runtimes.
- JUCE 9 (AGPLv3 or commercial): legal under GPLv3 section 13, but heavy.
- Superpowered: proprietary, incompatible with the GPL. Excluded.

Choose GPLv3 over GPLv2: Apache-2.0 code (Oboe) is not GPLv2-compatible ([FSF](https://www.gnu.org/licenses/license-list.html#apache2)). The GPL does not cover program output ([GPL FAQ](https://www.gnu.org/licenses/gpl-faq.html#GPLOutput)), so no DSP licence reaches Lloyd's released audio.

## 6. Deterministic rendering

- **One code path.** Preview plays the rendered buffer and export writes it. Re-render only the bars that change.
- **Cache TTS output per syllable.** eSpeak NG seeds from `time(NULL)`; call `espeak_ng_SetRandSeed()` before every synthesis ([common.c](https://github.com/espeak-ng/espeak-ng/blob/master/src/libespeak-ng/common.c)). Piper uses `noise_scale` 0.667.
- **Codec2's `codec2_rand()`** keeps a file-level `static` ([sine.c](https://github.com/drowe67/codec2/blob/main/src/sine.c)). Move it into instance state. WORLD reseeds on every call.
- **Seeded PRNGs** for every noise source, keyed by project, track, module and bar.
- **Time from the grid.** Step `k` starts at sample `round(k*60/bpm/4*fs)`, computed in double precision. LFO phases are also derived from the grid.
- **Split blocks at events.** Test that block sizes 64, 333 and 4096 give bit-identical output.
- **Floating point.**
  - Kotlin follows strict IEEE semantics ([JEP 306](https://openjdk.org/jeps/306)), but `Math.*` is not bit-reproducible; `StrictMath` is.
  - clang defaults to `-ffp-contract=on`. Tested for arm64 Android, `b0*x+z1` became `fmadd`; with `-ffp-contract=off` it became `fmul` plus `fadd`. Use `-ffp-contract=off` and never `-ffast-math`.
- **Flush denormals in code**, and keep golden-file hashes per preset.

## 7. CPU cost

Scalar code was measured on an i7-1195G7, as times faster than real time at 48 kHz:

| Kernel | Java | C (clang -O2) |
|---|---|---|
| 20-band vocoder (4 biquads per band) | 126 to 151x | 173 to 219x |
| 40-band vocoder | 71 to 73x | 97 to 109x |
| PV robot, FFT 1024, hop 256 | 156 to 202x | 186 to 197x |
| LPC order 32, window 1024, hop 256 | 141 to 152x | 148 to 154x |
| tanh, 4x oversampled | 79 to 128x | 188 to 204x |

Pixel 8 numbers are not researched; measure them with Jetpack Microbenchmark. Even ten times slower, a full chain stays faster than real time.

## 8. Electro-rap parameters

At 120 BPM, LFO rates are 0.5 Hz per bar, 2 Hz per 1/4, 4 Hz per 1/8 and 8 Hz per 1/16. Phase is locked to the bar. Typical targets:
- ring-mod frequency: 30 to 90 Hz over 1 bar
- flanger and phaser: 1 bar
- vocoder formant offset: sample-and-hold per 1/8
- carrier filter: 1/16 saw
- crush rate: per 1/4

Gate patterns (16 steps, `x` on, `.` off, `r` ratchet):

| Name | Pattern |
|---|---|
| Straight 8ths | `x.x.x.x.x.x.x.x.` |
| Offbeat | `.x.x.x.x.x.x.x.x` |
| Electro chop | `x.xx.x.xx.x.x.xx` |
| Stab 3-3-2 | `x..x..x.x..x..x.` |
| Roll out | `x.x.x.x.x.x.rrrr` |

## 9. Presets

Engine types: F = formant (eSpeak NG or a clean S.A.M.-like engine), D = diphone (Flite), N = neural (Piper, Kokoro).

| # | Name | TTS | Chain and parameters |
|---|---|---|---|
| 1 | Brazen Head I/II/III | F | section 2, exactly |
| 2 | Flat Twenty (VSM 201 style) | N | 20 bands, 100 Hz to 8 kHz, series Q 2.8, 3/30 ms, V/UV to noise, pulse plus 10% noise carrier, chord per bar |
| 3 | Sixteen Channel (Moog style) | D | 16 bands, 50 Hz to 5080 Hz, 6/40 ms, HF direct at -10 dB, saw chords, freeze on the last 16th of each phrase |
| 4 | Vocoder Plus (VP-330 style) | F | 10 bands, 200 Hz to 6 kHz, Q 2.2, blue-noise sibilants, paraphonic saw chords, BBD chorus |
| 5 | Supersaw Choir | N | 24 bands, 7-saw chord carrier changing per bar, unvoiced noise, stereo chorus |
| 6 | Golden Throat (talkbox) | N | LPC order 24, mono saw on the melody lane, 40 ms glide, +2 semitone bends on accents, light tanh |
| 7 | NuVo Talker | N | 32 bands up to 12 kHz, saw plus square, voice above 6 kHz at -8 dB, carrier notes 20 ms early |
| 8 | Hard Tune | N | pitch tracking, snap to the grid note, WORLD resynthesis, 1-bar phaser |
| 9 | Robovox Singer | F | pitch set per syllable at synthesis, formant ratio automated per step (0.85 to 1.15), 12-bit crush |
| 10 | Monotone Unit | F | `espeakRANGE` 0, formant 0.85, straight-8ths gate at 70% |
| 11 | Speak & Spell | D (8 kHz) | TMS5100 emulation, grid pitch, 8-bit clip, zero-order hold |
| 12 | Codec 700 | N | Codec2 700C, 0.5% bit errors, 300 Hz to 3.4 kHz |
| 13 | Exterminator (Dalek) | any, slow rate | +4 dB at 1 kHz, 30 Hz ring mod with 1 Hz wow, soft clip |
| 14 | Centurion (Cylon) | F | 16-band 6th-order vocoder, PWM pulse plus sine at C2, 6-stage phaser (feedback 0.6, 1 bar), slow compressor, 8 kHz low-pass |
| 15 | Phase Robot | any | PV robot, FFT 1024, hop from the root note, chorus |
| 16 | Whisper Unit | N | PV whisper, FFT 512, hop 64, high-pass at 300 Hz |
| 17 | Tin Can | any | 3 combs on chord tones, g 0.85, 400 Hz to 3 kHz band-pass, 10-bit |
| 18 | Tight Delay Electro | F | 16-band vocoder, then a 3 ms comb (g 0.6), then a 1/16 slapback |
| 19 | Stutter MC | N | Hard Tune, electro-chop gate, 1/32 repeats at the bar end, 8-bit |
| 20 | Bass Droid | any | formant 0.75, -12 harmonizer at 50%, tanh at 2x oversampling, 3 kHz low-pass |

## 10. Recommended architecture

- **C++17 DSP engine through the NDK.** Kotlin handles the UI, project model, grid and presets (JSON). One JNI call renders a region into a float buffer.
  - Why C++: the TTS engines are native anyway, and the reusable code (mda, WORLD, Codec2, Signalsmith, MAME, PFFFT) is C or C++.
  - Pure Kotlin would be fast enough for the IIR effects (section 7), but two DSP languages would double the testing.
- **Number formats.** Process at 48 kHz in float32. Use double for time, phases and low-frequency or high-Q filter state; this cost 10 to 15% in the measurements.
- **Blocks** of 256 frames, split at every event.
- **Chain.** Pre, then Core (vocoder, LPC, PV, ring, codec), then Colour (formant, comb, crush, drive), then Rhythm (gate, stutter), then Space (chorus, phaser, flanger), then Output (EQ, limiter at -1 dBFS).
  - A parallel consonant bus bypasses the Core.
  - Carriers come from the chord and melody lanes.
- **Automation.**
  - Lanes on the 16th grid compile to sample-accurate events.
  - Smoothing: 5 ms for gains, 20 ms for filters.
  - LFOs are phase-locked to the grid.
- **Oversampling** only inside nonlinear modules: 2x plus ADAA, 4x for foldback. Carriers are band-limited. No oversampling for the vocoder, LPC, combs or crush.
- **Playback and export.** Preview plays the render through Oboe; export writes the same buffer as 24-bit WAV.
- **Build** with `-O2 -ffp-contract=off`, and run golden-file tests in CI.

## 11. Open points

- S.A.M.: the C port's author says he "cannot put my code under any specific open source software license" ([s-macke/SAM](https://github.com/s-macke/SAM)). A clean reimplementation is needed.
- MBROLA voices "may not be sold or incorporated into any product which is sold without prior permission" ([LICENSE.md](https://github.com/numediart/MBROLA-voices/blob/master/LICENSE.md)). This probably falls under Lloyd's exclusion; use Flite (BSD-like) for diphone presets.
- Per-voice licences of Piper and Kokoro voices, for output used in released music: not researched.
- Pixel 8 and ART benchmarks, and the Android NDK builds of Q and Signalsmith: not researched.
- Listening notes for each Codec2 mode, the VSM 201 filter slopes, the band centres of the SVC-350 and VC-10, and the original Dalek frequency (secondary sources only): not researched.
- Ableton import details (sample rate of Lloyd's sets): not researched.
