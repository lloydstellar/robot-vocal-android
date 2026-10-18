#!/usr/bin/env python3
"""Packs CMUdict into the binary lexicon the app reads (app/src/main/assets/lexicon/cmudict.bin).

Get cmudict.dict and LICENSE from https://github.com/cmusphinx/cmudict and run:

    python3 tools/make_lexicon.py path/to/cmudict.dict

Of the variants of a word the one with the fewest syllables is kept ("every"
as EV-RY, "fire" as one syllable), the way words are said in rap.

Format, all integers big-endian:
    "RVLX", version (u8) = 1, count (u32)
    word offsets  (count + 1 times u32) into the word blob
    phone offsets (count + 1 times u32) into the phone blob
    word blob: the words in lower case, sorted by byte value, no separators
    phone blob: one byte per phone: bits 0-5 the index in PHONES, bits 6-7 stress + 1 on vowels
"""
import os
import struct
import sys

PHONES = ["AA", "AE", "AH", "AO", "AW", "AY", "B", "CH", "D", "DH", "EH", "ER", "EY", "F", "G", "HH",
          "IH", "IY", "JH", "K", "L", "M", "N", "NG", "OW", "OY", "P", "R", "S", "SH", "T", "TH",
          "UH", "UW", "V", "W", "Y", "Z", "ZH"]
INDEX = {p: i for i, p in enumerate(PHONES)}


def encode(pron):
    out = bytearray()
    for token in pron:
        stress = token[-1] if token[-1].isdigit() else None
        base = token[:-1] if stress else token
        code = INDEX[base]
        if stress is not None:
            code |= (int(stress) + 1) << 6
        out.append(code)
    return bytes(out)


def main():
    source = sys.argv[1]
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    target = os.path.join(root, "app/src/main/assets/lexicon/cmudict.bin")
    best = {}
    with open(source, encoding="utf-8") as f:
        for line in f:
            line = line.split("#")[0].strip()
            if not line:
                continue
            head, *pron = line.split()
            word = head.split("(")[0].lower()
            syllables = sum(1 for t in pron if t[-1].isdigit())
            if word not in best or syllables < best[word][0]:
                best[word] = (syllables, pron)
    words = sorted(best, key=lambda w: w.encode("ascii", "replace"))
    word_blob, phone_blob = bytearray(), bytearray()
    word_offsets, phone_offsets = [0], [0]
    for w in words:
        word_blob += w.encode("ascii", "replace")
        phone_blob += encode(best[w][1])
        word_offsets.append(len(word_blob))
        phone_offsets.append(len(phone_blob))
    with open(target, "wb") as f:
        f.write(b"RVLX" + struct.pack(">BI", 1, len(words)))
        f.write(struct.pack(f">{len(word_offsets)}I", *word_offsets))
        f.write(struct.pack(f">{len(phone_offsets)}I", *phone_offsets))
        f.write(word_blob)
        f.write(phone_blob)
    print(f"{len(words)} words, {os.path.getsize(target)} bytes -> {target}")


if __name__ == "__main__":
    main()
