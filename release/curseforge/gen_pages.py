"""Builds the four CurseForge description pages (HTML for the CurseForge editor).

python gen_pages.py            -> NeoForge projects shown as "waiting for approval"
python gen_pages.py --approved -> NeoForge projects linked normally

Writes mod_fabric.html, mod_neoforge.html, api_fabric.html, api_neoforge.html next to this script.
CurseForge keeps: text-align, colored spans, images, tables with cell colors. It drops <details>.
"""
import os
import sys

APPROVED = '--approved' in sys.argv
HERE = os.path.dirname(os.path.abspath(__file__))
CF = 'https://www.curseforge.com/minecraft/mc-mods/'
GH_MOD = 'https://github.com/T4V00/cobbletcg-shop'
GH_API = 'https://github.com/T4V00/masterball-api'
GH_REL = GH_MOD + '/releases/tag/v1.1.0'

PROJ = {
    ('mod', 'fabric'): dict(name='MasterBall Traders Co.', slug='masterball-traders-co',
                            logo='https://media.forgecdn.net/avatars/2233/941/639310067206628415.png'),
    ('api', 'fabric'): dict(name='MasterBall Traders API', slug='masterball-traders-api',
                            logo='https://media.forgecdn.net/avatars/2232/960/639309770159236549.png'),
    ('mod', 'neoforge'): dict(name='MasterBall Traders Co. (NeoForge)', slug='masterball-traders-co-neoforge',
                              logo='https://media.forgecdn.net/avatars/2247/513/639314019396527279.png'),
    ('api', 'neoforge'): dict(name='MasterBall Traders API (NeoForge)', slug='masterball-traders-api-neoforge',
                              logo='https://media.forgecdn.net/avatars/2247/492/639314016787418430.png'),
}
LOADER = {'fabric': 'Fabric', 'neoforge': 'NeoForge'}

GOLD, PURPLE, PINK, CYAN, GREEN, GREY = '#f5c242', '#b98cff', '#ff6fae', '#5ccfff', '#7ddc7d', '#a8a8a8'


def c(text, color):
    return f'<span style="color: {color};">{text}</span>'


def center(inner, tag='p'):
    return f'<{tag} style="text-align: center;">{inner}</{tag}>'


def h2(emoji, text, color):
    return f'<h2>{emoji} {c(text, color)}</h2>'


def link(kind, loader, label=None, inline=False):
    """Link to one of the four projects; a NeoForge project not approved yet gets a 'waiting' note instead."""
    p = PROJ[(kind, loader)]
    label = label or p['name']
    if loader == 'neoforge' and not APPROVED and inline:
        return f'<strong>{label}</strong> ({c("⏳ waiting for CurseForge approval", GOLD)})'
    if loader == 'neoforge' and not APPROVED:
        return (f'<strong>{label}</strong><br>{c("⏳ <em>Waiting for CurseForge approval</em>", GOLD)}'
                f'<br><span style="color: #a8a8a8;">Meanwhile: <a href="{GH_REL}">jar on GitHub</a></span>')
    return f'<a href="{CF}{p["slug"]}"><strong>{label}</strong></a>'


def loader_table():
    rows = ''
    for loader, icon in (('fabric', '🧵'), ('neoforge', '🦊')):
        rows += (f'<tr><td style="text-align: center;"><strong>{icon} {LOADER[loader]}</strong><br><span style="color: #a8a8a8;">Minecraft 1.21.1</span></td>'
                 f'<td style="text-align: center;">{link("mod", loader)}</td>'
                 f'<td style="text-align: center;">{link("api", loader)}</td></tr>')
    return ('<table style="width: 100%;"><tbody>'
            f'<tr><td style="text-align: center; background-color: #3a2a55;"><strong>Loader</strong></td>'
            f'<td style="text-align: center; background-color: #3a2a55;"><strong>🏪 The mod</strong></td>'
            f'<td style="text-align: center; background-color: #3a2a55;"><strong>📚 The card library (API)</strong></td></tr>'
            f'{rows}</tbody></table>'
            + center(c('Install the mod <strong>and</strong> the API <strong>for the same loader</strong>.', GREY)))


