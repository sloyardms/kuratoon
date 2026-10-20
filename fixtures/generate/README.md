# Fixture generator

Builds the synthetic import folders in `fixtures/import/` (see `../README.md` for the cases).
Every image is drawn from scratch: a solid background with its chapter and page number in large
text, so ordering bugs are visible at a glance. Generated images are git-ignored; manifests
(`kuratoon.yml`, `source.txt`, ...) are committed.

Output is deterministic (fixed sizes, colors, vendored font, JPEG settings; no timestamps or
randomness), so two runs give byte-identical files. Byte-identity holds for a given Pillow version,
which is why `requirements.txt` pins it exactly.

## Usage

Python 3.10+.

```bash
python -m venv .venv
.venv/bin/pip install -r fixtures/generate/requirements.txt     # Windows: .venv\Scripts\pip
.venv/bin/python fixtures/generate/generate.py                  # all implemented cases
.venv/bin/python fixtures/generate/generate.py --case series-basic --out /tmp/import
.venv/bin/python fixtures/generate/generate.py --list           # cases and their status
.venv/bin/python fixtures/generate/check_deterministic.py       # used by CI
```

Generating a case deletes and rebuilds `<out>/<case>/`. Cases not yet implemented raise
`NotImplementedError("phase 3")` (the CLI exits with code 2).

## Font

`fonts/DejaVuSans-Bold.ttf` is DejaVu Sans Bold 2.37, vendored so rendering never depends on system
fonts. License (Bitstream Vera / public domain changes): `fonts/LICENSE`.
