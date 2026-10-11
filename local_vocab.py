"""Convert a locally supplied word list to a Gboard personal dictionary ZIP.

No network calls. This migrates explicit words and shortcuts, not a prediction model.
For TSV use: Chinese word<TAB>full Pinyin (optional spaces/apostrophes).
For an iFlytek text export, pronunciation is generated locally and must be reviewed.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys
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


def normalize_pinyin(value: str) -> str:
    value = value.lower().replace("ü", "v").replace("u:", "v")
    return re.sub(r"[\s'’]", "", value)


def convert(source: Path, destination: Path, kind: str, max_chars: int) -> dict:
    records = []
    skipped = []
    seen = set()
    for number, raw in enumerate(source.read_text(encoding="utf-8-sig").splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        fields = raw.strip().split("\t")
        if kind == "tsv":
            if len(fields) != 2:
                skipped.append({"line": number, "reason": "expected word and Pinyin columns"})
                continue
            word, spelling = (field.strip() for field in fields)
            generated = False
        else:
            # The documented iFlytek text format has a word and optional type.
            word = raw.strip().split(maxsplit=1)[0]
            spelling = None
            generated = True
        if not HAN.fullmatch(word) or not 2 <= len(word) <= max_chars:
            skipped.append({"line": number, "reason": "not a Chinese word within length limit"})
            continue
        if spelling is None:
            from pypinyin import lazy_pinyin, Style
            spelling = OVERRIDES.get(word) or "".join(lazy_pinyin(word, style=Style.NORMAL))
        spelling = normalize_pinyin(spelling)
        if not PINYIN.fullmatch(spelling):
            skipped.append({"line": number, "reason": "Pinyin must be Latin letters without tones"})
            continue
        key = (word, spelling)
        if key in seen:
            continue
        seen.add(key)
        records.append({"word": word, "pinyin": spelling, "generated": generated})

    if not records:
        raise ValueError("No valid Chinese words; no dictionary was created.")
    if len(records) > 5000:
        raise ValueError("More than 5000 words; split the input and review it in smaller batches.")
    destination.parent.mkdir(parents=True, exist_ok=True)
    text = "# Gboard Dictionary version:1\n" + "".join(
        f"{record['pinyin']}\t{record['word']}\tzh-CN\n" for record in records
    )
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("dictionary.txt", text.encode("utf-8"))
    review = {"source_format": kind, "locale": "zh-CN", "entries": records,
              "skipped": skipped,
              "limits": ["Only explicit word shortcuts; prediction model and frequencies are not transferred.",
                         "Generated pronunciation needs review, especially names and polyphonic characters.",
                         "Native ZIP import has not been tested on this phone; system dictionary insertion was tested."]}
    destination.with_suffix(".review.json").write_text(
        json.dumps(review, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return {"words": len(records), "skipped": len(skipped),
            "needs_pronunciation_review": sum(record["generated"] for record in records)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path, help="Gboard dictionary ZIP path")
    parser.add_argument("--format", choices=("tsv", "ifly-text"), default="tsv")
    parser.add_argument("--max-chars", type=int, default=12)
    args = parser.parse_args()
    if not 2 <= args.max_chars <= 32:
        parser.error("--max-chars must be between 2 and 32")
    try:
        result = convert(args.source, args.output, args.format, args.max_chars)
    except (OSError, ValueError, ImportError) as failure:
        parser.exit(1, f"Conversion failed: {failure}\n")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
