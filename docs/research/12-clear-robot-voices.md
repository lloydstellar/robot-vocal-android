# 12. Clear robot voices

Research for Robot Vocal: how to make the robots easier to understand in a track, so a listener follows the words. The starting point was a list of studio advice for clear vocoder vocals that Lloyd put together (sources at the end). Researched and measured 2026-10-20.

## 1. Key findings

- **The advice comes down to four things.** Bring up the soft parts of words (clip gain on consonants, then compression), give the vocoder more highs and less low end, give s and t a path of their own, and keep the level going in steady. Speech research says the same about speech in noise: at the same loudness, speech gets clearer when its energy moves up to the range of 1 to 4 kHz and its soft parts come up (SSDRC, Zorila, Kandia and Stylianou 2012), and that also works on synthetic speech (Valentini-Botinhao et al. 2013, about 4 dB).
- **Presence and a fast compressor on every voice.** Measured as in section 4, a high-pass, a shelf of 6 dB above 2 kHz and a fast compressor together with the other changes gave 6 more words in 100 against an electro beat at the same loudness (from 23 to 29, averaged over 16 voices). The app now does this to every voice before any effect.
- **The shelf is for the voice too.** Even unlifted, a burst came out 1 to 4 dB louder next to its vowel than before, because the shelf brightens noise more than a vowel: small ticks here and there, Lloyd said. With the shelf on the voice only, every burst, h and hiss is back at or a little under its old level next to the vowel, and only m, n, l and the vowels gain.
- **The compressor may only lift voice.** At first it lifted everything soft by up to 12 dB, and the bursts of p, t and k, the h and the f came up from 10 to 15 dB under their vowel to 1 to 4 dB under it: hard short bursts of hiss between the letters, which Lloyd heard at once. Now it lifts only what is voice (an m, an l, a weak vowel), by at most 6 dB at 2 to 1, and leaves every sound that is noise or half noise where it was. That sounds cleaner and measured better too: over six voices, 52 words in 100 in quiet where the first version had 49 and version 1.0.0 46, and 28 with the beat (first version 30, 1.0.0 23).
- **The lift needs a de-esser.** The shelf and the compressor made s and sh as loud as the vowels or louder (from about 7 dB under them to 0 to 3 dB over), and Lloyd heard it at once: a sharp s on many voices. A de-esser that knows where every s is now takes them back to 6 dB under their vowel, and f, v and th to 10 dB.
- **Clip gain per consonant did not work** for the app's voices. Bringing every consonant to a set level next to its vowel helped DESKTOP by 9 words in 100 and cost TIN MAN 7; louder f, th, s and h were the cause. A milder setting and a version that lifted only the highs of the consonants did no better. It is left out.
- **Two faults made effects harder to follow.** The engines speak at levels up to 40 dB apart: UNIT ONE peaks 10 dB above full scale, the retro voices sit 30 dB below it. So the compressor before every effect took 15 to 18 dB off UNIT ONE and nothing off the retro voices. And the crushers of BRAZEN HEAD I and TIN CAN counted their 5 and 6 bits from full scale, so on a quiet voice the steps were coarser than the voice. Now every voice reaches the effects at one level and a crusher counts from the loudest moment. With kal, in quiet, these two fixes alone took BRAZEN HEAD I from 6 to 27 words in 100 and TIN CAN from 15 to 53; with everything else, to 50 and 69.
- **Lines sounded over each other.** A line that starts with a pickup ("the", "a") runs up into the bar of the line before, whose last word was planned to the end of that bar: the two overlapped by up to 140 ms in ROBOT 8THS, and the last consonant of the line was lost under the pickup. Flite, which speaks its phones one after the other, then fell behind the plan by the overlap, line after line: in a test of 16 lines kal and SPELLER ended a second behind the beat. Now the next line goes first, as a rapper cuts a held word short: the last word of a line ends 10 ms before the pickup's first consonant, and Flite's phones always end on their planned ends.
- **The vocoders switch to noise for unvoiced sounds**, as studio vocoders do with their unvoiced switch, but driven by the phonemes, so the switch is never late and never wrong. On a pitch an s is a buzz; on noise it is an s. The talkboxes do the same. The bands follow the voice with 1 ms attack and 10 ms release, as the advice says; only the lowest bands, below 200 Hz, take longer, so they do not ride on their own waves.

