# Neural TTS on Android for Robot Vocal

Research date: 2026-10-18. Scope: English lyrics, rendered offline on a Pixel 8 class phone (Tensor G3, 8 GB RAM, [Google specs](https://support.google.com/pixelphone/answer/7158570)). The output goes into commercially released music. This round was cut short by a budget stop. Items marked "not researched" were not checked.

## 1. Summary

- No engine on Android returns timings through a ready-made API. sherpa-onnx returns only samples and a sample rate, and its maintainers have no plans for word timing ([#3727](https://github.com/k2-fsa/sherpa-onnx/issues/3727)). For beat-grid placement we need our own ONNX graphs that expose durations, or better, accept them.
- The licence rule removes most popular voices. Kokoro, KittenTTS and MeloTTS have undisclosed training data. Many Piper voices were fine-tuned from the research-only Lessac voice. Voices that pass the rule: Piper `en_US-libritts-high` (904 speakers), Piper `ljspeech`, `cori`, `kristin`, `norman` and `john`, Coqui VITS LJSpeech and VCTK (109 speakers), and Matcha LJSpeech (its weights licence is not stated explicitly).
- Recommended path: VITS (Piper) voices on ONNX Runtime, with the graph split at the duration step so the app can force per-phoneme frame counts (about 11.6 ms resolution). espeak-ng (GPL) handles phonemes. That is allowed because the app is open source.

## 2. Licence rule used here

- GPL components are allowed. The FSF says "The output of a program is not, in general, covered by the copyright on the code of the program" ([GPL FAQ](https://www.gnu.org/licenses/gpl-faq.html#WhatCaseIsOutputGPL)).
- Excluded: any model or dataset with non-commercial, research-only, unclear or undisclosed terms.
- Strict provenance: a voice fine-tuned from an excluded checkpoint is excluded too. The Lessac licence grants use "exclusively for Research Purposes" and excludes commercialising voice synthesis products ([licence](https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html)).
- ShareAlike data is treated as excluded. Creative Commons says that if models or outputs based on ShareAlike content are shared publicly, "following the ShareAlike condition would require AI developers to use the same CC license as the original works" ([CC on AI training](https://creativecommons.org/using-cc-licensed-works-for-ai-training-2/)).
- CC BY data is allowed with credit. The same page: "attribution could be a simple link to the source of the dataset".
- This is a technical reading, not legal advice.

## 3. Runtime

**sherpa-onnx**

- Apache-2.0, release v1.13.8 on 2026-09-10, last commit 2026-10-17 ([repo](https://github.com/k2-fsa/sherpa-onnx), [release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8)).
- Packaging: the AAR is 50.1 MB for 4 ABIs. For arm64 it holds `libonnxruntime.so` (22.2 MB unpacked, 8.0 MB compressed) and `libsherpa-onnx-jni.so` (4.8 MB). It is available through JitPack as `com.github.k2-fsa:sherpa-onnx:1.13.8` ([jitpack.yml](https://github.com/k2-fsa/sherpa-onnx/blob/master/jitpack.yml)).
- 16 KB pages: aligned since [PR 2520](https://github.com/k2-fsa/sherpa-onnx/issues/2417). The v1.13.8 arm64 libraries have `p_align 0x4000`.
- Kotlin API ([Tts.kt](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/Tts.kt)):
  - `OfflineTts(assetManager or null, config)`.
  - `generate(text, sid, speed)` and `generateWithConfig(text, GenerationConfig)`.
  - Configs for VITS, Matcha, Kokoro, Kitten, ZipVoice, Pocket and Supertonic.
  - The result is `GeneratedAudio(samples, sampleRate)` only. The C struct has the same fields ([c-api.h](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/c-api/c-api.h)).
- Controls (speed, length/noise scales, silence scale) apply per call, never per word or phoneme.
- Open [PR 4055](https://github.com/k2-fsa/sherpa-onnx/pull/4055) adds phoneme or token input.
- espeak-ng is statically linked into the JNI library today ([cmake](https://github.com/k2-fsa/sherpa-onnx/blob/master/cmake/espeak-ng-for-piper.cmake)). Version 2.0.0 will remove it for licence reasons ([#3731](https://github.com/k2-fsa/sherpa-onnx/issues/3731), "release it next week" on 2026-10-16). Phonemes will then come from a lexicon or a separate GPL AAR, `piper-phonemize` v1.5.7 (2026-10-14, 12.7 MB, per PR 4055).

**ONNX Runtime directly.** `onnxruntime-android` 1.31.0 was released 2026-10-09 ([release](https://github.com/microsoft/onnxruntime/releases/tag/v1.31.0)); the AAR is 55.1 MB for all ABIs. Running our own split graphs needs ORT directly. Do not combine it with the sherpa AAR: they clash on `libonnxruntime.so` ([#3291](https://github.com/k2-fsa/sherpa-onnx/issues/3291)).

**Speed.** Official RTF figures exist only for a Raspberry Pi 4 ([rtf page](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/rtf.html), 4 threads):

| Model | RTF |
|---|---|
| Piper medium | about 0.36 |
| Matcha LJSpeech | 0.41 |
| vits-vctk | 2.2 |
| Kokoro v1.1 | 3.2 |

No Pixel 8 RTF or RAM figures are published.

## 4. Engines

Sizes are package or ONNX sizes from the cited pages. "Durations" says whether a published graph outputs or accepts per-phoneme durations.

| Engine | Size | English speakers | Durations | Verdict |
|---|---|---|---|---|
| Piper VITS ([voices](https://huggingface.co/rhasspy/piper-voices)) | medium ONNX 63 MB (sherpa int8 about 21, fp16 about 36); high 114 to 137 MB | 1 to 904 per voice | None in ONNX; computed inside as `w_ceil` ([models.py](https://github.com/rhasspy/piper/blob/master/src/python/piper_train/vits/models.py)) | Per voice, section 5 |
| Coqui VITS `vits-coqui-en-ljspeech` / `-en-vctk` | 115 to 122 MB | 1 / 109 | Same as VITS | Allowed. "apache 2.0" in [Coqui models.json](https://raw.githubusercontent.com/idiap/coqui-ai-TTS/dev/TTS/.models.json); LJSpeech is public domain, VCTK is CC BY 4.0 (credit) |
| vits-ljs / vits-vctk (original VITS, [doc](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html)) | 109 / 116 MB | 1 / 109 | Same as VITS | Excluded until the weights licence is found (unverified) |
| MMS `vits-mms-eng` ([doc](https://k2-fsa.github.io/sherpa/onnx/tts/mms.html)) | 108 MB | 1 | Same as VITS | Excluded: CC-BY-NC-4.0 |
| Matcha `matcha-icefall-en_US-ljspeech` ([doc](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/matcha.html)) | 77 MB, plus HiFi-GAN v2 (3 MB) or v1 (55 MB) | 1 | Python returns `attn`; [ONNX export](https://github.com/shivammehta25/Matcha-TTS/blob/main/matcha/onnx/export.py) outputs mel/wav only | Conditional: LJSpeech is public domain, but no explicit weights licence. Avoid the `vocos-22khz-univ` vocoder (ShareAlike data in training) |
| Kokoro-82M v1.0 ([card](https://huggingface.co/hexgrad/Kokoro-82M)) | fp32 326, fp16 163, int8 92 MB ([onnx-community](https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX)) | 28 (20 US, 8 UK), plus blends | Outputs durations (25 ms units) in the [kokoro-onnx export](https://github.com/thewh1teagle/kokoro-onnx/blob/main/scripts/export.py) and the [timestamped ONNX](https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX-timestamped); accepts host durations in the [ExecuTorch split](https://github.com/NorbertKlockiewicz/kokoro-export/blob/main/demo/inference_example.py) | Excluded by default: per-voice data undisclosed, partly "synthetic audio generated by closed TTS models"; questions unanswered ([#376](https://github.com/hexgrad/kokoro/issues/376)). Decision for Lloyd |
| KittenTTS 0.8 nano/micro/mini ([repo](https://github.com/KittenML/KittenTTS), 0.8.1 on 2026-02-24) | 24 to 78 MB | 8 | ONNX outputs `duration`; [LiteRT split](https://huggingface.co/litert-community/kitten-tts-nano-0.8) repeats durations on the host | Excluded: training data undisclosed |
| kitten-tts-2 (1.7B) | 506 to 947 MiB | 47 | Not researched | Excluded: [revocable community licence](https://huggingface.co/KittenML/kitten-tts-2/blob/main/LICENSE.md) |
| MeloTTS English ([repo](https://github.com/myshell-ai/MeloTTS)) | 163 MB (sherpa) | 5 accents | VITS-style `w_ceil` | Excluded: training data not stated ([#177](https://github.com/myshell-ai/MeloTTS/issues/177)) |
| StyleTTS2 LJSpeech / LibriTTS ([repo](https://github.com/yl4579/StyleTTS2)) | 750 / 771 MB (.pth) | training-set voices | As Kokoro, by design | Allowed for training-set voices, but no Android port was found |
| Supertonic 2 / 3 ([doc](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/supertonic-3-en.html)) | 85 / 129 MB | 10 (v3) | One duration per text chunk only | Excluded pending check: OpenRAIL tag, training data not stated |
| Pocket TTS ([doc](https://k2-fsa.github.io/sherpa/onnx/tts/pocket.html)) | int8 98 MB | zero-shot cloning | None exposed | Not usable as packaged: the sherpa bundle says "non-commercial" ([#3971](https://github.com/k2-fsa/sherpa-onnx/issues/3971)). Upstream weights not researched |
| ZipVoice ([doc](https://k2-fsa.github.io/sherpa/onnx/tts/zipvoice.html)) | int8 109 MB, plus 54 MB vocoder | zero-shot | None exposed | Not researched (Emilia data licence) |
| Inflect nano/micro v2, Kokoro-7M-Distill | 22 to 43 MB / 29 MB | 1 | Inflect ships a duration/decode split | Excluded: undisclosed synthetic corpus ([Inflect](https://huggingface.co/owensong/Inflect-Nano-v2/blob/main/docs/DATA_AND_VOICE.md)); the distill inherits Kokoro's data |

Not researched in this round: piper1-gpl (licence change, phoneme alignment feature), rhasspy/piper archive status, Mimic 3, Larynx, Coqui XTTS-v2, Kyutai TTS 1.6B, NeuTTS, Chatterbox, OuteTTS, Orpheus, F5-TTS, other 2025 to 2026 models, and neural voices that already sound robotic.

The `en_US-glados` Piper voice in sherpa-onnx has no stated dataset, so it is excluded.

## 5. English Piper voices

Sources: each voice's MODEL_CARD, for example the [libritts high card](https://huggingface.co/rhasspy/piper-voices/raw/main/en/en_US/libritts/high/MODEL_CARD). The licence of the voice weights in the `rhasspy/piper-voices` repo itself was not researched. Confirm it before release.

| Voice | Speakers | Data and licence | Training | Verdict |
|---|---|---|---|---|
| en_US-libritts-high (136.7 MB) | 904 | LibriTTS ([OpenSLR 60](http://www.openslr.org/60/)), CC BY 4.0 | from scratch | **Allowed**, credit LibriTTS |
| en_US-ljspeech high, medium | 1 | LJ Speech, public domain | from scratch | **Allowed** |
| en_GB-cori high, medium | 1 | LibriVox, public domain | from scratch | **Allowed** |
| en_US-kristin, en_US-norman (medium) | 1 each | LibriVox, public domain | from scratch | **Allowed** |
| en_US-john-medium | 1 | LibriVox, public domain | fine-tuned from kristin | **Allowed** |
| en_US-bryce-medium | 1 | own voice, public domain | parent "unreleased voice" | Excluded (unclear parent) |
| en_US-libritts_r, en_GB-vctk, en_US-arctic, en_GB-alba, en_GB-aru, en_US-joe, en_US-mike, en_US-reza_ibrahim, en_US-sam, en_GB-jenny_dioco, en_US-kusal | 904, 109, 18, 1, 12, 1, 1, 1, 1, 1, 1 | CC BY 4.0, CC0, Apache-2.0, CMU, or "See URL" | fine-tuned from lessac | Excluded under strict provenance; decision for Lloyd |
| en_US-lessac, en_US-ryan, en_US-hfc_female/male, en_US-l2arctic, en_GB-semaine | various | research-only, CC BY-NC(-SA) | | Excluded |
| en_GB-northern_english_male, en_GB-southern_english_female | 1 each | CC BY-SA 4.0 | lessac / ryan parent | Excluded |
| en_US-kathleen, en_US-amy, en_US-danny, en_GB-alan | 1 each | CC0 or "See URL" | ryan or lessac parent | Excluded |

## 6. Timing: reading and forcing durations

One sixteenth note lasts `15 / BPM` seconds, which is 125 ms at 120 BPM. Model frame sizes:

- Piper/VITS: 256 samples ([hop_length](https://github.com/rhasspy/piper/blob/master/src/python/piper_train/vits/lightning.py)), so 11.6 ms at 22,050 Hz and 16 ms at 16 kHz. Matcha uses the same.
- Kokoro and Kitten: 600 samples at 24 kHz, so 25 ms ([Kokoro pipeline.py](https://github.com/hexgrad/kokoro/blob/main/kokoro/pipeline.py)).

Two placement rules:

- Carry rounding remainders from phoneme to phoneme, so the drift stays under half a frame.
- Do the final placement in samples at frame boundaries.

The perceived beat of a syllable is "not onset, offset or vowel onset but some complex function of them all" ([P-centres](https://johnmorton.co.uk/professional-life/theories/perceptual-centres/)). With per-phoneme durations we can put the vowel onset on the grid and expose an offset to the user.

| Method | Works with | Quality |
|---|---|---|
| A. Read durations, then cut and time-stretch syllables | Kokoro and Kitten exports; VITS or Matcha after an export change | Good; the robot DSP hides stretch artefacts |
| B. Force durations: run the duration model, replace the frame counts, run the decoder | VITS, Matcha, Melo (own split); Kokoro (published ExecuTorch split); Kitten (LiteRT split) | Best |
| C. One call per syllable or word, trimmed and stretched | any engine, including sherpa-onnx today | Choppy, but fits staccato robot delivery |

For Piper the split is a build-time graph edit. Model 1 ends at `w_ceil` and also outputs the encoder tensors. Model 2 takes those tensors plus durations and runs flow and decoder. This follows from `infer()` (lines 702 to 709 of [models.py](https://github.com/rhasspy/piper/blob/master/src/python/piper_train/vits/models.py)). It works on the ONNX file alone, which matters for `norman`, whose checkpoint was not saved. This approach is my own inference and has not been tested. Durations far from the predicted ones may sound unnatural.

Time-stretch options for A and C:

- [Signalsmith Stretch](https://github.com/Signalsmith-Audio/signalsmith-stretch): MIT, last commit 2026-09-25.
- [Bungee](https://github.com/bungee-audio-stretch/bungee): MPL-2.0, v2.4.35 on 2026-10-08.
- [Rubber Band](https://breakfastquay.com/rubberband/): GPL, v4.0.0 on 2024-10-25.

Syllable map: each English syllable has one vowel nucleus. Use user hyphens ("tech-no-lo-gic") first and rules as a fallback. CMUdict has a BSD-style licence ([LICENSE](https://github.com/cmusphinx/cmudict/blob/master/LICENSE)).

## 7. Shipping models

**Google Play limits** ([help 9859372](https://support.google.com/googleplay/android-developer/answer/9859372)):

| Item | Limit |
|---|---|
| Base module | 500 MB |
| Each asset pack | 1.5 GB |
| Install-time total | 4 GB |
| Fast-follow plus on-demand total | 30 GB |
| Asset packs per bundle | 100 |
| Mobile-data dialog | above 200 MB |

**Play Asset Delivery.** Fast-follow and on-demand packs land as real files at `getPackLocation().assetsPath()`. The app must show the size before fetching ([guide](https://developer.android.com/guide/playcore/asset-delivery/integrate-java); `asset-delivery-ktx:2.3.0`).

**Policy.** Native `.so` files must ship through Play, because Play bans downloading executable code ([policy](https://support.google.com/googleplay/android-developer/answer/9888379)). Model files are data and may be downloaded.

**16 KB pages.** Required for apps targeting API 35+. Updates without it are blocked from 2027-02-01 ([page sizes](https://developer.android.com/guide/practices/page-sizes)).

**Downloading from Hugging Face**

- Anonymous limit: 3,000 file requests per 5 minutes per IP ([rate limits](https://huggingface.co/docs/hub/en/rate-limits)).
- Pin URLs to a commit (`resolve/<sha>/`).
- Verify the SHA-256 that the tree API reports as `lfs.oid` ([example](https://huggingface.co/api/models/rhasspy/piper-voices/tree/main/en/en_US/libritts_r/medium)).
- GitHub Releases works as a mirror: files must be under 2 GiB each, with no bandwidth limit ([docs](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)).

**Licence notices**

- GPL-3.0 section 5d: interactive interfaces must show "Appropriate Legal Notices". Section 6d: providing the source from a designated place, such as the public GitHub repo, satisfies the source requirement ([text](https://www.gnu.org/licenses/gpl-3.0.txt)).
- Apache-2.0: show NOTICE contents "within a display generated by the Derivative Works" ([licence](https://www.apache.org/licenses/LICENSE-2.0)).
- CC BY: credit each dataset in the app, and preferably in release credits.
- [AboutLibraries](https://github.com/mikepenz/AboutLibraries) (15.3.0, 2026-10-13) builds the screen and accepts manual entries for native libraries and models.

## 8. Recommendation (under the rule)

**Ranked engines and voices**

1. **Piper VITS with split graphs on ONNX Runtime plus espeak-ng.** Voices: `en_US-libritts-high` (904 speakers), `ljspeech`, `cori` (UK), `kristin`, `norman`, `john`. Timing: method B at 11.6 ms; method C during prototyping.
2. **Coqui VITS VCTK (109 speakers) and LJSpeech.** Same split method. Speed on the Pixel 8 is unmeasured; the original VITS VCTK runs at RTF 2.2 on a Pi 4.
3. **Matcha LJSpeech with icefall HiFi-GAN v1/v2**, once upstream confirms the weights licence. Timing: own encoder/decoder split.
4. **Kokoro v1.0, only if Lloyd accepts the provenance risk.** It has the best timing tooling and 28 voices plus blends.

Taken together, ranks 1 to 3 give more than 1,000 distinct speakers before any DSP.

**Size budgets** (arithmetic from the sizes above, plus about 25 MB of runtime):

| Budget | Contents | Download |
|---|---|---|
| Small | Runtime plus 4 single-speaker Piper medium voices, int8 or fp16 | about 110 to 170 MB, all in the base module |
| Medium | Small plus libritts-high, Coqui VCTK and Matcha LJSpeech | about 450 to 500 MB; extras as on-demand packs or downloads |
| Large | Medium plus Piper high variants, plus Kokoro (if accepted) | about 0.9 to 1.1 GB |

**Order of work**

1. Prototype with sherpa-onnx using method C.
2. Build the Piper graph-split tool on a desktop and test forced durations on `libritts-high`.
3. Move the render path to ONNX Runtime 1.31.0.
4. Benchmark RTF and RAM on the Pixel 8.

## 9. Open questions and risks

- Lloyd decides on Kokoro (undisclosed data; some voice names match commercial voices) and on Lessac-derived Piper voices. Accepting the latter would add `libritts_r` and Piper VCTK.
- Weights licences need confirming: `rhasspy/piper-voices` (not researched), Matcha icefall (not stated), original VITS (unverified).
- Forced durations are untested, both for quality at large stretch ratios and for the graph edit itself.
- There are no Pixel 8 speed or memory numbers yet.
- sherpa-onnx 2.0.0 changes the phonemizer setup. Kokoro int8 had an unconfirmed artefact report ([#3754](https://github.com/k2-fsa/sherpa-onnx/issues/3754)).
- The not-researched list in section 4 is still open, including models that already sound robotic.
