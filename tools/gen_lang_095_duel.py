#!/usr/bin/env python3
"""0.9.5 · Duelo PvP del Pokémon TCG: textos nuevos (K.O., cambio de turno, pantalla final, evolución en el registro)
y repaso de redacción de algunos existentes. Fusiona las claves en es_es / en_us (idempotente, conserva el orden).
Uso: python -I -X utf8 tools/gen_lang_095_duel.py"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"
P = "tcgshop.ptcg.ui."

# clave -> (español, inglés)
KEYS = {
    # nuevas
    P + "ko": ("¡K.O.!", "K.O.!"),
    P + "log_evolved": ("%s evoluciona a %s", "%s evolves into %s"),
    P + "type.0": ("Cualquiera", "Any"),
    P + "turn_hint_me": ("Roba, juega tus cartas y ataca", "Draw, play your cards and attack"),
    P + "turn_hint_opp": ("Observa la jugada del rival", "Watch your opponent's move"),
    P + "result_win_sub": ("¡Has ganado la partida! Buen trabajo, Entrenador.", "You won the match! Great job, Trainer."),
    P + "result_lose_sub": ("Esta vez no ha podido ser. ¡A por la revancha!", "Not this time. Go for a rematch!"),
    P + "result_draw_sub": ("Nadie se lleva la victoria esta vez.", "Nobody takes the win this time."),
    P + "result_turns": ("%s turnos", "%s turns"),
    P + "result_prizes_you": ("Tus Premios: %s/6", "Your Prizes: %s/6"),
    P + "result_prizes_opp": ("Rival: %s/6", "Opponent: %s/6"),
    # redacción más clara de textos existentes
    P + "no_energy": ("Falta Energía", "Not enough Energy"),
    P + "prize_taken": ("+%s Premio(s)", "+%s Prize(s)"),
    P + "reward": ("Recompensa: $%s CobbleDollars", "Reward: $%s CobbleDollars"),
    P + "penalty": ("Penalización: -$%s", "Penalty: -$%s"),
    P + "retreat_n": ("Retirada: %s", "Retreat: %s"),
    P + "attached": ("Energías unidas:", "Attached Energy:"),
    P + "rating_change": ("Puntos de Liga: %s", "League points: %s"),
    P + "coin_tails": ("¡Cruz!", "Tails!"),
    P + "ready_attack": ("¡Puedes atacar!", "Ready to attack!"),
    P + "pick_target": ("Elige un destino iluminado", "Pick a glowing target"),
}


def merge(file_name: str, idx: int) -> None:
    path = LANG / file_name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, v in KEYS.items():
        data[k] = v[idx]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    merge("es_es.json", 0)
    merge("en_us.json", 1)
    print(f"{len(KEYS)} claves fusionadas en es_es.json y en_us.json")
