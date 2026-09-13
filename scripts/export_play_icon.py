#!/usr/bin/env python3
"""Export the Google Play icon from the byte-identical iOS/Android source."""
from pathlib import Path
import argparse
import hashlib
import json
import tempfile
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "store/google-play"
SOURCE = ROOT / "MAGCalc/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
ANDROID = ROOT / "android/app/src/main/res/drawable-nodpi/ic_launcher.png"

def export(destination):
    with Image.open(SOURCE) as source:
        source.load()
        if source.size != (1024, 1024) or source.mode != "RGB":
            raise ValueError("Canonical iOS icon must be an opaque 1024x1024 RGB PNG")
        source.resize((512, 512), Image.Resampling.LANCZOS).save(destination)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if SOURCE.read_bytes() != ANDROID.read_bytes():
        raise AssertionError("Android launcher bytes differ from the canonical iOS icon")
    manifest = {str(SOURCE.relative_to(ROOT)): hashlib.sha256(SOURCE.read_bytes()).hexdigest()}
    if args.check:
        assert json.loads((OUT / "icon-source.json").read_text()) == manifest, "Launcher source changed: regenerate and update every Store locale"
        with tempfile.TemporaryDirectory() as temp:
            rendered = Path(temp) / "icon.png"
            export(rendered)
            with Image.open(rendered) as actual, Image.open(OUT / "icon-512.png") as stored:
                actual.load()
                stored.load()
                assert stored.size == (512, 512)
                assert ImageChops.difference(actual.convert("RGB"), stored.convert("RGB")).getbbox() is None, "Store icon differs from Android launcher artwork"
    else:
        OUT.mkdir(parents=True, exist_ok=True)
        export(OUT / "icon-512.png")
        (OUT / "icon-source.json").write_text(json.dumps(manifest, indent=2) + "\n")
