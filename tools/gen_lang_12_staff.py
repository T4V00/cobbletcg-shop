"""1.1 texts (second batch), area "staff": cashiers who walk up to customers and their upgrades (speed, range, accuracy,
handles hagglers), up to three guards, the phone's Upgrades tab, the cash register window (withdraw by percentage or a
custom amount with an "are you sure?", deposit) and the Manager Goggles stat bars.
Code: shop/CashierUpgrades.java, entity/EmployeeEntity.java, shop/ShopServer.java, client/screen/RegisterScreen.java,
client/screen/PhoneScreen.java, client/ShopClient.java.

Usage: python tools/gen_lang_12_staff.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ cashier upgrades
    "tcgshop.cashup.stat.speed": ("Rapidez", "Speed"),
    "tcgshop.cashup.stat.range": ("Alcance", "Range"),
    "tcgshop.cashup.stat.accuracy": ("Precisión", "Accuracy"),
    "tcgshop.cashup.stat.haggle": ("Atiende regateadores", "Handles hagglers"),
    "tcgshop.cashup.effect.speed.0": ("Cobra en 2 s", "Rings up in 2 s"),
    "tcgshop.cashup.effect.speed.1": ("Cobra en 1.5 s y camina más rápido", "Rings up in 1.5 s, walks faster"),
    "tcgshop.cashup.effect.speed.2": ("Cobra en 1.1 s y camina más rápido", "Rings up in 1.1 s, walks faster"),
    "tcgshop.cashup.effect.speed.3": ("Cobra en 0.8 s y va volando", "Rings up in 0.8 s, hurries everywhere"),
    "tcgshop.cashup.effect.range.0": ("Sale a atender hasta a 5 bloques de la caja", "Walks up to customers 5 blocks from the register"),
    "tcgshop.cashup.effect.range.1": ("Atiende hasta a 9 bloques", "Serves up to 9 blocks away"),
    "tcgshop.cashup.effect.range.2": ("Atiende hasta a 14 bloques", "Serves up to 14 blocks away"),
    "tcgshop.cashup.effect.range.3": ("Atiende en toda la tienda (24 bloques)", "Serves anywhere in the shop (24 blocks)"),
    "tcgshop.cashup.effect.accuracy.0": ("Atención normal", "Ordinary service"),
    "tcgshop.cashup.effect.accuracy.1": ("Cuidadoso: clientes más contentos, +2 puntos de probabilidad de propina",
                                         "Careful: happier customers, +2 points tip chance"),
    "tcgshop.cashup.effect.accuracy.2": ("Muy cuidadoso: +4 puntos de probabilidad de propina",
                                         "Very careful: +4 points tip chance"),
    "tcgshop.cashup.effect.accuracy.3": ("Impecable: +6 puntos de propina y clientes felices",
                                         "Flawless: +6 points tip chance, very happy customers"),
    "tcgshop.cashup.effect.haggle.0": ("Los regateadores te esperan a ti", "Hagglers wait for you"),
    "tcgshop.cashup.effect.haggle.1": ("Cierra los regateos a un precio justo cuando tú no estás atendiendo",
                                       "Closes haggling customers at a fair price when you're not dealing with them"),
    "tcgshop.cashup.no_cashier": ("Primero contrata un cajero para esta tienda.", "Hire a cashier for this shop first."),
    "tcgshop.cashup.maxed": ("%s ya está al máximo.", "%s is already maxed out."),
    "tcgshop.cashup.locked_haggle": ("«Atiende regateadores» necesita Rapidez, Alcance y Precisión al nivel 3 y una tienda %s o mejor.",
                                     "\"Handles hagglers\" needs Speed, Range and Accuracy at level 3 and a %s shop or better."),
    "tcgshop.cashup.bought": ("Mejora comprada para tus cajeros: %s nivel %s/%s (%s).",
                              "Upgrade bought for your cashiers: %s level %s/%s (%s)."),
    "tcgshop.cashup.thanks.0": ("¡Gracias, jefe! Ahora sí voy a atender como se debe.", "Thanks, boss! Now I'll serve them properly."),
    "tcgshop.cashup.thanks.1": ("Con esto nadie se queda esperando en la fila.", "With this, nobody waits in line."),
    "tcgshop.cashup.thanks.2": ("¡Ya verás qué rápido cobro ahora!", "You'll see how fast I ring them up now!"),
    "tcgshop.cashup.thanks.3": ("Me siento todo un profesional de la caja.", "I feel like a true register pro."),
    "tcgshop.cashup.haggle_closed": ("%1$s cerró el trato con %2$s: %3$s por %4$s.", "%1$s closed the deal with %2$s: %3$s for %4$s."),
    "tcgshop.cashup.haggle_closed_window": ("%s ya cerró este trato por ti.", "%s already closed this deal for you."),
    "tcgshop.cashier.haggle.0": ("Mira, %s: te lo dejo en %s y todos contentos.", "Look, %s: I'll let it go for %s and everyone's happy."),
    "tcgshop.cashier.haggle.1": ("Trato hecho, %s. Son %s.", "Deal, %s. That's %s."),
    "tcgshop.cashier.haggle.2": ("Ni tú ni yo: %s, quedamos en %s.", "Let's meet halfway, %s: %s."),
    "tcgshop.cashier.haggle.3": ("Precio justo para ti, %s: %s. ¡Que lo disfrutes!", "A fair price for you, %s: %s. Enjoy it!"),
    "tcgshop.cashier.walkup.0": ("¡Te cobro aquí mismo, %s!", "I'll ring you up right here, %s!"),
    "tcgshop.cashier.walkup.1": ("No hace falta esperar, %s, ya voy.", "No need to wait, %s, I'm coming."),
    "tcgshop.cashier.walkup.2": ("¡Fila rápida! Pásame eso, %s.", "Express line! Hand that over, %s."),
    "tcgshop.cashier.walkup.3": ("Un momento, %s, te atiendo enseguida.", "One moment, %s, I'll be right with you."),
    "tcgshop.cashier.to_haggle.0": ("Yo me encargo de ese trato, %s.", "I'll handle that deal, %s."),
    "tcgshop.cashier.to_haggle.1": ("¿Regateando, %s? Hablemos.", "Haggling, %s? Let's talk."),
    "tcgshop.cashier.to_haggle.2": ("El jefe está ocupado, %s. ¿Qué me ofreces?", "The boss is busy, %s. What's your offer?"),
    "tcgshop.cashier.to_haggle.3": ("Vamos a ponernos de acuerdo, %s.", "Let's come to an agreement, %s."),

    # ------------------------------------------------------------------ guards (up to three)
    "tcgshop.staff.guard_max": ("Ya tienes %s guardias, el máximo que puede tener una tienda.",
                                "You already have %s guards, the most a shop can have."),
    "tcgshop.role.guards": ("Guardias", "Guards"),
    "tcgshop.phone.guards_count": ("%s/%s contratados", "%s/%s hired"),
    "tcgshop.phone.wage_each": ("Sueldo: %s/día cada uno", "Wage: %s/day each"),
    "tcgshop.phone.hire_one": ("+ Contratar", "+ Hire"),
    "tcgshop.phone.fire_one": ("− Despedir", "− Fire"),
    "tcgshop.phone.staff_tip_upgrades": ("Mejora a tus cajeros y guardias en la pestaña Mejoras.",
                                         "Upgrade your cashiers and guards in the Upgrades tab."),

    # ------------------------------------------------------------------ phone: upgrades tab
    "tcgshop.phone.tab.upgrades": ("Mejoras", "Upgrades"),
    "tcgshop.phone.up.cashiers": ("Cajeros", "Cashiers"),
    "tcgshop.phone.up.guards": ("Guardias", "Guards"),
    "tcgshop.phone.up.need_cashier": ("Contrata un cajero para comprarlas", "Hire a cashier to buy these"),
    "tcgshop.phone.up.need_guard": ("Contrata un guardia para comprarlas", "Hire a guard to buy these"),
    "tcgshop.phone.up.level": ("Nivel %s/%s", "Level %s/%s"),
    "tcgshop.phone.up.hard": ("Difícil de conseguir", "Hard to get"),
    "tcgshop.phone.up.now": ("Ahora: %s", "Now: %s"),
    "tcgshop.phone.up.next": ("Siguiente: %s", "Next: %s"),
    "tcgshop.phone.up.maxed": ("Al máximo", "Maxed out"),
    "tcgshop.phone.up.locked": ("Bloqueada", "Locked"),
    "tcgshop.phone.up.confirm": ("¿Seguro? Clic", "Sure? Click"),
    "tcgshop.phone.up.haggle_req": ("Necesita Rapidez, Alcance y Precisión al nivel 3 y una tienda %s o mejor.",
                                    "Needs Speed, Range and Accuracy at level 3 and a %s shop or better."),

    # ------------------------------------------------------------------ cash register window
    "tcgshop.register.title": ("Caja registradora", "Cash register"),
    "tcgshop.register.withdraw": ("Retirar", "Withdraw"),
    "tcgshop.register.deposit": ("Depositar", "Deposit"),
    "tcgshop.register.balance": ("En la caja: %s", "In the register: %s"),
    "tcgshop.register.wallet": ("Tu billetera: %s", "Your wallet: %s"),
    "tcgshop.register.wages_hint": ("Sueldos por día: %s. Deja dinero para pagarlos.", "Daily wages: %s. Leave money to pay them."),
    "tcgshop.register.of_register": ("Porcentaje de la caja o monto exacto:", "Share of the register or an exact amount:"),
    "tcgshop.register.of_wallet": ("Porcentaje de tu billetera o monto exacto:", "Share of your wallet or an exact amount:"),
    "tcgshop.register.custom": ("Monto", "Amount"),
    "tcgshop.register.do_withdraw": ("Retirar %s", "Withdraw %s"),
    "tcgshop.register.do_deposit": ("Depositar %s", "Deposit %s"),
    "tcgshop.register.confirm_withdraw": ("¿Seguro que quieres retirar %s?", "Are you sure you want to withdraw %s?"),
    "tcgshop.register.confirm_deposit": ("¿Seguro que quieres depositar %s?", "Are you sure you want to deposit %s?"),
    "tcgshop.register.yes": ("Sí, hazlo", "Yes, do it"),
    "tcgshop.register.cancel": ("Cancelar", "Cancel"),
    "tcgshop.register.done": ("¡Listo!", "Done!"),
    "tcgshop.register.dormant_no_deposit": ("No puedes depositar en una tienda inactiva.", "You can't deposit into a dormant shop."),
    "tcgshop.shop.deposited": ("Depositaste %s en la caja.", "You deposited %s into the register."),
    "tcgshop.shop.deposit_none": ("No hay nada que depositar.", "There's nothing to deposit."),
    "tcgshop.phone.withdraw_btn": ("Retirar… (%s)", "Withdraw… (%s)"),
    "tcgshop.phone.deposit_btn": ("Depositar…", "Deposit…"),

    # ------------------------------------------------------------------ Manager Goggles bars
    "tcgshop.goggles.bar.reputation": ("Reputación", "Reputation"),
    "tcgshop.goggles.bar.theft": ("Riesgo de robo", "Theft risk"),
    "tcgshop.goggles.bar.clean": ("Limpieza", "Cleanliness"),
    "tcgshop.goggles.bar.decor": ("Decoración", "Decoration"),
    "tcgshop.goggles.bar.staff": ("Personal", "Staff"),
}


def main():
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in T.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(T), "texts")


if __name__ == "__main__":
    main()
