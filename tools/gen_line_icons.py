"""Génère LineIconData.kt à partir des données de Tabler Icons (licence MIT).

Usage :
    npm pack @tabler/icons && tar -xzf tabler-icons-*.tgz
    python3 tools/gen_line_icons.py package/tabler-nodes-outline.json \
        app/src/main/java/fr/epure/launcher/ui/icons/LineIconData.kt

Pour ajouter une icône : ajouter son nom Tabler (https://tabler.io/icons) à la liste `want`.
"""
import json, sys
nodes_path, out_path = sys.argv[1], sys.argv[2]
d = json.load(open(nodes_path))
want = """brand-chrome brand-firefox brand-edge brand-opera brand-safari brand-instagram brand-threads brand-whatsapp brand-telegram brand-signal
brand-tiktok brand-spotify brand-deezer brand-youtube brand-netflix brand-disney brand-discord brand-snapchat brand-x
brand-twitter brand-facebook brand-messenger brand-linkedin brand-gmail brand-google-maps brand-google-drive brand-google-play brand-google-photos
brand-google brand-waze brand-uber brand-amazon brand-paypal brand-pinterest brand-reddit brand-twitch brand-zoom brand-teams brand-skype brand-slack
brand-notion brand-soundcloud brand-bluesky brand-airbnb brand-booking brand-revolut brand-steam brand-xbox
brand-shazam brand-tinder brand-openai brand-android brand-apple brand-strava brand-vlc brand-github brand-trello brand-dropbox brand-onedrive
brand-office brand-mastodon brand-vimeo brand-tumblr brand-wechat brand-line brand-viber brand-kick brand-patreon brand-google-home brand-google-one
brand-shopee brand-etsy brand-deliveroo brand-mcdonalds brand-coinbase brand-binance brand-gravatar
brand-producthunt brand-medium brand-figma brand-adobe brand-lastfm brand-tidal brand-apple-podcast
phone message-circle messages camera photo settings clock alarm calendar mail map map-2 map-pin music headphones calculator address-book user users
folder files download world cloud sun cloud-rain notes note checklist list-check shopping-bag shopping-cart building-bank wallet credit-card file
device-gamepad-2 movie video news briefcase heart heartbeat book school car bike plane train bus home lock key shield qrcode scan microphone compass
clipboard pencil brush palette search bell star gift tools device-mobile device-tv device-watch bluetooth wifi cast battery thermometer
umbrella coffee apps layout-grid robot sparkles language stethoscope tag hanger antenna-bars-5 adjustments-horizontal message-chatbot brain
player-play radio books book-2 basket truck-delivery tool settings-2 cloud-download photo-scan scissors recharging run barbell ticket
building-store receipt chart-line calendar-event device-floppy printer user-circle""".split()
icons = {}
for name in want:
    if name not in d:
        continue
    paths = [attrs["d"] for kind, attrs in d[name] if kind == "path" and attrs.get("d")]
    if paths:
        icons[name] = paths

def kstr(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'

names = sorted(icons)
chunks = [names[i:i + 40] for i in range(0, len(names), 40)]
out = []
out.append("package fr.epure.launcher.ui.icons\n")
out.append("// Fichier généré à partir de Tabler Icons 3.52.0 (https://tabler.io/icons, licence MIT,")
out.append("// © 2020-2026 Paweł Kuna). Ne pas modifier à la main : relancer tools/gen_line_icons.py.\n")
out.append("/** Tracés (viewBox 24×24, trait de 2) des icônes au trait, par nom Tabler. */")
out.append("internal object LineIconData {")
out.append("    val paths: Map<String, Array<String>> = HashMap<String, Array<String>>(%d).apply {" % (len(names) * 2))
for i in range(len(chunks)):
    out.append("        part%d(this)" % i)
out.append("    }\n")
for i, chunk in enumerate(chunks):
    out.append("    private fun part%d(m: HashMap<String, Array<String>>) {" % i)
    for name in chunk:
        out.append("        m[%s] = arrayOf(%s)" % (kstr(name), ", ".join(kstr(p) for p in icons[name])))
    out.append("    }\n")
out[-1] = out[-1].rstrip("\n")
out.append("}")
open(out_path, "w").write("\n".join(out) + "\n")
print(len(names), "icônes générées")
