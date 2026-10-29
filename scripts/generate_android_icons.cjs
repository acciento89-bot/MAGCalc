#!/usr/bin/env node
'use strict';

// CPU asset rendering only. --verify uses Node built-ins; --check re-renders with Sharp.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const originPath = 'android/artwork/ORIGIN.json';
const manifestPath = 'android/artwork/generation.json';
const masterPath = 'android/artwork/launcher.svg';
const launcherPath = 'android/app/src/main/res/drawable-nodpi/ic_launcher.png';
const noticePath = 'android/app/src/main/assets/licenses/project-artwork/NOTICE.txt';
const scriptPath = 'scripts/generate_android_icons.cjs';
const sha256 = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const local = relative => {
  assert(!path.isAbsolute(relative) && !relative.split(/[\\/]/).includes('..'), 'Expected project-local path');
  return path.join(root, relative);
};
const read = relative => fs.readFileSync(local(relative));
const origin = JSON.parse(read(originPath));
const source = read(masterPath);
const sourceHash = sha256(source);
const store = origin.googlePlayDirectory;
const outputs = [launcherPath, `${store}/icon-512.png`, `${store}/icon.svg`, `${store}/icon-source.json`];
assert(sourceHash === origin.masterSha256, 'Master differs from its authorship record');
assert(!/<(?:image|text|use|script)\b|\bhref\s*=|url\s*\(|@font/i.test(source.toString()), 'Master must contain only local geometry');
assert(/viewBox="0 0 1024 1024"/.test(source.toString()), 'Expected 1024 master');

function resourceBinding() {
  const manifest = read('android/app/src/main/AndroidManifest.xml').toString();
  assert(manifest.includes('android:icon="@drawable/ic_launcher"'), 'Launcher binding changed');
  assert(!manifest.includes('android:roundIcon='), 'Unexpected round-icon override');
  const resources = [];
  function walk(directory) {
    for (const item of fs.readdirSync(directory, { withFileTypes: true })) {
      const full = path.join(directory, item.name);
      if (item.isDirectory()) walk(full);
      else if (/ic_launcher|adaptive|foreground|background/i.test(item.name)) resources.push(path.relative(root, full));
    }
  }
  walk(local('android/app/src/main/res'));
  assert.deepEqual(resources.sort(), [launcherPath], 'Unexpected competing/adaptive launcher resource');
}

function pngInfo(bytes) {
  assert(bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), 'Expected PNG');
  return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20), colorType: bytes[25] };
}

function verify() {
  resourceBinding();
  const manifest = JSON.parse(read(manifestPath));
  assert.equal(manifest.masterSha256, sourceHash, 'Stale master hash');
  assert.equal(manifest.generatorSha256, sha256(read(scriptPath)), 'Stale generator hash');
  assert.equal(manifest.originSha256, sha256(read(originPath)), 'Stale origin hash');
  assert.equal(manifest.noticeSha256, sha256(read(noticePath)), 'Stale artwork notice hash');
  assert.deepEqual(Object.keys(manifest.outputs).sort(), outputs.slice().sort(), 'Unexpected output set');
  for (const output of outputs) assert.equal(sha256(read(output)), manifest.outputs[output].sha256, `Stale generated output: ${output}`);
  assert(read(`${store}/icon.svg`).equals(source), 'Store SVG differs from master');
  assert.deepEqual(JSON.parse(read(`${store}/icon-source.json`)), { [masterPath]: sourceHash }, 'Wrong Play icon input');
  assert.deepEqual(pngInfo(read(launcherPath)), { width: 1024, height: 1024, colorType: 2 }, 'Launcher must be opaque RGB1024');
  assert.deepEqual(pngInfo(read(`${store}/icon-512.png`)), { width: 512, height: 512, colorType: 2 }, 'Play icon must be opaque RGB512');
  return manifest;
}

async function render() {
  let sharp;
  try { sharp = require('sharp'); }
  catch { throw new Error('Rendering requires Sharp0.35.5. Use an existing Sharp runtime/NODE_PATH; --verify needs no renderer or installation.'); }
  assert.equal(sharp.versions.sharp, '0.35.5', 'Use recorded Sharp0.35.5 for reproducible raster outputs');
  const rendered = {};
  for (const [output, size] of [[launcherPath, 1024], [`${store}/icon-512.png`, 512]]) {
    rendered[output] = await sharp(source).resize(size, size).flatten({ background: '#ffffff' }).removeAlpha()
      .png({ compressionLevel: 9, adaptiveFiltering: false, palette: false }).toBuffer();
  }
  rendered[`${store}/icon.svg`] = source;
  rendered[`${store}/icon-source.json`] = Buffer.from(JSON.stringify({ [masterPath]: sourceHash }, null, 2) + '\n');
  return { rendered, renderer: sharp.versions };
}

(async () => {
  const args = process.argv.slice(2);
  assert(args.length === 0 || args.length === 1 && ['--check', '--verify'].includes(args[0]), 'Use no option, --check or --verify');
  if (args[0] === '--verify') {
    verify();
    console.log(`${origin.application}: SVG/recipe/notice/launcher/Play hashes and resource binding PASS`);
    return;
  }
  const { rendered, renderer } = await render();
  if (args[0] === '--check') {
    verify();
    for (const [output, bytes] of Object.entries(rendered)) assert(read(output).equals(bytes), `Re-render differs: ${output}`);
    console.log(`${origin.application}: original SVG re-render and all Android icon outputs PASS`);
    return;
  }
  resourceBinding();
  for (const [output, bytes] of Object.entries(rendered)) {
    fs.mkdirSync(path.dirname(local(output)), { recursive: true });
    fs.writeFileSync(local(output), bytes);
  }
  const outputHashes = Object.fromEntries(Object.entries(rendered).map(([file, bytes]) => [file, { sha256: sha256(bytes), bytes: bytes.length }]));
  const manifest = {
    application: origin.application, master: masterPath, masterSha256: sourceHash,
    generator: scriptPath, generatorSha256: sha256(read(scriptPath)),
    origin: originPath, originSha256: sha256(read(originPath)), notice: noticePath, noticeSha256: sha256(read(noticePath)),
    renderer, outputs: outputHashes,
  };
  fs.writeFileSync(local(manifestPath), JSON.stringify(manifest, null, 2) + '\n');
  verify();
  console.log(`${origin.application}: generated original Android launcher and Play icon; SHA256 manifest verified`);
})().catch(error => { console.error(error.message); process.exitCode = 1; });
