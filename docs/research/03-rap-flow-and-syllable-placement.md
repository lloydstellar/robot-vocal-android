# 03: Rap flow and syllable placement

Status: research note, October 2026. Scope: English lyrics only. Gaps are marked **not researched**. Numbers marked "computed" were calculated for this note from public corpora (method in Appendix A).

## 1. What the literature and the data say

### The 16th-note grid

Rap scholarship transcribes flow on a grid of 16 sixteenth-note cells per 4/4 bar. Adams draws "lyric charts" in which "each column in the chart represents a sixteenth-note subdivision of the beat" and each row is one bar (Adams 2009, note 2). MCFlow, the largest open rap corpus (124 songs, 374 verses, 5,803 bars), quantizes "to the nearest 16th, triplet-16th, or 32nd note" and transcribes passages that lag "behind the beat" on the beat (Condit-Schultz 2016). Ohriner defends the 16-cell space against finer grids because emcees and critics "overwhelmingly emphasize a cardinality-16 metric space" (Ohriner 2019b, para. 2.4).

Computed from the current MCFlow repository (66,409 syllables, 6,444 bars): 97.3% of syllable onsets sit on a 16th position, 2.0% on a 32nd and 0.6% on a triplet position. Notated syllable durations are 16ths (63.9%), 8ths (28.7%), 32nds (2.6%), dotted 8ths (2.1%) and quarters (1.0%). Condit-Schultz reports that "85% of durations in a given rap verse alternate between two primary durations related by the ratio of two to one".

### Density and speed

| Measure | Value | Source |
|---|---|---|
| Typical rap speed | about 4.5 syllables/s, unchanged 1980-2014 | Condit-Schultz 2016 |
| Mean syllabic density, 472 verses | 4.24 syllables/s | Duinker 2020, ch. 5 |
| Syllables per bar | median 11, IQR 9-12, 5th-95th percentile 4-15, max 27 | computed, MCFlow |
| Syllables per bar by tempo | 12.8 at 60-80 BPM, 10.5 at 90-100, 8.5 at 110-141 | computed, MCFlow |
| Inter-onset interval | median 174 ms, IQR 153-283 ms, 5th percentile 121 ms | computed, MCFlow |
| Phrase length | median 8 syllables over 3 beats; most common "five syllables over two beats, or around ten syllables over four beats" | computed; Condit-Schultz 2016 |
| Song tempo | MCFlow median 96 BPM (IQR 88-108); Duinker: 84-105 most common, range 62-154 | computed; Duinker 2020 |

Slower beats get more syllables per bar, so syllables per second stay near 4 to 4.5. Manabe notes that a 16th at 134 ms is "approaching the 120 ms limit for comprehending vocal articulation (London 2012, 27-35)" (Manabe 2019, note 27). The app should therefore treat about 120 ms as the shortest syllable slot.

| BPM | 16th (ms) | Triplet 8th (ms) | Syllables/s if every 16th is filled |
|---|---|---|---|
| 70 | 214 | 286 | 4.7 |
| 90 | 167 | 222 | 6.0 |
| 100 | 150 | 200 | 6.7 |
| 120 | 125 | 167 | 8.0 |
| 128 | 117 | 156 | 8.5 |
| 140 | 107 | 143 | 9.3 |
| 160 | 94 | 125 | 10.7 |

(computed: 16th = 15000/BPM, triplet 8th = 20000/BPM)

### Where onsets, stresses and rhymes fall

Computed from MCFlow. Stress in MCFlow is the transcriber's perceived stress (two levels), rhymes are hand-marked.

| Slot | Count | % of all onsets | % of stressed syllables | P(stressed given onset) | % of stressed rhymes |
|---|---|---|---|---|---|
| 1 | 1 | 7.3 | 12.1 | 0.95 | 7.0 |
| 2 | e | 5.2 | 1.6 | 0.17 | 1.3 |
| 3 | & | 7.6 | 9.8 | 0.73 | 3.5 |
| 4 | a | 6.1 | 3.0 | 0.28 | 2.7 |
| 5 | 2 | 7.4 | 12.1 | 0.93 | 10.2 |
| 6 | e | 5.1 | 1.6 | 0.18 | 1.4 |
| 7 | & | 7.3 | 9.0 | 0.71 | 4.4 |
| 8 | a | 6.3 | 3.4 | 0.30 | 3.6 |
| 9 | 3 | 7.3 | 11.9 | 0.93 | 11.5 |
| 10 | e | 5.5 | 1.9 | 0.20 | 1.4 |
| 11 | & | 7.0 | 7.6 | 0.62 | 5.5 |
| 12 | a | 6.1 | 5.0 | 0.47 | 13.5 |
| 13 | 4 | 6.0 | 9.8 | 0.94 | 21.7 |
| 14 | e | 4.4 | 1.3 | 0.17 | 2.3 |
| 15 | & | 6.3 | 8.4 | 0.76 | 8.0 |
| 16 | a | 5.0 | 1.6 | 0.18 | 1.8 |

