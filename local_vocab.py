"""Convert a locally supplied word list to a Gboard personal dictionary ZIP.

No network calls. This migrates explicit words and shortcuts, not a prediction model.
For TSV use: Chinese word<TAB>full Pinyin (optional spaces/apostrophes).
For an iFlytek text export, pronunciation is generated locally and must be reviewed.
"""
from __future__ import annotations

import argparse
import json
import mmap
from pathlib import Path
import re
import sys
import struct
import zipfile

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / ".deps"))
HAN = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]+\Z")
PINYIN = re.compile(r"[a-z]+\Z")

# Public benchmark pronunciations, including the context-dependent 否 (pi).
OVERRIDES = {
    "陟罚臧否": "zhifazangpi",
    "拾遗补阙": "shiyibuque",
}


class WordIndexReadings:
    """Read pronunciations from the shipped GBI3 index; never read Rime learning."""
    def __init__(self, path: Path):
        with path.open('rb') as source:
            self.data = mmap.mmap(source.fileno(), 0, access=mmap.ACCESS_READ)
        if len(self.data) < 12 or self.data[:4] != b'GBI3':
            self.close()
            raise ValueError('Not a GBI3 word index')
        self.count, size = struct.unpack_from('>II', self.data, 4)
        self.han = 12 + self.count * 4
        self.blob = 12 + self.count * 8 + size * 16
        if self.blob > len(self.data):
            self.close()
            raise ValueError('Truncated word index')

    def close(self):
        self.data.close()

    def record(self, position):
        index, = struct.unpack_from('>I', self.data, self.han + position * 4)
        offset, = struct.unpack_from('>I', self.data, 12 + index * 4)
        offset += self.blob
        length, = struct.unpack_from('>H', self.data, offset)
        code = self.data[offset + 2:offset + 2 + length].decode('utf-8')
        offset += 2 + length
        length, = struct.unpack_from('>H', self.data, offset)
        word = self.data[offset + 2:offset + 2 + length].decode('utf-8')
        weight, = struct.unpack_from('>I', self.data, offset + 2 + length)
        return word, code, weight

    def reading(self, word):
        low, high = 0, self.count
        while low < high:
            middle = (low + high) // 2
            if self.record(middle)[0] < word:
                low = middle + 1
            else:
                high = middle
        best = None
        while low < self.count:
            found, code, weight = self.record(low)
            if found != word:
                break
            if best is None or weight > best[1]:
                best = code, weight
            low += 1
        return best[0] if best else None


def normalize_pinyin(value: str) -> str:
    value = value.lower().replace("ü", "v").replace("u:", "v")
    return re.sub(r"[\s'’]", "", value)


def convert(source: Path, destination: Path, kind: str, max_chars: int, encoding: str = "utf-8-sig", word_index: Path | None = None) -> dict:
    if word_index is not None:
        index = WordIndexReadings(word_index)
        try:
            return _convert(source, destination, kind, max_chars, encoding, index)
        finally:
            index.close()
    return _convert(source, destination, kind, max_chars, encoding, None)


def _convert(source, destination, kind, max_chars, encoding, index):
    if source.stat().st_size > 16 * 1024 * 1024:
        raise ValueError("Input exceeds 16 MiB; split and review the word list first.")
    records = []
    skipped = []
    seen = set()
    export_body = False
    for number, raw in enumerate(source.read_text(encoding=encoding).splitlines(), 1):
        if kind == 'ifly-text' and raw.strip() == '###以下为正文内容###':
            export_body = True
            continue
        if not raw.strip() or (not export_body and raw.lstrip().startswith("#")):
            continue
        fields = raw.strip().split("\t")
        if kind == "tsv":
            if len(fields) != 2:
                skipped.append({"line": number, "reason": "expected word and Pinyin columns"})
                continue
            word, spelling = (field.strip() for field in fields)
            generated = False
        else:
            # Plain word-list exports only: word plus optional metadata. Not a
            # decoder for version-dependent iFlytek binary/encrypted backups.
            word = raw.strip().split(maxsplit=1)[0]
            spelling = None
            generated = True
        if not HAN.fullmatch(word) or not 1 <= len(word) <= max_chars:
            skipped.append({"line": number, "reason": "not a Chinese word within length limit"})
            continue
        if spelling is None:
            spelling = OVERRIDES.get(word) or (index.reading(word) if index else None)
            generated = spelling is None
            if generated:
                from pypinyin import lazy_pinyin, Style
                spelling = "".join(lazy_pinyin(word, style=Style.NORMAL))
        spelling = normalize_pinyin(spelling)
        if not PINYIN.fullmatch(spelling) or len(spelling) > 128:
            skipped.append({"line": number, "reason": "Pinyin must be at most 128 Latin letters without tones"})
            continue
        key = (word, spelling)
        if key in seen:
            continue
        seen.add(key)
        records.append({"word": word, "pinyin": spelling, "generated": generated})

    if not records:
        raise ValueError("No valid Chinese words; no dictionary was created.")
    if len(records) > 50000:
        raise ValueError("More than 50000 words; split and review the input.")
    destination.parent.mkdir(parents=True, exist_ok=True)
    text = "# Gboard Dictionary version:1\n" + "".join(
        f"{record['pinyin']}\t{record['word']}\tzh-CN\n" for record in records
    )
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("dictionary.txt", text.encode("utf-8"))
    review = {"source_format": kind, "locale": "zh-CN", "entries": records,
              "skipped": skipped,
              "limits": ["Only explicit word shortcuts; prediction model and frequencies are not transferred.",
                         "iFlytek's type column is contact/other, not frequency; exported text has no original pronunciation.",
                         "Index readings use the local dictionary's highest-weight reading, not the original learned reading.",
                         "Generated pronunciation needs review, especially names and polyphonic characters.",
                         "Use the enhanced keyboard's local dictionary import and confirm the entry count.",
                         "Binary/encrypted iFlytek backups and cloud prediction models are not supported."]}
    destination.with_suffix(".review.json").write_text(
        json.dumps(review, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return {"words": len(records), "skipped": len(skipped),
            "readings_from_local_dictionary": sum(not record['generated'] for record in records) if kind == 'ifly-text' else 0,
            "needs_pronunciation_review": sum(record["generated"] for record in records)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path, help="Gboard dictionary ZIP path")
    parser.add_argument("--format", choices=("tsv", "ifly-text"), default="tsv")
    parser.add_argument("--max-chars", type=int, default=32)
    parser.add_argument("--encoding", choices=("utf-8-sig", "utf-16", "gb18030"), default="utf-8-sig",
                        help="Explicit text encoding; use utf-16 only for a BOM-marked text export")
    parser.add_argument("--word-index", type=Path, help="Optional shipped GBI3 dictionary for known phrase pronunciations")
    args = parser.parse_args()
    if not 1 <= args.max_chars <= 32:
        parser.error("--max-chars must be between 1 and 32")
    try:
        result = convert(args.source, args.output, args.format, args.max_chars, args.encoding, args.word_index)
    except (OSError, ValueError, ImportError) as failure:
        parser.exit(1, f"Conversion failed: {failure}\n")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