## 2. The advice and the app

| Advice | In the app |
|---|---|
| Clip gain of 3 to 6 dB on t, k, p, d, ch and on soft h, l, m, n, v; sibilants down | Measured per phoneme and left out (section 5); the compressor of the voice chain brings soft sounds up instead, smoothly and where they are soft |
| A gate before the compressor | The voice chain only compresses where a phoneme sounds; rests stay as they are |
| A fast compressor (1176 style: 1 ms, 60 ms, 4:1) and then a slow one (LA-2A style, 3:1, 3 to 6 dB) | The chain before every effect: one working level, 4:1 at 1 ms and 60 ms, then 3:1 at 10 ms and 400 ms; on eight voices of five engines the fast one takes 1 to 4 dB off (6 dB on the loudest peaks), the slow one 3 dB, after the voice chain has done its part |
| High-pass at 80 to 100 Hz, a shelf of 4 to 6 dB from about 3 kHz | The voice chain: high-pass at 90 Hz, shelf of 6 dB above 2 kHz |
| A de-esser after the EQ, but not so much that the vocoder loses its consonants | A de-esser by phoneme at the end of the voice chain: s, sh, z, ch and j down to 6 dB under their vowel, f and th to 10 dB, above 2.5 kHz only, and only where they are louder than that |
| Vocoder attack as short as possible, release 8 to 10 ms | 1 ms and 10 ms; at least half a period and two periods of the band's frequency |
| Unvoiced open, high-pass at 4.5 to 6 kHz | The hiss path at 5.2 kHz as before, plus the unvoiced switch |
| 16 to 20 bands | As before: 16 to 24 |
| A bright carrier, a higher register | The presets keep their carriers; BRAZEN HEAD III and BASS DROID stay dark on purpose |
| Do not overdrive the input | One working level for every engine, so a drive or a crusher sounds the same on every voice |
| Speak slowly and clearly | The formant voices now breathe after p, t and k before r, l, w and y ("cry", "play", "twin"), as speech does |
| Some highs of the dry voice under the vocoder | The hiss path; BRAZEN HEAD I and MONOTONE UNIT also keep some dry voice |

## 3. What the app does now

- **Voice chain** (`render/Clarity.kt`), on every voice before any effect, so also on DRY: high-pass at 90 Hz, shelf of 6 dB above 2 kHz on the voice only (the noise of a burst, an h or an s keeps its own colour), then a compressor that follows the voice in 5 ms windows with 1 ms attack and 15 ms release and brings it up towards the loudest moment of the take, 2 to 1 and at most 6 dB. It lifts only voice: where the phonemes say noise (all of p, t, k, f, s, sh, h, ch, the bursts of b, d and g, and v, z and the th of "this", which are half noise) it lifts nothing, with a glide of 5 ms. What lies more than 40 dB down is lifted less and less, so a stop's closure stays a gap, and it only works within 15 ms of a phoneme. Last comes the de-esser: every s, sh, z, ch and j whose hiss above 2.5 kHz is louder than 6 dB under the vowel of its syllable (f, v and th: 10 dB) is blended towards its own band under 2.5 kHz until it is not, by at most 12 dB, with 5 ms ramps.
- **Before every effect** (`dsp/RobotPresets.kt`): the voice to a working level of -20 dBFS RMS, a high-pass at 70 Hz, the two compressors, and the voice to -18 dBFS for the effect. The level is the RMS of the 20 ms frames within 40 dB of the loudest.
- **Vocoder** (`dsp/Vocoder.kt`): the carrier turns to noise over 3 ms where the voice is noise: a whole f, s, sh and h, the burst of p, t, k, b, d and g (their last 20 ms, at most 40 %) and the second half of ch and of the j of "judge"; v, z and the th of "this" get half noise and half carrier. In the silent closure of a stop the carrier stays as it was: the vowel before still rings in the bands for a few milliseconds, and on noise that ring was a short burst of hiss. Attack and release as in section 2.
- **Talkbox**: the same switch.
- **Crusher**: its steps are counted from the loudest moment of what it gets.
- **SPELLING CHIP**: the voice is levelled so the loudest frame is energy step 12 of 15, as a recording was levelled before it was encoded; voicing comes from the phonemes.
- **Formant engine**: p, t and k breathe in the shape of a following r, l, w or y.
- **Timing** (`render/TimingPlanner.kt`, `voice/FliteEngine.kt`): the last syllable of a line ends 10 ms before the next line's first consonant, as planned for that line on its own; only if even the shortest end does not fit is the pickup compressed. Flite gives every phone at least 12 ms and takes it off the next one.