Onsets are spread almost evenly, but stress is not: 45.9% of stressed syllables land on the four beats, 34.8% on the 8th offbeats and 19.3% on the other 16ths. Ohriner's 75-verse genre corpus gives the same picture for accents within the beat: 46.1% on the beat, 38.9% on the "&", 9.9% on the last 16th and 5.1% on the "e" (computed from his flowBook data). Beats 2 and 4 carry the snare backbeat ("bass-drum events on beats one and three alternating with snare-drum events on beats two and four", Ohriner 2016), and stressed syllables use them as much as beats 1 and 3.

Rhyme and line ends (computed, MCFlow): 84.2% of phrase-final stressed syllables are rhymed. Of those rhymes, 27.5% fall on beat 4, 20.7% on the 16th before it, 11.7% on the "&" of 4, and 78.1% somewhere in beats 3 and 4. In 51.6% of bars the last stressed syllable is a rhyme; when it is, 40.2% sit on beat 4 and 27.2% on the "&" of 4. Duinker counts one third of 5,544 rhymes on the downbeat of beat 4 and calls rhymes one 16th early "anticipation rhymes", "a hallmark of old-school flow" (Duinker 2020, ch. 5). Adams lists placement of rhyming syllables, placement of accented syllables, correspondence of syntax and bars, and syllables per beat as the metrical techniques of flow (Adams 2009, para. 8).

Pickups and rests (computed, MCFlow): 38.4% of phrases start on the last three 16ths of the previous bar and only 14.2% on the downbeat; 59.5% of phrase starts follow a notated rest; 73% of bars contain a rest, most often on slot 1 (18%) and slot 15 (16.9%). Jay-Z's "99 Problems" shows the pattern: rhymes "on or before the fourth beat, leaving ample space at the end of each measure for subsequent anacruses" (Duinker 2020, ex. 5.8).

### Groove classes and styles

Ohriner models "vocal groove" as the durations between accents, "usually a combination of IAIs of 2 or 3 units in length" (Ohriner 2019a, p. 136, quoted by Burkhart 2021). Within a 16-unit bar this yields seven classes. Share of his genre corpus at exact match (flowBook, Table 4.3 data): `<22222222>` 44%, `<3322222>` 27%, `<3232222>` 10%, `<332332>` 7%, `<333322>` 5%, `<3223222>` 4%, `<333232>` 3%. In MCFlow the most common stress layers per bar are `X.X.X.X.X.X.X.X.` (5.5% of bars), `X.X.X.X.X.X.X...` (5.3%), `X.X.X.X.X..X..X.` (4.7%) and the double tresillo `X..X..X.X..X..X.` (2.1%) (computed). Full onset patterns are far more varied: 4,279 distinct patterns in 5,791 bars, so presets should guide placement, not dictate it.

Krims's three flow styles are "sung", "percussion-effusive" and "speech-effusive" (Krims 2000, via Adams 2009 and Duinker 2019). Sung style has "repeated rhythmic groupings, on-beat accents, and rather strict rhyming schemes"; percussion-effusive uses "off-beat attacks and a sharply-attacked and crisp delivery" (Duinker 2019, quoting Krims pp. 51-52). Adams contrasts Kurtis Blow's "Basketball" (couplets on successive fourth beats, nothing shorter than a 16th) with later flows full of enjambment and 32nds (Adams 2009).

### Triplet flow, double time, half time

Duinker defines triplet flow as rapping "where the unit pulse, or tactus, is triple-divided" and separates mixed, phrasal and total triplet flow (Duinker 2019). In his 50-song corpus (2012-2017), "with only five exceptions, their tempos fall between 58 and 75 bpm", which he notes "could also be construed as 120-150 bpm" if the pulse is doubled; "Versace" is listed at 66, "Bad and Boujee" at 63, "Bodak Yellow" at 62. Sixteenth-note triplets at 60-75 BPM give 6 to 7.5 syllables/s, and about half the songs show delayed entries or tresillo-like grouping dissonance. In triplet flow "the on-beat syllable of the triplet is always emphasized" (Ohriner 2019b, note 9). Manabe credits "Versace" (2013) with making "rapping in continuous triplets to a 4/4 beat the rage in trap" (Manabe 2019). MCFlow, which ends in 2014, has only 0.95% triplet durations (computed). Below about 75 BPM rappers mix very fast and slow rhythms, including "double-time flow" in 32nds and "stutter rap (a series of scotch snaps, or short-long groupings, on each downbeat)" (Duinker 2020, ch. 7, citing Komaniecki 2019).

