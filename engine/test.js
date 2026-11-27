// Bundle test in Node: full bot-vs-bot matches.
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const file = path.join(__dirname, '..', 'common', 'src', 'main', 'resources', 'assets', 'tcgshop', 'ptcg', 'engine.js');
const ctx = { console };
ctx.globalThis = ctx;
vm.createContext(ctx);
let t0 = Date.now();
vm.runInContext(fs.readFileSync(file, 'utf8'), ctx, { filename: 'engine.js' });
const P = ctx.PTCG;
console.log('load', Date.now() - t0, 'ms');
t0 = Date.now();
console.log('cards', P.init(), Date.now() - t0, 'ms');
console.log('energies', P.energiesJson());
const games = Number(process.argv[2] || 3);
for (let g = 0; g < games; g++) {
  const d1 = JSON.parse(P.buildDeck('', 100 + g)), d2 = JSON.parse(P.buildDeck('', 200 + g));
  const v1 = P.validateDeck(JSON.stringify(d1)), v2 = P.validateDeck(JSON.stringify(d2));
  if (g === 0) console.log('sample deck', d1.slice(0, 20).join(' | '));
  t0 = Date.now();
  const err = P.create('g' + g, JSON.stringify({ id: 1, name: 'A', deck: d1, bot: true }), JSON.stringify({ id: 2, name: 'B', deck: d2, bot: true }));
  let steps = 0, view;
  while (steps < 5000) {
    if (!P.botStep('g' + g)) {
      view = JSON.parse(P.view('g' + g, 1));
      if (view.winner !== -1 || view.phase === 7) break;
      // stuck on a prompt? try auto
      const a = P.auto('g' + g, 1), b = P.auto('g' + g, 2);
      if (a && b) { console.log('stuck', a, b, JSON.stringify(view.prompt)); break; }
    }
    steps++;
  }
  view = JSON.parse(P.view('g' + g, 1));
  console.log(`match ${g}: validation ${v1 || 'ok'}/${v2 || 'ok'} create=${err || 'ok'} steps=${steps} turn=${view.turn} winner=${view.winner} phase=${view.phase} prizes ${view.me && view.me.prizes}-${view.opp && view.opp.prizes} ${Date.now() - t0}ms`);
  P.dispose('g' + g);
}