def you_are_here(kind, loader):
    other = 'neoforge' if loader == 'fabric' else 'fabric'
    if other == 'neoforge' and not APPROVED:
        other_txt = (f'Playing on <strong>NeoForge</strong>? Its own project is {c("⏳ waiting for CurseForge approval", GOLD)}; '
                     f'until then grab the NeoForge jars from the <a href="{GH_REL}">GitHub release</a>.')
    else:
        other_txt = f'Playing on <strong>{LOADER[other]}</strong>? Get {link(kind, other)} instead.'
    return (f'<blockquote><p>🧩 <strong>This is the {c(LOADER[loader], CYAN)} version.</strong> {other_txt}</p></blockquote>')


def table(rows, head=None):
    out = '<table style="width: 100%;"><tbody>'
    if head:
        out += '<tr>' + ''.join(f'<td style="background-color: #3a2a55;"><strong>{h}</strong></td>' for h in head) + '</tr>'
    for r in rows:
        out += '<tr>' + ''.join(f'<td>{x}</td>' for x in r) + '</tr>'
    return out + '</tbody></table>'


def ul(items):
    return '<ul>' + ''.join(f'<li>{i}</li>' for i in items) + '</ul>'


HR = '<hr>'

DISCLAIMER = center('<span style="color: #a8a8a8;">Fan-made project, not affiliated with or endorsed by Nintendo, The Pokémon Company, Game Freak, Creatures, '
                    'TCGplayer or the TCG Card Shop Simulator developers. Pokémon names are trademarks of their owners. '
                    'No official art is included in these mods. Prices are for in-game use only.</span>')