Half time: trap is "often programmed in half-time", typically "around 70 (140) BPM" (Wikipedia, Trap music). Scholars disagree on notation: Duinker notates trap at 60-75 with the snare on 2 and 4, Komaniecki at 137-140 (Duinker 2020, note 202). The app works at the tempo the user types. At 140 BPM with a half-time snare on beat 3, the "beat 4 rhyme" of the slow bar falls on beat 3 of every second fast bar.

### Micro-timing and swing

Roger Linn defines MPC swing as the share of an 8th given to the first 16th: "50% is no swing" and "66% means perfect triplet swing"; "a 90 BPM swing groove will feel looser at 62% than at a perfect swing setting of 66%", and "a swing setting of 54% will loosen up the feel without it sounding like swing" (Attack Magazine, 2 July 2013). In 30 canonical breakbeats the median 16th swing ratio is "1.2:1" (about 54.5% in MPC terms), uncorrelated with tempo (Frane 2017). Swing per genre beyond these two sources: not researched.

Vocal timing: Duinker heard lagging in 96 of 472 verses (20%), swung timing in 47 and speech-like timing in 56 (Duinker 2020, ch. 5). Measured lags: Snoop Dogg in "Nuthin' but a G Thang" (95 BPM) 13 to 41 ms early in a phrase, 95 to 132 ms at its end; Rampage in "Flava in Ya Ear (Remix)" (89 BPM) 46 to 179 ms; André 3000 in "Rosa Parks" swings 16ths with beat-upbeat ratios of 1.5 to 2.75, mean near 2.0 (Duinker 2020, ch. 7). Ohriner measures Kendrick Lamar about 96 ms behind the beat in one line, and a swing from 52 ms behind to 72 ms ahead between lines of "Momma" (Ohriner 2019b). Thresholds: 25 to 40 ms for duration differences (Levitin et al. 2000, cited by Ohriner 2019b); Benadon treats deviations under 50 ms as imperceptible (via Duinker 2020). For alignment, Ohriner marks a syllable at "the midpoint of the increase in amplitude at the beginning of a vowel", not at its first consonant. Published P-centre figures: not researched.

### Tempo ranges

| Style | Tempo | Source |
|---|---|---|
| Hip hop (general) | 60-100 | Ableton Learning Music, "Tempo and genre" |
| Boom bap, golden age | 84-105 most common; MCFlow median 96 | Duinker 2020; computed |
| Electro, electro-funk | 105-136 for the tracks in section 2 (database values) | SongBPM |
| Miami bass | not researched (Wikipedia only says "raised dance tempos") | |
| Trap | 50-88 half time (100-176), typically 70 (140) | Wikipedia, Trap music |
| UK drill | about 138-151 | Wikipedia, UK drill, citing Complex (2018); Chicago drill not researched |
| House | 115-130 | Ableton; Wikipedia |
| Techno | 120-150 (Wikipedia); 120-140 "techno/trance" (Ableton) | |

## 2. Robot, vocoder and electro phrasing

Tempos are SongBPM values (Spotify analysis, not checked by ear). The precise grid placement of these vocals is **unverified**: no published transcription was found, and it should be checked by ear before a preset claims to copy a track.

