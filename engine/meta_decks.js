// Competitive metagame decks (recent top archetypes) for the Hard / Champion AI.
// Resolves each name to the engine's card (prefers ones with an image in cobbletcg and modern sets),
// fills up to 60 and checks that the engine accepts them. Usage: node meta_decks.js
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.join(__dirname, '..');
const ctx = { console };
ctx.globalThis = ctx;
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(root, 'common/src/main/resources/assets/tcgshop/ptcg/engine.js'), 'utf8'), ctx, { filename: 'engine.js' });
const P = ctx.PTCG;
P.init();
const cards = JSON.parse(P.cardsJson());
const mapped = new Set(Object.values(JSON.parse(fs.readFileSync(path.join(root, 'common/src/main/resources/assets/tcgshop/ptcg/cards.json'), 'utf8'))));
const MODERN = ['MEG', 'PFL', 'ASC', 'BLK', 'WHT', 'DRI', 'JTG', 'PRE', 'SSP', 'SCR', 'SFA', 'TWM', 'TEF', 'PAF', 'PAR', 'MEW', 'OBF', 'PAL', 'SVI', 'SVP', 'SVE', 'MEE', 'CRZ', 'SIT', 'LOR', 'PGO', 'ASR', 'BRS', 'FST'];
const byName = {};
for (const c of cards) (byName[c.n.toLowerCase()] = byName[c.n.toLowerCase()] || []).push(c);

function resolve(name) {
  const list = byName[name.toLowerCase()];
  if (!list) return null;
  const score = c => (mapped.has(c.f) ? 100 : 0) + (MODERN.indexOf(c.s) >= 0 ? 60 - MODERN.indexOf(c.s) : 0);
  return list.slice().sort((a, b) => score(b) - score(a))[0].f;
}

const ENERGY = { Grass: 'Grass Energy', Fire: 'Fire Energy', Water: 'Water Energy', Lightning: 'Lightning Energy', Psychic: 'Psychic Energy',
  Fighting: 'Fighting Energy', Darkness: 'Darkness Energy', Metal: 'Metal Energy' };

const DECKS = [
  { id: 'dragapult', name: 'Dragapult ex', energy: ['Psychic', 'Fire'], list: [
    [4, 'Dreepy'], [4, 'Drakloak'], [3, 'Dragapult ex'], [2, 'Duskull'], [2, 'Dusclops'], [1, 'Dusknoir'], [1, 'Fezandipiti ex'], [1, 'Budew'],
    [1, 'Bloodmoon Ursaluna ex'], [4, "Lillie's Determination"], [4, 'Arven'], [2, 'Iono'], [2, "Boss's Orders"], [1, "Professor Turo's Scenario"],
    [4, 'Buddy-Buddy Poffin'], [4, 'Ultra Ball'], [3, 'Rare Candy'], [2, 'Night Stretcher'], [1, 'Counter Catcher'], [1, 'Super Rod'],
    [1, 'Unfair Stamp'], [2, 'Technical Machine: Evolution'], [3, 'Psychic Energy'], [2, 'Fire Energy'], [1, 'Luminous Energy']] },
  { id: 'gardevoir', name: 'Gardevoir ex', energy: ['Psychic', 'Darkness'], list: [
    [4, 'Ralts'], [3, 'Kirlia'], [2, 'Gardevoir ex'], [2, 'Drifloon'], [1, 'Scream Tail'], [1, 'Munkidori'], [1, 'Fezandipiti ex'], [1, 'Mew ex'],
    [1, 'Frillish'], [4, 'Arven'], [3, 'Iono'], [1, "Professor's Research"], [2, "Boss's Orders"], [4, 'Buddy-Buddy Poffin'], [4, 'Ultra Ball'],
    [4, 'Earthen Vessel'], [2, 'Rare Candy'], [1, 'Counter Catcher'], [2, 'Night Stretcher'], [1, 'Super Rod'], [1, 'Technical Machine: Evolution'],
    [2, 'Bravery Charm'], [1, 'Artazon'], [1, 'Prime Catcher'], [2, 'Darkness Energy'], [9, 'Psychic Energy']] },
  { id: 'charizard', name: 'Charizard ex', energy: ['Fire'], list: [
    [3, 'Charmander'], [1, 'Charmeleon'], [2, 'Charizard ex'], [2, 'Pidgey'], [1, 'Pidgeotto'], [2, 'Pidgeot ex'], [2, 'Duskull'], [1, 'Dusclops'],
    [1, 'Dusknoir'], [1, 'Fezandipiti ex'], [1, 'Moltres'], [4, 'Arven'], [3, 'Iono'], [2, "Boss's Orders"], [2, "Lillie's Determination"],
    [4, 'Rare Candy'], [4, 'Buddy-Buddy Poffin'], [3, 'Ultra Ball'], [1, 'Nest Ball'], [1, 'Super Rod'], [2, 'Night Stretcher'], [1, 'Prime Catcher'],
    [1, 'Counter Catcher'], [1, 'Technical Machine: Evolution'], [8, 'Fire Energy']] },
  { id: 'ragingbolt', name: 'Raging Bolt ex', energy: ['Grass', 'Lightning', 'Fighting'], list: [
    [4, 'Raging Bolt ex'], [4, 'Teal Mask Ogerpon ex'], [1, 'Slither Wing'], [1, 'Fezandipiti ex'], [4, "Professor Sada's Vitality"],
    [4, 'Earthen Vessel'], [2, 'Energy Retrieval'], [4, 'Nest Ball'], [2, 'Ultra Ball'], [3, 'Pokégear 3.0'], [1, 'Bravery Charm'],
    [3, "Boss's Orders"], [2, 'Crispin'], [1, 'Prime Catcher'], [1, 'Switch'], [1, 'Pal Pad'], [8, 'Grass Energy'], [4, 'Lightning Energy'],
    [4, 'Fighting Energy']] },
  { id: 'gholdengo', name: 'Gholdengo ex', energy: ['Metal'], list: [
    [4, 'Gimmighoul'], [4, 'Gholdengo ex'], [1, 'Fezandipiti ex'], [4, 'Superior Energy Retrieval'], [4, 'Earthen Vessel'], [4, 'Arven'],
    [3, 'Iono'], [2, "Boss's Orders"], [4, 'Ultra Ball'], [3, 'Buddy-Buddy Poffin'], [2, 'Night Stretcher'], [1, 'Counter Catcher'],
    [1, 'Prime Catcher'], [1, 'Super Rod'], [1, 'Switch'], [1, 'Pal Pad'], [12, 'Metal Energy']] },
  { id: 'terapagos', name: 'Terapagos ex', energy: ['Fire'], list: [
    [3, 'Terapagos ex'], [4, 'Hoothoot'], [3, 'Noctowl'], [2, 'Pidgey'], [2, 'Pidgeot ex'], [2, 'Fan Rotom'], [1, 'Fezandipiti ex'],
    [4, 'Arven'], [3, 'Iono'], [1, 'Briar'], [2, "Boss's Orders"], [4, 'Buddy-Buddy Poffin'], [4, 'Ultra Ball'], [3, 'Rare Candy'],
    [2, 'Nest Ball'], [1, 'Super Rod'], [1, 'Night Stretcher'], [3, 'Area Zero Underdepths'], [1, 'Maximum Belt'], [2, 'Jet Energy'],
    [4, 'Double Turbo Energy']] },
  { id: 'miraidon', name: 'Miraidon ex', energy: ['Lightning'], list: [
    [3, 'Miraidon ex'], [2, 'Iron Hands ex'], [2, 'Raichu V'], [1, 'Mew ex'], [1, 'Fezandipiti ex'], [4, 'Electric Generator'], [4, 'Arven'],
    [3, 'Iono'], [3, "Boss's Orders"], [4, 'Nest Ball'], [4, 'Ultra Ball'], [2, 'Switch'], [1, 'Prime Catcher'], [2, 'Bravery Charm'],
    [1, 'Super Rod'], [12, 'Lightning Energy']] },
  { id: 'archaludon', name: 'Archaludon ex', energy: ['Metal'], list: [
    [4, 'Duraludon'], [3, 'Archaludon ex'], [1, 'Fezandipiti ex'], [4, 'Arven'], [3, 'Iono'], [2, "Boss's Orders"], [4, 'Buddy-Buddy Poffin'],
    [4, 'Ultra Ball'], [4, 'Earthen Vessel'], [2, 'Night Stretcher'], [1, 'Prime Catcher'], [1, 'Super Rod'], [2, 'Switch'], [1, 'Pal Pad'],
    [12, 'Metal Energy']] },
];
const FILL = ['Ultra Ball', 'Buddy-Buddy Poffin', 'Night Stretcher', "Boss's Orders", 'Iono', 'Arven', 'Super Rod', 'Switch', 'Counter Catcher'];

