#!/usr/bin/env python3
"""Generate the synthetic import fixtures under fixtures/import/.

Every image is drawn from scratch (solid background + its chapter/page number in large text), so no
real manga/comic content ever enters the repo. Output is deterministic: fixed sizes, colors, the
vendored font and fixed JPEG settings, with no timestamps or randomness. Running the generator twice
produces byte-identical files (see check_deterministic.py).

Usage:
    python fixtures/generate/generate.py [--case NAME ...] [--out fixtures/import]
"""

from __future__ import annotations

import argparse
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
DEFAULT_OUT = HERE.parent / "import"
FONT_PATH = HERE / "fonts" / "DejaVuSans-Bold.ttf"

FAKE_SITE = "http://fake-manga.test"

# Rendering constants. Changing any of these changes every generated file.
PAGE_SIZE = (800, 1200)
TEXT_COLOR = (255, 255, 255)
SUBTEXT_COLOR = (230, 230, 230)
BORDER_COLOR = (20, 20, 20)
BORDER_WIDTH = 12
CHAPTER_FONT_SIZE = 140
PAGE_FONT_SIZE = 220
LABEL_FONT_SIZE = 36
JPEG_OPTIONS = {"quality": 85, "subsampling": 2, "optimize": False, "progressive": False}
PNG_OPTIONS = {"optimize": False, "compress_level": 6}
WEBP_OPTIONS = {"quality": 80, "method": 4, "lossless": False}

# Background per chapter, cycled; dark enough for white text to stay readable.
PALETTE = [
    (52, 73, 94),
    (142, 68, 173),
    (39, 128, 96),
    (192, 57, 43),
    (41, 98, 160),
    (160, 100, 20),
    (90, 90, 90),
    (22, 120, 140),
]


def _font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONT_PATH), size)


def render_page(path: Path, chapter: str, page: int, color_index: int, label: str,
                size: tuple[int, int] = PAGE_SIZE) -> None:
    """Draw one page showing its chapter and page number. The format follows the file suffix
    (.jpg/.jpeg, .png, .webp)."""
    width, height = size
    image = Image.new("RGB", size, PALETTE[color_index % len(PALETTE)])
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, width - 1, height - 1], outline=BORDER_COLOR, width=BORDER_WIDTH)

    center_x = width // 2
    # Tall strips keep the numbers near the top, visible without scrolling.
    text_height = min(height, PAGE_SIZE[1])
    draw.text((center_x, text_height * 0.30), f"Ch {chapter}", font=_font(CHAPTER_FONT_SIZE),
              fill=TEXT_COLOR, anchor="mm")
    draw.text((center_x, text_height * 0.58), f"{page:03d}", font=_font(PAGE_FONT_SIZE),
              fill=TEXT_COLOR, anchor="mm")
    draw.text((center_x, height - 60), label, font=_font(LABEL_FONT_SIZE),
              fill=SUBTEXT_COLOR, anchor="mm")

    path.parent.mkdir(parents=True, exist_ok=True)
    # No exif/icc/dpi arguments: Pillow then writes no metadata that could vary between runs.
    suffix = path.suffix.lower()
    if suffix in (".jpg", ".jpeg"):
        image.save(path, format="JPEG", **JPEG_OPTIONS)
    elif suffix == ".png":
        image.save(path, format="PNG", **PNG_OPTIONS)
    elif suffix == ".webp":
        image.save(path, format="WEBP", **WEBP_OPTIONS)
    else:
        raise ValueError(f"unsupported page format: {path.name}")