## 4. How it was measured

A listening test with people was not possible for every change, so a speech recognizer stood in for the listener, as is common in speech synthesis research: whisper.cpp with the small English model, run on the computer.

- **Sentences.** 48 semantically unpredictable sentences after Benoît et al. (1996), made of common one-syllable words: "the map cried in the poor leg", "why does the coat steal the soft pin". Context cannot fill in what was not heard.
- **Song.** Every sentence is one line, rapped at 134 BPM in ROBOT 8THS on the voice's own note.
- **Beat.** ELECTRO 2, an 8th-note saw bass and a dark pad. The beat is set 6 dB above the voice (0 dB for the effects, which are harder), measured as the RMS of the voice where it sounds against the RMS of the music, so every version of a voice is equally loud in the mix.
- **Listening.** Every line is cut out and recognised on its own; tags such as "[music]" are suppressed. The score is the share of the sentence's words that come back.
- **Limits.** A recognizer is not a person. It understands vocoded speech far worse than people do, so for the effects only large differences mean something. 48 sentences give an interval of about plus or minus 5 words in 100 for one comparison; differences of a few words are noise.

## 5. Results

Words recognised out of 100, 48 sentences per row, before (version 1.0.0) and after. These two tables were measured before the de-esser was added; section 5.3 shows that it changes little.

### 5.1 The voices, dry

| Voice, dry | In quiet, before | after | Beat 6 dB louder, before | after |
|---|---:|---:|---:|---:|
| UNIT ONE | 48 | 52 | 31 | 32 |
| ROBOVOX | 50 | 57 | 24 | 33 |
| TIN MAN | 34 | 37 | 30 | 30 |
| DEEP UNIT | 45 | 48 | 15 | 19 |
| ALLOPHONE | 6 | 6 | 10 | 8 |
| HOME MICRO | 46 | 47 | 22 | 23 |
| CRISP MICRO | 49 | 41 | 33 | 26 |
| DESKTOP | 62 | 65 | 25 | 37 |
| MONOTONE | 58 | 64 | 27 | 43 |
| TREBLE | 51 | 50 | 24 | 40 |
| KAL | 67 | 76 | 32 | 47 |
| SPELLER | 23 | 36 | 13 | 20 |
| ROBOSOFT | 35 | 27 | 11 | 17 |
| KLATT 1 | 29 | 31 | 24 | 26 |
| STANDARD M | 41 | 38 | 28 | 32 |
| ANIKA ROBOT | 45 | 38 | 22 | 32 |
| **Mean** | **43.1** | **44.5** | **23.3** | **29.2** |

With the beat 14 of the 16 voices are easier to follow, by 6 words in 100 on average: most of all KAL (32 to 47), the retro voices and ROBOVOX. KAL and SPELLER also gain from the timing of the lines. In quiet the average hardly moves (43 to 45): the formant voices, KAL and SPELLER gain, while ROBOSOFT, ANIKA ROBOT and CRISP MICRO lose 6 to 8. For one voice that is about the margin of chance (plus or minus 6), but CRISP MICRO loses at both levels.

### 5.2 The effects, on KAL

| Effect, on KAL | In quiet, before | after | Beat as loud, before | after |
|---|---:|---:|---:|---:|
| BRAZEN HEAD I | 6 | 50 | 3 | 25 |
| BRAZEN HEAD II | 29 | 34 | 18 | 24 |
| BRAZEN HEAD III | 40 | 44 | 28 | 29 |
| FLAT TWENTY | 5 | 17 | 6 | 8 |
| SIXTEEN CHANNEL | 12 | 31 | 2 | 8 |
| SUPERSAW CHOIR | 18 | 24 | 11 | 12 |
| MONOTONE UNIT | 4 | 20 | 0 | 13 |
| MOUTH TUBE | 13 | 28 | 2 | 4 |
| TALK TUBE | 8 | 19 | 1 | 1 |
| SPELLING CHIP | 18 | 17 | 11 | 17 |
| EXTERMINATOR | 62 | 67 | 41 | 54 |
| CENTURION | 3 | 12 | 2 | 3 |
| PHASE ROBOT | 9 | 7 | 5 | 5 |
| JET | 33 | 37 | 15 | 24 |
| TIN CAN | 15 | 69 | 13 | 56 |
| TIGHT DELAY | 38 | 50 | 31 | 45 |
| STUTTER | 0 | 2 | 0 | 1 |
| BASS DROID | 42 | 47 | 25 | 35 |
| WHISPER UNIT | 16 | 25 | 12 | 13 |
| EIGHT BIT | 66 | 76 | 44 | 59 |
| **Mean** | **22.0** | **33.9** | **13.6** | **22.0** |

