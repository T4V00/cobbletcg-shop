"""Texts for the merchants update: selling reasons, haggling by personality, reputation by surroundings,
expensive set lock, vanilla Wholesaler and bad reputation for killing NPCs. %s in the reasons = the product."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

# ---------------------------------------------------------------- selling reasons (120)
# urgent money / liquidity (40)
CASH = [
    ("Tengo que pagar el alquiler este mes y me falta dinero, así que vendo mi %s.", "I have to pay rent this month and I'm short, so I'm selling my %s."),
    ("Se me ha roto el coche y la reparación es carísima. Necesito vender este %s ya.", "My car broke down and the repair is huge. I need to sell this %s now."),
    ("Me han cortado la luz por no pagar la factura. Este %s es lo único que puedo vender rápido.", "They cut my power for an unpaid bill. This %s is the only thing I can sell fast."),
    ("Llevo dos meses sin trabajo. Vendo mi %s para ir tirando.", "I've been out of work for two months. Selling my %s to get by."),
    ("Tengo una multa que vence mañana. Te dejo este %s si me lo pagas ahora.", "I have a fine due tomorrow. This %s is yours if you pay me now."),
    ("El veterinario de mi Growlithe cuesta un dineral. Necesito vender este %s.", "My Growlithe's vet bill is huge. I need to sell this %s."),
    ("Mi casero quiere el pago hoy mismo. Por eso vendo este %s.", "My landlord wants payment today. That's why I'm selling this %s."),
    ("Me he quedado sin dinero a mitad de mes. Este %s me salva la semana.", "I ran out of money mid-month. This %s saves my week."),
    ("Tengo que pagar la matrícula de la universidad y no me llega. Vendo mi %s.", "I have to pay my tuition and I'm short. Selling my %s."),
    ("Debo dinero a un amigo y quiero devolvérselo ya. Este %s tiene que irse.", "I owe a friend money and want to pay him back now. This %s has to go."),
    ("Se me ha estropeado la nevera y necesito una nueva. Vendo mi %s.", "My fridge broke and I need a new one. Selling my %s."),
    ("Tengo que viajar urgentemente para ver a mi familia y el billete es caro. Vendo este %s.", "I have to travel urgently to see family and the ticket is expensive. Selling this %s."),
    ("El dentista me ha dado un presupuesto que da miedo. Toca vender mi %s.", "The dentist gave me a scary quote. Time to sell my %s."),
    ("Me han subido la cuota del gimnasio... y todo lo demás. Vendo este %s para cuadrar cuentas.", "My gym fee went up... and everything else too. Selling this %s to balance the books."),
    ("Tengo que pagar el seguro del coche esta semana. Este %s me ayuda.", "I have to pay my car insurance this week. This %s helps."),
    ("Mi tarjeta está al límite y quiero bajar la deuda. Vendo mi %s.", "My card is maxed out and I want to cut the debt. Selling my %s."),
    ("Me mudo y la fianza del piso nuevo es enorme. Necesito vender este %s.", "I'm moving and the new deposit is huge. I need to sell this %s."),
    ("La caldera de casa ha muerto en pleno invierno. Vendo mi %s para arreglarla.", "My boiler died in the middle of winter. Selling my %s to fix it."),
    ("Se me ha caído el móvil al agua. Necesito uno nuevo y vendo este %s.", "I dropped my phone in water. I need a new one, so I'm selling this %s."),
    ("Tengo que pagar la boda de mi hermana y me toca poner dinero. Vendo mi %s.", "I have to chip in for my sister's wedding. Selling my %s."),
    ("Me ha llegado una factura del hospital inesperada. Este %s tiene que convertirse en dinero.", "I got an unexpected hospital bill. This %s has to become money."),
    ("No tengo para la compra de esta semana. Te vendo mi %s.", "I can't afford groceries this week. I'll sell you my %s."),
    ("Mi portátil ha muerto y lo necesito para trabajar. Vendo este %s.", "My laptop died and I need it for work. Selling this %s."),
    ("Mi bici, que es mi transporte, la han robado. Vendo este %s para otra.", "My bike, my only transport, got stolen. Selling this %s for another."),
    ("Tengo que pagar impuestos atrasados. Me toca deshacerme de este %s.", "I owe back taxes. I have to get rid of this %s."),
    ("Mi abuela necesita unas gafas nuevas y quiero pagárselas. Vendo mi %s.", "My grandma needs new glasses and I want to pay for them. Selling my %s."),
    ("Se ha inundado mi cocina y el seguro no lo cubre todo. Vendo este %s.", "My kitchen flooded and insurance won't cover it all. Selling this %s."),
    ("Necesito dinero para el carné de conducir. Este %s me lo paga en parte.", "I need money for my driving licence. This %s covers part of it."),
    ("Me han despedido hoy. Prefiero vender este %s antes que quedarme sin nada.", "I got fired today. I'd rather sell this %s than end up with nothing."),
    ("Tengo que pagar el campamento de mi hijo. Vendo mi %s.", "I have to pay for my kid's summer camp. Selling my %s."),
    ("La reparación del tejado no puede esperar. Necesito vender este %s.", "The roof repair can't wait. I need to sell this %s."),
    ("Me he pasado gastando en las fiestas y ahora estoy pelado. Vendo mi %s.", "I overspent on the holidays and now I'm broke. Selling my %s."),
    ("Tengo una deuda con el banco que vence ya. Este %s me da un respiro.", "I have a bank payment due now. This %s gives me some breathing room."),
    ("Necesito pagar la residencia de mi padre este mes. Vendo este %s.", "I need to pay my dad's care home this month. Selling this %s."),
    ("El taller me ha retenido la moto hasta que pague. Vendo mi %s.", "The garage is holding my motorbike until I pay. Selling my %s."),
    ("Me he quedado sin gasolina y sin dinero lejos de casa... bueno, casi. Vendo este %s.", "I ran out of fuel and money far from home... well, almost. Selling this %s."),
    ("Quiero pagar una deuda pendiente antes de que suban los intereses. Vendo mi %s.", "I want to clear a debt before the interest goes up. Selling my %s."),
    ("Mi perro se comió algo raro y la clínica cuesta un ojo de la cara. Vendo este %s.", "My dog ate something weird and the clinic costs a fortune. Selling this %s."),
    ("Esta semana cobro tarde y no llego a fin de mes. Te vendo mi %s.", "I get paid late this week and won't make it to month's end. I'll sell you my %s."),
    ("Necesito efectivo hoy, no mañana. Este %s es tuyo si llegamos a un trato.", "I need cash today, not tomorrow. This %s is yours if we make a deal."),
]
# seeking profit (35)
PROFIT = [
    ("Lo compré barato hace tiempo y ahora vale más. Es momento de sacar beneficio con este %s.", "I bought it cheap a while ago and now it's worth more. Time to cash in on this %s."),
    ("El mercado está alto ahora mismo. Vendo mi %s antes de que baje.", "The market is high right now. Selling my %s before it drops."),
    ("Compro y vendo para ganarme la vida. Este %s es negocio, nada personal.", "I buy and sell for a living. This %s is business, nothing personal."),
    ("He visto que este %s ha subido de precio esta semana. Quiero aprovecharlo.", "I saw this %s went up this week. I want to take advantage."),
    ("Abrí una caja entera y me sobró este %s. Lo vendo para recuperar lo invertido y algo más.", "I opened a whole box and this %s was spare. Selling it to get my money back and then some."),
    ("Busco vender este %s por encima de lo que me costó. Así funciona esto.", "I'm looking to sell this %s for more than I paid. That's how it works."),
    ("Soy revendedor, no te voy a engañar: quiero ganar dinero con este %s.", "I'm a reseller, I won't lie to you: I want to make money on this %s."),
    ("Con lo que saque de este %s compraré más producto para revender.", "With what I get for this %s I'll buy more stock to resell."),
    ("Dicen que va a salir una reimpresión. Vendo mi %s mientras aún vale.", "They say a reprint is coming. Selling my %s while it's still worth it."),
    ("Lo tengo para invertir y ya ha dado lo que tenía que dar. Toca vender este %s.", "I kept it as an investment and it's paid off. Time to sell this %s."),
    ("Un amigo me lo pidió por menos, pero sé que vale más. Por eso vengo a venderte este %s.", "A friend asked for it cheaper, but I know it's worth more. That's why I'm selling you this %s."),
    ("Quiero diversificar mi inversión. Este %s ya ha cumplido.", "I want to diversify my investment. This %s has done its job."),
    ("Hago mis cuentas cada mes y este %s me deja un buen margen ahora.", "I do my sums every month and this %s gives me a good margin right now."),
    ("Lo conseguí en una oferta y lo vendo al precio real. Este %s es pura ganancia para mí.", "I got it on sale and I'm selling at the real price. This %s is pure profit for me."),
    ("Mi plan es simple: compro barato, vendo caro. Te toca este %s.", "My plan is simple: buy low, sell high. This %s is your turn."),
    ("He estado esperando el momento justo para vender este %s, y es ahora.", "I've been waiting for the right moment to sell this %s, and it's now."),
    ("Las tiendas pagan bien estos días. Vengo a ver cuánto me das por este %s.", "Shops pay well these days. Let's see what you'll give me for this %s."),
    ("Quiero sacarle rentabilidad a mi colección. Empiezo por este %s.", "I want to make my collection pay. Starting with this %s."),
    ("Estoy ahorrando para una carta muy cara y voy vendiendo cosas como este %s con beneficio.", "I'm saving for a very expensive card and selling things like this %s at a profit."),
    ("Compré varias unidades de este %s pensando en revenderlas. Aquí tienes una.", "I bought several of this %s planning to resell them. Here's one."),
    ("En internet se vende más caro, pero prefiero cerrar rápido contigo este %s.", "Online it sells higher, but I'd rather close this %s quickly with you."),
    ("Los precios están subiendo mucho. Quiero asegurar la ganancia de este %s.", "Prices are climbing fast. I want to lock in the gain on this %s."),
    ("Esto es un negocio para mí: si me pagas bien, este %s es tuyo.", "This is business for me: pay me well and this %s is yours."),
    ("Quiero reinvertir en producto nuevo. Vendo este %s con beneficio.", "I want to reinvest in new stock. Selling this %s at a profit."),
    ("Lo saqué en un sobre que me costó poco. Este %s vale mucho más.", "I pulled it from a cheap pack. This %s is worth much more."),
    ("He hecho un estudio del mercado y este %s está en su mejor precio.", "I've studied the market and this %s is at its best price."),
    ("Soy coleccionista e inversor a la vez. Este %s es de la parte de inversor.", "I'm a collector and an investor. This %s belongs to the investor side."),
    ("No lo necesito para nada, sólo quiero ganarle dinero a este %s.", "I don't need it at all, I just want to make money on this %s."),
    ("Me han dicho que tu tienda paga bien. Vengo a sacarle partido a este %s.", "I heard your shop pays well. I'm here to get the most out of this %s."),
    ("Estoy montando mi propio negocio y necesito capital. Este %s, con margen, me ayuda.", "I'm starting my own business and need capital. This %s, with a margin, helps."),
    ("Ganar dinero es ganar dinero. Este %s no va a venderse solo.", "Money is money. This %s won't sell itself."),
    ("Lo compré en otra ciudad donde era más barato. Aquí vale más este %s.", "I bought it in another town where it was cheaper. This %s is worth more here."),
    ("Vendo ahora porque cuando todos vendan, el precio de este %s caerá.", "I'm selling now because when everyone sells, this %s will drop."),
    ("Quiero cerrar el año con beneficios. Este %s me ayuda a cuadrar.", "I want to close the year in profit. This %s helps me balance."),
    ("Si lo guardo, quizás baje. Si lo vendo ahora, gano. Este %s se va hoy.", "If I keep it, it might drop. If I sell now, I win. This %s goes today."),
]
# other reasons (45): duplicates, quitting, collection change, inheritance, clearance, space, capital, don't value it...
OTHER = [
    ("Lo tengo repetido. No necesito dos de este %s.", "I have a duplicate. I don't need two of this %s."),
    ("Ya no juego al TCG desde hace años. Este %s acumula polvo.", "I haven't played the TCG in years. This %s is gathering dust."),
    ("Me paso a coleccionar otra expansión. Este %s ya no encaja.", "I'm switching to another set. This %s doesn't fit anymore."),
    ("Era de mi abuelo. Lo heredé, pero no sé qué hacer con este %s.", "It was my grandpa's. I inherited it, but I don't know what to do with this %s."),
    ("Cerré mi pequeña tienda y estoy liquidando el stock, incluido este %s.", "I closed my little shop and I'm clearing stock, including this %s."),
    ("Me mudo a un piso pequeño y no me cabe todo. Este %s tiene que irse.", "I'm moving into a tiny flat and can't fit everything. This %s has to go."),
    ("Quiero comprar una consola y necesito juntar dinero. Empiezo por este %s.", "I want to buy a console and need to raise money. Starting with this %s."),
    ("Sinceramente, este %s no me dice nada. Prefiero otras cartas.", "Honestly, this %s does nothing for me. I prefer other cards."),
    ("Mi pareja dice que tengo demasiadas cartas. Negocio: vendo este %s.", "My partner says I have too many cards. Compromise: I sell this %s."),
    ("Me lo regalaron, pero no colecciono. Mejor que lo tenga alguien que lo aprecie, este %s.", "It was a gift, but I don't collect. Better someone who appreciates this %s has it."),
    ("Estoy ordenando el trastero y he encontrado este %s.", "I'm cleaning out the storage room and found this %s."),
    ("Ahora sólo colecciono Pokémon de tipo fuego. Este %s no entra.", "I only collect Fire types now. This %s doesn't make the cut."),
    ("Mi hijo ha perdido el interés y me pidió que vendiera este %s.", "My kid lost interest and asked me to sell this %s."),
    ("Estoy reuniendo dinero para un viaje a Kanto... bueno, a Japón. Vendo este %s.", "I'm saving for a trip to Kanto... well, Japan. Selling this %s."),
    ("Me he hecho minimalista. Me deshago de cosas, incluido este %s.", "I've gone minimalist. I'm getting rid of things, including this %s."),
    ("Estoy completando otra colección y uso este %s para financiarla.", "I'm completing another collection and using this %s to fund it."),
    ("Lo gané en un torneo, pero no es mi estilo. Te vendo este %s.", "I won it at a tournament, but it's not my style. Selling you this %s."),
    ("Hice limpieza de mis binders y este %s sobraba.", "I cleaned out my binders and this %s was surplus."),
    ("Un familiar me dejó su colección y no puedo quedármela toda. Vendo este %s.", "A relative left me their collection and I can't keep it all. Selling this %s."),
    ("Ya tengo la versión que me gusta. Este %s es la otra.", "I already have the version I like. This %s is the other one."),
    ("Voy a dejar de comprar cartas un tiempo. Empiezo vendiendo este %s.", "I'm taking a break from buying cards. Starting by selling this %s."),
    ("Mi tienda de barrio cerró y me quedé con este %s sin vender.", "My local shop closed and I was left with this %s unsold."),
    ("Prefiero tener dinero para ir a torneos que tener este %s guardado.", "I'd rather have money for tournaments than keep this %s stored."),
    ("Lo saqué para un amigo que al final no lo quiso. Te vendo este %s.", "I got it for a friend who didn't want it in the end. Selling you this %s."),
    ("Me paso al TCG de otra franquicia. Este %s ya no lo uso.", "I'm moving to another franchise's TCG. I don't use this %s anymore."),
    ("Mi colección está completa y este %s es de sobra.", "My collection is complete and this %s is extra."),
    ("Quiero comprar una carta concreta y este %s es mi moneda de cambio.", "I want to buy a specific card and this %s is my bargaining chip."),
    ("Estoy reformando la habitación y vendo cosas, como este %s.", "I'm redoing my room and selling stuff, like this %s."),
    ("Me aburrí de la expansión de este %s. Busco otra cosa.", "I got bored of this %s's set. Looking for something else."),
    ("Mis padres me obligan a vender algo antes de comprar más. Elijo este %s.", "My parents make me sell something before buying more. I pick this %s."),
    ("Hice una apuesta con un amigo y perdí: tengo que vender este %s.", "I made a bet with a friend and lost: I have to sell this %s."),
    ("Mi ex me dejó este %s y no quiero recuerdos.", "My ex left me this %s and I don't want the memories."),
    ("He liquidado mi puesto del mercadillo. Me quedó este %s.", "I shut down my market stall. I was left with this %s."),
    ("No tengo dónde guardar bien este %s y no quiero que se estropee.", "I have nowhere safe to store this %s and I don't want it ruined."),
    ("Quiero dinero para gradear otras cartas mejores. Vendo este %s.", "I want money to grade better cards. Selling this %s."),
    ("Este %s me lo encontré en una mudanza y no es mío... bueno, ahora sí.", "I found this %s during a move and it isn't mine... well, it is now."),
    ("Me gustan más las ilustraciones antiguas. Este %s es demasiado moderno para mí.", "I prefer older artwork. This %s is too modern for me."),
    ("Estoy ahorrando para un Pokédoll gigante. Vendo este %s.", "I'm saving up for a giant Pokédoll. Selling this %s."),
    ("Ya tengo uno firmado y este %s es el normal. Fuera.", "I already have a signed one and this %s is the plain one. Out it goes."),
    ("Doy un giro a mi vida y suelto lastre: este %s incluido.", "I'm turning my life around and letting things go: this %s included."),
    ("Necesito hueco en la estantería para mis figuras. Este %s sobra.", "I need shelf space for my figures. This %s is surplus."),
    ("La colección era de mi hermano mayor, que se fue a estudiar fuera. Me dijo que vendiera este %s.", "The collection was my big brother's, who moved away to study. He told me to sell this %s."),
    ("He cambiado de baraja y este %s ya no tiene sitio.", "I changed decks and this %s no longer has a place."),
    ("Vendo para financiar mi propia tienda algún día. Este %s es el primer paso.", "I'm selling to fund my own shop someday. This %s is the first step."),
    ("A mí este %s no me parece gran cosa, pero quizás a ti sí.", "This %s doesn't seem like much to me, but maybe it does to you."),
]
assert len(CASH) == 40 and len(PROFIT) == 35 and len(OTHER) == 45, (len(CASH), len(PROFIT), len(OTHER))

# ---------------------------------------------------------------- haggling
N = {}
def cat(name, pairs):
    N[name] = pairs

PERSONA_NAMES = {
    "collector": ("Coleccionista", "Collector"), "beginner": ("Principiante", "Beginner"), "expert": ("Experto", "Expert"),
    "negotiator": ("Negociador", "Negotiator"), "desperate": ("Desesperado", "Desperate"), "casual": ("Vendedor ocasional", "Casual seller"),
}
cat("neg_accept_collector", [("Trato hecho. Cuídalo bien, es especial.", "Deal. Take good care of it, it's special."),
                             ("Me cuesta separarme de él, pero está bien. Hecho.", "It's hard to let go, but fine. Done."),
                             ("De acuerdo. Sé que acabará en buenas manos.", "All right. I know it's going to good hands.")])
cat("neg_accept_beginner", [("¡Vale! Me parece bien, creo.", "Okay! Sounds good, I think."),
                            ("¡Genial, trato hecho! ¿Así se hace, no?", "Great, deal! That's how it's done, right?"),
                            ("Perfecto, no tenía ni idea de cuánto pedir.", "Perfect, I had no idea how much to ask.")])
cat("neg_accept_expert", [("Es un precio justo. Hecho.", "That's a fair price. Done."),
                          ("Aceptable. Sabes lo que haces.", "Acceptable. You know what you're doing."),
                          ("Bien. Ninguno de los dos sale perdiendo.", "Fine. Neither of us loses out.")])
cat("neg_accept_negotiator", [("Me has apretado, pero cerramos.", "You squeezed me, but we have a deal."),
                              ("Está bien, está bien. Trato hecho.", "Fine, fine. Deal."),
                              ("Buen regateo. Es tuyo.", "Nice haggling. It's yours.")])
cat("neg_accept_desperate", [("¡Gracias, de verdad! Me salvas.", "Thank you, really! You're saving me."),
                             ("Hecho, hecho. Lo necesitaba muchísimo.", "Done, done. I really needed this."),
                             ("Uf, menos mal. Trato hecho.", "Phew, thank goodness. Deal.")])
cat("neg_accept_casual", [("Vale, me parece bien.", "Okay, works for me."),
                          ("Sin problema, es tuyo.", "No problem, it's yours."),
                          ("Hecho. Un placer.", "Done. A pleasure.")])
cat("neg_counter_collector", [("No puedo bajar tanto. Te lo dejo en %s.", "I can't go that low. I'll do %s."),
                              ("Tiene mucho valor para mí. ¿%s?", "It means a lot to me. %s?"),
                              ("Lo máximo que puedo bajar es %s.", "The lowest I can go is %s.")])
cat("neg_counter_beginner", [("Mmm... ¿y si quedamos en %s?", "Hmm... how about %s?"),
                             ("No sé mucho de precios, pero ¿%s te vale?", "I don't know much about prices, but does %s work?"),
                             ("Vale, bajo un poco: %s.", "Okay, I'll drop a bit: %s.")])
cat("neg_counter_expert", [("Conozco el mercado. %s, y es generoso.", "I know the market. %s, and that's generous."),
                           ("Tu oferta es baja. Te propongo %s.", "Your offer is low. I propose %s."),
                           ("Lo vale. Como mucho, %s.", "It's worth it. At most, %s.")])
cat("neg_counter_negotiator", [("Ni tú ni yo: %s.", "Meet me halfway: %s."),
                               ("Interesante... Pero digamos %s.", "Interesting... but let's say %s."),
                               ("Venga, te lo dejo en %s y no se hable más.", "Come on, %s and that's that.")])
cat("neg_counter_desperate", [("Vale, vale... ¿%s? Lo necesito ya.", "Okay, okay... %s? I need it now."),
                              ("Bajo a %s, pero por favor, decídete.", "I'll drop to %s, but please decide."),
                              ("%s. No puedo esperar mucho más.", "%s. I can't wait much longer.")])
cat("neg_counter_casual", [("Bueno, %s y lo dejamos ahí.", "Well, %s and we leave it there."),
                           ("Te lo dejo en %s.", "I'll let you have it for %s."),
                           ("¿Qué tal %s?", "How about %s?")])
cat("neg_hold_collector", [("No bajo de %s. Es una pieza especial.", "I won't go below %s. It's a special piece."),
                           ("%s. Ni un ₽ menos.", "%s. Not a coin less.")])
cat("neg_hold_beginner", [("Creo que %s es lo justo... ¿no?", "I think %s is fair... right?"),
                          ("Me han dicho que no baje de %s.", "I was told not to go below %s.")])
cat("neg_hold_expert", [("%s. Es el precio y lo sabes.", "%s. That's the price and you know it."),
                        ("No voy a moverme de %s.", "I won't move from %s.")])
cat("neg_hold_negotiator", [("Ya he bajado bastante. %s.", "I've already dropped enough. %s."),
                            ("%s es mi última palabra... por ahora.", "%s is my final word... for now.")])
cat("neg_hold_desperate", [("No puedo bajar más de %s, lo siento.", "I can't go lower than %s, sorry."),
                           ("%s... necesito al menos eso.", "%s... I need at least that.")])
cat("neg_hold_casual", [("Me quedo en %s.", "I'll stay at %s."),
                        ("%s, no me apetece regatear más.", "%s, I don't feel like haggling more.")])
cat("neg_insulted_collector", [("¿Eso? Es un insulto a esta pieza.", "That? It's an insult to this piece."),
                               ("No sabes lo que tienes delante.", "You don't know what you're looking at."),
                               ("Con esa oferta me ofendes.", "That offer offends me.")])
cat("neg_insulted_beginner", [("Eh... creo que eso es muy poco.", "Uh... I think that's way too low."),
                              ("¿En serio? Hasta yo sé que vale más.", "Seriously? Even I know it's worth more."),
                              ("Mmm, no, eso no.", "Hmm, no, not that.")])
cat("neg_insulted_expert", [("Por favor. No me hagas perder el tiempo.", "Please. Don't waste my time."),
                            ("Esa cifra no tiene nada que ver con el mercado.", "That number has nothing to do with the market."),
                            ("No negocio con ofertas ridículas.", "I don't negotiate with ridiculous offers.")])
cat("neg_insulted_negotiator", [("Jaja, buen intento. No.", "Haha, nice try. No."),
                                ("Empiezas muy bajo, amigo.", "You're starting very low, friend."),
                                ("Si quieres jugar, juguemos en serio.", "If you want to play, let's play for real.")])
cat("neg_insulted_desperate", [("Estoy desesperado, no soy tonto.", "I'm desperate, not stupid."),
                               ("Eso es aprovecharse de mí...", "That's taking advantage of me..."),
                               ("Por favor, no me hagas esto.", "Please, don't do this to me.")])
cat("neg_insulted_casual", [("Bah, así no.", "Meh, not like that."),
                            ("Eso es demasiado poco.", "That's too little."),
                            ("No, gracias.", "No, thanks.")])
cat("neg_walkaway_collector", [("Prefiero quedármelo. Adiós.", "I'd rather keep it. Goodbye."),
                               ("No nos entendemos. Me voy.", "We don't see eye to eye. I'm off."),
                               ("Ya encontraré a alguien que lo valore.", "I'll find someone who values it.")])
cat("neg_walkaway_beginner", [("Bueno... creo que mejor me voy.", "Well... I think I'd better go."),
                              ("Lo preguntaré en otro sitio.", "I'll ask somewhere else."),
                              ("Me estoy liando. Otro día.", "I'm getting confused. Another day.")])
cat("neg_walkaway_expert", [("Sé lo que vale. Otro lo pagará.", "I know what it's worth. Someone else will pay."),
                            ("Fin de la negociación.", "End of negotiation."),
                            ("No hay trato.", "No deal.")])
cat("neg_walkaway_negotiator", [("Se acabó el juego. Me voy.", "Game over. I'm leaving."),
                                ("Hoy no cerramos. Suerte.", "No deal today. Good luck."),
                                ("Has tensado demasiado la cuerda.", "You pushed it too far.")])
cat("neg_walkaway_desperate", [("No puedo perder más tiempo... adiós.", "I can't waste more time... goodbye."),
                               ("Tengo que buscar a otro comprador ya.", "I have to find another buyer right now."),
                               ("Lo siento, me tengo que ir.", "Sorry, I have to go.")])
cat("neg_walkaway_casual", [("Bueno, da igual. Hasta luego.", "Well, never mind. See you."),
                            ("Me lo quedo, no pasa nada.", "I'll keep it, no worries."),
                            ("Otra vez será.", "Maybe next time.")])
cat("neg_think_lower", [("Si te ayuda a decidirte... %s.", "If it helps you decide... %s."),
                        ("Vale, piénsalo. Te lo dejo en %s.", "Okay, think about it. I'll do %s."),
                        ("Para que no se te escape: %s.", "So you don't miss out: %s."),
                        ("Mira, %s y es tuyo.", "Look, %s and it's yours."),
                        ("Te hago un favor: %s.", "I'll do you a favour: %s.")])
cat("neg_think_same", [("Tómate tu tiempo, pero no mucho.", "Take your time, but not too much."),
                       ("Claro, piénsalo.", "Sure, think it over."),
                       ("No tengo prisa... bueno, un poco.", "I'm in no hurry... well, a bit."),
                       ("El precio sigue siendo el mismo.", "The price is still the same."),
                       ("Te espero, pero no eternamente.", "I'll wait, but not forever.")])
cat("neg_final_no", [("Entonces no hay trato.", "Then there's no deal."),
                     ("Si eso es lo máximo, me voy.", "If that's your max, I'm leaving."),
                     ("No me llega. Lo siento.", "It's not enough. Sorry."),
                     ("Así no me compensa.", "It's not worth it for me like that."),
                     ("Pues me lo quedo.", "Then I'll keep it.")])
cat("neg_reject_ok", [("Vale, no pasa nada. Gracias igualmente.", "Okay, no problem. Thanks anyway."),
                      ("Entendido. Quizás otro día.", "Understood. Maybe another day."),
                      ("Sin problema, buscaré otro comprador.", "No problem, I'll find another buyer."),
                      ("Está bien. Que vaya bien la tienda.", "That's fine. Good luck with the shop."),
                      ("De acuerdo, gracias por escucharme.", "All right, thanks for listening."),
                      ("Lo entiendo. Hasta luego.", "I get it. See you later.")])
cat("neg_reject_bad", [("Pues vaya. Pensaba que aquí sí me ayudarían.", "Well. I thought you'd help me here."),
                       ("Qué decepción de tienda.", "What a disappointing shop."),
                       ("Ni lo has pensado... qué poco interés.", "You didn't even think about it... no interest at all."),
                       ("No volveré por aquí.", "I won't be back."),
                       ("Hmpf. Otro sitio me tratará mejor.", "Hmph. Somewhere else will treat me better."),
                       ("Ya veo cómo tratáis a los vendedores.", "I see how you treat sellers.")])
cat("neg_rude", [("¿Timo? ¡Qué maleducado!", "A rip-off? How rude!"),
                 ("No hace falta hablarme así.", "There's no need to talk to me like that."),
                 ("¡Pienso contarlo a todo el mundo!", "I'm going to tell everyone!"),
                 ("Increíble. Vaya forma de tratar a la gente.", "Unbelievable. What a way to treat people."),
                 ("Me voy y no vuelvo.", "I'm leaving and not coming back."),
                 ("¡Qué tienda más desagradable!", "What an unpleasant shop!")])
cat("neg_no_money", [("Vaya, parece que no tienes suficiente dinero.", "Oops, looks like you don't have enough money."),
                     ("Te falta dinero... ¿hacemos otra cosa?", "You're short on money... shall we do something else?"),
                     ("Sin dinero no hay trato, amigo.", "No money, no deal, friend.")])
cat("neg_arg_market_good", [("Hmm, tienes razón con el mercado.", "Hmm, you're right about the market."),
                            ("Bien visto, es un precio razonable.", "Good point, it's a reasonable price."),
                            ("Sí, he visto precios parecidos.", "Yes, I've seen similar prices.")])
cat("neg_arg_market_bad", [("¿Mercado? Esa cifra no es del mercado.", "Market? That number isn't the market."),
                           ("No me vengas con el mercado con esa oferta.", "Don't talk market with that offer."),
                           ("El mercado dice otra cosa.", "The market says otherwise.")])
cat("neg_arg_elsewhere_good", [("Puede que tengas razón...", "You might be right..."),
                               ("Bueno, no tengo tiempo de buscar más.", "Well, I don't have time to keep looking."),
                               ("Ya me han ofrecido menos en otros sitios, sí.", "I've been offered less elsewhere, yes.")])
cat("neg_arg_elsewhere_bad", [("Eso lo dicen todos.", "Everyone says that."),
                              ("Hay muchas tiendas, no me presiones.", "There are plenty of shops, don't pressure me."),
                              ("¿Ah, sí? Pues probaré suerte fuera.", "Oh really? Then I'll try my luck elsewhere.")])
cat("neg_arg_broke_good", [("Te entiendo, todos pasamos por eso.", "I get it, we've all been there."),
                           ("Bueno, no quiero arruinarte.", "Well, I don't want to ruin you."),
                           ("Vale, intentaré ayudarte.", "Okay, I'll try to help.")])
cat("neg_arg_broke_bad", [("Ese no es mi problema.", "That's not my problem."),
                          ("Una tienda sin dinero... ya.", "A shop with no money... sure."),
                          ("Pues ahorra y vuelve.", "Then save up and come back.")])
cat("seller_back", [("¡Hola otra vez! Traigo algo nuevo de mi colección.", "Hi again! I brought something new from my collection."),
                    ("Volví. La última vez me trataste bien.", "I'm back. Last time you treated me well."),
                    ("¿Te acuerdas de mí? Tengo más cosas que vender.", "Remember me? I have more things to sell."),
                    ("Otra vez por aquí, con producto fresco.", "Here again, with fresh stock."),
                    ("He vaciado otro binder. ¿Echamos un vistazo?", "I emptied another binder. Want to take a look?")])
# comments about the surroundings
cat("comment_no_roof", [("¿No tiene techo esta tienda? ¿Y si llueve?", "This shop has no roof? What if it rains?"),
                        ("Las cartas al aire libre... qué miedo.", "Cards out in the open... scary."),
                        ("Una tienda sin techo, eso es nuevo.", "A shop without a roof, that's new."),
                        ("Ojalá no llueva mientras miro.", "Hope it doesn't rain while I browse.")])
cat("comment_no_walls", [("Esto parece más un puesto que una tienda.", "This feels more like a stall than a shop."),
                         ("Sin paredes cualquiera puede llevarse algo.", "Without walls anyone could walk off with something."),
                         ("Le falta cerrar un poco el local.", "This place needs some walls."),
                         ("Corre mucho aire aquí dentro... si es que hay dentro.", "It's windy in here... if there even is an in here.")])
cat("comment_dark", [("Qué oscuro está esto, no veo las cartas.", "It's so dark, I can't see the cards."),
                     ("¿Podrías poner más luz?", "Could you add more light?"),
                     ("Con esta penumbra no sé lo que compro.", "In this gloom I don't know what I'm buying."),
                     ("Da un poco de miedo tan oscuro.", "It's a bit creepy being this dark.")])
cat("comment_bright", [("¡Qué bien iluminado! Se ven todos los brillos.", "So well lit! You can see every shine."),
                       ("Me encanta lo luminosa que es la tienda.", "I love how bright the shop is."),
                       ("Con esta luz las holo lucen genial.", "The holos look great in this light."),
                       ("Da gusto entrar aquí.", "It's a pleasure to come in here.")])
cat("comment_hostile", [("¡Había un monstruo ahí fuera! No me siento seguro.", "There was a monster outside! I don't feel safe."),
                        ("¿Eso era un zombi? Me voy rápido.", "Was that a zombie? I'm leaving quickly."),
                        ("Deberías proteger mejor la entrada.", "You should protect the entrance better."),
                        ("Casi me ataca algo de camino aquí.", "Something nearly attacked me on the way here."),
                        ("Con monstruos cerca no vuelvo de noche.", "With monsters around I won't come back at night.")])
cat("comment_farm_animals", [("¿Por qué hay vacas en la tienda?", "Why are there cows in the shop?"),
                             ("Huele a granja aquí dentro.", "It smells like a farm in here."),
                             ("Los animales van a pisar las cartas.", "The animals will trample the cards."),
                             ("Esto parece un establo con cartas.", "This looks like a barn with cards.")])
cat("comment_pets", [("¡Qué mascota tan mona!", "What a cute pet!"),
                     ("Me encanta que la tienda tenga mascota.", "I love that the shop has a pet."),
                     ("¿Puedo acariciarlo?", "Can I pet it?"),
                     ("Una tienda con mascota siempre es mejor.", "A shop with a pet is always better.")])
cat("comment_pokemon", [("¡Hay Pokémon en la tienda! Qué ambiente.", "There are Pokémon in the shop! What a vibe."),
                        ("Me encanta ver Pokémon entre las estanterías.", "I love seeing Pokémon among the shelves."),
                        ("Esto sí es una tienda temática.", "Now this is a themed shop."),
                        ("¿Ese Pokémon trabaja aquí?", "Does that Pokémon work here?"),
                        ("Los Pokémon le dan vida al local.", "The Pokémon bring the place to life.")])
cat("comment_cozy", [("Qué tienda tan bonita y ordenada.", "What a beautiful, tidy shop."),
                     ("Se nota que cuidas los detalles.", "You can tell you care about the details."),
                     ("Este sitio tiene muy buenas vibras.", "This place has great vibes."),
                     ("Ojalá todas las tiendas fueran así.", "I wish every shop were like this."),
                     ("Me quedaría aquí horas.", "I could stay here for hours.")])
cat("comment_cheap", [("¡Los precios están genial aquí!", "The prices here are great!"),
                      ("Más barato que en ningún sitio.", "Cheaper than anywhere."),
                      ("Esto es una ganga tras otra.", "It's one bargain after another."),
                      ("Voy a avisar a mis amigos de estos precios.", "I'm telling my friends about these prices.")])
cat("comment_pricey", [("Uf, qué caro está todo.", "Ugh, everything is so expensive."),
                       ("Estos precios están por las nubes.", "These prices are sky-high."),
                       ("En internet está más barato.", "It's cheaper online."),
                       ("Con estos precios no compro nada.", "At these prices I'm not buying anything.")])
cat("comment_empty", [("Hay muy poco producto, ¿no?", "There's not much stock, is there?"),
                      ("Las estanterías están casi vacías.", "The shelves are almost empty."),
                      ("Mete más cartas, que no hay nada.", "Put out more cards, there's nothing here."),
                      ("Vine a comprar y no hay qué.", "I came to buy and there's nothing.")])
cat("comment_infamy", [("He oído cosas muy feas de esta tienda...", "I've heard really ugly things about this shop..."),
                       ("Dicen que aquí ha pasado algo terrible.", "They say something terrible happened here."),
                       ("No sé si debería estar aquí.", "I'm not sure I should be here."),
                       ("Entro rápido y salgo. Este sitio me da mala espina.", "Quick in and out. This place gives me the creeps."),
                       ("La gente del barrio habla mal de ti.", "People around here speak badly of you.")])
cat("murder_witness", [("¡¿Qué has hecho?! ¡Me largo de aquí!", "What did you do?! I'm getting out of here!"),
                       ("¡Socorro! ¡Un asesino!", "Help! A murderer!"),
                       ("¡No pienso volver nunca!", "I'm never coming back!"),
                       ("¡Voy a llamar a alguien!", "I'm calling someone!"),
                       ("¡Esta tienda es peligrosa!", "This shop is dangerous!"),
                       ("Lo he visto todo... me voy ya.", "I saw everything... I'm leaving now.")])

T = {}
for i, (es, en) in enumerate(CASH): T[f"tcgshop.motive.cash.{i}"] = (es, en)
for i, (es, en) in enumerate(PROFIT): T[f"tcgshop.motive.profit.{i}"] = (es, en)
for i, (es, en) in enumerate(OTHER): T[f"tcgshop.motive.other.{i}"] = (es, en)
for name, pairs in N.items():
    for i, (es, en) in enumerate(pairs): T[f"tcgshop.npc.{name}.{i}"] = (es, en)
for k, v in PERSONA_NAMES.items(): T[f"tcgshop.persona.{k}"] = v

T.update({
    # deal window
    "tcgshop.deal.asks": ("Pide: %s", "Asks: %s"),
    "tcgshop.deal.your_offer": ("Tu oferta:", "Your offer:"),
    "tcgshop.deal.set_market": ("90% mercado", "90% market"),
    "tcgshop.deal.r_accept": ("Aceptar (%s)", "Accept (%s)"),
    "tcgshop.deal.r_counter": ("Contraoferta: %s", "Counter-offer: %s"),
    "tcgshop.deal.r_market": ("«Es buena oferta según el mercado»", "\"It's a good offer for the market\""),
    "tcgshop.deal.r_elsewhere": ("«No conseguirás más en otro sitio»", "\"You won't get more elsewhere\""),
    "tcgshop.deal.r_broke": ("«Ahora mismo no puedo pagar tanto»", "\"I can't pay that much right now\""),
    "tcgshop.deal.r_max": ("«Es lo máximo que puedo ofrecerte»", "\"That's the most I can offer\""),
    "tcgshop.deal.r_think": ("Me interesa, déjame pensarlo", "Interested, let me think"),
    "tcgshop.deal.r_no_thanks": ("No me interesa, gracias", "Not interested, thanks"),
    "tcgshop.deal.r_rude": ("¡Vaya timo!", "What a rip-off!"),
    "tcgshop.deal.thinking": ("Pensando", "Thinking"),
    "tcgshop.deal.closed": ("Clic para cerrar", "Click to close"),
    # new reviews
    "tcgshop.review.deal_no_neutral": ("No llegamos a un trato, sin problema", "We didn't make a deal, no problem"),
    "tcgshop.review.deal_no_bad": ("Rechazaron mi oferta sin pensarlo", "They turned down my offer without a thought"),
    "tcgshop.review.deal_rude": ("Me trataron fatal", "They treated me terribly"),
    "tcgshop.review.deal_walk": ("No nos pusimos de acuerdo en el precio", "We couldn't agree on a price"),
    "tcgshop.review.unsafe": ("Había monstruos cerca, no me sentí seguro", "There were monsters nearby, I didn't feel safe"),
    "tcgshop.review.infamy": ("Esta tienda tiene muy mala fama", "This shop has a terrible reputation"),
    "tcgshop.review.murder": ("¡Vi un asesinato en la tienda!", "I witnessed a murder in the shop!"),
    "tcgshop.review.env_no_roof": ("La tienda no tiene techo", "The shop has no roof"),
    "tcgshop.review.env_no_walls": ("La tienda no tiene paredes", "The shop has no walls"),
    "tcgshop.review.env_dark": ("Demasiado oscura", "Too dark"),
    "tcgshop.review.env_empty": ("Casi no había producto", "There was hardly any stock"),
    "tcgshop.review.env_cozy": ("Una tienda preciosa y ordenada", "A lovely, tidy shop"),
    "tcgshop.review.env_bright": ("Muy bien iluminada", "Very well lit"),
    "tcgshop.review.env_pokemon": ("Me encantaron los Pokémon de la tienda", "I loved the shop's Pokémon"),
    "tcgshop.review.env_pets": ("Tenía una mascota adorable", "It had an adorable pet"),
    "tcgshop.review.env_cheap": ("Precios estupendos", "Great prices"),
    # reputation: new notes and factors
    "tcgshop.rep.m_aesthetics": ("Estética", "Aesthetics"),
    "tcgshop.rep.m_value": ("Calidad/precio", "Value"),
    "tcgshop.rep.m_safety": ("Seguridad", "Safety"),
    "tcgshop.rep.roof": ("Techo", "Roof"),
    "tcgshop.rep.walls": ("Paredes", "Walls"),
    "tcgshop.rep.lighting": ("Iluminación", "Lighting"),
    "tcgshop.rep.doors": ("Puertas", "Doors"),
    "tcgshop.rep.hostiles": ("Monstruos cerca", "Monsters nearby"),
    "tcgshop.rep.animals": ("Animales y Pokémon", "Animals & Pokémon"),
    "tcgshop.rep.infamy": ("Mala fama (muertes)", "Infamy (killings)"),
    "tcgshop.rep.unattended": ("Clientes sin atender", "Unattended customers"),
    "tcgshop.rep.tip.roof": ("Consejo: pon techo a la zona de los muebles; a los clientes no les gusta comprar a la intemperie.", "Tip: put a roof over the furniture area; customers dislike shopping outdoors."),
    "tcgshop.rep.tip.walls": ("Consejo: cierra la tienda con paredes (el cristal también cuenta) y deja una puerta.", "Tip: enclose the shop with walls (glass counts too) and leave a door."),
    "tcgshop.rep.tip.lighting": ("Consejo: añade antorchas, faroles o luz; una tienda oscura no vende.", "Tip: add torches, lanterns or lights; a dark shop doesn't sell."),
    "tcgshop.rep.tip.doors": ("Consejo: una puerta da sensación de tienda de verdad.", "Tip: a door makes it feel like a real shop."),
    "tcgshop.rep.tip.hostiles": ("Consejo: hay monstruos cerca. Ilumina los alrededores y vigila la entrada.", "Tip: there are monsters nearby. Light up the area and guard the entrance."),
    "tcgshop.rep.tip.animals": ("Consejo: saca los animales de granja de la tienda; las mascotas y algunos Pokémon sí gustan.", "Tip: move farm animals out of the shop; pets and a few Pokémon are welcome."),
    "tcgshop.rep.tip.infamy": ("Consejo: matar a los NPC de la tienda arruina tu fama. Se recupera poco a poco cada día.", "Tip: killing shop NPCs ruins your reputation. It recovers a little each day."),
    "tcgshop.rep.tip.unattended": ("Consejo: atiende a los clientes con \"!\" antes de que se acabe su barra (60 s).", "Tip: serve customers with a \"!\" before their bar runs out (60 s)."),
    # set lock, bad reputation, Wholesaler
    "tcgshop.lock.need_tier": ("Los sobres y cajas más valiosos (Base Set, Team Rocket...) sólo se venden a dueños de una tienda de %s o superior.",
                               "The most valuable packs and boxes (Base Set, Team Rocket...) are only sold to owners of a %s shop or better."),
    "tcgshop.infamy.kill": ("Has matado a %s. Tus tiendas pierden %s de reputación y los clientes huyen.", "You killed %s. Your shops lose %s reputation and customers flee."),
    "tcgshop.infamy.kill_noshop": ("Has matado a %s. Si tuvieras tienda, perdería %s de reputación.", "You killed %s. If you had a shop, it would lose %s reputation."),
    "tcgshop.sell.done": ("Vendes %s objetos por %s.", "You sell %s items for %s."),
    "tcgshop.sell.no_cards": ("No llevas nada que se pueda vender aquí.", "You're not carrying anything that can be sold here."),
})


def main():
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in T.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, indent=2, ensure_ascii=False), encoding="utf-8")
    print(len(T), "texts;", "NPC categories:", {k: len(v) for k, v in N.items() if False} or len(N))


if __name__ == "__main__":
    main()
