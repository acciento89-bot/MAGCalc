# MAGCalc Android artwork

`launcher.svg` is the independent geometric master. `ORIGIN.json` records its code authorship and inputs. It replaces the old Android PNG input; the preserved iOS icon is not read by this pipeline.

Generate with `node scripts/generate_android_icons.cjs` using existing Sharp0.35.5 (via local module resolution or `NODE_PATH`). `--check` re-renders and compares every output byte. `--verify` needs only Node and checks the master, generator, notice and output hashes plus the real manifest/resource binding. No renderer download, native build or store operation is performed.

The generated1024RGB launcher remains the existing `@drawable/ic_launcher` resource. There is no adaptive or round-icon override. The same master produces `store/google-play/icon-512.png` for every Google Play language; `icon.svg` is an editable derivative, not the upload file. `generation.json` binds all exact inputs and outputs.

The artwork notice under `app/src/main/assets/licenses/project-artwork/NOTICE.txt` is included by the normal Android assets source set. The next APK/AAB must verify that notice and the original font OFLs. Actual compiled icon/launcher verification remains pending.