Every vocoder and talkbox gained, most of all where the crusher drowned the voice (BRAZEN HEAD I, TIN CAN). The recognizer still follows little of the vocoders with a beat as loud as the voice; vocoded words are hard in a dense mix, as the studio advice warns.

### 5.3 With the de-esser

| Voice, dry | In quiet, before | after | Beat 6 dB louder, before | after |
|---|---:|---:|---:|---:|
| UNIT ONE | 48 | 52 | 31 | 32 |
| ROBOVOX | 50 | 57 | 24 | 33 |
| TIN MAN | 34 | 37 | 30 | 30 |
| DEEP UNIT | 45 | 48 | 15 | 19 |
| ALLOPHONE | 6 | 6 | 10 | 8 |
| HOME MICRO | 46 | 47 | 22 | 23 |
| CRISP MICRO | 49 | 41 | 33 | 26 |
| DESKTOP | 62 | 65 | 25 | 37 |
| MONOTONE | 58 | 64 | 27 | 43 |
| TREBLE | 51 | 50 | 24 | 40 |
| KAL | 67 | 76 | 32 | 47 |
| SPELLER | 23 | 36 | 13 | 20 |
| ROBOSOFT | 35 | 27 | 11 | 17 |
| KLATT 1 | 29 | 31 | 24 | 26 |
| STANDARD M | 41 | 38 | 28 | 32 |
| ANIKA ROBOT | 45 | 38 | 22 | 32 |
| **Mean** | **43.1** | **44.5** | **23.3** | **29.2** |

| Effect, on KAL | In quiet, before | after | Beat as loud, before | after |
|---|---:|---:|---:|---:|
| BRAZEN HEAD I | 6 | 50 | 3 | 25 |
| BRAZEN HEAD II | 29 | 34 | 18 | 24 |
| BRAZEN HEAD III | 40 | 44 | 28 | 29 |
| FLAT TWENTY | 5 | 17 | 6 | 8 |
| SIXTEEN CHANNEL | 12 | 31 | 2 | 8 |
| SUPERSAW CHOIR | 18 | 24 | 11 | 12 |
| MONOTONE UNIT | 4 | 20 | 0 | 13 |
| MOUTH TUBE | 13 | 28 | 2 | 4 |
| TALK TUBE | 8 | 19 | 1 | 1 |
| SPELLING CHIP | 18 | 17 | 11 | 17 |
| EXTERMINATOR | 62 | 67 | 41 | 54 |
| CENTURION | 3 | 12 | 2 | 3 |
| PHASE ROBOT | 9 | 7 | 5 | 5 |
| JET | 33 | 37 | 15 | 24 |
| TIN CAN | 15 | 69 | 13 | 56 |
| TIGHT DELAY | 38 | 50 | 31 | 45 |
| STUTTER | 0 | 2 | 0 | 1 |
| BASS DROID | 42 | 47 | 25 | 35 |
| WHISPER UNIT | 16 | 25 | 12 | 13 |
| EIGHT BIT | 66 | 76 | 44 | 59 |
| **Mean** | **22.0** | **33.9** | **13.6** | **22.0** |
| | In quiet: 1.0.0 | without de-esser | with | With the beat: 1.0.0 | without de-esser | with |
|---|---:|---:|---:|---:|---:|---:|
| UNIT ONE | 48 | 52 | 52 | 31 | 32 | 33 |
| KAL | 67 | 76 | 77 | 32 | 47 | 45 |
| DESKTOP | 62 | 65 | 65 | 25 | 37 | 39 |
| ROBOSOFT | 35 | 27 | 25 | 11 | 17 | 16 |
| SPELLER | 23 | 36 | 35 | 13 | 20 | 20 |
| STANDARD M | 41 | 38 | 38 | 28 | 32 | 26 |
| BRAZEN HEAD I on KAL | 6 | 50 | 50 | 3 | 25 | 24 |
| MONOTONE UNIT on KAL | 4 | 20 | 19 | 0 | 13 | 14 |
| MOUTH TUBE on KAL | 13 | 28 | 28 | 2 | 4 | 3 |

