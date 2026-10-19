# 10. A S.A.M. voice we may ship

Research for Robot Vocal: a second search for a voice that sounds like S.A.M. (Software Automatic Mouth, Don't Ask Software, 1982, on the Commodore 64, Atari and Apple II), after the first formant attempt (CHIP EIGHT, a square wave through a 4-bit crusher) did not sound like it. Researched 2026-10-19.

Licence rule from Lloyd: only explicit licences that allow commercial use and fit GPL-3.0; nothing without a licence, nothing non-commercial. Tables count as much as code.

## 1. Key findings

- **S.A.M. was never released.** The 1982 manual asks for "the express written permission of DON'T ASK, Inc." and forbids translating it. Nothing from SoftVoice, Inc. (the current owner) or from Mark Barton, its author, grants anything; not in the 34 issues of the C port, not in Barton's 2020 interview, not on text2speech.com. SoftVoice does answer mail: in 2017 it allowed personal, non-commercial use of its Windows engine to one person, which shows how it handles requests.
- **Every "SAM mode" carries S.A.M.'s data.** Mutable Instruments Braids, Shruthi-1, Ambika and Plaits use S.A.M.'s vowel rows (8 of Braids' 17 rows are S.A.M. rows number for number); the author's note says "This is an implementation of SAM." The same holds for the SAM modules and packages for VCV Rack, Surge, Godot, Unity, Rust, Go, npm, micro:bit and Arduino: they wrap the unlicensed port or its tables, sometimes under a GPL or MIT label that cannot cover it.
- **No clean-room version exists.** Speako8 (MIT) and klattsch (MIT) are clean, but they are Klatt-style synthesizers that only remind of S.A.M. narrator-ts (MIT) is a transcription of SoftVoice's Amiga narrator code, so it is out as well.
- **The method is free.** Copyright covers code and tables, not methods (17 U.S.C. 102(b); Directive 2009/24/EC, art. 1(2)). A renderer of our own, written from the public description below with tables of our own, can be shipped. The name cannot: the manual calls Software Automatic Mouth a trademark of Don't Ask Inc.
- **Why CHIP EIGHT sounded wrong.** S.A.M. has no filters. Three oscillators sit on the formants and restart on every pitch period; that restart is the buzz, and the square on F3 gives a hard top that no resonator makes. A smooth Klatt voice crushed afterwards keeps the Klatt spectrum and only gains noise.

## 2. The public description

| What | Source and words |
|---|---|
| Three generators, summed | README of the C port: "A = A1 * sin ( f1 * t ) + A2 * sin ( f2 * t ) + A3 * rect( f3 * t ) where rect is a rectangular function with the same periodicity like sin." |
| Pitch by restarting them | SAM2600 README: "resetting the DDS counters every pitch period produces a nice f0 buzz with very little CPU time." Emilie Gillet's tech note describes the same reset. |
| 16 levels | README comments on the 6502 loop: "load sine value (high 4 bits)", "get amplitude (in low 4 bits)", "multiplication table". |
| 4-bit output | README: "get highest 4 bits" into the SID's volume register; Wikipedia on the C64's 4-bit volume DAC; the Atari manual on its sound register. |
| Rate | Not published. A loop of about 26 instructions on a 1 MHz 6502 points to a few kHz, around 7 kHz (inferred). |
| Hiss | README: "it seems that it uses some noise (e. g. for "s") using a table with random values." |
| Mouth and throat | Manual: knobs that "adjust the size of S.A.M.'s throat and his mouth", 128 normal, six preset voices. |
| Vowels | SoftVoice's own formant page lists rounded Peterson and Barney (1952) values: beet 270, 2300, 3000; boot 300, 870, 2250 and so on. |

## 3. What the app does with it

The home computer engine (`voice/retro/MicroSynth.kt`, `MicroEngine.kt`) is written from section 2 and the literature:

- Two sines on F1 and F2 and a square on F3, restarted on every pulse; 16 levels for each oscillator, a 4-bit sine and a 4-bit output, 1-bit noise for the hiss; a step rate of about 7350 a second, each step held.
- Formants, transitions and timing from the retro engine's phoneme table (whackintalk, MIT, itself based on Klatt), not from S.A.M. The level of each oscillator is the Klatt cascade's gain at that formant, with the range in dB halved; the hiss follows the table's frication and breath levels the same way. Voiced fricatives follow Klatt (1980): the hiss rides on the voice.
- Every period is a whole number of steps, with the step clock set per note, so notes are in tune and the grit stays harmonic.
- Mouth moves F1 and throat the higher formants, after what they follow in phonetics (mouth opening and tract length). Five voices under names of our own.

Measured: F1 and F2 of AA, UW, AE and IH within 20 and 15 % of the table; notes at 98, 220 and 392 Hz within 0.3 %; s and sh at about -7 dB from the vowel, z at -10 dB, and f, th and h at one 4-bit step, about -17 dB.

## 4. Open

- SoftVoice could be asked for written permission to use S.A.M.'s own tables under terms that fit GPL-3.0. Any ordinary commercial licence would not fit, because eSpeak NG is in the same app. Contact: info@text2speech.com. Lloyd decides.
- The Atari version had gaps in the sound whenever the screen's DMA ran ("lights on"); a gravelly preset could imitate that.

## Sources

- s-macke/SAM README, https://github.com/s-macke/SAM (read for its description only; the code has no licence)
- S.A.M. manual, Don't Ask Software, 1982
- rossumur/SAM2600 README, https://github.com/rossumur/SAM2600
- Emilie Gillet, Speech synthesis algorithms, https://pichenettes.github.io/mutable-instruments-documentation/tech_notes/speech_synthesis_algorithms/
- SoftVoice formant page, http://www.text2speech.com/formant.html
- Mark Barton interview, Cherry Audio, 2020-07-23
- Joseph Katz (SoftVoice) to the NVDA list, 2017, https://nvda.groups.io/g/nvda/topic/softvoice_information_request/5170758
- G. E. Peterson and H. L. Barney, Control methods used in a study of the vowels, JASA 24(2), 1952
- D. H. Klatt, Software for a cascade/parallel formant synthesizer, JASA 67(3), 1980