| Track | BPM | Voice technology (documented) | Phrasing (documented) |
|---|---|---|---|
| Kraftwerk, "The Robots" (1978) | 115 | main lyrics "produced by vocal synthesis and processed through a vocoder"; Russian line "spoken in a pitched-down voice" (Wikipedia) | short refrain lines, repetition |
| Kraftwerk, "Numbers" (1981) | 129 | album credits list vocoder and speech synthesis (Wikipedia, Computer World) | "a robotic voice counting from one to eight in German", then other languages, "spoken in a staccato, robotic style" (Songfacts) |
| Kraftwerk, "Pocket Calculator" / "Computer World" (1981) | 136 / 129 | as above | short first-person phrases; lists (from the lyrics) |
| Daft Punk, "Harder, Better, Faster, Stronger" (2001) | 123 (123.45 measured, Scalo 2026) | vocoded; Discovery used a Roland SVC-350 and a DigiTech Vocalist (Wikipedia) | every line is four two-syllable words (from the lyrics) |
| Daft Punk, "Technologic" (2005) | 127 | "electronically transposed voice"; "monotonous pitch-shifted voice" (Wikipedia) | commands "spoken in rhythm to a beat" (Wikipedia) |
| Daft Punk, "Robot Rock" (2005) | 112 | vocoder (Wikipedia) | title phrase repeated |
| Afrika Bambaataa & Soulsonic Force, "Planet Rock" (1982) | 127 | vocoder sound from a Lexicon PCM41 "really, really tight delay" (Baker in Buskin 2008, via Wikipedia) | a sung chorus, a list of cities, and G.L.O.B.E.'s "MC popping": "Instead of being on the beat, being off the beat" (Baker 2013) |
| Egyptian Lover, "Egypt, Egypt" (1984) | 127 | Roland SVC-350 vocoder (Wikipedia) | not researched |
| Cybotron, "Clear" (1983) | 125 | "sparse, Chipmunk-style vocals" (LA Times 1993, via Wikipedia) | not researched |
| Dopplereffekt, "Scientist" | 127 | not researched | not researched |
| Zapp, "More Bounce to the Ounce" (1980) | 105 | Roger Troutman's talk box (Wikipedia) | "a slower, funkier tempo" (Chris Frantz, via Wikipedia) |
| Mantronix, "Fresh Is the Word" | 98 | not researched | not researched |
| Newcleus, "Jam on It" (1984) | 116 | "vocals that were sped up" on "Jam-On Revenge" (AllMusic, via Wikipedia) | "wikki wikki" repetition |

What the documented material shares: a fixed vocal pitch (vocoder carrier, speech synthesizer, talk box, sped-up tape), lists and counting, repeated short phrases, call and response between a lead and a group, and tempos of about 112 to 136 BPM. Duinker finds early triplet passages in "Planet Rock" (Duinker 2019, note 17).

## 3. Syllables and stress from English text

CMUdict (GitHub, October 2025) has 126,052 words in 135,166 entries, ARPAbet phones with stress digits (1 primary, 2 secondary, 0 none) and a BSD-style licence that makes use "completely unrestricted". Syllables are the phones that carry a digit. Its stress marks need care: most one-syllable function words are marked 1 ("to", "of", "I", "you", "my", "for", "at", "it"), and only "the", "a", "and", "in" and a few others have 0 in their first entry. Noun and verb variants ("record", "present", "object", "permit") are listed without part of speech. Variants differ in syllable count: "fire", "hour", "our" (1 or 2), "every", "family", "different", "camera" (2 or 3).

Coverage on rap lyrics (computed): 98.2% of MCFlow word tokens and 90.9% of types are in CMUdict. Missing words are slang ("homie", "tryna", "holla", "whatcha", "shawty"), names and spelled-out numbers ("eightoheight"). "vocoder", "dancefloor", "BPM" and "808" are missing; "technologic" and "kraftwerk" are present.

Fallbacks for words not in the dictionary (computed against CMUdict; a guess counts as right if any variant matches):

| Method | Correct syllable count, CMUdict types | Same, MCFlow tokens |
|---|---|---|
| Count vowel groups | 79.0% | 87.6% |
| Rule heuristic (silent e, -ed, -es, hiatus such as "lion", "idea", syllabic m) | 90.7% | 98.5% |
| TeX hyphenation points + 1 (Liang patterns, en-us) | 51.5% | 92.3% |

Hyphenation is the wrong tool for syllables: Liang's patterns "find 89% of the hyphens in a pocket dictionary word list" (Liang 1983), and the en-us file (Kuiken, permissive licence) never splits a single letter off ("about", "every" stay whole; hyphenmin 2 and 3) and follows morphology ("hy-phen-ation").

| Primary-stress method (correct syllable count given) | Held-out CMUdict types | MCFlow words treated as unknown (tokens) |
|---|---|---|
| Always first syllable | 67.3% | 78.9% |
| Hand rules (unstressed prefixes, -tion, -ic, -ity, -ee) | 69.1% | 87.5% |
| Suffix table learned from CMUdict, backing off from 6 letters | 86.0% (2 syll. 90.6%, 3 syll. 81.1%, 4 syll. 81.6%) | 85.3% |

