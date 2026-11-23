// Bundles bridge.ts + the twinleaf.gg engine into a single file for GraalJS.
// Usage: node build.js   ->  ../src/main/resources/assets/tcgshop/ptcg/engine.js
const esbuild = require('esbuild');
const path = require('path');

const stub = path.join(__dirname, 'stub.js');
const nodeOnly = ['fs', 'path', 'child_process', 'crypto', 'os', 'util', 'events', 'stream', 'zlib', 'http', 'https', 'net', 'url',
  'node-fetch', '@tcgdex/sdk', '@progress/pako-esm', 'reflect-metadata', 'typeorm', 'express', 'socket.io', 'sqlite3', 'mysql2',
  'nodemailer', 'jimp', 'multer', 'dotenv', 'find-config', 'body-parser'];

esbuild.build({
  entryPoints: [path.join(__dirname, 'bridge.ts')],
  bundle: true,
  outfile: path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'tcgshop', 'ptcg', 'engine.js'),
  format: 'iife',
  platform: 'neutral',
  target: 'es2020',
  mainFields: ['module', 'main'],
  minify: process.argv.includes('--min'),
  legalComments: 'inline',
  logLevel: 'warning',
  alias: Object.fromEntries(nodeOnly.map(m => [m, stub])),
  banner: { js: '/* Pokémon TCG engine: twinleaf.gg (MIT) based on ryuu-play (MIT, (c) 2020 keeshii). Bridge: MasterBall Traders Co.. */\n' +
    'var global = globalThis; var process = globalThis.process || { env: {}, argv: [], platform: "graal", nextTick: function (f) { f(); } };' }
}).then(r => console.log('ok', r.warnings.length, 'warnings')).catch(() => process.exit(1));
