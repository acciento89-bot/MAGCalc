#!/usr/bin/env python3
"""Compatibility entry point for the Android SVG/Sharp icon recipe; no raster work in Python."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
raise SystemExit(subprocess.call(["node", str(ROOT / "scripts/generate_android_icons.cjs"), *sys.argv[1:]], cwd=ROOT))
