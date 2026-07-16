import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const output = resolve(scriptDirectory, '../public/runtime-config.js');
const token = process.argv.includes('--empty') ? '' : process.env['MAPBOX_ACCESS_TOKEN'] || '';
const payload = `window.__SENTINEL_CONFIG__ = Object.freeze(${JSON.stringify({
  mapboxAccessToken: token,
})});\n`;

await mkdir(dirname(output), { recursive: true });
await writeFile(output, payload, 'utf8');