The de-esser takes the s back from as loud as the vowel to 6 dB under it and changes the scores by no more than chance.

### 5.4 The final version: a gentler compressor and a shelf for the voice only

| | In quiet: 1.0.0 | first version | final | With the beat: 1.0.0 | first version | final |
|---|---:|---:|---:|---:|---:|---:|
| UNIT ONE | 48 | 52 | 61 | 32 | 33 | 35 |
| KAL | 67 | 77 | 75 | 32 | 45 | 42 |
| DESKTOP | 62 | 65 | 70 | 25 | 39 | 36 |
| ROBOSOFT | 35 | 25 | 33 | 11 | 16 | 16 |
| SPELLER | 23 | 35 | 37 | 13 | 20 | 17 |
| STANDARD M | 40 | 38 | 38 | 27 | 26 | 25 |
| **Mean** | **45.8** | **48.6** | **52.4** | **23.3** | **29.8** | **28.4** |

The first version is the one with the strong compressor and the de-esser (section 5.3). The final one lifts and brightens only the voice: clearer in quiet, about as clear with the beat, and without the bursts of hiss or the ticks. Version 1.0.0 was rendered again for this table, so its numbers differ by a word here and there from section 5.1.

## 6. Open

- The formant voices (UNIT ONE, TIN MAN) gained least: their consonants are what limits them, not their level. Sharper bursts and more precise fricative spectra in the formant engine are the next step.
- Voices recorded at 8 kHz (kal, SPELLER) have nothing above 4 kHz for the vocoder's top bands and the hiss path. Noise above 5 kHz, shaped by the voice's 2.5 to 4 kHz band during unvoiced phonemes, could give them air (report 06, section 3).
- A dark carrier hides consonants. BRAZEN HEAD III and BASS DROID stay dark on purpose; a little drive on their carrier would add highs.

## Sources

- Lloyd's list "Vocoderstem beter verstaanbaar maken" (2026-10-20), with: Sound on Sound, Mike Senior, "Vocal Mix Prep"; Apple, EVOC 20 manual (level and frequency tips, avoiding artefacts); Nail The Mix, "The 1176 into LA-2A trick"; MusicRadar, the vocoder guide and vocal chains; Reason's BV512 vocoder (HF emphasis); Sonicstate interview with Anthony Rother (2022).
- Zorila, T.-C., Kandia, V. and Stylianou, Y. (2012). Speech-in-noise intelligibility improvement based on spectral shaping and dynamic range compression. Interspeech 2012. And Tsiaras, Zorila, Stylianou and Akamine (2014), real-time SSDRC, ICASSP 2014 show and tell.
- Valentini-Botinhao, C., et al. (2013). Edinburgh and FORTH entry to the Hurricane Challenge: spectral shaping, compression and Lombard adaptation on a synthetic voice, about 4 dB better in speech-shaped noise and 5 dB with a competing talker.
- Hazan, V. and Simpson, A. (1998). The effect of cue-enhancement on the intelligibility of nonsense word and sentence materials presented in noise. Speech Communication 24, 211-226.
- Kennedy, E., Levitt, H., Neuman, A. C. and Weiss, M. (1998). Consonant-vowel intensity ratios for maximizing consonant recognition by hearing-impaired listeners. JASA 103, 1098-1114.
- Krause, J. C. and Braida, L. D. (2004). Acoustic properties of naturally produced clear speech at normal speaking rates. JASA 115, 362-378. And (2009), JASA 125, 3346-3357: more energy at 1 to 3 kHz explains part of the advantage of clear speech; filtering the envelope alone does not help.
- Benoît, C., Grice, M. and Hazan, V. (1996). The SUS test: a method for the assessment of text-to-speech synthesis intelligibility using Semantically Unpredictable Sentences. Speech Communication 18, 381-392.