def mod_page(loader):
    p = PROJ[('mod', loader)]
    neo = loader == 'neoforge'
    api_link = link('api', loader, 'MasterBall Traders API' + (' (NeoForge)' if neo else ''), inline=True)
    req_loader = 'NeoForge 21.1+' if neo else 'Fabric API + Fabric Loader 0.18.4+'
    dollars = ('<tr><td>💲 Recommended</td><td><a href="' + CF + 'cobbledollars">CobbleDollars</a> (NeoForge build)</td>'
               '<td>Shared currency with other mods. Without it the mod uses its own wallet.</td></tr>') if neo else (
               '<tr><td>✅ <strong>Required</strong></td><td><a href="' + CF + 'cobbledollars">CobbleDollars</a></td>'
               '<td>Everything is bought and sold in CobbleDollars.</td></tr>')
    compat = (f'🆕 <strong>Native NeoForge 1.21.1 build</strong>, same features as the Fabric version. Tested with Cobblemon 1.8 NeoForge modpacks.'
              if neo else
              f'✅ <strong>Compatible with COBBLEVERSE 1.8.0</strong> (Cobblemon 1.8.4, Fabric, Minecraft 1.21.1): just drop the mod and the API into <code>mods</code>.')
    install = [
        f'Create a Minecraft <strong>1.21.1</strong> profile with <strong>{"NeoForge 21.1+" if neo else "Fabric Loader 0.18.4+"}</strong> (the CurseForge app does it for you).',
        (f'Add {api_link} and <strong>{p["name"]}</strong> (plus CobbleDollars if you like)' if neo else
         f'Add {api_link}, <strong>{p["name"]}</strong>, <strong>CobbleDollars</strong> and <strong>Fabric API</strong>')
        + ' to <code>mods</code>. Cobblemon is strongly recommended.',
        'Open a world: you start with <strong>5 packs and a binder</strong>.',
        'Buy a <strong>TCG Phone</strong> from the Mysterious Wholesaler, a travelling merchant who shows up near players.',
        'Press <strong>Y</strong> for the mod options (official art download, card art and more).',
    ]
    s = []
    s.append(center(f'<img src="{p["logo"]}" alt="MasterBall Traders Co." width="160" height="160">'))
    s.append(center(c(p['name'], GOLD), 'h1'))
    s.append(center(f'<strong>{c("Open your own Pokémon card shop in Minecraft.", PINK)}</strong><br>'
                    'Rip packs, chase hits, price your stock, haggle with customers, stop thieves and build the best TCG store on the server.'))
    s.append(center('🃏 <strong>11,700+ cards</strong> &nbsp;·&nbsp; 📦 <strong>272 packs</strong> &nbsp;·&nbsp; 🏪 <strong>12 shop tiers</strong>'
                    ' &nbsp;·&nbsp; ⚔️ <strong>real TCG matches</strong> &nbsp;·&nbsp; 📱 <strong>a phone full of apps</strong>'))
    s.append(you_are_here('mod', loader))
    s.append(f'<p>{compat}</p>')
    s.append(HR)
    s.append(h2('📥', 'Pick your loader', CYAN))
    s.append(loader_table())
    s.append(HR)
    s.append(h2('🎉', 'Version 1.1 — the shop gets real', PINK))
    s.append(table([
        ['🔒 <strong>Open / closed shop</strong>', 'Close it and customers, events and thieves stop. Break your shop block and <strong>nothing is lost</strong>: money, stats, staff and reviews come back when you place it.'],
        ['🧑‍💼 <strong>Better staff</strong>', 'Cashiers walk up to customers, <strong>cashier upgrades</strong> (one even handles hagglers), <strong>up to 3 guards</strong> and a clear Upgrades tab.'],
        ['💰 <strong>Smarter register</strong>', 'Withdraw by percentage with a confirmation, or deposit money. Tips are capped at $50,000.'],
        ['🗣️ <strong>Customers with charm</strong>', 'A simpler haggling window. Got a good offer? Don\'t get greedy: tell a <strong>joke</strong>, a <strong>story</strong> or a <strong>compliment</strong> for a bigger tip. 1,000+ new lines and 200+ Latin American names.'],
        ['🧹 <strong>Clean &amp; pretty</strong>', 'Mop up droppings, clean graffiti, raise the new <strong>decoration bar</strong>, and show off statues, a 3-block-wide shutter and ten new decor pieces.'],
        ['🎩 <strong>New faces</strong>', 'The <strong>Mayor</strong> collects taxes (refuse and face his Cobblemon team), robbed collectors fight back, Scalpers walk in, and the <strong>CREATive Engineer</strong> sells Create gear.'],
        ['📱 <strong>Phone upgrades</strong>', 'Grade 10 prices, a <strong>Ranking app</strong> to dethrone <strong>Don Cobbledome</strong>, owner of the Cobbledale Cobbledome, a $1,000,000 loan and a call to the Furniture Seller.'],
        ['🧤 <strong>New toys</strong>', '<strong>Box-Opening Gloves</strong>, binders that never drop on death, set-completion rewards, shelf markup, new gambling tables and sounds.'],
        ['🎲 <strong>Harder pulls</strong>', 'Much rarer hits and high grades: a booster box only rarely pays for itself.'],
    ]))
    s.append(center(c('Full list in each file\'s changelog.', GREY)))
    s.append(HR)
    s.append(h2('✨', 'What you can do', GOLD))
    s.append(table([
        ['🏪 <strong>Run a card shop</strong>', 'Claim a plot and grow it from a Paper stall to a 100×100 Netherite megastore. Fill it with displays, shelves, pallets, graded-card walls and RGB showcases.'],
        ['👥 <strong>Serve living customers</strong>', 'Regulars, picky buyers, whales, hagglers, sellers and 10 unique characters. They browse, buy, tip, review you… and some try to steal.'],
        ['🎴 <strong>Rip packs the right way</strong>', 'Card-by-card flips, holo shine, god packs and the market value of every pull. Open 1 to 100 packs at once.'],
        ['📈 <strong>Real market prices</strong>', 'Cards, packs and boxes priced from real TCGplayer data, with a daily moving market.'],
        ['📱 <strong>The TCG Phone</strong>', 'Market and charts, quick sell, shop management, messages, loans, stats, grading, rankings and the league.'],
        ['📚 <strong>Binders &amp; grading</strong>', 'A General Binder for your whole collection, custom Personal Binders, a Binder Market and CobbleGrade slabs from 1.0 to 10.'],
        ['⚔️ <strong>Real Pokémon TCG</strong>', 'Official rules at play tables against the AI or other players, plus a ranked league with collectible 3D coins.'],
        ['🌍 <strong>A world to explore</strong>', 'Trash cans, abandoned card shops, a Wandering Duelist, a casino with real odds and 78 shop events.'],
        ['⚙️ <strong>Fully configurable</strong>', 'Prices, wages, rewards and chances live in config files; reload in game with <code>/tcgshop reload</code>.'],
    ]))
    s.append(HR)
    s.append(h2('🧰', 'Requirements', CYAN))
    s.append('<table style="width: 100%;"><tbody>'
             '<tr><td style="background-color: #3a2a55;"><strong></strong></td><td style="background-color: #3a2a55;"><strong>Mod</strong></td><td style="background-color: #3a2a55;"><strong>Notes</strong></td></tr>'
             f'<tr><td>✅ <strong>Required</strong></td><td>{api_link} 0.3.0+</td><td>Cards, packs, rarities and card art.</td></tr>'
             + dollars +
             f'<tr><td>✅ <strong>Required</strong></td><td>{req_loader}</td><td>Minecraft 1.21.1 · Java 21</td></tr>'
             f'<tr><td>⭐ Recommended</td><td><a href="{CF}cobblemon">Cobblemon</a></td><td>Pokémon battles, the Mayor\'s team, event Pokémon and more.</td></tr>'
             '<tr><td>➕ Optional</td><td>CobbleFurnies, Lucky\'s Cozyhome</td><td>Chairs for customers and play tables, furniture.</td></tr>'
             '<tr><td>➕ Optional</td><td>Pokeblocks</td><td>Pokédolls as decoration and stock.</td></tr>'
             '<tr><td>➕ Optional</td><td>Accessories</td><td>Wear your binder in an accessory slot.</td></tr>'
             '<tr><td>➕ Optional</td><td>Create</td><td>Unlocks the CREATive Engineer.</td></tr>'
             '</tbody></table>')
    s.append(h2('🚀', 'How to install', GREEN))
    s.append('<ol>' + ''.join(f'<li>{i}</li>' for i in install) + '</ol>')
    s.append(f'<p><strong>On a server:</strong> install the same mods on the server and on every client.</p>')
    s.append(HR)
    s.append(h2('💬', 'Feedback', PINK))
    s.append(f'<p><strong>Please leave a comment!</strong> Bugs, balance, ideas, things you love or hate: every comment is read and shapes the next update. '
             f'Issues and the full config documentation live on <a href="{GH_MOD}">GitHub</a>.</p>')
    s.append(h2('🙏', 'Credits', PURPLE))
    s.append(ul([
        '<strong>Thank you for playing.</strong> Seriously.',
        'Inspired by <strong>TCG Card Shop Simulator</strong> and <strong>Pokémon TCG Pocket</strong>.',
        'Pokémon TCG rules engine from <a href="https://github.com/twinleafgg/twinleafgg">twinleaf.gg</a>, based on <a href="https://github.com/keeshii/ryuu-play">ryuu-play</a> (MIT, included).',
        'Market data from TCGplayer via <a href="https://tcgcsv.com">tcgcsv.com</a>.',
        f'Source code and every jar: <a href="{GH_REL}">GitHub release v1.1.0</a>.',
    ]))
    s.append(h2('🤖', 'AI disclosure', GREY))
    s.append('<p>Made with the help of <strong>Claude</strong> (Anthropic): code, procedurally generated textures, texts and balancing. '
             'The project is directed, play-tested and curated by a human.</p>')
    s.append(HR)
    s.append(DISCLAIMER)
    return ''.join(s)