const out = [];
for (const d of DECKS) {
  const deck = [], missing = [];
  const count = {};
  const add = (f, n) => { for (let k = 0; k < n; k++) deck.push(f); };
  for (const [n, name] of d.list) {
    const f = resolve(name);
    if (!f) { missing.push(name); continue; }
    add(f, n);
  }
  // fill with basics of the type and staples up to 60; trim energy if there is too much
  let i = 0;
  while (deck.length < 60) {
    const fill = i % 3 === 2 ? resolve(FILL[(i / 3 | 0) % FILL.length]) : resolve(ENERGY[d.energy[i % d.energy.length]]);
    i++;
    if (!fill) continue;
    const same = deck.filter(x => x === fill).length;
    if (!/ Energy/.test(fill) && same >= 4) continue;
    deck.push(fill);
  }
  while (deck.length > 60) {
    const idx = deck.findIndex(x => / Energy /.test(x + ' '));
    deck.splice(idx >= 0 ? idx : deck.length - 1, 1);
  }
  const problem = P.validateDeck(JSON.stringify(deck));
  console.log(d.name.padEnd(16), problem === '' ? 'OK' : 'INVALID ' + problem, missing.length ? 'missing: ' + missing.join(', ') : '');
  if (problem === '') out.push({ id: d.id, name: d.name, cards: deck });
}
// quick test: each deck plays against another until the end
for (let k = 0; k < out.length; k++) {
  const a = out[k], b = out[(k + 1) % out.length];
  P.create('m' + k, JSON.stringify({ id: 1, name: 'A', deck: a.cards, bot: true }), JSON.stringify({ id: 2, name: 'B', deck: b.cards, bot: true }));
  let steps = 0, v;
  while (steps++ < 3000) { if (!P.botStep('m' + k)) { v = JSON.parse(P.view('m' + k, 1)); if (v.phase === 7 || v.winner !== -1) break; } }
  v = JSON.parse(P.view('m' + k, 1));
  console.log('  match', a.name, 'vs', b.name, '-> winner', v.winner, 'turn', v.turn);
  P.dispose('m' + k);
}
fs.writeFileSync(path.join(__dirname, 'meta_decks.json'), JSON.stringify(out, null, 1));
fs.mkdirSync(path.join(root, 'common/src/main/resources/data/tcgshop'), { recursive: true });
fs.writeFileSync(path.join(root, 'common/src/main/resources/data/tcgshop/ptcg_meta_decks.json'), JSON.stringify(out));
console.log('meta decks:', out.length);