Rule count plus suffix table gets both syllable count and stress right for 78.6% of held-out words and 79.1% of MCFlow tokens. In CMUdict, 88.6% of two-syllable words have primary stress on the first syllable. Of 8,052 words that split into two common words (this includes some suffixed forms such as "act+ing"), 90.5% carry primary stress in the first part, and syllable counts add up in 96.8%, so splitting compounds such as "dancefloor" is worth doing. eSpeak NG stress output, published grapheme-to-phoneme accuracy and number normalisation rules: not researched.

Recommended pipeline: normalise numbers and abbreviations (808 becomes "eight oh eight"; letters for initialisms, a word for acronyms), look up CMUdict (keep apostrophes), split unknown compounds into dictionary words, otherwise apply the rule count and the suffix table, demote function words to unstressed, and show every guessed word in the editor so the user can confirm syllables and stress.

## 4. Placement algorithm

Text-setting research gives the principles: "matching of stress to strong position", "avoidance of long lapses", "avoidance of extreme syllable compression" and alignment of phrase ends with line ends (Hayes 2009); correspondence is "free at the beginnings of units, strict at ends", and syllables get beats in proportion to their natural duration, with phrase-final syllables lengthened (Hayes and Kaun 1996). The algorithm is dynamic programming over syllables and slots with a cost that encodes those rules and the corpus statistics above.

```
INPUT
  syllables s[0..n-1]: stress in {P, S, U}, func (function word), heavy (long vowel or coda),
                       wordEnd, punct in {none, comma, stop}, rhyme (default: last P of the line)
  preset: S (16 or 12 slots/bar), tmpl[S] chars {X x R r p .}, pS[S] = P(stressed | onset),
          dens = [dmin, dmax] syllables per bar, maxPickup, gate, swing, offsetMs
  bpm, locks (syllable -> fixed slot), prevEnd (first free slot after the previous line)
slotMs = 60000 / bpm / (S / 4)

choose bars:
  Nmin = max(1, ceil(n / dmax)); Nmax = min(4, max(Nmin, ceil(n / dmin)))
  for N in Nmin..Nmax: run DP; total[N] = cost + 1.0*(N - Nmin) + (1.5 if N == 3)
  take the N with the lowest total (user override wins)

DP for N bars: slots j = -maxPickup .. N*S - 1 (j >= prevEnd, locked syllables fixed)
  state (i, j, d): syllable i starts at slot j, previous syllable lasted d slots
  D[0][j] = onset(0, j) + 0.05 * max(0, j)                  # beginnings free, no drift
  D[i][j][d] = min over j' = j - d, d' of
               D[i-1][j'][d'] + ioi(i-1, d, d') + lapse(i-1, j', j) + onset(i, j)
  answer = min over j of D[n-1][j][*]; backtrack; the last syllable holds to the bar end

onset(i, j):  k = j mod S
  c  = 2.0 if tmpl[k] == '.' else 0
  c += -ln pS[k]                    if stress P
  c += -0.5 ln pS[k]                if stress S
  c += -(0.7 if func else 1) ln(1 - pS[k])   if stress U       (each term capped at 4)
  if j < 0: c += 0.3 * |j| if stress U else 3.0                 # pickups are unstressed
  if rhyme: c += 0 on R in the last bar, 0.5 on r, else 1.5 + 0.3 * slots to nearest R;
            +3 if not in the last bar                           # endings strict
ioi(i, d, d'):   d = slots until the next onset
  c  = 5.0 if d * slotMs < 120                                  # too fast to articulate
  c += 0.8 * max(0, d - 2)          if not wordEnd              # no long holds inside a word
  c += 0.4 * max(0, d - 2)          if wordEnd and punct none
  c += 1.5 if d < 3                 if punct comma               # want a rest at a comma
  c += 2.0 if d < 4                 if punct stop                # want a breath at a stop
  c += 1.0 if heavy and punct != none and d == 1                # phrase ends get time
  c += 0.3 if d / d' not in {1, 2, 1/2}                         # two durations, ratio 2:1
lapse(i, j', j): 0.6 per beat slot strictly between j' and j, unless punct[i] != none

RENDER
  t(j) = barStart + (j mod S) * slotMs + offsetMs
  16-grid swing: the 2nd and 4th 16th of each beat move by (swing - 0.5) * 2 * slotMs
  place the vowel onset of the rendered syllable at t(j); sound for d * slotMs * gate
```

