# MAGCalc Google Play icon

Use `icon-512.png` for every Google Play language/custom listing after the corresponding Android build passes icon and native QA. It is generated from the original project-authored `android/artwork/launcher.svg`; no Apple PNG is an input.

Generate with `node scripts/generate_android_icons.cjs` (existing Sharp0.35.5); verify rendered bytes with `--check` or committed hash/resource bindings with `--verify` (Node only). See `android/artwork/ORIGIN.json` and `generation.json`. Actual Store images were not uploaded by this source change.