def api_page(loader):
    p = PROJ[('api', loader)]
    neo = loader == 'neoforge'
    mod_link = link('mod', loader, 'MasterBall Traders Co.' + (' (NeoForge)' if neo else ''), inline=True)
    s = []
    s.append(center(f'<img src="{p["logo"]}" alt="MasterBall Traders API" width="140" height="140">'))
    s.append(center(c(p['name'], GOLD), 'h1'))
    s.append(center(f'<strong>{c("The Pokémon TCG card library behind MasterBall Traders Co.", PINK)}</strong>'))
    s.append(center('🃏 <strong>11,767 cards</strong> &nbsp;·&nbsp; 📦 <strong>272 sealed packs</strong> &nbsp;·&nbsp; 🗂️ <strong>68 sets</strong>'
                    ' &nbsp;·&nbsp; ✨ <strong>42 official rarities</strong>'))
    s.append(you_are_here('api', loader))
    s.append(f'<p>📚 This is a <strong>library mod</strong>: on its own it only adds the card and pack items. Install it together with {mod_link}, the Pokémon TCG shop simulator that needs it.</p>')
    s.append(HR)
    s.append(h2('📥', 'Pick your loader', CYAN))
    s.append(loader_table())
    s.append(HR)
    s.append(h2('🎁', 'What it provides', GOLD))
    s.append(table([
        ['🃏 <strong>Every physical card</strong>', '11,767 cards and 272 sealed packs from 68 sets, from Base Set (1999) to the 2026 sets. No TCG Pocket cards.'],
        ['✨ <strong>Official rarities</strong>', '42 rarities from Common to Mega Hyper Rare, each era\'s holo pattern and <strong>print variants</strong>: reverse holo, Poké Ball and Master Ball reverse, 1st Edition, Shadowless and more.'],
        ['📦 <strong>Realistic packs</strong>', '5 cards drawn by official rarity family, rare semi-god packs and <strong>god packs that are always full art</strong>.'],
        ['🎨 <strong>Original art by default</strong>', 'Every card gets generated pixel art with a type-colored border and its real name. No official art is included.'],
        ['🖼️ <strong>Official art, if you want it</strong>', 'On first launch the API asks whether to download the official card art (~3 GB, resumable, with a progress bar). Change your mind any time with <strong>Y</strong> → <em>Download official art</em>.'],
        ['📁 <strong>Your own images</strong>', 'Drop images in <code>PNGS POKEMOMN</code> or the <code>imageFolder</code> set in <code>config/masterball-api.json</code>.'],
    ]))
    s.append(h2('🛠️', 'For mod developers', PURPLE))
    s.append('<p>Version 0.3.0 adds a loader-independent platform layer (<code>dev.masterball.api.platform</code>): networking, events, renderers, '
             'player data and more, with a bridge for each loader. MasterBall Traders Co. is built on it, so its code is shared between Fabric and NeoForge.</p>')
    s.append(HR)
    s.append(h2('🧰', 'Requirements', CYAN))
    req = 'NeoForge 21.1+' if neo else 'Fabric API · Fabric Loader 0.16+'
    s.append(table([['✅ <strong>Required</strong>', f'{req} · Minecraft 1.21.1 · Java 21']]))
    s.append(f'<p>💻 Source code and issues: <a href="{GH_API}">GitHub · T4V00/masterball-api</a></p>')
    s.append(h2('🤖', 'AI disclosure', GREY))
    s.append('<p>Made with the help of <strong>Claude</strong> (Anthropic). The project is directed, play-tested and curated by a human.</p>')
    s.append(HR)
    s.append(DISCLAIMER)
    return ''.join(s)


for (kind, loader) in PROJ:
    html = mod_page(loader) if kind == 'mod' else api_page(loader)
    with open(os.path.join(HERE, f'{kind}_{loader}.html'), 'w', encoding='utf-8') as f:
        f.write(html)
    print(kind, loader, len(html))
