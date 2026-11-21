// Offline SVG render to build/branding only. Use a local Sharp installation or workspace NODE_PATH.
const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'build', 'branding');
async function main() {
  for (const shape of ['square', 'round']) {
    const input = await fs.readFile(path.join(output, `launcher-${shape}.svg`));
    for (const size of [48, 72, 96, 144, 192, 512, 1024]) {
      await sharp(input, { density: 1200 }).resize(size, size, { kernel: 'lanczos3' })
        .png().toFile(path.join(output, `launcher-${shape}-${size}.png`));
    }
  }
  console.log('Rendered square/round icon candidates at 48–1024 px');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
