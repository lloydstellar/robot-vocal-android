# Retro and robotic speech engines for Robot Vocal (Android)

Research date: 2026-10-18. Scope: English only. This is a technical survey, not legal advice.

Licence policy (decided by the project owner): the app is open source on GitHub, so GPL-family code is allowed. Excluded: anything with non-commercial or research-only terms on code, voice data or generated audio, and anything without a clear open licence (for those, clean-room options are noted in section 5).

Evidence: "verified" means built and run on Linux x86_64 (GCC 16.2.1, no CMake) on 2026-10-18, with output formats checked by ffprobe. Everything else was read at the linked source. "Not researched" marks open points.

## 1. Ranked shortlist

| # | Engine | What it adds | Beat-grid timing | Licence (code / voice data) | Closed-source OK? | Latest release / commit |
|---|---|---|---|---|---|---|
| 1 | [eSpeak NG](https://github.com/espeak-ng/espeak-ng) | 104 voice variants, incl. robosoft1 to 8, UniRobot, anikaRobot, klatt1 to 6 | Reads phoneme onsets (ms and sample); cannot set durations | GPL-3.0+ / GPL-3.0+ | No | 1.52.0 (2024-12-12); commit 2026-09-22 |
| 2 | [Flite](https://github.com/festvox/flite) kal, kal16 + CMU ARCTIC slt, rms, awb, bdl, clb, jmk, ksp | Classic 8 kHz LPC diphone robot (kal) plus 7 statistical voices | Exact for kal via `duration_model_func` and `f0_model_func` (verified) | BSD-like / free incl. commercial | Yes | v2.2 (2020-08-13); commit 2022-05-16 |
| 3 | [hts_engine](https://hts-engine.sourceforge.net/) + Flite front end, ARCTIC slt voice | Smooth buzzy vocoder at 48 kHz | Exact via label alignment; per-frame f0 (verified) | Modified BSD / CC BY 3.0 + ARCTIC | Yes, with credit | 1.10 (2015-12-25); voice 1.06 (2016-12-25) |
| 4 | [MBROLA](https://github.com/numediart/MBROLA) + en1 voice | 1990s diphone voice (British male) | Exact: ms and Hz points per phoneme in `.pho` (verified) | AGPL-3.0 / en1 "for any purpose" | No | 3.3 (2019-12-17); commit 2025-07-20 |
| 5 | [rsynth](https://github.com/rhdunn/rsynth) | 1990s Unix "say" formant voice, monotone flag | Exact: honours `.pho` durations (verified) | GPL/LGPL / rules, CMUdict | No | commit 2014-01-12 |
| 6 | [Gnuspeech SA](https://ftp.gnu.org/gnu/gnuspeech/) | Tube-model voice, 5 voice configs | Edit 4 ms parameter frames, re-render (verified) | GPL-3.0+ | No | 0.1.5 (2015-10-14) |
| 7 | TMS5110/5220 LPC model + our own encoder | Speak & Spell sound for any text, no TI data | 25 ms frames, pitch per frame | MAME BSD-3 / MIT encoder method | Yes | MAME master; [python_wizard](https://github.com/ptwz/python_wizard) push 2024-10-21 |
| 8 | [whackintalk](https://github.com/joehahndev/whackintalk) (port to Kotlin) | 18 MacinTalk-style voices incl. "zarvox" robot and singing voices | 5 ms frames, exact silences | MIT / CMUdict BSD-style | Yes | 0.3.0 (2026-09-28) |
| 9 | [narrator-ts](https://github.com/bitplane/narrator-ts) (port) | Amiga narrator sound with a free voice; monotone mode | Frames per phoneme (needs instrumentation) | MIT / own tables + public-domain NRL rules | Yes, after provenance review | 0.2.1 (2026-08-05) |
| 10 | [klatt-syn](https://github.com/chdh/klatt-syn) (port) | Base for our own Votrax, S.A.M. or DECtalk-like tables | Frames we supply | MIT | Yes | npm 1.0.9 (2026-06-26) |
| 11 | Effects: [Codec2](https://github.com/drowe67/codec2), LPC-10 ([spandsp](https://github.com/freeswitch/spandsp/blob/master/src/lpc10_decode.c)), [WORLD](https://github.com/mmorise/World) | Radio and military vocoder robot on any voice; WORLD flattens pitch and warps time | n/a | LGPL-2.1, LGPL-2.1, modified BSD | Yes (LGPL rules) | Codec2 1.2.0 (2023-07-24); WORLD v1.0.1 (2026-02-18) |
| 12 | [RHVoice](https://github.com/RHVoice/RHVoice) slt, ksp | HTS voices, 24 kHz | Word callbacks only | LGPL/GPL mix / ARCTIC | No | 1.18.4 (2026-03-31) |
| 13 | Android TTS, allow-listed installed engines | Whatever engine the user installs | `onRangeStart`, word level | per engine | n/a | API 21+ |

Ranking weighs distinct robot voices, timing control, licence clarity and Android effort.

Beat-grid method: for engines with duration input (2 to 10) compute each phoneme's duration so the vowel onset lands on the beat. For eSpeak NG and installed engines, render per syllable, read the onset from events, then shift and stretch with [Sonic](https://github.com/waywardgeek/sonic) (Apache-2.0, also in AndroidX Media3 as `SonicAudioProcessor`) or [Signalsmith Stretch](https://github.com/Signalsmith-Audio/signalsmith-stretch) (MIT). [eCantorix](https://github.com/divVerent/ecantorix) (GPL-3.0, Perl) does exactly this with eSpeak and sox and is a useful design reference.

## 2. Licence notes

- App licence should be GPL-3.0-or-later (eSpeak NG and Gnuspeech require it). It can absorb LGPL, Apache-2.0, MIT, BSD and MPL-2.0 code; AGPL-3.0 (MBROLA) is allowed through section 13 of both licences. GPL-2.0-only code is not compatible: the original [Talkie](https://github.com/going-digital/Talkie/blob/master/Talkie/talkie.cpp) says only "released under GPLv2 license".
- Program output is "not, in general, covered by the copyright on the code" unless the program copies parts of itself into it ([GPL FAQ](https://www.gnu.org/licenses/gpl-faq.html#WhatCaseIsOutputGPL)). eSpeak NG compiles 203 short GPL consonant samples into its data, so tiny processed fragments reach the audio. Low risk, but an interpretation.
- Voice data terms decide commercial use, separately from code.
- A repository without a licence grants no permission.
- In the EU, "neither the functionality of a computer program nor the programming language and the format of data files" are protected ([CJEU C-406/10](https://en.wikipedia.org/wiki/SAS_Institute_Inc_v_World_Programming_Ltd)). Re-implementing an old method is possible; copying its code or data tables is not.

## 3. Verified timing results

- **eSpeak NG 1.52.0** (autotools build), en-us+robosoft, "one two three four": events `w 0 ms`, `'V 78 ms (sample 1730)`, `n 118 ms`, word 2 at 169 ms, `t 218 ms`, `'u: 258 ms`, word 3 at 327 ms, end 798 ms. 22050 Hz 16-bit. `-P 0` gave a monotone (F0 standard deviation 0.6 Hz against 16.8 Hz). `--pho` output works only with MBROLA voices.
- **MBROLA** (us2 voice, test only): a `.pho` with four 500 ms words at flat 110 Hz rendered 2.093 s for 2.100 s requested, 110.3 Hz in all voiced frames, onsets at 550, 1050 and 1550 ms. Output runs 2 to 8 ms short at the end, so place onsets by computed position. 16 kHz.
- **Flite**: `flite -psdur` prints segment end times (kal: `pau:0.220 w:0.275 aa:0.416 n:0.471 ...`). Replacing `duration_model_func` and `f0_model_func` in the C API put kal exactly on a 120 BPM grid (words ending at 0.750, 1.250, 1.750, 2.250 s) at flat 110 Hz. `--setf int_f0_target_stddev=0` gives a monotone (kal 94/95/98 Hz at p10/p50/p90). The statistical (ClusterGen) voices ignore the duration hook because `cg_make_params` in `src/cg/cst_cg.c` recomputes durations; exact timing there needs a patch of about 20 lines or per-word SSML rate. kal is 8 kHz, the others 16 kHz.
- **hts_engine** with ARCTIC slt: time-stamped labels with phoneme alignment put word boundaries exactly at 0.25, 0.75, 1.25, 1.75 and 2.25 s; writing 110 Hz into the lf0 stream gave 109.3/110.1/110.6 Hz. 48 kHz, 5 ms frames.
- **rsynth**: a `.pho` asking for 1300 ms rendered 1.297 s; `+E` traces element durations (`W.79 U.299 N.119 ...`); `+M` holds F0 fixed. 11025 Hz default, 22050 Hz with `-r`. Built with `-fcommon -std=gnu89` and relaxed GCC 16 errors.
- **Gnuspeech SA**: built with one g++ command (no CMake); 44100 Hz; `-p` writes 16 parameters every 4 ms (pitch, glottal and fricative values, eight tube radii, velum); `gnuspeech_sa_trm` re-rendered a file with a flattened pitch column.
- **S.A.M. C port** (not shippable, no licence): 8-bit output at 22050 Hz; `-debug` prints per-phoneme lengths in frames. Report 10 has what may be used from its public description.
- **narrator-ts**: free voice, 22030 Hz 8-bit; `mode: 1` held about 108 to 116 Hz where mode 0 fell from 116 to 75 Hz.
- **SVOX Pico** ([naggety/picotts](https://github.com/naggety/picotts)): four runs of the same sentence gave 0.464, 1.648, 0.476 and 2.504 s on x86-64. Unusable as built.

## 4. Engine notes

**eSpeak NG.** Klatt source selection (`klatt N`, [klatt.c](https://github.com/espeak-ng/espeak-ng/blob/master/src/libespeak-ng/klatt.c)): 1 impulsive, 2 natural, 3 and 4 sampled, 5 sawtooth (buzziest; klatt5, robosoft), 6 NV Access speechPlayer. Rate 80 to 450 wpm; SSML prosody, breaks and marks. English data set about 0.88 MB; library 577 KB. Android: official engine on [F-Droid](https://f-droid.org/en/packages/com.reecedunn.espeak/) (1.52.0); embedding libespeak-ng via NDK and our own JNI is low effort; the C API is process-global (one synthesis thread). Original eSpeak (1.48.04, 2014) is superseded.

**Flite.** [COPYING](https://github.com/festvox/flite/blob/master/COPYING): "free to use in commercial products". ARCTIC voices: "This voice is free for use for any purpose (commercial or otherwise)" ([slt COPYING](http://festvox.org/cmu_arctic/cmu_arctic/cmu_us_slt_arctic/COPYING)). The 11 additional ARCTIC speakers ([listing](http://festvox.org/cmu_arctic/packed/)) have no licence statement: avoid. Plain C, no dependencies; kal library 1.45 MB, libflite 0.52 MB. The old Android wrapper (2017, NDK r10e) is obsolete, so we write our own NDK build and JNI.

**MBROLA voices** ([repo](https://github.com/numediart/MBROLA-voices), commit 2020-03-30): en1 (Edinburgh CSTR data) "Permission to use, copy, modify, distribute this database for any purpose is hereby granted without fee": allowed. us1, us2 (Babel Technology, now part of Acapela/Tobii Dynavox) and us3 (OGI) "may not be sold or incorporated into any product which is sold without prior permission": excluded until written permission. MBROLA has no Android port; plain C library mode (`init_MBR`, `write_MBR`, `read_MBR`) makes it easy.

**RHVoice.** Code: LGPL-2.1+ core, GPL-3.0 combination with MAGE ([License.md](https://github.com/RHVoice/RHVoice/blob/master/doc/en/License.md)). Voices: slt and ksp use the CMU licence (allowed); bdl, clb, alan have no licence file (unverified); dasha-eng is CC BY-SA 4.0 (unclear for audio); evgeniy-eng and lyubov are CC BY-NC-ND 4.0 (excluded). The Android app calls `rangeStart` per word.

**Speech chips.** MAME's emulators are BSD-3-Clause (verified headers of tms5220.cpp, tms5110.cpp, votrax.cpp, sp0256.cpp, mea8000.cpp and others in [src/devices/sound](https://github.com/mamedev/mame/tree/master/src/devices/sound)). The data is the problem: Votrax SC-01 needs its 512-byte internal ROM, SP0256-AL2 its 2 KB allophone ROM, and Speak & Spell / TI-99 words are TI ROM data; [ArminJo/Talkie](https://github.com/ArminJo/Talkie) (GPL-3.0, 1.4.0, 2024-11-05) ships "over 1000 words of speech data" from such products. Clean route: TMS model plus our own LPC frames encoded from our own audio (python_wizard and [BlueWizard](https://github.com/patrick99e99/BlueWizard), MIT, show the method). The Philips MEA 8000 needs no ROM but has no open encoder (not researched further).

**Codecs.** Codec2's README says modes other than 3200 are not actively maintained. spandsp's LPC-10 is based on the US DoD reference code: "They do not exert copyright claims on their code". MELP: not researched; avoid.

**Gnuspeech** is C++ (about 22,000 lines, 2.6 MB data), GPL-3.0+. **rsynth** is C (about 8,900 lines); avoid its BEEP and Oxford dictionaries (Debian put rsynth 2.0 in non-free because "commercial use of the file should contact the Oxford University Press", [Debian](https://sources.debian.org/src/rsynth/2.0-6/debian/copyright/)).

**New retro re-creations.** whackintalk: "a from-scratch recreation of the classic MacinTalk formant speech synthesizer", Klatt 1980 with KLGLOTT88, 22050 Hz, not run (needs a TypeScript build). narrator-ts: MIT, "free voice" built "from published phonetics", NRL 7948 rules; its renderer was checked "sample-exact" against the original binary, so review its provenance notes before shipping. Both are weeks old with a single author; listen before committing.

**Other Klatt and articulatory code.** GPL-3.0: [rhdunn/klatt](https://github.com/rhdunn/klatt) (2015), [klatt80](https://github.com/jh4xsy/klatt80), Praat KlattGrid (GPL). [Pink Trombone](https://dood.al/pinktrombone/) is MIT (2017) but has no text front end. [VocalTractLab](https://www.vocaltractlab.de/index.php?page=vocaltractlab-download) 2.4 (2025-12-05) is GPL but heavy and its speaker-file terms are not stated. [STK VoicForm](https://github.com/thestk/stk) (MIT-style, 5.0.1, 2023-08-10) gives sung robot vowels. [LESS](https://github.com/mlyoung101/LESS) (MPL-2.0, unfinished, 2022) aims at the Atari ST STSPEECH sound.

## 5. Clean-room options for excluded classics

| Target | Why excluded | Clean route |
|---|---|---|
| S.A.M. (1982) | No licence. The ports name SoftVoice, Inc. as owner ([sam-js](https://github.com/discordier/sam)); its site [text2speech.com](http://www.text2speech.com/) is still online and offers licensing | narrator-ts free voice (same author's Amiga successor), or our own S.A.M.-style renderer from the public method description in the [s-macke README](https://github.com/s-macke/SAM) (two sines plus a rectangle wave, 4-bit amplitudes, 8-bit output), tables from published formant data, text via the public-domain NRL 7948 rules ([DTIC](https://apps.dtic.mil/sti/pdfs/ADA021929.pdf), [17 U.S.C. 105](https://www.law.cornell.edu/uscode/text/17/105)). Do not copy S.A.M. code or tables. Small engine plus tuning by ear. |
| Votrax SC-01 | Internal ROM | MAME BSD-3 filter model with our own 64-phoneme table |
| SP0256-AL2 | Allophone ROM | BSD-3 LPC model with our own allophones (no open SP0256 encoder found) |
| Speak & Spell | TI ROM words | TMS model plus our own LPC frames (rank 7) |
| DECtalk | Proprietary (below) | Klatt voice with our own rules (klatt-syn); whackintalk shows a from-scratch Klatt TTS |
| Amiga narrator, MacinTalk | Original binaries | narrator-ts, whackintalk |

## 6. Engines to avoid

| Engine | Reason |
|---|---|
| S.A.M. and all ports (s-macke C, sam-js, ESP8266SAM, zam, SamSharp, OVOS plugin) | No licence; port authors call it "abandonware" while the owner still exists |
| DECtalk ([dectalk/dectalk](https://github.com/dectalk/dectalk/blob/develop/LICENCE)) and derivatives such as dectalk-wasm | LICENCE: "proprietary to ... FONIX Corporation ... authorized only pursuant to a valid written license" |
| [AmigaNarrator](https://github.com/nicodex/AmigaNarrator) | Runs the original Amiga binaries ("This file is not supplied") |
| Original chip ROM data (Votrax, SP0256, TI words, Talkie vocabularies) | Third-party copyright, no permission found |
| MBROLA us1, us2, us3 | No-sale clause; needs written permission |
| SVOX Pico | Non-deterministic output on 64-bit (verified); AOSP disabled it because it "doesn't work when built 64-bit"; DMCA claims against wrapper files ([notice copy](https://github.com/gmn/nanotts/blob/master/DMCA_Takedown_Notice_from_Cambridge_Mobile_LTD)); lingware without source |
| Festival on Android, Mimic 1, MaryTTS | Heavy (C++/Scheme, Java with `javax.sound`); Mimic 1 adds an unlicensed voice; Festival's British voices need the OALD lexicon ("may not be used for commercial purposes"); MaryTTS DFKI voices are CC BY-ND (unclear for audio) |
| RHVoice evgeniy-eng, lyubov; Flite's 11 additional ARCTIC voices | Non-commercial terms, or no licence |
| Google or Samsung system voices for export | No terms found for commercial use of generated audio |
| Chipspeech, softvoice-sapi5, Qlatt, retrotalk character presets | Commercial, proprietary engine inside, no licence, or imitation of named characters |
| Sinsy, Open JTalk | Japanese only |
| VocalTractLab | Effort; speaker-file terms not stated |

## 7. Android TextToSpeech as a source

- `synthesizeToFile(CharSequence, Bundle, File, String)` (API 21) and the `ParcelFileDescriptor` variant (API 30). AOSP [FileSynthesisCallback](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/speech/tts/FileSynthesisCallback.java) writes a 44-byte WAV header with the engine's rate and format (8-bit, 16-bit or float). Input limit 4000 characters.
- [UtteranceProgressListener](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener): `onStart`/`onDone` (15), `onStop` (23), `onBeginSynthesis` and `onAudioAvailable` (24, raw PCM, also for file synthesis), `onRangeStart(id, start, end, frame)` (26), "Only called if the engine supplies timing information". File synthesis forwards it.
- eSpeak NG's service reports words only ([TtsService.java](https://github.com/espeak-ng/espeak-ng/blob/master/android/src/com/reecedunn/espeak/TtsService.java)); RHVoice also reports words. Google's engine: not researched.
- Apps targeting API 30+ need `<queries>` with action `android.intent.action.TTS_SERVICE` ([docs](https://developer.android.com/training/package-visibility/use-cases)).
- Engines in separate apps do not impose their code licence on ours, but their voice terms still apply. Recommendation: allow export only from an allow-list (eSpeak NG, RHVoice slt/ksp).

## 8. Open questions, risks and points not researched

- Listening tests by Lloyd are needed: no audio was judged by ear in this research.
- whackintalk and narrator-ts are very new single-author projects; narrator-ts provenance (disassembly-based renderer) needs a decision.
- MBROLA us1/us2/us3 and S.A.M.: permission could be requested from Tobii Dynavox/Acapela, OGI and SoftVoice, Inc. S.A.M. would also need a licence from the port author.
- HTS slt requires attribution (CC BY 3.0) in released music credits or app credits; decide where.
- Output sample rates range from 8 kHz to 48 kHz; export needs a resampler (for example [libsamplerate](https://github.com/libsndfile/libsamplerate), BSD-2-Clause, 0.2.2 from 2021).
- eSpeak NG event positions drift at sonic-accelerated rates (fix only in unreleased 1.53.0): stay at or below 450 wpm.
- Not researched: Google TTS range callbacks and terms; MEA 8000 encoding; MELP licensing; Codec2 Android build; SoundTouch and Rubber Band licence details; the Votrax SC-02 model; voice names that may raise trademark issues (use descriptive names, not "Speak & Spell" or "MacinTalk").
