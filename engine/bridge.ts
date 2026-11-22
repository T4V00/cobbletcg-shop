/*
 * Puente entre el motor de Pokémon TCG de twinleaf.gg (MIT, basado en ryuu-play de keeshii, MIT) y MasterBall Traders Co..
 * Se compila con esbuild a un único archivo y se ejecuta dentro de Minecraft con GraalJS. Todas las funciones
 * reciben y devuelven texto (JSON) para que la interoperabilidad con Java sea trivial.
 */
import { Store } from '../../vendor/twinleafgg/ptcg-server/src/game/store/store';
import { StoreHandler } from '../../vendor/twinleafgg/ptcg-server/src/game/store/store-handler';
import { State, GamePhase } from '../../vendor/twinleafgg/ptcg-server/src/game/store/state/state';
import { Player } from '../../vendor/twinleafgg/ptcg-server/src/game/store/state/player';
import { PokemonCardList } from '../../vendor/twinleafgg/ptcg-server/src/game/store/state/pokemon-card-list';
import { Card } from '../../vendor/twinleafgg/ptcg-server/src/game/store/card/card';
import { PokemonCard } from '../../vendor/twinleafgg/ptcg-server/src/game/store/card/pokemon-card';
import { TrainerCard } from '../../vendor/twinleafgg/ptcg-server/src/game/store/card/trainer-card';
import { EnergyCard } from '../../vendor/twinleafgg/ptcg-server/src/game/store/card/energy-card';
import { SuperType, Stage, CardType, EnergyType, TrainerType } from '../../vendor/twinleafgg/ptcg-server/src/game/store/card/card-types';
import { CardManager } from '../../vendor/twinleafgg/ptcg-server/src/game/cards/card-manager';
import { Arbiter } from '../../vendor/twinleafgg/ptcg-server/src/game/core/arbiter';
import { AddPlayerAction } from '../../vendor/twinleafgg/ptcg-server/src/game/store/actions/add-player-action';
import { ResolvePromptAction } from '../../vendor/twinleafgg/ptcg-server/src/game/store/actions/resolve-prompt-action';
import { ConcedeAction } from '../../vendor/twinleafgg/ptcg-server/src/game/store/actions/concede-action';
import { PlayCardAction, PlayerType, SlotType, CardTarget } from '../../vendor/twinleafgg/ptcg-server/src/game/store/actions/play-card-action';
import { AttackAction, PassTurnAction, RetreatAction, UseAbilityAction, UseStadiumAction } from '../../vendor/twinleafgg/ptcg-server/src/game/store/actions/game-actions';
import { Action } from '../../vendor/twinleafgg/ptcg-server/src/game/store/actions/action';
import { Prompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/prompt';
import { ChooseCardsPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/choose-cards-prompt';
import { ChoosePokemonPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/choose-pokemon-prompt';
import { ChoosePrizePrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/choose-prize-prompt';
import { ConfirmPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/confirm-prompt';
import { SelectPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/select-prompt';
import { SelectOptionPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/select-option-prompt';
import { AlertPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/alert-prompt';
import { ShowCardsPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/show-cards-prompt';
import { ConfirmCardsPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/confirm-cards-prompt';
import { ShowMulliganPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/show-mulligan-prompt';
import { WaitPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/wait-prompt';
import { AttachEnergyPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/attach-energy-prompt';
import { ChooseEnergyPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/choose-energy-prompt';
import { DiscardEnergyPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/discard-energy-prompt';
import { MoveEnergyPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/move-energy-prompt';
import { MoveDamagePrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/move-damage-prompt';
import { PutDamagePrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/put-damage-prompt';
import { RemoveDamagePrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/remove-damage-prompt';
import { OrderCardsPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/order-cards-prompt';
import { ChooseAttackPrompt } from '../../vendor/twinleafgg/ptcg-server/src/game/store/prompts/choose-attack-prompt';
import { DeckAnalyser } from '../../vendor/twinleafgg/ptcg-server/src/game/cards/deck-analyser';
import { Format, CardTag } from '../../vendor/twinleafgg/ptcg-server/src/game/store/card/card-types';
import { CheckHpEffect, CheckAttackCostEffect, CheckProvidedEnergyEffect, CheckRetreatCostEffect } from '../../vendor/twinleafgg/ptcg-server/src/game/store/effects/check-effects';
import { StateUtils } from '../../vendor/twinleafgg/ptcg-server/src/game/store/state-utils';
import { SimpleTacticsAi } from '../../vendor/twinleafgg/ptcg-server/src/simple-bot/simple-tactics-ai';
import { allSimpleTactics, allPromptResolvers, defaultStateScores, defaultArbiterOptions } from '../../vendor/twinleafgg/ptcg-server/src/simple-bot/simple-bot-definitions';
import * as sets from '../../vendor/twinleafgg/ptcg-server/src/sets';

// ------------------------------------------------------------------ cartas

let initialized = false;

function init(): number {
  if (initialized) return CardManager.getInstance().getAllCards().length;
  const cm = CardManager.getInstance();
  for (const value of Object.values(sets as any)) {
    if (!Array.isArray(value) || value.length === 0 || !(value[0] instanceof Card)) continue;
    try {
      cm.defineSet(value as Card[]);
    } catch (e) {
      // un set con nombres repetidos: se añaden las cartas que no choquen
      for (const c of value as Card[]) {
        try { cm.defineSet([c]); } catch (ignored) { /* repetida */ }
      }
    }
  }
  initialized = true;
  return cm.getAllCards().length;
}

function brief(c: Card): any {
  const o: any = { f: c.fullName, n: c.name, s: c.set, num: c.setNumber, st: c.superType };
  if (c instanceof PokemonCard) {
    o.stage = c.stage;
    o.hp = c.hp;
    o.ct = c.cardType;
    o.ev = c.evolvesFrom || '';
    o.ex = (c as any).tags ? (c as any).tags.map((t: any) => String(t)) : [];
  } else if (c instanceof TrainerCard) {
    o.tt = c.trainerType;
  } else if (c instanceof EnergyCard) {
    o.et = c.energyType;
    o.prov = (c as any).provides || [];
  }
  return o;
}

/** Lista compacta de todas las cartas (para emparejarlas con cobbletcg y montar mazos). */
function cardsJson(): string {
  init();
  return JSON.stringify(CardManager.getInstance().getAllCards().map(brief));
}

function detail(c: Card): any {
  const o = brief(c);
  o.text = (c as any).text || '';
  if (c instanceof PokemonCard) {
    o.attacks = c.attacks.map(a => ({ n: a.name, c: a.cost, d: a.damage, dc: a.damageCalculation || '', t: a.text || '' }));
    o.powers = c.powers.map(p => ({ n: p.name, t: p.text || '', pt: (p as any).powerType }));
    o.retreat = c.retreat.length;
    o.weak = c.weakness.map(w => ({ t: w.type, v: (w as any).value || 0 }));
    o.res = c.resistance.map(r => ({ t: r.type, v: (r as any).value || 0 }));
  }
  return o;
}

function detailsJson(namesJson: string): string {
  init();
  const out: any = {};
  for (const n of JSON.parse(namesJson) as string[]) {
    const c = CardManager.getInstance().getCardByName(n);
    if (c) out[n] = detail(c);
  }
  return JSON.stringify(out);
}

// ------------------------------------------------------------------ partidas

class Match implements StoreHandler {
  store: Store;
  arbiter = new Arbiter();
  bots: { [id: number]: SimpleTacticsAi } = {};
  busy = false;
  /** Animaciones pendientes para los clientes (ataques, habilidades, cambios de fase), con número de secuencia. */
  anims: any[] = [];
  seq = 0;

  constructor() {
    this.store = new Store(this);
  }

  onStateChange(state: State): void { /* el puente lee el estado bajo demanda */ }

  /**
   * Resuelve lo que no es una decisión de ningún jugador: barajados y monedas (árbitro) y las pausas de animación
   * (WaitPrompt), que se convierten en eventos de animación para el cliente.
   */
  pump(): void {
    for (let guard = 0; guard < 400; guard++) {
      const state = this.store.state;
      let action: ResolvePromptAction | undefined;
      for (const p of state.prompts) {
        if (p.result !== undefined) continue;
        if (p instanceof WaitPrompt) {
          if (p.showVisual !== false && p.message) {
            const active = state.players.find(pl => pl.id === p.playerId);
            this.anims.push({ seq: ++this.seq, player: p.playerId, kind: String(p.message), ms: p.duration,
              lastAttack: state.lastAttack ? state.lastAttack.name : '', src: active && active.active.getPokemonCard() ? active.active.getPokemonCard()!.fullName : '' });
            while (this.anims.length > 12) this.anims.shift();
          }
          action = new ResolvePromptAction(p.id, null);
          break;
        }
        action = this.arbiter.resolvePrompt(state, p);
        if (action) break;
      }
      if (!action) return;
      try {
        this.store.dispatch(action);
      } catch (e) {
        return;
      }
    }
  }

  dispatch(action: Action): string {
    try {
      this.store.dispatch(action);
      this.pump();
      return '';
    } catch (e: any) {
      this.pump();
      return e && e.message ? String(e.message) : 'ERROR';
    }
  }
}

const matches: { [id: string]: Match } = {};
const botOptions: any = {
  tactics: allSimpleTactics,
  promptResolvers: allPromptResolvers,
  scores: defaultStateScores,
  arbiter: defaultArbiterOptions
};

function create(id: string, p1Json: string, p2Json: string): string {
  init();
  const m = new Match();
  matches[id] = m;
  for (const pj of [p1Json, p2Json]) {
    const p = JSON.parse(pj);
    if (p.bot) m.bots[p.id] = new SimpleTacticsAi({ id: p.id, name: p.name } as any, botOptions, p.deck);
    const err = m.dispatch(new AddPlayerAction(p.id, p.name, p.deck));
    if (err) return err;
  }
  return '';
}

function dispose(id: string): void {
  delete matches[id];
}

function target(t: any): CardTarget {
  return { player: t.player, slot: t.slot, index: t.index };
}

/** Acción de un jugador (JSON: {t:"play"|"attack"|"ability"|"retreat"|"stadium"|"pass"|"concede", ...}). */
function act(id: string, clientId: number, json: string): string {
  const m = matches[id];
  if (!m) return 'NO_MATCH';
  const a = JSON.parse(json);
  switch (a.t) {
    case 'play': return m.dispatch(new PlayCardAction(clientId, a.hand, target(a.target)));
    case 'attack': return m.dispatch(new AttackAction(clientId, a.name));
    case 'ability': return m.dispatch(new UseAbilityAction(clientId, a.name, target(a.target)));
    case 'retreat': return m.dispatch(new RetreatAction(clientId, a.bench));
    case 'stadium': return m.dispatch(new UseStadiumAction(clientId));
    case 'pass': return m.dispatch(new PassTurnAction(clientId));
    case 'concede': return m.dispatch(new ConcedeAction(clientId));
  }
  return 'UNKNOWN_ACTION';
}

function myPrompt(m: Match, clientId: number): Prompt<any> | undefined {
  return m.store.state.prompts.find(p => p.playerId === clientId && p.result === undefined);
}

/** Responde al prompt actual del jugador (resultado ya codificado como lo espera el motor). */
function resolve(id: string, clientId: number, promptId: number, resultJson: string): string {
  const m = matches[id];
  if (!m) return 'NO_MATCH';
  const p = myPrompt(m, clientId);
  if (!p || p.id !== promptId) return 'PROMPT_GONE';
  // como el servidor oficial: decodificar (índices -> cartas / Pokémon) y validar antes de pasarlo al motor
  let result: any;
  try {
    result = p.decode(JSON.parse(resultJson), m.store.state);
    if (p.validate(result, m.store.state) === false) return 'INVALID_PROMPT_RESULT';
  } catch (e: any) {
    return e && e.message ? String(e.message) : 'INVALID_PROMPT_RESULT';
  }
  return m.dispatch(new ResolvePromptAction(promptId, result));
}

/** Sugerencia de jugada para el jugador (lo que haría la IA de twinleaf en su lugar). */
function suggest(id: string, clientId: number): string {
  const m = matches[id];
  if (!m) return '';
  const state = m.store.state;
  const player = state.players.find(pl => pl.id === clientId);
  if (!player || myPrompt(m, clientId)) return '';
  const active = state.players[state.activePlayer];
  if (state.phase !== GamePhase.PLAYER_TURN || !active || active.id !== clientId) return '';
  const ai = new SimpleTacticsAi({ id: clientId, name: player.name } as any, botOptions, null);
  let a: any;
  try { a = ai.decodeNextAction(state); } catch (e) { return ''; }
  if (!a) return '';
  if (a instanceof PlayCardAction) return JSON.stringify({ t: 'play', hand: a.handIndex, target: a.target, f: player.hand.cards[a.handIndex] ? player.hand.cards[a.handIndex].fullName : '' });
  if (a instanceof AttackAction) return JSON.stringify({ t: 'attack', name: a.name });
  if (a instanceof RetreatAction) return JSON.stringify({ t: 'retreat', bench: a.benchIndex });
  if (a instanceof UseAbilityAction) return JSON.stringify({ t: 'ability', name: a.name, target: a.target });
  if (a instanceof UseStadiumAction) return JSON.stringify({ t: 'stadium' });
  if (a instanceof PassTurnAction) return JSON.stringify({ t: 'pass' });
  return '';
}

/** El bot responde por el jugador al prompt actual (botón "automático" y respaldo para prompts complejos). */
function auto(id: string, clientId: number): string {
  const m = matches[id];
  if (!m) return 'NO_MATCH';
  const state = m.store.state;
  const player = state.players.find(pl => pl.id === clientId);
  const p = myPrompt(m, clientId);
  if (!player || !p) return 'NO_PROMPT';
  const ai = m.bots[clientId] || new SimpleTacticsAi({ id: clientId, name: player.name } as any, botOptions, null);
  const action = ai.resolvePrompt(player, state, p);
  if (!action) return 'NO_AUTO';
  return m.dispatch(action);
}

/** Un paso de los bots: devuelve true si alguno ha hecho algo. */
function botStep(id: string): boolean {
  const m = matches[id];
  if (!m) return false;
  for (const key of Object.keys(m.bots)) {
    const bot = m.bots[Number(key)];
    let action: Action | undefined;
    try {
      action = bot.decodeNextAction(m.store.state);
    } catch (e) {
      action = undefined;
    }
    if (!action) continue;
    const err = m.dispatch(action);
    if (err) {
      // el bot se ha equivocado: si es su turno, pasa; si era un prompt, que lo resuelva el automático
      const s = m.store.state;
      if (s.phase === GamePhase.PLAYER_TURN && s.players[s.activePlayer] && s.players[s.activePlayer].id === Number(key)
          && !s.prompts.some(p => p.result === undefined)) {
        m.dispatch(new PassTurnAction(Number(key)));
      }
    }
    return true;
  }
  return false;
}

// ------------------------------------------------------------------ vista

function hpOf(m: Match, player: Player, list: PokemonCardList): number {
  try {
    const e = new CheckHpEffect(player, list);
    m.store.reduceEffect(m.store.state, e);
    return e.hp;
  } catch (err) {
    const c = list.getPokemonCard();
    return c ? c.hp : 0;
  }
}

function pokemonView(m: Match, player: Player, list: PokemonCardList, defs: Set<string>): any {
  const top = list.getPokemonCard();
  if (!top) return null;
  defs.add(top.fullName);
  const energies = list.cards.filter(c => c.superType === SuperType.ENERGY).map(c => c.fullName);
  energies.forEach(e => defs.add(e));
  const tools = (list.tools || []).map(c => c.fullName);
  tools.forEach(t => defs.add(t));
  const below = list.getPokemons().filter(c => c !== top).map(c => c.fullName);
  return {
    f: top.fullName, hp: hpOf(m, player, list), dmg: list.damage, en: energies, tools,
    sc: list.specialConditions, below, fresh: list.pokemonPlayedTurn === m.store.state.turn
  };
}

/** Pokémon en juego que puede tocar un prompt (con daño, PS y energías unidas con su posición en la pila). */
function boardTargets(m: Match | null, state: State, perspective: number, playerType: PlayerType, slots: SlotType[], blocked: CardTarget[], defs: Set<string>): any[] {
  const out: any[] = [];
  const me = state.players.find(pl => pl.id === perspective);
  if (!me) return out;
  const opp = state.players.find(pl => pl !== me);
  const sides: [PlayerType, Player | undefined][] = [];
  if (playerType === PlayerType.BOTTOM_PLAYER || playerType === PlayerType.ANY) sides.push([PlayerType.BOTTOM_PLAYER, me]);
  if (playerType === PlayerType.TOP_PLAYER || playerType === PlayerType.ANY) sides.push([PlayerType.TOP_PLAYER, opp]);
  for (const [pt, pl] of sides) {
    if (!pl) continue;
    const lists: [SlotType, number, PokemonCardList][] = [];
    if (slots.includes(SlotType.ACTIVE)) lists.push([SlotType.ACTIVE, 0, pl.active]);
    if (slots.includes(SlotType.BENCH)) pl.bench.forEach((b, i) => lists.push([SlotType.BENCH, i, b]));
    for (const [slot, index, list] of lists) {
      const top = list.getPokemonCard();
      if (!top) continue;
      if ((blocked || []).some(b => b.player === pt && b.slot === slot && b.index === index)) continue;
      defs.add(top.fullName);
      const energies: any[] = [];
      list.cards.forEach((c, i) => { if (c.superType === SuperType.ENERGY) { energies.push({ i, f: c.fullName }); defs.add(c.fullName); } });
      out.push({ player: pt, slot, index, f: top.fullName, dmg: list.damage, hp: m ? hpOf(m, pl, list) : top.hp, en: energies, mine: pt === PlayerType.BOTTOM_PLAYER });
    }
  }
  return out;
}

function cardTargetCandidates(state: State, p: ChoosePokemonPrompt): any[] {
  return boardTargets(null, state, p.getPerspectivePlayerId(), p.playerType, p.slots, p.options.blocked || [], new Set<string>());
}

let currentMatch: Match | null = null;

function promptView(state: State, p: Prompt<any>, defs: Set<string>): any {
  const o: any = { id: p.id, type: p.type, msg: (p as any).message || '' };
  if ((p as any).sourceCard) { o.src = (p as any).sourceCard.fullName; defs.add(o.src); }
  if (p instanceof ChooseCardsPrompt) {
    o.kind = 'cards';
    o.secret = !!p.options.isSecret;
    o.cards = p.cards.cards.map(c => { defs.add(c.fullName); return c.fullName; });
    o.min = p.options.min;
    o.max = p.options.max;
    o.cancel = !!p.options.allowCancel;
    o.blocked = p.options.blocked || [];
  } else if (p instanceof ChoosePokemonPrompt) {
    o.kind = 'pokemon';
    o.targets = boardTargets(currentMatch, state, p.getPerspectivePlayerId(), p.playerType, p.slots, p.options.blocked || [], defs);
    o.targets.forEach((t: any) => defs.add(t.f));
    o.min = p.options.min;
    o.max = p.options.max;
    o.cancel = !!p.options.allowCancel;
  } else if (p instanceof ChoosePrizePrompt) {
    o.kind = 'prize';
    o.count = p.options.count;
    o.cancel = !!p.options.allowCancel;
    const owner = state.players.find(pl => pl.id === p.playerId);
    o.slots = owner ? owner.prizes.map((pr, i) => pr.cards.length > 0 ? i : -1).filter(i => i >= 0) : [];
  } else if (p instanceof ConfirmPrompt) {
    o.kind = 'confirm';
  } else if (p instanceof SelectPrompt || p instanceof SelectOptionPrompt) {
    o.kind = 'select';
    o.values = (p as any).values;
    o.cancel = !!((p as any).options && (p as any).options.allowCancel);
  } else if (p instanceof AlertPrompt || p instanceof ShowMulliganPrompt) {
    o.kind = 'ok';
  } else if (p instanceof ShowCardsPrompt || p instanceof ConfirmCardsPrompt) {
    o.kind = 'ok';
    const list = (p as any).cards;
    const arr: Card[] = Array.isArray(list) ? list : (list && list.cards) || [];
    o.cards = arr.map((c: Card) => { defs.add(c.fullName); return c.fullName; });
  } else if (p instanceof AttachEnergyPrompt) {
    o.kind = 'attach';
    o.cards = p.cardList.cards.map(c => { defs.add(c.fullName); return c.fullName; });
    o.blocked = p.options.blocked || [];
    o.targets = boardTargets(currentMatch, state, p.getPerspectivePlayerId(), p.playerType, p.slots, p.options.blockedTo || [], defs);
    o.min = p.options.min;
    o.max = p.options.max;
    o.cancel = !!p.options.allowCancel;
    o.sameTarget = !!p.options.sameTarget;
    o.differentTargets = !!p.options.differentTargets;
  } else if (p instanceof MoveEnergyPrompt) {
    o.kind = 'moveEnergy';
    o.targets = boardTargets(currentMatch, state, p.getPerspectivePlayerId(), p.playerType, p.slots, [], defs);
    o.blockedFrom = p.options.blockedFrom || [];
    o.blockedTo = p.options.blockedTo || [];
    o.min = p.options.min;
    o.max = p.options.max === undefined ? 99 : p.options.max;
    o.cancel = !!p.options.allowCancel;
  } else if (p instanceof DiscardEnergyPrompt) {
    o.kind = 'discardEnergy';
    o.targets = boardTargets(currentMatch, state, p.getPerspectivePlayerId(), p.playerType, p.slots, p.options.blockedFrom || [], defs);
    o.min = p.options.min;
    o.max = p.options.max === undefined ? 99 : p.options.max;
    o.cancel = !!p.options.allowCancel;
  } else if (p instanceof ChooseEnergyPrompt) {
    o.kind = 'chooseEnergy';
    o.cards = p.energy.map(e => { defs.add(e.card.fullName); return e.card.fullName; });
    o.provides = p.energy.map(e => e.provides);
    o.cost = p.cost;
    o.cancel = !!p.options.allowCancel;
  } else if (p instanceof PutDamagePrompt) {
    o.kind = 'putDamage';
    o.targets = boardTargets(currentMatch, state, p.getPerspectivePlayerId(), p.playerType, p.slots, p.options.blocked || [], defs);
    o.damage = p.damage;
    o.maxAllowed = p.maxAllowedDamage;
    o.partial = !!(p.options as any).allowPlacePartialDamage;
    o.step = (p.options as any).damageMultiple || 10;
    o.cancel = !!p.options.allowCancel;
  } else if (p instanceof MoveDamagePrompt || p instanceof RemoveDamagePrompt) {
    o.kind = p instanceof MoveDamagePrompt ? 'moveDamage' : 'removeDamage';
    o.targets = boardTargets(currentMatch, state, p.getPerspectivePlayerId(), p.playerType, p.slots, [], defs);
    o.blockedFrom = (p.options as any).blockedFrom || [];
    o.blockedTo = (p.options as any).blockedTo || [];
    o.min = p.options.min;
    o.max = p.options.max === undefined ? 99 : p.options.max;
    o.cancel = !!p.options.allowCancel;
  } else if (p instanceof OrderCardsPrompt) {
    o.kind = 'order';
    o.cards = p.cards.cards.map(c => { defs.add(c.fullName); return c.fullName; });
    o.cancel = !!p.options.allowCancel;
  } else if (p instanceof ChooseAttackPrompt) {
    o.kind = 'chooseAttack';
    o.cards = p.cards.map(c => { defs.add(c.fullName); return c.fullName; });
    o.attacks = p.cards.map(c => (c instanceof PokemonCard ? c.attacks.map(a => a.name) : []));
    o.blocked = p.options.blocked || [];
    o.cancel = !!p.options.allowCancel;
  } else {
    o.kind = 'other';
  }
  return o;
}

function playerView(m: Match, pl: Player, me: boolean, defs: Set<string>): any {
  const o: any = {
    id: pl.id, name: pl.name, deck: pl.deck.cards.length, hand: pl.hand.cards.length,
    prizes: pl.prizes.filter(p => p.cards.length > 0).length,
    discard: pl.discard.cards.map(c => { defs.add(c.fullName); return c.fullName; }),
    lost: pl.lostzone.cards.length,
    active: pokemonView(m, pl, pl.active, defs),
    bench: pl.bench.map(b => pokemonView(m, pl, b, defs)),
    supporter: pl.supporter.cards.map(c => { defs.add(c.fullName); return c.fullName; })
  };
  if (me) {
    o.handCards = pl.hand.cards.map(c => { defs.add(c.fullName); return c.fullName; });
    const turn = m.store.state.turn;
    o.used = { sup: pl.supporterTurn === turn, energy: pl.energyPlayedTurn === turn, retreat: pl.retreatedTurn === turn,
      stadium: pl.stadiumUsedTurn === turn };
    // qué ataques del Activo puedes pagar ahora mismo (mismas comprobaciones que el motor) y si puedes retirarte
    o.atkOk = [];
    const top = pl.active.getPokemonCard();
    if (top) {
      for (const a of top.attacks) {
        try {
          const cost = new CheckAttackCostEffect(pl, a);
          m.store.reduceEffect(m.store.state, cost);
          const prov = new CheckProvidedEnergyEffect(pl, pl.active);
          m.store.reduceEffect(m.store.state, prov);
          o.atkOk.push(StateUtils.checkEnoughEnergy(prov.energyMap, cost.cost as CardType[]));
        } catch (e) { o.atkOk.push(true); }
      }
      try {
        const rc = new CheckRetreatCostEffect(pl);
        m.store.reduceEffect(m.store.state, rc);
        const prov = new CheckProvidedEnergyEffect(pl);
        m.store.reduceEffect(m.store.state, prov);
        o.retreatCost = rc.cost.length;
        o.retreatOk = StateUtils.checkEnoughEnergy(prov.energyMap, rc.cost as CardType[]);
      } catch (e) { o.retreatOk = true; }
    }
  }
  return o;
}

function view(id: string, clientId: number): string {
  const m = matches[id];
  if (!m) return '{}';
  currentMatch = m;
  const state = m.store.state;
  const defs = new Set<string>();
  const me = state.players.find(p => p.id === clientId);
  const opp = state.players.find(p => p.id !== clientId);
  let stadium: string | null = null;
  for (const pl of state.players) if (pl.stadium.cards.length > 0) stadium = pl.stadium.cards[0].fullName;
  if (stadium) defs.add(stadium);
  const prompt = myPrompt(m, clientId);
  const active = state.players[state.activePlayer];
  const o: any = {
    phase: state.phase, turn: state.turn, myTurn: !!active && active.id === clientId, winner: state.winner,
    meIndex: state.players.findIndex(p => p.id === clientId),
    me: me ? playerView(m, me, true, defs) : null,
    opp: opp ? playerView(m, opp, false, defs) : null,
    stadium,
    prompt: prompt ? promptView(state, prompt, defs) : null,
    waiting: state.prompts.some(p => p.result === undefined && p.playerId !== clientId),
    logs: state.logs.slice(-20).map(l => ({ i: l.id, m: l.message, p: l.params, c: l.client })),
    anims: m.anims, seq: m.seq,
    meId: clientId,
    lastAttack: state.lastAttack ? state.lastAttack.name : ''
  };
  o.defs = {};
  defs.forEach(n => {
    const c = CardManager.getInstance().getCardByName(n);
    if (c) o.defs[n] = detail(c);
  });
  return JSON.stringify(o);
}

// ------------------------------------------------------------------ mazos

function basicEnergy(type: CardType): string | null {
  init();
  let best: Card | null = null;
  for (const c of CardManager.getInstance().getAllCards()) {
    if (c instanceof EnergyCard && c.energyType === EnergyType.BASIC && (c as any).provides && (c as any).provides.length === 1
        && (c as any).provides[0] === type) {
      if (!best || (c.set === 'SVE' && best.set !== 'SVE')) best = c;
    }
  }
  return best ? best.fullName : null;
}

/** Energías básicas disponibles: [{type, f}]. */
function energiesJson(): string {
  const out: any[] = [];
  for (const t of [CardType.GRASS, CardType.FIRE, CardType.WATER, CardType.LIGHTNING, CardType.PSYCHIC, CardType.FIGHTING,
    CardType.DARK, CardType.METAL]) {
    const f = basicEnergy(t);
    if (f) out.push({ type: t, f });
  }
  return JSON.stringify(out);
}

function rng(seed: number): () => number {
  let s = seed >>> 0 || 1;
  return () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; };
}

const MODERN = ['SVI', 'PAL', 'OBF', 'MEW', 'PAR', 'PAF', 'TEF', 'TWM', 'SFA', 'SCR', 'SSP', 'PRE', 'JTG', 'DRI', 'BLK', 'WHT', 'MEG',
  'SVP', 'BRS', 'ASR', 'LOR', 'SIT', 'CRZ', 'PGO', 'EVS', 'FST', 'CRE', 'BST', 'VIV', 'RCL', 'DAA', 'SSH', 'CEL', 'PBL', 'PFL', 'POR', 'CRI'];

function hasTag(c: Card, t: CardTag): boolean {
  return ((c as any).tags || []).includes(t);
}

/** Motivo por el que un mazo no es legal ("" = legal). Mismas reglas que el analizador del motor. */
function deckProblem(deck: string[]): string {
  init();
  if (deck.length !== 60) return 'DECK_SIZE:' + deck.length;
  const cm = CardManager.getInstance();
  const names: { [n: string]: number } = {};
  let basic = false, ace = 0, radiant = 0, star = 0;
  const prism = new Set<string>();
  for (const f of deck) {
    const c = cm.getCardByName(f);
    if (!c) return 'UNKNOWN_CARD:' + f;
    const be = c instanceof EnergyCard && c.energyType === EnergyType.BASIC;
    if (!be) {
      names[c.name] = (names[c.name] || 0) + 1;
      if (names[c.name] > 4) return 'MAX_COPIES:' + c.name;
    }
    if (c instanceof PokemonCard && c.stage === Stage.BASIC) basic = true;
    if (hasTag(c, CardTag.ACE_SPEC) && ++ace > 1) return 'ACE_SPEC:' + c.name;
    if (hasTag(c, CardTag.RADIANT) && ++radiant > 1) return 'RADIANT:' + c.name;
    if (hasTag(c, CardTag.STAR) && ++star > 1) return 'STAR:' + c.name;
    if (hasTag(c, CardTag.PRISM_STAR)) {
      if (prism.has(c.name)) return 'PRISM_STAR:' + c.name;
      prism.add(c.name);
    }
  }
  const has = (n: string) => names[n] !== undefined;
  if ((has('Professor Sycamore') && has('Professor Juniper')) || (has('Professor Juniper') && has('Professor\'s Research'))
      || (has('Professor Sycamore') && has('Professor\'s Research'))) return 'PROFESSOR_COMBO';
  if (has('Lysandre') && has('Boss\'s Orders')) return 'BOSS_COMBO';
  if (!basic) return 'NO_BASIC';
  if (!new DeckAnalyser(deck).isValid(Format.STANDARD)) return 'ENGINE_RULES';
  return '';
}

/**
 * Monta un mazo legal de 60 cartas a partir de un pool {fullName: copias} (máx. 4 por nombre salvo Energía básica):
 * líneas evolutivas completas, entrenadores clave (respetando ACE SPEC / Radiante únicos y combinaciones prohibidas)
 * y Energía básica gratis del tipo principal hasta 60. chaos (0..1) = cuánto azar hay al elegir (IA fácil = mucho).
 */
function buildDeckOnce(pool: { [f: string]: number }, house: boolean, rand: () => number, chaos: number, seeds: string[] = []): string[] {
  const cm = CardManager.getInstance();
  const avail = (f: string) => house ? 4 : (pool[f] || 0);
  const all = cm.getAllCards().filter(c => house ? (MODERN.includes(c.set) && (pool[c.fullName] || 0) > 0 || (!Object.keys(pool).length && MODERN.includes(c.set)))
    : (pool[c.fullName] || 0) > 0);
  const byName: { [n: string]: Card[] } = {};
  for (const c of all) (byName[c.name] = byName[c.name] || []).push(c);
  const deck: string[] = [];
  const countName: { [n: string]: number } = {};
  let ace = 0, radiant = 0;
  const banned = new Set<string>();
  const add = (c: Card, n: number) => {
    let added = 0;
    const isBasicEnergy = c instanceof EnergyCard && c.energyType === EnergyType.BASIC;
    if (banned.has(c.name)) return 0;
    if (hasTag(c, CardTag.ACE_SPEC)) { if (ace >= 1) return 0; n = 1; }
    if (hasTag(c, CardTag.RADIANT)) { if (radiant >= 1) return 0; n = 1; }
    if (hasTag(c, CardTag.STAR) || hasTag(c, CardTag.PRISM_STAR)) n = 1;
    while (added < n && deck.length < 60) {
      if (!isBasicEnergy && (countName[c.name] || 0) >= 4) break;
      if (!isBasicEnergy && deck.filter(x => x === c.fullName).length >= avail(c.fullName)) break;
      deck.push(c.fullName);
      countName[c.name] = (countName[c.name] || 0) + 1;
      added++;
    }
    if (added > 0) {
      if (hasTag(c, CardTag.ACE_SPEC)) ace++;
      if (hasTag(c, CardTag.RADIANT)) radiant++;
      if (c.name === 'Professor\'s Research') { banned.add('Professor Sycamore'); banned.add('Professor Juniper'); }
      if (c.name === 'Professor Sycamore') { banned.add('Professor\'s Research'); banned.add('Professor Juniper'); }
      if (c.name === 'Professor Juniper') { banned.add('Professor\'s Research'); banned.add('Professor Sycamore'); }
      if (c.name === 'Boss\'s Orders') banned.add('Lysandre');
      if (c.name === 'Lysandre') banned.add('Boss\'s Orders');
    }
    return added;
  };
  const pokes = all.filter(c => c instanceof PokemonCard) as PokemonCard[];
  const types: { [t: number]: number } = {};
  let lines = 0;
  // --- cartas que ya puso el jugador: se respetan y el mazo se arma a su alrededor
  const seedCount: { [f: string]: number } = {};
  for (const f of seeds) seedCount[f] = (seedCount[f] || 0) + 1;
  const seedTypes = new Set<number>();
  const seedNames: string[] = [];
  const lineFor = (c: PokemonCard): PokemonCard[] => {
    const line: PokemonCard[] = [c];
    let cur = c;
    while (cur.evolvesFrom) {
      const prev = (byName[cur.evolvesFrom] || []).filter(x => x instanceof PokemonCard) as PokemonCard[];
      if (prev.length === 0) break;
      cur = prev[0];
      line.unshift(cur);
    }
    cur = c;
    for (let k = 0; k < 2; k++) {
      const next = pokes.filter(x => x.evolvesFrom === cur.name).sort((a, b) => b.hp - a.hp)[0];
      if (!next) break;
      line.push(next);
      cur = next;
    }
    return line;
  };
  for (const f of Object.keys(seedCount)) {
    const c = cm.getCardByName(f);
    if (!c) continue;
    if (c instanceof EnergyCard && c.energyType === EnergyType.BASIC) continue; // la Energía básica se reparte al final
    if (c instanceof PokemonCard) {
      seedNames.push(c.name);
      c.cardType.forEach(t => { if (t !== CardType.COLORLESS) seedTypes.add(t); });
      const line = lineFor(c);
      const counts = line.length >= 3 ? [4, 2, 3] : line.length === 2 ? [4, 3] : [3];
      line.forEach((lc, i) => add(lc, Math.max(lc.fullName === f ? seedCount[f] : 0, counts[Math.min(i, counts.length - 1)])));
      for (const t of c.cardType) types[t] = (types[t] || 0) + 1;
      lines++;
    } else add(c, seedCount[f]);
  }
  const seedText = (c: Card) => seedNames.some(n => ((c as any).text || '').includes(n));
  const score = (c: PokemonCard) => c.hp + (c.attacks.reduce((a, b) => Math.max(a, b.damage || 0), 0)) * 1.2
    + (c.stage === Stage.STAGE_2 ? 40 : c.stage === Stage.STAGE_1 ? 20 : 0) + rand() * 200 * chaos
    + (seedTypes.size > 0 && c.cardType.some(t => seedTypes.has(t)) ? 400 : 0) + (seedText(c) ? 300 : 0);
  const tops = pokes.slice().sort((a, b) => score(b) - score(a));
  for (const top of tops) {
    if (lines >= 4 || deck.length >= 24) break;
    const line: PokemonCard[] = [top];
    let cur: PokemonCard = top, ok = true;
    while (cur.evolvesFrom) {
      const prev = (byName[cur.evolvesFrom] || []).filter(c => c instanceof PokemonCard) as PokemonCard[];
      if (prev.length === 0) { ok = false; break; }
      cur = prev[0];
      line.unshift(cur);
    }
    if (!ok || line[0].stage !== Stage.BASIC) continue;
    if ((countName[top.name] || 0) > 0) continue;
    // la línea debe ser del tipo principal (o incolora) para no mezclar demasiadas energías
    const mainTypes = Object.keys(types).map(Number).filter(t => t !== CardType.COLORLESS);
    if (mainTypes.length >= 2 && !top.cardType.some(t => t === CardType.COLORLESS || mainTypes.includes(t))) continue;
    const counts = line.length === 3 ? [4, 2, 3] : line.length === 2 ? [4, 3] : [3];
    line.forEach((c, i) => add(c, counts[i]));
    for (const t of top.cardType) types[t] = (types[t] || 0) + 1;
    lines++;
  }
  const staples = ['Professor\'s Research', 'Nest Ball', 'Ultra Ball', 'Boss\'s Orders', 'Rare Candy', 'Switch', 'Iono', 'Arven',
    'Buddy-Buddy Poffin', 'Super Rod', 'Night Stretcher', 'Earthen Vessel', 'Pokégear 3.0', 'Energy Retrieval', 'Quick Ball',
    'Evolution Incense', 'Marnie', 'Potion', 'Great Ball', 'Poké Ball', 'Hop', 'Hilda', 'Lillie\'s Determination'];
  const trainers = all.filter(c => c instanceof TrainerCard) as TrainerCard[];
  // Entrenadores que nombran a tus Pokémon semilla (p. ej. los que buscan o potencian a ese Pokémon)
  for (const c of trainers) if (seedNames.length && seedText(c) && deck.length < 44) add(c, 2);
  const hasStage2 = () => deck.some(f => { const k = cm.getCardByName(f) as any; return k && k.stage === Stage.STAGE_2; });
  for (const name of staples) {
    if (deck.length >= 44) break;
    const c = trainers.find(t => t.name === name);
    if (c) add(c, name === 'Rare Candy' ? (hasStage2() ? 3 : 0) : 3);
  }
  let stadium = false;
  for (const c of trainers.slice().sort(() => rand() - 0.5)) {
    if (deck.length >= 44) break;
    if (c.trainerType === TrainerType.STADIUM) { if (stadium) continue; if (add(c, 2) > 0) stadium = true; continue; }
    add(c, 2);
  }
  const typeList = Object.entries(types).sort((a, b) => b[1] - a[1]).map(e => Number(e[0])).filter(t => t !== CardType.COLORLESS);
  if (typeList.length === 0) typeList.push(CardType.FIRE);
  let i = 0;
  while (deck.length < 60) {
    const f = basicEnergy(typeList[i % Math.min(2, typeList.length)] as CardType) || basicEnergy(CardType.FIRE);
    if (!f) break;
    deck.push(f);
    i++;
  }
  return deck;
}

function buildDeck(poolJson: string, seed: number, chaos?: number, seedsJson?: string): string {
  init();
  const pool: { [f: string]: number } = poolJson ? JSON.parse(poolJson) : {};
  const house = !poolJson || chaos !== undefined && chaos >= 0 && (pool as any).__house === 1;
  delete (pool as any).__house;
  const level = chaos === undefined || chaos < 0 ? (house ? 0.6 : 0.15) : chaos;
  let best: string[] = [];
  for (let attempt = 0; attempt < 6; attempt++) {
    const seeds: string[] = seedsJson ? JSON.parse(seedsJson) : [];
    const deck = buildDeckOnce(pool, house, rng(seed + attempt * 7919), level, attempt < 4 ? seeds : seeds.slice(0, Math.max(1, seeds.length >> 1)));
    if (deckProblem(deck) === '') return JSON.stringify(deck);
    best = deck;
  }
  return JSON.stringify(best);
}

/** Comprueba un mazo: "" si es legal, o el motivo ("DECK_SIZE:n", "MAX_COPIES:nombre", "ACE_SPEC:nombre", "NO_BASIC"...). */
function validateDeck(deckJson: string): string {
  return deckProblem(JSON.parse(deckJson));
}

(globalThis as any).PTCG = { init, cardsJson, detailsJson, create, dispose, act, resolve, auto, botStep, view, energiesJson, buildDeck, validateDeck, suggest };
