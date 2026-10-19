# 11. The Speak & Spell as a voice

Research for Robot Vocal: can the sound of the 1978 Speak & Spell be a voice of its own, speaking any lyric, rather than an effect on another voice? Researched 2026-10-19.

Licence rule from Lloyd: only explicit licences that allow commercial use and fit GPL-3.0. Texas Instruments' word data is someone else's copyright unless a grant is found.

## 1. Key findings

- **Nothing ready-made can be used.** Every project that speaks text in a TI chip sound runs on data nobody may reuse: the toy's word ROMs, the TI-99/4A vocabulary and the Terminal Emulator II allophones (WORM), Street Electronics' Textalker ROM (Echo II), or TI and Acorn vocabularies copied into Talkie. Talkie's code itself is "GPLv2" without "or later". Plaits (MIT code) ships "LPC10 encoded words extracted from various TI ROMs" and words made from a macOS voice; its 15 phoneme frames have no stated source.
- **The chip can be copied exactly.** MAME's `tms5110.cpp` and `tms5110r.hxx` are BSD-3-Clause and hold the TMC0281 (the Speak & Spell's TMS5100) table set, checked against decapped chips and TI's patents: energy, pitch, ten reflection coefficient tables, the chirp and the interpolation shifts, plus a sample-level model of the synthesis.
- **The voice has to be ours.** The toy's voice was one radio announcer, recorded word by word, encoded and then hand-edited until it sounded right (Richard Wiggins: "a substantial amount of hand editing was used"). The way to get any text in that sound is to encode a free recorded male voice into the chip's frames. Flite's kal, already in the app, is a recorded US male voice at the chip's own 8 kHz.
- **Pitch and music clash.** The TMC0281 has 31 pitch periods between 52 and 195 Hz, 59 to 102 cents apart, so its own steps put notes up to half a semitone off.

## 2. The chip (TMC0281, from MAME)

- 8 kHz, a frame every 25 ms (200 samples), eight interpolation steps of 25 samples with shifts 3, 3, 3, 2, 2, 1, 1 and then the target itself.
- Frames: energy (4 bits, 0 silence, 15 stop), pitch (5 bits, 0 unvoiced), K1 and K2 (5 bits), K3 to K7 (4 bits), K8 to K10 (3 bits). Unvoiced frames carry K1 to K4 only; K5 to K10 are zero.
- Interpolation is held when the voicing changes and after silence; then the new values come at once.
- The source is the chirp (41 non-zero samples of a 52-entry table) at every pitch period, or noise: plus or minus 64 from a 13-bit shift register stepped 20 times a sample.
- A ten-stage lattice in integer arithmetic: coefficients of 10 bits, values of 15 bits, products shifted down 9; energy enters at the top.
- Output: the lattice's value clipped to 12 bits, of which the top 8 reach the DAC and the speaker. The loudest sounds clip hard.

## 3. What the app does with it

SPELLER (`voice/chip/`) renders the lyric with kal on a steady 100 Hz, encodes it, and speaks it through a Kotlin port of MAME's synthesis with the TMC0281 tables.

- **Frames follow the phonemes.** Every phoneme is cut into pieces of at most 25 ms, each analysed around its middle, and a frame runs from the middle of one piece to the middle of the next, so the chip glides across the border between two sounds and a short consonant keeps a moment of its own. Where the chip does not glide (a change of voicing, or after a rest) a frame starts exactly on the phoneme, so vowels start on their step as with the other voices. Voicing comes from the phoneme, pitch from the song; nothing is guessed.
- **Word by word.** The toy said every word on its own, and so does SPELLER: before a word that follows another without a rest, a silence of 15 ms comes off the end of the word before (at most 40 % of its last phoneme), and the next word starts on its step from silence, as the chip starts after a rest. In dense lines at 130 to 140 BPM the shortest phonemes are only 15 to 22 ms, against about 45 ms in short lines, and without these gaps the words ran into each other.
- **Word ends.** The chip fades into silence over a whole frame and then lets its filter ring on its sharpest resonances. At fast tempos that made words sound cut off and then run into each other: the last 5 ms of a word came out about 16 dB down, and 30 ms into a rest it still rang at about -19 dB. Now a word holds to its planned end and the filter lets go within about 5 ms of the rest.
- **In tune.** The chip's clock is set per note so that a period is a whole number of samples (110 Hz is 73 samples at 8030 a second). Every period is then the same, as on the chip at its own pitches, and the formants move by a percent or two at most.
- **Formants kept.** Each frame is analysed with ten-pole LPC and every coefficient goes to the nearest entry of the chip's tables. Voiced frames are analysed with the chirp's spectrum divided out (an idea from speakie, Apache-2.0 or MIT) and a mild lift of the highs (0.7). Measured over ten vowels on the retro engine's DESKTOP: F1 within 8 % and F2 within 3 % on average, where the usual strong lift (0.94) lost the low F1 of the i in "beat" and dividing out the chirp alone pushed U and I to the edges of the tables.
- **Loudness.** The energy code is found by running the frame's own filter on its own source; the loudest vowels just touch the chip's clipping.

## 4. Open

- An option to sing on the chip's own 31 pitch steps, for those who want it out of tune on purpose.
- A deeper announcer voice: CMU ARCTIC rms (free for commercial use, "subject to the pretty light restrictions") encoded offline, or a recording of our own. Both are new data sources; Lloyd decides.
- The SPELLING CHIP effect could move to the same exact chip, so "any voice through the chip" sounds like this voice does.
- The name Speak & Spell is TI's; it stays out of the app and the store listing.

## Sources

- MAME, src/devices/sound/tms5110.cpp, tms5110r.hxx and src/mame/ti/snspell.cpp at commit 7e7a99f, https://github.com/mamedev/mame (BSD-3-Clause)
- Texas Instruments, MOS Integrated Circuits for Voice Synthesis, June 1980, TMS5100 section
- US patent 4,209,836
- Richard Wiggins interview, Vintage Computing and Gaming, 2008, https://www.vintagecomputing.com/?p=528
- Adafruit, Bringing back the voice of Speak & Spell, https://learn.adafruit.com/bringing-back-the-voice-of-speak-spell
- python_wizard (MIT), https://github.com/ptwz/python_wizard; BlueWizard (MIT), https://github.com/patrick99e99/BlueWizard; speakie (Apache-2.0 or MIT), https://github.com/raphlinus/speakie
- Emilie Gillet, Plaits source and tech note (MIT), https://github.com/pichenettes/eurorack
- Flite and cmu_us_kal, https://github.com/festvox/flite; CMU ARCTIC, http://festvox.org/cmu_arctic/