def write_text(path: Path, content: str) -> None:
    """Write UTF-8 text with LF line endings regardless of platform."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


# --- cases -------------------------------------------------------------------------------------

SERIES_BASIC_CHAPTERS = [
    ("0", "Prologue"),
    ("1", "The Beginning"),
    ("2", "Rising"),
    ("3", "The Middle"),
    ("4", "Falling"),
    ("5", "The End"),
]
SERIES_BASIC_PAGES = 4


def build_series_basic(case_dir: Path) -> None:
    write_text(case_dir / "kuratoon.yml",
               "kuratoon: 1\n"
               "kind: manifest\n"
               f"url: {FAKE_SITE}/series/basic\n")
    for index, (number, title) in enumerate(SERIES_BASIC_CHAPTERS):
        chapter_dir = case_dir / f"Chapter {number} - {title}"
        for page in range(1, SERIES_BASIC_PAGES + 1):
            render_page(chapter_dir / f"{page:03d}.jpg", number, page, index, "series-basic")


def manifest(case_dir: Path, *lines: str) -> None:
    write_text(case_dir / "kuratoon.yml",
               "kuratoon: 1\nkind: manifest\n" + "".join(f"{line}\n" for line in lines))


def chapters(case_dir: Path, label: str, folders: list[tuple[str, str]], pages: int,
             color_offset: int = 0) -> None:
    """One folder per (folder name, chapter label) with pages 001.jpg ... in order."""
    for index, (folder, number) in enumerate(folders):
        for page in range(1, pages + 1):
            render_page(case_dir / folder / f"{page:03d}.jpg", number, page, index + color_offset,
                        label)


# Mixed page names. The number drawn on each image is its expected reading position, so a wrong
# order is visible. Sort: last number in the name, then natural order; no number goes last.
SERIES_MESSY_PAGES = ["1.jpg", "01.webp", "2.png", "page 10.jpg", "page 10.5.png", "extra.jpg"]
SERIES_MESSY_JUNK = {
    "Thumbs.db": b"\x00synthetic thumbs cache\x00",
    "desktop.ini": b"[.ShellClassInfo]\r\nIconResource=synthetic\r\n",
    ".DS_Store": b"\x00\x00\x00\x01Bud1",
    "notes.txt": b"scan notes\n",
    "source.url": b"[InternetShortcut]\r\nURL=http://fake-manga.test/\r\n",
}
SERIES_MESSY_CHAPTERS = [
    ("chapter 012", "12"),
    ("Ch.12.5", "12.5"),
    ("Vol 2 Chapter 10", "10"),
    ("045", "45"),
]


def build_series_messy(case_dir: Path) -> None:
    manifest(case_dir, f"url: {FAKE_SITE}/series/messy", "title: Messy Series", "type: manga",
             'authors: [ "Messy Author" ]')
    for index, (folder, number) in enumerate(SERIES_MESSY_CHAPTERS):
        for position, name in enumerate(SERIES_MESSY_PAGES, start=1):
            render_page(case_dir / folder / name, number, position, index, "series-messy")
        for name, content in SERIES_MESSY_JUNK.items():
            (case_dir / folder / name).write_bytes(content)


def build_series_update(case_dir: Path) -> None:
    manifest(case_dir, f"url: {FAKE_SITE}/series/basic")
    chapters(case_dir, "series-update",
             [("Chapter 6 - New Dawn", "6"), ("Chapter 7 - Aftermath", "7")], 3, color_offset=6)


def build_series_conflict(case_dir: Path) -> None:
    manifest(case_dir, f"url: {FAKE_SITE}/series/basic")
    # Chapter 3 exists in series-basic with 4 pages. This copy has 2, so Replace shrinks it and
    # progress beyond page 2 is clamped. Chapter 8 is new.
    chapters(case_dir, "series-conflict", [("Chapter 3 - The Middle (HQ)", "3")], 2, color_offset=3)
    chapters(case_dir, "series-conflict", [("Chapter 8", "8")], 3, color_offset=8)


def build_series_duplicate_number(case_dir: Path) -> None:
    manifest(case_dir, f"url: {FAKE_SITE}/series/duplicate-number")
    chapters(case_dir, "series-duplicate-number",
             [("Chapter 1", "1"), ("Chapter 2", "2"), ("chapter 02", "02")], 2)


def build_series_no_number(case_dir: Path) -> None:
    manifest(case_dir, f"url: {FAKE_SITE}/series/no-number")
    chapters(case_dir, "series-no-number", [("Chapter 1", "1"), ("Extra", "?")], 2)


def build_series_corrupt(case_dir: Path) -> None:
    manifest(case_dir, f"url: {FAKE_SITE}/series/corrupt", "title: Corrupt Series", "type: manga",
             'authors: [ "Corrupt Author" ]')
    chapters(case_dir, "series-corrupt", [("Chapter 1", "1"), ("Chapter 2", "2")], 3)
    # Cut one page in half, like an interrupted download.
    broken = case_dir / "Chapter 2" / "002.jpg"
    data = broken.read_bytes()
    broken.write_bytes(data[: len(data) // 2])


def build_series_html_only(case_dir: Path) -> None:
    write_text(case_dir / "source.html",
               "<!doctype html>\n"
               '<html><head><meta charset="utf-8">\n'
               "<title>HTML Only Series - Fake Manga</title>\n"
               f'<link rel="canonical" href="{FAKE_SITE}/series/html-only">\n'
               "</head><body><h1>HTML Only Series</h1></body></html>\n")
    chapters(case_dir, "series-html-only", [("Chapter 1", "1"), ("Chapter 2", "2")], 2)


def build_series_manifest_override(case_dir: Path) -> None:
    manifest(case_dir, f"url: {FAKE_SITE}/series/override", "title: Manifest Title Wins",
             "type: manhua", "language: ja")
    chapters(case_dir, "series-manifest-override", [("Chapter 1", "1")], 2)


def build_oneshot(case_dir: Path) -> None:
    write_text(case_dir / "source.txt", f"{FAKE_SITE}/oneshot/one\n")
    for page in range(1, 6):
        render_page(case_dir / f"{page:03d}.jpg", "1", page, 4, "oneshot")


def build_webtoon_tall(case_dir: Path) -> None:
    manifest(case_dir, f"url: {FAKE_SITE}/series/webtoon-tall", "title: Tall Webtoon",
             "type: webtoon", "language: ko", 'artists: [ "Tall Artist" ]')
    for page in range(1, 4):
        render_page(case_dir / "Episode 1" / f"{page:03d}.jpg", "1", page, 2, "webtoon-tall",
                    size=(800, 12000))


RESTORE_ID = "01a20000-0000-7000-8000-000000000001"
RESTORE_CHAPTERS = [
    ("1", "Restored Start", "2026-01-05T10:00:00Z"),
    ("2", None, "2026-02-10T10:00:00Z"),
    ("2.5", "Omake", "2026-02-11T10:00:00Z"),
]
RESTORE_PAGES = 3


def build_restore_snapshot(case_dir: Path) -> None:
    lines = [
        "kuratoon: 1",
        "kind: snapshot",
        f"id: {RESTORE_ID}",
        f"url: {FAKE_SITE}/series/restored",
        "plugin: example",
        "type: manga",
        "language: en",
        "adult: false",
        "title: Restored Series",
        'titles: [ "Restored (Alt)" ]',
        "status: COMPLETED",
        "year: 2021",
        "description: Restored from a sidecar.",
        'authors: [ "Restore Author" ]',
        'artists: [ "Restore Artist" ]',
        'genres: [ "Drama" ]',
        'tags: [ "hq" ]',
        "parodies: []",
        "characters: []",
        'locked: [ "title" ]',
        "cover: { file: cover.jpg, crop: { x: 0.1, y: 0, w: 0.8, h: 0.8 } }",
        "created_at: 2026-01-05T09:00:00Z",
        "chapters:",
    ]
    for index, (number, title, created_at) in enumerate(RESTORE_CHAPTERS):
        title_yaml = "null" if title is None else f'"{title}"'
        pages = ", ".join(f"{{ f: {p:04d}.jpg, w: {PAGE_SIZE[0]}, h: {PAGE_SIZE[1]} }}"
                          for p in range(1, RESTORE_PAGES + 1))
        lines.append(f'  - {{ number: {number}, title: {title_yaml}, folder: "{number}", '
                     f"created_at: {created_at}, pages: [ {pages} ] }}")
        for page in range(1, RESTORE_PAGES + 1):
            render_page(case_dir / number / f"{page:04d}.jpg", number, page, index,
                        "restore-snapshot")
    lines += ["relations: []", "moments: []"]
    write_text(case_dir / "kuratoon.yml", "\n".join(lines) + "\n")
    render_page(case_dir / "cover.jpg", "1", 1, 7, "restore-snapshot cover")


@dataclass(frozen=True)
class Case:
    name: str
    purpose: str
    build: Optional[Callable[[Path], None]] = None

    @property
    def implemented(self) -> bool:
        return self.build is not None


# Keyed by the folder names in fixtures/README.md.
CASES: dict[str, Case] = {c.name: c for c in [
    Case("series-basic", "manifest with url; Chapter 0 - Prologue ... Chapter 5 - The End",
         build_series_basic),
    Case("series-messy", "mixed chapter/page names, Thumbs.db, desktop.ini", build_series_messy),
    Case("series-update", "same URL as series-basic, chapters 6-7 (append)", build_series_update),
    Case("series-conflict", "same URL as series-basic, includes chapter 3 again",
         build_series_conflict),
    Case("series-duplicate-number", "Chapter 2 and chapter 02", build_series_duplicate_number),
    Case("series-no-number", "contains Extra/", build_series_no_number),
    Case("series-corrupt", "one truncated/corrupt jpg", build_series_corrupt),
    Case("series-html-only", "no URL, only source.html with a canonical link",
         build_series_html_only),
    Case("series-manifest-override", "manifest title/type/language differ from the fake site",
         build_series_manifest_override),
    Case("oneshot", "images directly in the folder, source.txt", build_oneshot),
    Case("webtoon-tall", "very tall strips for reader layout tests", build_webtoon_tall),
    Case("restore-snapshot", "complete kind: snapshot sidecar + chapters", build_restore_snapshot),
]}


def generate_case(name: str, out: Path) -> Path:
    """Regenerate one case from scratch into out/<name>. Returns the case directory."""
    case = CASES[name]
    if not case.implemented:
        raise NotImplementedError("phase 3")
    case_dir = out / name
    if case_dir.exists():
        shutil.rmtree(case_dir)
    case.build(case_dir)
    return case_dir


def implemented_cases() -> list[str]:
    return [name for name, case in CASES.items() if case.implemented]


def generate(names: list[str], out: Path) -> None:
    for name in names:
        generate_case(name, out)


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Generate synthetic import fixtures.")
    parser.add_argument("--case", action="append", dest="cases", metavar="NAME",
                        choices=list(CASES), help="case to generate (repeatable); "
                        "default: all implemented cases")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT,
                        help=f"output directory (default: {DEFAULT_OUT})")
    parser.add_argument("--list", action="store_true", help="list cases and exit")
    args = parser.parse_args(argv)

    if args.list:
        for case in CASES.values():
            status = "ok" if case.implemented else "phase 3"
            print(f"{case.name:26} {status:8} {case.purpose}")
        return 0

    names = args.cases or implemented_cases()
    for name in names:
        try:
            case_dir = generate_case(name, args.out)
        except NotImplementedError as e:
            print(f"{name}: not implemented ({e})", file=sys.stderr)
            return 2
        print(f"{name}: {case_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
