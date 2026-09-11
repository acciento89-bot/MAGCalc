# MAGCalc Google Play identity

Use `icon-512.png` for **every** Google Play language and custom listing. The app name is **MAGCalc**.

This is a direct raster export of the existing Android red expansion-tank artwork, with its existing `launcher_background` color. It is not a new icon design. Google’s rejection evidence on 2026-09-11 shows this same red tank installed on the device; the previously uploaded blue Store symbol was a different image.

Regenerate with `python3 scripts/export_play_icon.py`. Verify with `python3 scripts/export_play_icon.py --check` (Inkscape and Pillow required). The check compares the rendered pixels and source hashes. A future launcher change must update this Store asset and all Store languages together.