pS comes from the MCFlow table for 16-slot presets; for 12-slot presets it is 0.95, 0.30, 0.40 per beat (a design choice following Ohriner's first-triplet emphasis). The weights are starting values to tune by ear. With n up to 40 and 64 slots the DP has under 200,000 transitions per bar count, cheap on a phone. Re-running it with locks gives the "re-flow around my edits" function.

## 5. Flow presets

Pattern legend, one character per slot: `X` stressed syllable preferred, `x` onset allowed, `R` rhyme target, `r` second-choice rhyme slot, `p` pickup for the next line, `.` onset discouraged (rest or hold).

| # | Preset | Grid and pattern per bar | Rhyme | Syll./bar | BPM | Swing, offset | Basis and tracks it echoes |
|---|---|---|---|---|---|---|---|
| 1 | Robot 8ths | `X.x.X.x.X.x.R.x.` | 13 | 4-8 | 110-130 | 50%, 0 ms, gate 0.7 | One syllable per 8th, deadpan; the vocoder and speech-synthesis songs above ("The Robots" 115, "Pocket Calculator" 136); exact match unverified |
| 2 | Word per beat | `X.x.X.x.X.x.R.x.`, rule: each word starts on a beat; counting variant `X...X...X...R...` | 13 | 4-8 | 118-130 | 50%, 0 ms, gate 0.6 | Lists and commands: "Numbers" (129), "Technologic" (127), "Harder, Better, Faster, Stronger" (123) |
| 3 | Call and response | 2 bars: A `X.x.X.x.X.x.R...`, B `....X.x.X.x.R...` | 13 | 4-7 | 115-130 | 50%, 0 ms, bar B on a second voice | "Planet Rock" (127): chorus and city list (Baker 2013) |
| 4 | Electro 3+3+2 | `X.xX.xX.X.xX.xR.` | 15 | 6-10 | 105-130 | 50-54%, 0 ms | Ohriner's `<332332>` class; MCFlow double tresillo; Adams 2008 3+3+2 motive; electro of Egyptian Lover and Cybotron (not transcribed) |
| 5 | Old-school couplet | `X.x.X.x.X.xxR...` | 13, r 12 | 6-9 | 95-115 | 50%, 0 ms | Krims "sung" style; Kurtis Blow "Basketball" (Adams 2009); anticipation rhymes (Duinker 2020) |
| 6 | Boom bap laid back | `XxXxXxXxXxXRRxXx`, weights = MCFlow table | 13, r 12, 15 | 8-13 | 84-100 | 54-58%, +15 ms | MCFlow norms; Linn 54-62%; Frane 1.2:1; lagging in 20% of verses |
| 7 | Pickup flow | `X.xxX.x.XxxxR.pp` | 13, r 12 | 8-12 | 85-100 | 54%, +10 ms | 38% of phrases start on slots 14-16 (MCFlow); Jay-Z "99 Problems" (Duinker 2020) |
| 8 | 16th rapid fire | `XxxxXxxxXxxxRxxx` | 13 | 12-16 | 80-100 | 50%, 0 ms | Blackalicious "Blazing Arrow": in 12 bars "only five sixteenth-note beats do not have attacks" (Adams 2009) |
| 9 | Triplet flow | 12 slots: `XxxXxxXxxRxx`; option: enter one triplet late | 10 | 9-12 | 120-150 | none, 0 ms | Migos "Versace" (66 half time), "Bad and Boujee" (63), Cardi B "Bodak Yellow" (62) (Duinker 2019) |
| 10 | Half-time trap | 2 bars: `X.x.x.xxX.x.x.xx` / `X.x.x.xxR...x.xx` | bar 2 slot 9 | 6-10 | 130-160 | 50%, 0 ms | Snare on 3; trap at 70 (140); UK drill 138-151 |
| 11 | Stutter snap | `Xx..Xx..Xx..Rx..` | 13 | 6-8 | 130-150 | 50%, 0 ms | Scotch snaps on each beat; Komaniecki's examples at 140 and 137 BPM (via Duinker 2020) |

## 6. Grid editor

Show one row per bar with 16 cells (12 for triplet bars, switchable per bar), beat numbers and `e & a` labels, shaded beats 1 to 4, and a drum lane (kick, snare, hats) above. Each placed syllable shows its text, a stress mark, a colour band per word, a rhyme marker, and a hold bar over the cells it lasts. Warn in the cell when a syllable is shorter than about 120 ms, a stressed syllable sits on an `e` or `a`, or an unstressed syllable sits on beat 1.

Operations: move (drag, snaps to cells; ripple or swap mode), stretch or shorten (drag the right edge = hold), insert or delete a rest, lock a syllable and re-flow the unlocked ones (DP with locks), nudge a line by one cell or by milliseconds, split or merge syllables, set stress (P, S, U) and pick a pronunciation variant ("every" with 2 or 3 syllables), mark rhymes, stutter (repeat a syllable or word into the next cells, "ro-ro-robot", with shorter gates), apply a preset to a bar, copy a bar pattern, assign a bar to a second voice, undo and redo, and audition with the drums.

## 7. Worked examples

Example 1, Boom bap laid back, 92 BPM (163 ms per slot), one bar. "Vocoder voices in the city tonight": "vocoder" is not in CMUdict, so the rule count gives 3 syllables and the suffix table puts stress on the first (VO-co-der); "voices" VOI-ces, "city" CI-ty and "tonight" to-NIGHT come from CMUdict; "in" and "the" are function words. 11 syllables, rhyme on NIGHT.

```
slot  1   2   3   4   5    6    7   8    9   10  11  12  13     14  15  16
      VO  co  der -   VOI  ces  in  the  CI  ty  to  -   NIGHT  -   -   .
```

Every stressed syllable is on a beat and NIGHT is on beat 4. Onsets: 0, 163, 326, 652, 815, 978, 1141, 1304, 1467, 1630, 1957 ms; NIGHT holds 489 ms, then a 163 ms breath. Swing 56% moves the 2nd and 4th 16th of each beat 20 ms later; the line sits 15 ms behind the grid.

Example 2, Robot 8ths, 120 BPM (8th = 250 ms). "Robots marching, signal rising, never stopping": all six words are trochees (RO-bots with a secondary stress, MARCH-ing, SIG-nal, RIS-ing, NEV-er, STOP-ping). 12 syllables exceed 8 per bar, so the DP compares 2 and 3 bars and picks 2; rests land on both commas.

```
bar 1 slot  1   3     5      7    9  11  13   15
            RO  bots  MARCH  ing  .  .   SIG  nal
bar 2 slot  1    3    5  7   9    11  13    15
            RIS  ing  .  .   NEV  er  STOP  ping
```

Each syllable sounds 175 ms (gate 0.7), the comma rests last 500 ms, and STOP falls on beat 4 of bar 2.

Example 3, Triplet flow, 140 BPM (triplet 8th = 143 ms, 7 syllables/s). "Calculate, modulate, oscillate, generate": four words stressed on the first syllable with a secondary stress on "-late"/"-ate". One bar of 12 slots: CAL cu late | MOD u late | OS cil late | GEN er ate. Each stress opens a triplet; the "-ate" rhymes fall on the third triplet and GEN on beat 4. Onsets every 143 ms from 0 to 1571 ms; the bar lasts 1714 ms.

## 8. Not researched

eSpeak NG stress output and its licence terms for bundling; published grapheme-to-phoneme and stress accuracies; Krims, Paul Edwards ("How to Rap", only its table of contents seen), Martin Connor and Justin Williams in their own words (cited here through Adams, Duinker and Ohriner where at all); Kautny; swing per genre; P-centre figures; Miami bass and Chicago drill tempos; by-ear verification of the robot-track rhythms and tempos; Dutch lyrics (out of scope).

## Appendix A: computed figures

MCFlow: GitHub Computational-Cognitive-Musicology-Lab/MCFlow, commit 2f74391 (2024-04-25), 124 Humdrum files. Onsets were rebuilt from `**recip` durations per bar; a duration crossing a barline shifts the next bar; positions between 16ths were rounded down, as in Condit-Schultz 2016. Phrases start at break codes 3 or 4 and end at -3/-4. Ohriner data: GitHub mohriner/flowBook, commit faaec23. Dictionary tests: CMUdict commit 7479086, 117,467 alphabetic words; stress tests on a random 10% hold-out of words with 2 or more syllables; MCFlow words were also scored with the suffix table trained without them.

## Sources

- Adams, K. 2008. "Aspects of the Music/Text Relationship in Rap." Music Theory Online 14.2. https://mtosmt.org/issues/mto.08.14.2/mto.08.14.2.adams.html
- Adams, K. 2009. "On the Metrical Techniques of Flow in Rap Music." Music Theory Online 15.5. https://mtosmt.org/issues/mto.09.15.5/mto.09.15.5.adams.php
- Ableton. "Tempo and genre." Learning Music. https://learningmusic.ableton.com/make-beats/tempo-and-genre.html
- Baker, A. 2013. "Key Tracks: Arthur Baker on 'Planet Rock'." Red Bull Music Academy Daily, 4 April 2013. https://daily.redbullmusicacademy.com/2013/04/key-tracks-arthur-baker-on-planet-rock
- Burkhart, B. 2021. Review of Ohriner, Flow. IASPM Journal 11(2). https://iaspmjournal.net/index.php/IASPM_Journal/article/download/1085/pdf/7541
- CMUdict. https://github.com/cmusphinx/cmudict
- Condit-Schultz, N. 2016. "MCFlow: A Digital Corpus of Rap Transcriptions." Empirical Musicology Review 11(2). https://doi.org/10.18061/emr.v11i2.4961 ; data https://github.com/Computational-Cognitive-Musicology-Lab/MCFlow
- Duinker, B. 2019. "Good Things Come in Threes: Triplet Flow in Recent Hip-Hop Music." Popular Music 38(3): 423-456. Accepted manuscript: McGill repository, handle 20.500.14905/80413.
- Duinker, B. 2020. "Diversification and Post-Regionalism in North American Hip-Hop Flow." PhD diss., McGill University. Handle 20.500.14905/98227.
- Frane, A. V. 2017. "Swing Rhythm in Classic Drum Breaks From Hip-Hop's Breakbeat Canon." Music Perception 34(3): 291-302. https://doi.org/10.1525/mp.2017.34.3.291
- Hayes, B. 2009. "Textsetting as Constraint Conflict." In Towards a Typology of Poetic Forms, 43-62. Abstract: https://brucehayes.org/Textsetting
- Hayes, B., and A. Kaun. 1996. "The Role of Phonological Phrasing in Sung and Chanted Verse." The Linguistic Review 13. https://www.brucehayes.org/papers/HayesAndKaunMetrics1996.pdf
- hyph-utf8 English patterns (Kuiken, based on Liang). https://github.com/hyphenation/tex-hyphen
- Komaniecki, R. 2017. "Analyzing Collaborative Flow in Rap Music." Music Theory Online 23.4. https://mtosmt.org/issues/mto.17.23.4/mto.17.23.4.komaniecki.html
- Liang, F. M. 1983. "Word Hy-phen-a-tion by Com-put-er." PhD diss., Stanford. https://tug.org/docs/liang/liang-thesis.pdf
- Linn, R. 2013. "Roger Linn on Swing, Groove & the Magic of the MPC's Timing." Attack Magazine. https://www.attackmagazine.com/features/interview/roger-linn-swing-groove-magic-mpc-timing/
- Manabe, N. 2019. "We Gon' Be Alright? The Ambiguities of Kendrick Lamar's Protest Anthem." Music Theory Online 25.1. https://mtosmt.org/issues/mto.19.25.1/mto.19.25.1.manabe.html
- Ohriner, M. 2016. "Metric Ambiguity and Flow in Rap Music: A Corpus-Assisted Study of Outkast's 'Mainstream' (1996)." Empirical Musicology Review 11(2). https://doi.org/10.18061/emr.v11i2.4896
- Ohriner, M. 2019a. Flow: The Rhythmic Voice in Rap Music. Oxford University Press. Data: https://github.com/mohriner/flowBook
- Ohriner, M. 2019b. "Lyric, Rhythm, and Non-alignment in the Second Verse of Kendrick Lamar's 'Momma'." Music Theory Online 25.1. https://mtosmt.org/issues/mto.19.25.1/mto.19.25.1.ohriner.php
- Scalo, J. 2026. "Was Daft Punk Having a Laugh When They Chose the Tempo of Harder, Better, Faster, Stronger?" https://www.madebywindmill.com/tempi/blog/hbfs-bpm/
- SongBPM track pages, for example https://songbpm.com/@kraftwerk/numbers---2009-remastered-version and https://songbpm.com/@afrika-bambaataa/planet-rock (accessed 18 October 2026).
- Songfacts. "Numbers" by Kraftwerk. https://www.songfacts.com/facts/kraftwerk/numbers
- Wikipedia articles "Trap music", "UK drill", "House music", "Techno", "Electro (music)", "Miami bass", "Planet Rock (song)", "The Robots", "Computer World", "Technologic", "Human After All", "Discovery (Daft Punk album)", "Robot Rock (song)", "Egyptian Lover", "Clear (Cybotron song)", "More Bounce to the Ounce", "Roger Troutman", "Newcleus" (accessed 18 October 2026).
