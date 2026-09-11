#!/usr/bin/env python3
"""Export the existing Android launcher artwork for Google Play. Requires Inkscape."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res"
OUT = ROOT / "store/google-play"
A = "{http://schemas.android.com/apk/res/android}"
SOURCE = RES / "drawable/app_icon_source.xml"
COLORS = RES / "values/launcher_colors.xml"

def export(destination):
    vector = ET.parse(SOURCE).getroot()
    color = next(x.text for x in ET.parse(COLORS).getroot() if x.get("name") == "launcher_background")
    svg = ET.Element("svg", xmlns="http://www.w3.org/2000/svg", width="512", height="512",
                     viewBox=f"0 0 {vector.get(A+'viewportWidth')} {vector.get(A+'viewportHeight')}")
    ET.SubElement(svg, "rect", width="100%", height="100%", fill=color)
    for path in vector:
        if path.tag != "path":
            raise ValueError("Unsupported VectorDrawable element; extend exporter before changing artwork")
        attrs = {"d": path.attrib[A+"pathData"], "fill": path.get(A+"fillColor", "none").replace("@android:color/transparent", "none")}
        for native, target in [("strokeColor", "stroke"), ("strokeWidth", "stroke-width"), ("strokeLineCap", "stroke-linecap")]:
            if A+native in path.attrib:
                attrs[target] = path.attrib[A+native]
        ET.SubElement(svg, "path", attrs)
    with tempfile.TemporaryDirectory() as temp:
        source = Path(temp) / "icon.svg"
        ET.ElementTree(svg).write(source, encoding="utf-8", xml_declaration=True)
        subprocess.run(["inkscape", str(source), f"--export-filename={destination}", "--export-width=512", "--export-height=512"], check=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    manifest = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in [SOURCE, COLORS]}
    if args.check:
        assert json.loads((OUT / "icon-source.json").read_text()) == manifest, "Launcher source changed: regenerate and update every Store locale"
        from PIL import Image, ImageChops
        with tempfile.TemporaryDirectory() as temp:
            rendered = Path(temp) / "icon.png"
            export(rendered)
            with Image.open(rendered) as actual, Image.open(OUT / "icon-512.png") as stored:
                actual.load(); stored.load()
                assert stored.size == (512, 512) and stored.mode == "RGBA"
                assert ImageChops.difference(actual.convert("RGB"), stored.convert("RGB")).getbbox() is None, "Store icon differs from Android launcher artwork"
    else:
        OUT.mkdir(parents=True, exist_ok=True)
        export(OUT / "icon-512.png")
        (OUT / "icon-source.json").write_text(json.dumps(manifest, indent=2)+"\n")
