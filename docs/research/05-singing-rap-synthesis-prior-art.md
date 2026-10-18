# Prior art: text on musical notes with exact timing

Status: compact report, written 2026-10-18. Open points are marked *not researched*.

Licence rule for this project: the app will be open source, so GPL and AGPL code is **allowed**. Anything with non-commercial, research-only or no-sale terms (code, voices, models, data) is **excluded**, because the output goes into released music. A repository without any licence is also treated as excluded (no grant means all rights reserved). Lyrics are English only.

## 1. Key findings

- **Score-driven synths put the vowel on the note start, and the consonant before it.** Sinsy uses the first vowel of each note as the reference for its time-lag model ([Hono et al. 2021](https://arxiv.org/abs/2108.02776)), and NNSVS does the same ([arXiv 2210.15987](https://arxiv.org/html/2210.15987)). OpenUtau's DiffSinger phonemizer treats each vowel as "the first phoneme in the note's timespan" and stretches predicted durations between those anchors ([source](https://github.com/openutau/OpenUtau/blob/master/OpenUtau.Core/DiffSinger/DiffSingerBasePhonemizer.cs)). UTAU starts a sample `preutter` ms before the note ([UPhoneme.cs](https://github.com/openutau/OpenUtau/blob/master/OpenUtau.Core/Ustx/UPhoneme.cs)). Speech research points the same way: the perceived onset of a syllable lags its acoustic onset and may reflect "anticipation of a vowel onset" ([Rathcke et al., JASA 2024](https://doi.org/10.1121/10.0025777)).
- **At fast tempos the consonant lead-in has to shrink.** OpenUtau and Festival both have explicit rules for this (section 2).
- **480 ticks per quarter note is the common unit** (UST, USTX, VPR, UtaFormatix). Synthesizer V uses 705,600,000 "blicks" per quarter ([SVP.cs](https://github.com/openutau/OpenUtau/blob/master/OpenUtau.Core/Format/SVP.cs)).
- **Commercial "rap" is a per-note pitch mode, not a timing mode.** Synthesizer V 1.9.0b1 (2023-04-18) added Manual, Sing and Rap pitch modes per note, with intonation and tone controls, for English and Mandarin ([release notes](https://dreamtonics.com.cn/?p=941)). For VOCALOID6 we found no rap mode, only a voice (ALLEN) listed under Rap and Hip Hop ([vocaloid.com](https://www.vocaloid.com/en)).
- **Research rap generators take no durations.** Freestyler generates rap from lyrics plus accompaniment and "does not have duration conditions" ([arXiv 2408.15474](https://arxiv.org/html/2408.15474v1)). RapVerse has no beat or tempo input ([arXiv 2405.20336](https://arxiv.org/html/2405.20336v3)). Neither can guarantee grid lock.
- **We found no maintained open-source project that makes TTS rap on a BPM grid.** The GitHub sweep (12 `gh search repos` queries, 2026-10-18) turned up only hackathon prototypes without a licence or docs.

## 2. How existing tools place syllables in time

**UTAU / OpenUtau (classic voicebanks).** Each oto.ini entry has offset, consonant, cutoff, preutterance and overlap. The sample starts `preutter` ms before the note, and the previous note fades out over `overlap` ms. OpenUtau computes `consonantStretch = 2^(1 - velocity/100)`, so velocity 100 gives 1.0 and 200 gives 0.5. If notes are adjacent, the lead-in may take at most half of the previous note. If there is a gap shorter than the preutterance, the lead-in shrinks to fit the gap, and overlap shrinks with it. User edits are stored as deltas (`preutterDelta`, `overlapDelta`). Source: [UPhoneme.cs](https://github.com/openutau/OpenUtau/blob/master/OpenUtau.Core/Ustx/UPhoneme.cs).

**OpenUtau phonemizers.** A phonemizer returns phonemes with a tick `position` ([Phonemizer.cs](https://github.com/openutau/OpenUtau/blob/master/OpenUtau.Core/Api/Phonemizer.cs)). `SyllableBasedPhonemizer` gives leading consonants negative positions, so they sound before the note. Transitions default to 100 ms, scaled by `(300 - clamp(bpm, 90, 300)) / 210 / 3 + 0.33`, which works out to roughly 0.33 to 0.66. Transitions are then compressed to at most 80% of the note ([source](https://github.com/openutau/OpenUtau/blob/master/OpenUtau.Plugin.Builtin/SyllableBasedPhonemizer.cs)). Notes and phonetics are kept as separate layers: "music is music, phonetics is phonetics" ([wiki](https://github.com/openutau/OpenUtau/wiki/tech-note-%E2%80%90-music-is-music%2C-phonetics-is-phonetics-%28also-why-you-don%27t-need-legacy-plugins%29)). A `+` lyric extends the previous syllable ([wiki](https://github.com/openutau/OpenUtau/wiki/Developing-new-phonemizers)).

**Festival singing mode** ([singing-mode.scm](https://github.com/festvox/festival/blob/master/lib/singing-mode.scm), public domain). Each syllable starts earlier by the "perceptual onset" of its first phone, from a measured English table: t 50 ms, d 90, p 80, b 80, k 90, g 100, l 30, s 40, m 40, n 40. When a syllable is shorter than its natural length, all phones shrink proportionally. When it is longer, only the vowel stretches. One quirk is worth testing for: the tempo is computed as `bpm / 50.0`, with the comment "change this back to 60".

**Neural SVS.** NNSVS normalises predicted phone durations "so that the sum of the predicted durations equals to the note durations" ([arXiv 2210.15987](https://arxiv.org/html/2210.15987)). XiaoiceSing adds a syllable-level duration loss for the same purpose ([arXiv 2006.06261](https://arxiv.org/abs/2006.06261)).

## 3. Score and timing representations

| Format | Time unit | Note fields | Phoneme timing |
|---|---|---|---|
| UST (UTAU) | ticks, 480/quarter | Length, Lyric, NoteNum, PreUtterance, VoiceOverlap, Velocity, PBS/PBW/PBY/PBM | ms offsets per note ([parser](https://github.com/openutau/OpenUtau/blob/master/OpenUtau.Core/Classic/UstNote.cs)) |
| USTX (OpenUtau, YAML 1.2) | ticks, 480; tempo list | position, duration, tone, lyric, pitch points (ms, 0.1 semitone), vibrato | `phoneme_overrides` with tick offset and deltas ([wiki](https://github.com/openutau/OpenUtau/wiki/USTX-file-format)) |
| SVP (Synthesizer V, JSON) | blicks, 705,600,000/quarter | onset, duration, lyrics, phonemes, pitch, `musicalType` "singing" or "rap" | per-note attributes ([SVP.cs](https://github.com/openutau/OpenUtau/blob/master/OpenUtau.Core/Format/SVP.cs)) |
| VPR (VOCALOID 5/6, zip+JSON) | ticks; tempo stored as bpm x 100 | pos, duration, number, lyric, phoneme, velocity | *not researched* ([Vpr.kt](https://github.com/sdercolin/utaformatix3/blob/master/core/src/main/kotlin/core/io/Vpr.kt)) |
| MusicXML | divisions | lyric with `syllabic` single/begin/middle/end ([W3C](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/syllabic/)) | none |
| Standard MIDI File | PPQ ticks | lyric meta event FF 05, usually one per syllable ([MIDI.org](https://midi.org/smf-lyric-meta-event-definition/)) | none |
| Festival SINGING XML | beats or seconds at a BPM | `PITCH NOTE`, `DURATION BEATS`, `REST`; `+` splits syllables | onset table above |
| MBROLA .pho | ms | phoneme, duration, pitch pairs (% position, Hz) ([README](https://github.com/numediart/MBROLA)) | explicit per phoneme |
| DiffSinger .ds | seconds / frames | ph_seq, ph_dur, ph_num, note_seq, note_dur, f0 ([docs](https://github.com/openvpi/DiffSinger/blob/main/docs/BestPractices.md)) | explicit |
| DeepRapper | tokens | `[beat]` before the word on the beat; `[s]/[m]/[f]` density token per song ([ACL 2021](https://aclanthology.org/2021.acl-long.6)) | none |
| DiffRhythm | LRC lines with timestamps | line level only ([repo](https://github.com/ASLP-lab/DiffRhythm)) | none |

## 4. Projects and licence status

| Project | Licence | Last activity | Reuse value | Status |
|---|---|---|---|---|
| [OpenUtau](https://github.com/openutau/OpenUtau) | MIT | push 2026-10-18 | lead-in clamping, phonemizer design, USTX export | allowed |
| [OpenUtau Mobile](https://github.com/vocoder712/OpenUtauMobile) | Apache-2.0 | v1.1.7, 2025-12-31 | proof that DiffSinger/UTAU render on Android (.NET MAUI); README warns it is "very unstable" | allowed |
| [DiffSinger (openvpi)](https://github.com/openvpi/DiffSinger) | Apache-2.0 | push 2026-10-16 | duration/pitch model design | allowed (code) |
| [DiffSinger vocoders](https://github.com/openvpi/vocoders/releases) | weights CC BY-NC-SA 4.0 | 2025-02-27 | none | **excluded** |
| DiffSinger voicebanks | per bank: e.g. [NoLyui](https://nolyui.carrd.co/) allows original music, [MirrorTeru](https://mirrorteru.carrd.co/) needs permission | varies | check each bank | case by case |
| [NNSVS](https://github.com/nnsvs/nnsvs) / [ENUNU](https://github.com/oatsu-gh/ENUNU) | MIT / MIT | 2023-10-09 / 2025-12-14 | time-lag idea | allowed, mostly Japanese voices |
| [Sinsy](https://sinsy.sourceforge.net/) | Modified BSD | 0.92, 2015-12-25 | MusicXML front end | allowed but Japanese only |
| [Sinsy-NG](https://github.com/GloomyGrave/Sinsy-NG) | BSD + GPL-3.0 (eSpeak NG) | archived 2022-01-14 | formant singing of MusicXML, English default | allowed (archived) |
| [midi2voice](https://github.com/mathigatti/midi2voice) | MIT | 2022-07-20 | needs the online sinsy.jp service | allowed, not on-device |
| [Festival singing-mode](https://github.com/festvox/festival/blob/master/lib/singing-mode.scm) | public domain | repo push 2026-09-29 | onset table, fit rules | allowed |
| [eSpeak NG](https://github.com/espeak-ng/espeak-ng) | GPL-3.0 | push 2026-09-22 | SSML `prosody` rate and pitch ([docs](https://github.com/espeak-ng/espeak-ng/blob/master/docs/markup.md)) | allowed |
| [MBROLA engine](https://github.com/numediart/MBROLA) | AGPL-3.0 | push 2025-07-20 | .pho format | allowed |
| MBROLA voices (us1, us2, us3) | "may not be sold or incorporated into any product which is sold" ([us1](https://github.com/numediart/MBROLA-voices/blob/master/data/us1/license.txt)) | 2020 | none | **excluded** |
| [SAM](https://github.com/s-macke/SAM) | none; "copyright holder is SoftVoice, Inc." ([port](https://github.com/discordier/sam)) | 2024 | none | **excluded** |
| [Plaits speech](https://github.com/pichenettes/eurorack/tree/master/plaits/dsp/speech) | MIT file headers | push 2026-10-12 | formant and SAM-style DSP | code allowed; LPC words "extracted from various TI ROMs" **excluded** |
| [Talkie (ArminJo)](https://github.com/ArminJo/Talkie) | GPL-3.0 | 2026-06-22 | LPC player | code allowed, word data rights unclear |
| MAME [TMS5220](https://github.com/mamedev/mame/blob/master/src/devices/sound/tms5220.cpp), Votrax, SP0256 | BSD-3-Clause | active | chip voice emulation | code allowed; Votrax core loads ROM `sc01.bin`, ROMs **excluded** |
| [UtaFormatix3](https://github.com/sdercolin/utaformatix3) | Apache-2.0 (LICENSE.md) | push 2026-08-26 | Kotlin `Note(key, lyric, tickOn, tickOff, phoneme)`, 480 ticks/beat, converters | allowed |
| [LibreSVIP](https://github.com/SoulMelody/LibreSVIP) | MIT | push 2026-10-18 | format converters | allowed |
| [autovelocity](https://github.com/vocatart/autovelocity) | GPL-3.0 | *not researched* | consonant velocity from note length (baseline: quarter note at 120 BPM) | allowed |
| [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) | Apache-2.0, "permissive/non-copyrighted" data | 2025 | outputs per-phoneme `pred_dur` ([pipeline.py](https://github.com/hexgrad/kokoro/blob/main/kokoro/pipeline.py)); stock ONNX takes only global `speed` ([kokoro-onnx](https://github.com/thewh1teagle/kokoro-onnx)) | allowed |
| [Piper](https://github.com/OHF-Voice/piper1-gpl) | GPL-3.0 | push 2026-10-17 | per-phoneme sample counts ([ALIGNMENTS.md](https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/ALIGNMENTS.md)) | code allowed; lessac voice is research-only ([licence](https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html)) **excluded**; ljspeech is public domain, allowed |
| [Bark](https://github.com/suno-ai/bark) | MIT | 2024-08-19 | `♪` makes it sing; no timing control documented | allowed, not grid-lockable |
| [Microsoft Muzic](https://github.com/microsoft/muzic) (DeepRapper) | MIT, archived | 2026-08-05 | beat token idea | allowed |
| [DiffRhythm](https://github.com/ASLP-lab/DiffRhythm), [YuE](https://github.com/multimodal-art-projection/YuE), [ACE-Step](https://github.com/ace-step/ACE-Step) | Apache-2.0 | 2025 to 2026 | none on device (DiffRhythm needs at least 8 GB VRAM) | allowed, not usable on a phone |
| [AlignSTS](https://github.com/RickyL-2000/AlignSTS) | MIT | 2024-07-05 | rhythm adaptor idea | allowed |
| [GTSinger](https://github.com/AaronZ345/GTSinger/blob/master/dataset_license.md), [M4Singer](https://github.com/M4Singer/M4Singer/blob/master/dataset_license.md) | CC BY-NC-SA 4.0 | | none | **excluded** |
| [RapBank](https://github.com/NZqian/RapBank), [VISinger2](https://github.com/zhangyongmao/VISinger2) | no licence | 2024 | none | **excluded** |
| [Ableton Link](https://github.com/Ableton/link) | GPLv2+ or proprietary | push 2026-10-15 | tempo sync with Live | allowed |

## 5. Rap modelling elsewhere

- **Synthesizer V.** Rap mode is set per note. For rap notes the pitch curve is generated by a model, and v2 draws rap notes with rounded corners ([v2 docs](https://sv2.docs.dreamtonics.com/en/pitch), seen via a search snippet). The English rap voice Ritchy has HighTone, MidTone and LowTone modes. An early forum user found the output "passable", but said it "needs a lot of tweaking" ([forum](https://forum.synthesizerv.com/t/topic/8595)). Sound On Sound praises v2's phoneme lane for editing phoneme strength and duration ([review](https://www.soundonsound.com/reviews/dreamtonics-synthesizer-v-studio-2-pro)).
- **Rapping-singing synthesis** ([Markopoulos et al., SSW 2021](https://arxiv.org/abs/2111.09146)). Trained on read speech only, with phoneme-level duration classes and pitch embeddings. Timing is copied from a reference and fixed with WSOLA time-stretching. For rap, "the focus is less on musical notes, and more on pitch variation and rhythm".
- **DeepRapper.** Words are matched to detected beats within half an average word length. Songs get a density class by words per beat (below 3, 3, above 3).

## 6. Products and apps (features, UI, reviews)

| Product | What is relevant | Praise or complaints |
|---|---|---|
| Plogue Chipspeech | 12 vintage chip voices; "Each MIDI note advances to the next syllable" ([CDM 2015](https://cdm.link/?p=35949)); circuit-bend emulation ([Plogue](https://www.plogue.com/products/chipspeech.html)) | *not researched* |
| Yamaha VKB-100 | lyrics entered in an app, played from the keytar; released December 2017 ([Wikipedia](https://en.wikipedia.org/wiki/Vocaloid_Keyboard)) | *not researched* |
| Emvoice One | text box plus piano roll, a syllable per note, cloud rendering, voices sold separately ([Gearnews](https://gearnews.com/?p=54380)) | KVR reviewer: "zero customer service" ([KVR](https://www.kvraudio.com/product/emvoice-one-by-emvoice/reviews)) |
| Smule AutoRap (2012) | talk mode maps your speech syllables onto a beat; rap mode corrects rapping to given lyrics; freemium beats ([Rekkerd](https://rekkerd.org/?p=43660), [Engadget](https://www.engadget.com/2012-07-17-smule-launches-autorap-for-ios-and-android-engadgets-editors-d.html)) | current status *not researched* |
| Voicemod Text to Song (Dec 2022) | type lyrics over a catalogue song, 7 voices, clips under 30 s ([voicebot.ai](https://voicebot.ai/2022/12/15/voicemod-debuts-ai-text-to-song-generator-after-acquiring-synthetic-singing-ai-startup-voctro/)) | product page returned HTTP 410 on 2026-10-18 |
| Voloco | vocoder and talkbox presets | paywall and limited fine control ([review](https://violetrecording.com/voloco-review/)) |
| RoboVox (Android) | 32 voices, real-time mode, XY pad, WAV export ([listing](https://www.bluestacks.com/campaign/com.mikrosonic.DroidVox/de)) | clipping in headphone mode ([reviews](https://justuseapp.com/en/app/584847250/robovox-voice-changer/reviews)) |
| Ableton Live | Vocoder carriers: Noise, External, Modulator, Pitch Tracking ([manual](https://www.ableton.com/en/live-manual/12/live-audio-effect-reference/#vocoder)); short samples are assumed to be 1, 2, 4, 8 or 16 bars, and tempo guesses can be off by x2 or ÷2 ([warping](https://www.ableton.com/en/manual/audio-clips-tempo-and-warping/)) | |
| Roland VT-4 | robot mode, vocoder, MIDI input to go "beyond a monotone" ([MusicTech](https://www.musictech.net/reviews/roland-vt-4-voice-transformer/)) | robot mode "suitably metallic" |
| Arturia Vocoder V | 16 bands, internal synth with chord mode, 149 EUR ([Arturia](https://www.arturia.com/products/software-instruments/vocoder-v/overview)) | *not researched* |
| iZotope VocalSynth 2 | Compuvox "glitchy, computerized" module, MIDI mode, 199 USD ([iZotope](https://www.izotope.com/en/products/vocalsynth.html)) | *not researched* |
| Mutable Plaits | TRIG speaks a word, MORPH picks a word, TIMBRE goes "from Daleks to chipmunks" ([manual](https://pichenettes.github.io/mutable-instruments-documentation/modules/plaits/manual/)) | |
| GND-1T, Moddict Phonemer, Highly Liquid MIDISpeak | TI speech emulation with MIDI clock ([CDM](https://cdm.link/2024/07/speak-and-spell-gnd-1t/)); Eurorack formant TTS with trigger and gate modes ([ModularGrid](https://modulargrid.net/e/modules/view/59868)); MIDI retrofit for the Speak & Spell ([Synthtopia](https://www.synthtopia.com/?p=3582)) | |

In a limited search we found no Android app that sings typed lyrics on a BPM grid.

## 7. Recommended internal score model

```kotlin
const val PPQ = 480 // same as UST, USTX, VPR and UtaFormatix

data class Song(
    val title: String,
    val tempoMap: List<TempoEvent>,      // first event at tick 0
    val meterMap: List<MeterEvent>,
    val key: KeySpec?,                   // for scale-quantised pitch
    val preRollBars: Int = 1,            // room for consonants before bar 1
    val bars: List<Bar>,
    val syllables: Map<String, Syllable>,
    val tracks: List<VoiceTrack>,
    val automation: List<AutomationLane>,
)
data class TempoEvent(val tick: Long, val bpm: Double)
data class MeterEvent(val bar: Int, val numerator: Int, val denominator: Int)
data class KeySpec(val tonicMidi: Int, val scaleSteps: List<Int>)

data class Bar(val index: Int, val slotTicks: Int = PPQ / 4, val slots: List<Slot>)
sealed interface Slot {
    data class Start(val syllableId: String) : Slot
    data object Hold : Slot              // like OpenUtau "+"
    data object Rest : Slot
}

data class Syllable(
    val id: String,
    val text: String,
    val syllabic: Syllabic,              // MusicXML single/begin/middle/end
    val stress: Int,                     // 0, 1 or 2 from the lexicon
    val phonemes: List<Phoneme>,         // ARPAbet
    val anchor: Anchor = Anchor.VOWEL_ONSET,
    val nudgeMs: Double = 0.0,           // push or lay back
    val pitch: PitchSpec,
    val accent: Double = 0.0,
    val consonantVelocity: Int = 100,    // UTAU rule: stretch = 2^(1 - v/100)
    val gapRatio: Double = 0.0,          // staccato gap at the end
)
enum class Syllabic { SINGLE, BEGIN, MIDDLE, END }
enum class Anchor { VOWEL_ONSET, SYLLABLE_ONSET }

data class Phoneme(
    val symbol: String,
    val role: Role,                      // ONSET, NUCLEUS, CODA
    val override: PhonemeOverride? = null, // user deltas, kept on regeneration
)
enum class Role { ONSET, NUCLEUS, CODA }
data class PhonemeOverride(val symbol: String? = null, val offsetMs: Double? = null, val lengthScale: Double? = null)

sealed interface PitchSpec {
    data object TrackMonotone : PitchSpec
    data class Note(val midi: Int, val cents: Int = 0, val glideMs: Double = 0.0) : PitchSpec
    data class Speech(val baseMidi: Int, val contour: Contour, val rangeSemitones: Double) : PitchSpec
}
enum class Contour { FLAT, RISE, FALL, PEAK }

data class VoiceTrack(val voiceId: String, val fxChain: List<String>)
data class AutomationLane(val param: String, val points: List<AutoPoint>) // e.g. "formant", "crush", "stutterDiv"
data class AutoPoint(val tick: Long, val value: Double, val step: Boolean = false)

// Derived, never edited directly: regenerated from the score.
data class PhonemeEvent(val syllableId: String, val symbol: String, val startSample: Long, val lengthSamples: Int)
```

Rules:
1. Authored timing is in ticks. Milliseconds only appear in nudges, overrides and the derived `PhonemeEvent` list.
2. The vowel onset of each `Start` slot lands exactly on its tick. Onset consonants lead in before it, with lengths from a per-phoneme table (start from Festival's English offsets), scaled by consonant velocity and by the OpenUtau tempo factor.
3. The lead-in takes at most half of the previous syllable and never crosses a rest. Short syllables shrink all phones. Long syllables stretch only the nucleus.
4. User edits are stored as deltas (the OpenUtau pattern), so changing the lyrics or BPM regenerates everything else.
5. Exports: a WAV that starts at a bar line after the pre-roll and lasts whole bars, a MIDI file with one FF 05 lyric event per syllable, and optionally USTX.

## 8. Features worth copying, ranked

1. Vowel-on-the-beat anchoring with tempo-aware consonant lead-in (UTAU, OpenUtau, Festival).
2. A lyrics box with automatic syllables, plus a slot editor (Start, Hold, Rest) and inline `+` and `-` tokens.
3. Flow presets by density (DeepRapper's words-per-beat classes): straight 16ths, triplets, double time.
4. A pitch mode per syllable (monotone, note, speech contour), shown in a different shape (Synthesizer V).
5. A phoneme lane with duration and strength handles, plus a `[phonemes]` override (OpenUtau, Synthesizer V 2).
6. Voice character macros: formant shift independent of pitch, and chip-style voices (Plaits TIMBRE, Chipspeech, RoboVox XY pad).
7. Vocoder carrier choice: noise, pitch tracking, chord synth (Ableton, Arturia).
8. Effect locks per slot, such as stutter or crush (*idea from PO-35 parameter locks; official TE docs not researched*).
9. An Ableton hand-off with bar-aligned WAV, MIDI with lyrics and BPM in the file name, plus Ableton Link.
10. Phrase loops for live use (Chipspeech, VKB-100).

## 9. Pitfalls seen in other products

1. Putting the consonant on the note start makes words sound late.
2. Fixed consonant lengths at high BPM swallow the vowel.
3. Licence traps hide in dependencies: vocoder weights, voice datasets, chip ROMs, unlicensed repos (section 4).
4. Cloud dependence: Emvoice needs internet, and Voicemod's Text to Song page is gone (HTTP 410).
5. Short or social-only exports, such as Voicemod's 30-second clips.
6. Paywalls, and rap modes that "need a lot of tweaking" (Voloco, Synthesizer V forum).
7. DAW mis-warping when a file is not whole bars or does not start on a downbeat (Ableton x2/÷2 guesses).
8. Tempo maths errors (Festival's `bpm / 50.0`). Unit-test rendered onsets against the grid.
9. Generative rap models without duration input cannot be grid-locked.
10. Large models on phones are unstable (the OpenUtau Mobile README warns about memory).

## 10. Not researched

Play Store review mining for robot-voice and TTS-singing apps; the official UTAU spec; VOCALOID rap practice; CeVIO, VoiSona and NEUTRINO (mostly Japanese, terms only seen second-hand); Synthesizer V EULA text; Teenage Engineering official docs; microKORG 2; projects that make Talkie sing; speech in Pure Data, SuperCollider and Sonic Pi; TeleMelody details; Jukebox licence; current status of AutoRap; Suno and Udio terms (secondary sources only); sherpa-onnx and Matcha-TTS duration control; the Synthesizer V rap intonation internals.
