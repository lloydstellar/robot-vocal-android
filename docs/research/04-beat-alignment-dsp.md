# Beat alignment DSP: putting synthesized syllables on a BPM grid

Research note for Robot Vocal (Android, Kotlin + Compose). Offline render at 48 kHz, WAV export, preview with an example drum loop. Written 2026-10-18. Licence rule from the project: GPL is fine (the app is open source), anything with non-commercial or "no sale" terms is not. Lyrics are English only.

No listening tests or timing measurements were run; everything below comes from papers, documentation and source code. Points marked "not researched" are open.

## 1. Short answer

- Put the **vowel onset** of each syllable on the grid, not the start of the consonant. For long onsets (/str/, /spl/), move the anchor a little earlier, into the consonant cluster.
- Render first, then **place each syllable at a frame computed from its beat index** using exact rational arithmetic. Never accumulate per-beat lengths.
- Use engines whose phoneme durations you control where possible (strategy B). Fall back to engine phoneme timings plus vowel-only stretching (strategy C). Per-word rendering and placement (strategy A) is the quickest first version.
- Mix vocal and drums into one audio stream for the preview. Two players cannot be kept sample-locked.
- Write an `acid` chunk with tempo and beat count in the WAV. REAPER reads it; for Ableton, LA2L sets tempo itself.

## 2. Which point of a syllable sits on the beat

