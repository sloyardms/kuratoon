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
    """Draw one page showing its chapter and page number and save it as JPEG."""
    width, height = size
    image = Image.new("RGB", size, PALETTE[color_index % len(PALETTE)])
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, width - 1, height - 1], outline=BORDER_COLOR, width=BORDER_WIDTH)

    center_x = width // 2
    draw.text((center_x, height * 0.30), f"Ch {chapter}", font=_font(CHAPTER_FONT_SIZE),
              fill=TEXT_COLOR, anchor="mm")
    draw.text((center_x, height * 0.58), f"{page:03d}", font=_font(PAGE_FONT_SIZE),
              fill=TEXT_COLOR, anchor="mm")
    draw.text((center_x, height - 60), label, font=_font(LABEL_FONT_SIZE),
              fill=SUBTEXT_COLOR, anchor="mm")

    path.parent.mkdir(parents=True, exist_ok=True)
    # No exif/icc/dpi arguments: Pillow then writes no metadata that could vary between runs.
    image.save(path, format="JPEG", **JPEG_OPTIONS)


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


@dataclass(frozen=True)
class Case:
    name: str
    purpose: str
    build: Optional[Callable[[Path], None]] = None

    @property
    def implemented(self) -> bool:
        return self.build is not None


# Keyed by the folder names in fixtures/README.md. Cases without a builder arrive in phase 3.
CASES: dict[str, Case] = {c.name: c for c in [
    Case("series-basic", "manifest with url; Chapter 0 - Prologue ... Chapter 5 - The End",
         build_series_basic),
    Case("series-messy", "mixed chapter/page names, Thumbs.db, desktop.ini"),
    Case("series-update", "same URL as series-basic, chapters 6-7 (append)"),
    Case("series-conflict", "same URL as series-basic, includes chapter 3 again"),
    Case("series-duplicate-number", "Chapter 2 and chapter 02"),
    Case("series-no-number", "contains Extra/"),
    Case("series-corrupt", "one truncated/corrupt jpg"),
    Case("series-html-only", "no URL, only source.html with a canonical link"),
    Case("series-manifest-override", "manifest title/type/language differ from the fake site"),
    Case("oneshot", "images directly in the folder, source.txt"),
    Case("webtoon-tall", "very tall strips for reader layout tests"),
    Case("restore-snapshot", "complete kind: snapshot sidecar + chapters"),
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