| Finding | Source |
|---|---|
| P-centre model: P = αx + βy + k, x = onset consonant duration up to vowel onset, y = vowel plus coda. Compromise values α = 0.65, β = 0.25 explained 88% and 96% of variance in two digit sets. Relative to the vowel onset: dPv = -(1-α)dC + βdV, so about -0.35 ms per ms of onset and +0.25 ms per ms of rime. | Marcus 1981, [PDF](https://drstephenmmarcus.com/publications/Perception-Psychophysics-1981-P-Centers.pdf) |
| Marcus located vowel onsets "from the most rapid increase in energy in the region of the first two formants (500-1,500 Hz)", within 15 ms of manual marking. | same |
| Other slopes disagree: deleting /ʃ/ frication moved the P-centre by 0.95 ms per ms (P-centre stays near the vowel); production data give onset slope about 0.75, vowel about 0.25. | Cooper et al. 1986, Perez 1997, summarised in [Villing 2010](https://mural.maynoothuniversity.ie/2284/1/Villing_2010_-_PhD_Thesis.pdf) |
| Clusters: as onsets grew from about 100 to 220 ms, the metronome click moved about 0 to 85 ms before the vowel (Swedish /s, st, str/). German /ʃt, ʃtr/ words: taps 20 to 40 ms before the vowel. Simple CV German syllables: about 10 ms before to 30 ms after the vowel onset. | Rapp-Holmgren 1971, Janker 1996a/b, via Villing 2010 |
| Equal durations, voiced stops had P-centres 27 ms earlier than voiceless stops. | Harsin 1997, via Villing 2010 |
| In English connected speech, the "fastest energy rise" landmark (maxD) and the vowel onset predict taps equally (intercept 0.33 ms, not significant). | Rathcke et al. 2024, [JASA 155(4)](https://pubs.aip.org/asa/jasa/article/155/4/2698/3283278/Testing-an-acoustic-model-of-the-P-center-in) |
| Speaking to a 250 ms metronome: inter-vowel intervals SD 6.10 ms, inter-syllable-onset intervals SD 18.86 ms. Synthesized trains with regular syllable onsets had irregular vowel onsets (SD 19.29 ms). | Rathcke 2025, [Communications Biology](https://pmc.ncbi.nlm.nih.gov/articles/PMC11754750) |
| "There is no generally accepted model of the exact P-center location"; it is "mostly assumed to lie somewhere close to the vowel onset". The first vowel after silence is anticipated more. | Rathcke, [CUP chapter](https://www.cambridge.org/core/books/rhythms-of-speech-and-language/pcenter-effect-and-the-domain-of-beat-perception-in-speech/B2C7ECDB04F16EE327A38445D2C8EFF7) |
| Rap analysis uses the vowel onset (midpoint of its amplitude rise) as the syllable's time point; rappers drift expressively, for example 52 ms behind and 72 ms ahead of the beat. | Ohriner 2019, [MTO](https://mtosmt.org/issues/mto.19.25.1/mto.19.25.1.ohriner.html) |
| Rap vocal onsets land later than other genres and have the smallest spread. | Georgieva et al. 2026, [TISMIR](https://transactions.ismir.net/articles/10.5334/tismir.278) |

**Rule for the app (inference from the table):** anchor = vowel onset minus a lead ℓ = min(λ·C, ℓmax), with C the onset length. Default λ = 0.25 (sourced range 0.05 to 0.39), ℓmax = 60 ms, both user-tunable as a "feel" control. Ignore the rime term by default, because rime effects are weaker and less reliable (Cooper 1988, Harsin 1997 in Villing 2010).

## 3. How singing synthesizers do it

| Tool | Convention | Source |
|---|---|---|
| UTAU | Preutterance "determines how much of the sample is before the beginning of a note" and sits at the end of the consonant; overlap is the crossfade with the previous note. | [UTAU Wiki](https://utau.wikidot.com/tutorials:cv-otoing-guide-by-kiyoteru) |
| OpenUtau (MIT) | Audio starts `preutter` ms before the note. Consonant scale = 2^(1 - VEL/100). Auto-fit: if preutter - overlap > 0.5 × previous note, scale both down; preutter never exceeds the previous note or a shorter gap. | [UPhoneme.cs](https://github.com/stakira/OpenUtau/blob/master/OpenUtau.Core/Ustx/UPhoneme.cs) |
| Yamaha (VOCALOID origin) | Consonant starts "earlier than the actual singing-starting time point", vowel start "set equal to" it. | [US7124084B2](https://patents.google.com/patent/US7124084B2/en) |
| Sinsy | Singers start consonants before the note; the first vowel is the timing reference for the note. | [Hono et al. 2021](https://arxiv.org/abs/2108.02776) |
| Synthesizer V 2 | Phoneme Timing panel; splitting notes to fix phoneme length "no longer recommended"; Rap Mode per note. | [docs](https://sv2.docs.dreamtonics.com/en/prev-version-changes), [notes panel](https://sv2.docs.dreamtonics.com/en/notes-panel) |

All of them align the vowel with the note and borrow consonant time from the previous note. OpenUtau's auto-fit rule is a good model for syllables that do not fit.

## 4. Strategies

| | A: render per syllable or word, place | B: set phoneme durations in the engine | C: render naturally, warp to grid |
|---|---|---|---|
| Timing accuracy | Sample-exact placement; anchor needs phoneme timing | Exact by construction | Depends on markers and stretcher |
| Sound | Choppy joins, lost coarticulation if split per syllable; acceptable as robot style | Most natural joins | Stretch artefacts, mostly in long stretches |
| Needs | Vowel onset per chunk | Duration API | Phoneme timings or detection, a stretcher |
| Fits | eSpeak NG, sherpa-onnx voices | Own formant synth, own diphone engine, Flite | Piper (alignments), Kokoro |

Inference: B sounds best and is the most reliable where available. A per word (not per syllable) is the fastest way to a working product. C is the route for neural voices.

## 5. Engines: timing and duration control

| Engine | Timing out | Duration in | Licence | Notes |
|---|---|---|---|---|
| eSpeak NG 1.52 | `espeakEVENT_PHONEME`, `audio_position` "the time in mS"; enable with `espeakINITIALIZE_PHONEME_EVENTS`; `espeak_SetPhonemeTrace(espeakPHONEMES_MBROLA)` prints .pho with durations | Only global rate (`espeakRATE` 80 to 450 wpm) and SSML; no per-phoneme setter in speak_lib.h | GPL-3.0 | [speak_lib.h](https://github.com/espeak-ng/espeak-ng/blob/master/src/include/espeak-ng/speak_lib.h) |
| Flite | Segment item feature `end` | `cart_duration` sets `end` from z-scores × `duration_stretch` × token `local_duration_stretch`; module is the voice feature `duration_model_func`, so it can be replaced. Clustergen voices compute per-state durations in `cg_make_params` in `frame_advance` steps (patch there). | BSD-style | [cst_synth.c](https://github.com/festvox/flite/blob/master/src/synth/cst_synth.c), [cst_cg.c](https://github.com/festvox/flite/blob/master/src/cg/cst_cg.c) |
| MBROLA | n/a | .pho: phoneme, duration ms, pitch targets (% of phone, Hz) | Binary AGPL-3.0; voices "may not be sold or incorporated into any product which is sold" | Excluded by the licence rule. Copy the .pho idea for an own diphone engine. [README](https://github.com/numediart/MBROLA), [voice terms](https://github.com/numediart/MBROLA-voices/blob/master/LICENSE.md) |
| SAM (s-macke) | Frame counts from `phonemeLengthTable` | Per-phoneme frames | No licence ("Abandonware") | Excluded; write an own SAM-like synth. [README](https://github.com/s-macke/SAM) |
| Piper (piper1-gpl 1.8.0) | Experimental since 1.3.1: patched ONNX exposes `w_ceil`, giving samples per phoneme id | `length_scale` only; per-phoneme override needs own graph edit | GPL-3.0 | [ALIGNMENTS.md](https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/ALIGNMENTS.md) |
| Kokoro | `pred_dur` frames, 600 samples at 24 kHz (25 ms) | Replaceable in PyTorch code before the alignment matrix; ONNX needs a split export | Apache-2.0 | [pipeline.py](https://github.com/hexgrad/kokoro/blob/main/kokoro/pipeline.py), [model.py](https://github.com/hexgrad/kokoro/blob/main/kokoro/model.py) |
| sherpa-onnx | `SherpaOnnxGeneratedAudio` has samples, n, sample_rate only | none | Apache-2.0 | [c-api.h](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/c-api/c-api.h) |
| Android TTS | `onRangeStart` gives a text range and frame, "only called if the engine supplies timing information" | none | platform | [UtteranceProgressListener](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener) |

## 6. Time-stretch libraries

| Library | Licence | Fit for this app | Source |
|---|---|---|---|
| Rubber Band 4.0 | GPL-2.0-or-later or commercial | `setKeyFrameMap` maps source frames to output frames (offline only): warp markers. R3 "higher-quality ... vocals", "much more CPU" | [site](https://breakfastquay.com/rubberband/), [header](https://github.com/breakfastquay/rubberband/blob/default/rubberband/RubberBandStretcher.h) |
| Signalsmith Stretch | MIT | Sounds best between 0.75x and 1.5x; variable ratio through block sizes; `seek`/`flush` for exact length | [README](https://github.com/Signalsmith-Audio/signalsmith-stretch) |
| Bungee 2.4 | MPL-2.0 | Phase vocoder; position set per grain (about 100 per second); Android builds | [README](https://github.com/bungee-audio-stretch/bungee) |
| SoundTouch 2.4 | LGPL-2.1 | WSOLA-like; `-speech` parameter set | [site](https://www.surina.net/soundtouch/) |
| Sonic | Apache-2.0 | Speech speed-up over 2x; Media3's Sonic.java is based on it | [README](https://github.com/waywardgeek/sonic) |
| WORLD | modified BSD | Analysis/resynthesis; flat F0 for robot sound | [README](https://github.com/mmorise/World) |

Artefacts: WSOLA "introduces noticeable stuttering artifacts" at transients; the phase vocoder smears transients and adds "phasiness"; identity phase locking reduces both ([Driedger and Müller, DAFx-14](https://www.audiolabs-erlangen.de/content/resources/MIR/TSMtoolbox/2014_DriedgerMueller_TSM-Toolbox_DAFX.pdf)). Non-uniform compression that mimics fast speech beat linear compression: comprehension +5 to 31 points, preferred 95% of the time ([MACH1](https://engineering.purdue.edu/~malcolm/interval/1997-060/)). Klatt's rule DUR = ((INHDUR - MINDUR) × PRCNT)/100 + MINDUR gives each phone a floor, for example /s/ 125 ms inherent, 50 ms minimum ([Klatt 1979](https://www.coli.uni-saarland.de/Phonetics/icphs/ICPhS1979/p9.2_290.pdf)).

So: stretch or shorten only the steady middle of vowels, keep consonants, and compress consonants no further than their MINDUR. A self-written pitch-synchronous OLA (TD-PSOLA style) on vowel middles is enough for robot vocals and can flatten F0 at the same time; Rubber Band R3 is the quality option (GPL is allowed).

## 7. When no engine timing exists

- Vowel onset: maximum energy rise in 500 to 1,500 Hz (Marcus 1981), or maxD: 40 ms energy window at 1 ms hop, moving average order 6, difference, drop frames with zero-crossing rate above 4000, smooth (order 10), take the maximum (Rathcke et al. 2024).
- Syllable nuclei: intensity peaks above the median (+0 or 2 dB) with a preceding dip of 2 or 4 dB, voiced only; r = .71 to .88 against human counts ([de Jong and Wempe 2009](https://www.fon.hum.uva.nl/archive/2009/2009-brm-JongWempe.pdf)).
- Published vowel-onset detectors get "the majority of VOPs within 40 ms" ([Tripathi and Rao 2019](https://arxiv.org/abs/1908.08668)). Forced aligners on TIMIT put 63% (MFA) to 70% (WebMAUS) of phone onsets within 20 ms ([Charsiu, Table 1](https://arxiv.org/abs/2110.03876)). Both are too coarse for grid work, so engine timings come first.
- On Android: Vosk (Apache-2.0) gives word times and a grammar-constrained recognizer, no phone times in its C API ([vosk_api.h](https://github.com/alphacep/vosk-api/blob/master/src/vosk_api.h)). Licences of analysis code: aubio GPL-3.0, Essentia AGPL-3.0, librosa ISC, aeneas AGPL-3.0 (GitHub metadata).

## 8. Sample-accurate maths

Keep BPM as a fraction P/Q (127 = 127/1, 127.5 = 255/2), sr = 48000, g grid steps per beat, all in 64-bit integers.

- Frames per beat F = 60·sr·Q/P. At 127 BPM: 2,880,000/127 = 22677.165 frames; a 16th is 5669.29 frames (118.1 ms).
- Grid frame of step k: t(k) = floor((120·sr·Q·k + P·g) / (2·P·g)) (round half up), always from k.
- Drift if you add a rounded 22677 per beat instead: 21/127 frame lost per beat, 42 frames (0.88 ms) after 256 beats, 210 frames (4.4 ms) after 1,270 beats.
- Swing on 16ths with ratio s (0.5 straight, 2/3 triplet feel): step position u(k) = k + (k odd ? 2s - 1 : 0), t = round(F·u/g). The percentage convention is not researched; expose s directly.
- Loop length for B beats: N = floor((120·sr·Q·B + P) / (2P)), the same as LA2L's `Math.round(rate * 60 / bpm * beats)` (LA2L docs, `references/web.md`). 4 beats at 127 BPM: 90,709 frames.
- Tail: render into N + T frames, then out[n] = Σ buf[n + mN] for m ≥ 0, n < N. Pre-roll before frame 0 wraps to the loop end the same way.
- Engine rate to 48 kHz: n48 = round(n_engine · 48000 / r_engine), for example × 320/147 from 22,050 Hz.
- Any effect with latency L frames: shift its output by -L.

## 9. Preview playback

- One stream, one mix: both buffers have length N and are summed in one callback or one write loop, so they cannot drift. Oboe's RhythmGame sample mixes its backing track and claps with a Mixer in one callback and keeps the UI in sync from the song position updated in `onAudioReady` ([README](https://github.com/google/oboe/blob/main/samples/RhythmGame/README.md)). Oboe 1.11.0 (Apache-2.0) ships as Prefab `com.google.oboe:oboe`.
- `AudioTrack.getTimestamp` returns a frame and the time it "was presented or is committed to be presented"; poll "approximately once every 10 seconds to once per minute" once stable, and extrapolate. `AudioTimestamp` uses the `System.nanoTime()` timebase and "cannot account for any delay unknown to the implementation" ([AudioTrack](https://developer.android.com/reference/android/media/AudioTrack), [AudioTimestamp](https://developer.android.com/reference/android/media/AudioTimestamp)).
- `setLoopPoints` needs MODE_STATIC and a stopped or paused track; low-latency mode needs the sink's sample rate.
- Playhead: frame = ts.framePosition + (now - ts.nanoTime)·sr/1e9, position = frame mod N; read it in `drawBehind` or a lambda modifier so only the draw phase reruns ([Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)).
- Android 17 blocks playback from apps that are not visible and have no foreground service, silently ([behavior changes](https://developer.android.com/about/versions/17/behavior-changes-all#bg-audio)). Stop the preview on background.
- Whether timestamps include Bluetooth delay: not researched.

Recommendation (inference): AudioTrack MODE_STREAM, float, 48 kHz, a writer thread that mixes vocal + gain × drums per block and ramps the drum gain over about 5 ms when toggled. Move to Oboe only if this proves too coarse.

## 10. Tempo metadata in WAV

`acid` chunk, 24 bytes after the header ([libsndfile wav.c](https://github.com/libsndfile/libsndfile/blob/master/src/wav.c)):

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | flags: 0x01 one-shot, 0x02 root note set, 0x04 stretch on, 0x08 disk based, 0x10 unknown (set by Acidizer) |
| 4 | u16 | root note |
| 6 | u16 | unknown, 0x8000 |
| 8 | f32 | unknown, 0 |
| 12 | u32 | number of beats |
| 16 | u16 + u16 | meter denominator, numerator (libsndfile is unsure of the order) |
| 20 | f32 | tempo |

`smpl` has loop points but no tempo (same file); BWF `bext` v2 has no tempo field ([EBU Tech 3285](https://tech.ebu.ch/docs/tech/tech3285.pdf)); iXML has no tempo element, its SPEED object is film speed ([iXML](http://www.gallery.co.uk/ixml/object_Details.html)).

| DAW | Reads tempo from | Source |
|---|---|---|
| REAPER | `acid` (since 1.58), file name, cue/marker metadata (7.56); writes `acid` on render | [whatsnew.txt](https://www.reaper.fm/whatsnew.txt) |
| Ableton Live 12 | Manual names no WAV tempo metadata; assumes short samples are "1, 2, 4, 8, or 16 bars" | [manual](https://www.ableton.com/en/manual/audio-clips-tempo-and-warping/) |
| FL Studio | FL Studio 5 (2004): "Retains ACID Loop markers and allows following of project tempo"; current versions unverified | [Sonicstate](https://sonicstate.com/news/2004/11/26/fl-studio-5-ships) |
| Logic Pro | 10.1 manual: Apple Loops, Flex Time regions and Logic's own bounces follow tempo; `acid` unverified | [Apple](https://help.apple.com/logicpro/mac/10.1/en.lproj/lgcpef37cd28.html) |
| Bitwig | unverified | |

Write `acid` (flags 0x00 for a loop, beats, 4/4, tempo) and put tempo and beats in the file name, for example `robot_127bpm_4beats.wav`. LA2L remains the authoritative path into Live.

## 11. Existing vocal quantizers

Live's Quantize moves "the nearest transient to the closest grid line" ([manual](https://www.ableton.com/en/manual/audio-clips-tempo-and-warping/)). Melodyne's Quantize Time moves each note's "musical starting point", "the moment the sound really unfolds and the pitch first becomes discernible", toward its beat ([help](https://helpcenter.celemony.com/M5/doc/melodyneEssential5/en/M5tour_MacroTiming)): close to the vowel-onset rule. VocAlign and Revoice Pro align a dub to a guide recording, not to a grid ([VocAlign](https://www.synchroarts.com/products/vocalign-ultra/overview), [Revoice](https://www.synchroarts.com/products/revoice-pro/overview)). Auto-Align 2 corrects "time and phase issues between multiple mics" and is no quantizer ([Sound Radix](https://www.soundradix.com/products/auto-align-2/)). Open source: [RoboRap](https://github.com/AyiStar/RoboRap) (no licence) stretches each cloud-TTS word to one beat with librosa, using the word start as reference; no other grid quantizer for speech was found.

## 12. Recommended pipeline

1. **Grid.** BPM as P/Q, meter, grid g, swing s. Each syllable gets a start step k and a length. Compute t(k) per section 8.
2. **Phonemes and syllables.** English lyrics to phonemes (engine front end), split into syllables: onset consonants, vowel, coda.
3. **Render.** Per engine: B, synthesize with planned durations (step 5) in one pass; A, render per word; C, render the phrase. Resample to 48 kHz and map timings.
4. **Timings per syllable.** Consonant onset c, vowel onset v, end e, from the engine (eSpeak events, Flite `end`, Piper `w_ceil`, Kokoro `pred_dur`, own synth). Refine v within ±10 ms with the 500 to 1,500 Hz energy-rise detector. Detection only as fallback.
5. **Plan.** C = v - c. Lead ℓ = min(0.25·C, 60 ms). Pre-roll R = C - ℓ. Place the render so that frame v - ℓ lands on t(k) + δ (δ = global feel, default 0).
   - Pre-roll limit (OpenUtau rule): R ≤ 0.5·(t(k) - t(kprev)) and R ≤ any silent gap. If not, compress onset consonants by f = max(ΣMINDUR / C, limit / C), then allow a 5 to 10 ms crossfade overlap.
   - Body: Bd = (t(knext) - Rnext) - (t(k) + ℓ) - gate gap. Vowel target V* = Bd - coda, coda kept natural but not below MINDUR.
   - Longer than natural: B sets the duration; C stretches the vowel's middle 60% (PSOLA/WSOLA or Rubber Band key frames); or a "chop" mode keeps the natural length and leaves silence.
   - Shorter: shorten the vowel to at least 40 ms (Klatt MINDUR for /ə/), then the coda to MINDUR, then cut with a 5 ms fade.
6. **Robot DSP** after timing, with latency compensated.
7. **Mix and loop.** Buffer N + T, sum each syllable at its frame, wrap tail and pre-roll into N.
8. **Export.** 48 kHz WAV, 24-bit or float, `acid` chunk, tempo in file name, send to LA2L as `kind: loop` with `bpm` and `lengthBeats`.
9. **Preview.** Same N-frame vocal buffer plus drum buffer in one stream, playhead from the audio timestamp.

## 13. Build order

1. Grid, swing, loop length and wrap maths with unit tests (127 BPM, 4 beats = 90,709 frames).
2. Preview stream with drums, toggle and playhead.
3. eSpeak NG per word with phoneme events, placement by vowel onset, chop mode. First usable robot rap.
4. Vowel-only fitting (own PSOLA/WSOLA), consonant compression with Klatt floors, OpenUtau-style auto-fit.
5. Own SAM-like formant synth with direct phoneme durations (strategy B).
6. Neural voices through strategy C (Piper alignments, Kokoro `pred_dur`).
7. Export metadata and LA2L.

## 14. Not researched

Accuracy of eSpeak NG phoneme events against the audio; detector error on synthetic voices; Rubber Band key-frame accuracy and CPU cost on a Pixel 8; listening tests of the three strategies; Bluetooth latency in timestamps; current FL Studio, Logic and Bitwig handling of `acid`; MPC-style swing percentages; Scott 1998, Howell 1988, Hermes 1990 and YIN in the original; DiffSinger and VOCALOID manual conventions; resampler choice (libsamplerate BSD-2-Clause and r8brain MIT exist).
