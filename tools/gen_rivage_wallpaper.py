"""Génère le fond d'écran « Rivage » : un rivage vu du ciel, en noir et blanc.

Image entièrement procédurale (bruit fractal déformé), créée pour Épure :
aucune photo n'est utilisée.

Usage :
    python3 tools/gen_rivage_wallpaper.py app/src/main/res/drawable-nodpi/rivage_wallpaper.jpg
"""
import sys

import numpy as np
from PIL import Image, ImageFilter

W, H = 1080, 2400
PERIOD = 256
rng = np.random.default_rng(11)


def make_gradients():
    angles = rng.uniform(0, 2 * np.pi, (PERIOD, PERIOD))
    return np.cos(angles), np.sin(angles)


GRADIENTS = [make_gradients() for _ in range(4)]


def noise2(x, y, grads):
    """Bruit de gradient lissé en coordonnées libres (≈ [-1, 1])."""
    gx, gy = grads
    x0f, y0f = np.floor(x), np.floor(y)
    fx, fy = x - x0f, y - y0f
    x0 = x0f.astype(np.int64) % PERIOD
    y0 = y0f.astype(np.int64) % PERIOD
    x1, y1 = (x0 + 1) % PERIOD, (y0 + 1) % PERIOD

    def dot(ix, iy, dx, dy):
        return gx[iy, ix] * dx + gy[iy, ix] * dy

    sx = fx * fx * fx * (fx * (fx * 6 - 15) + 10)
    sy = fy * fy * fy * (fy * (fy * 6 - 15) + 10)
    a = dot(x0, y0, fx, fy) + sx * (dot(x1, y0, fx - 1, fy) - dot(x0, y0, fx, fy))
    b = dot(x0, y1, fx, fy - 1) + sx * (dot(x1, y1, fx - 1, fy - 1) - dot(x0, y1, fx, fy - 1))
    return (a + sy * (b - a)) * 1.45


def fbm(x, y, octaves, ridged=False, seed=0):
    """Somme d'octaves, chacune tournée et décalée pour éviter tout motif en grille."""
    total = np.zeros_like(x)
    amp, norm, freq = 1.0, 0.0, 1.0
    local = np.random.default_rng(1000 + seed)
    for i in range(octaves):
        angle = local.uniform(0, np.pi)
        ox, oy = local.uniform(0, PERIOD, 2)
        ca, sa = np.cos(angle), np.sin(angle)
        xr = (x * ca - y * sa) * freq + ox
        yr = (x * sa + y * ca) * freq + oy
        n = noise2(xr, yr, GRADIENTS[(i + seed) % len(GRADIENTS)])
        if ridged:
            n = (1.0 - np.abs(n)) ** 2
        total += amp * n
        norm += amp
        amp *= 0.52
        freq *= 2.03
    return total / norm


def smoothstep(e0, e1, v):
    t = np.clip((v - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


yy, xx = np.mgrid[0:H, 0:W].astype(np.float64)
u, v = xx / 160.0, yy / 160.0

# Ligne de côte : descend en s'incurvant, la mer à droite.
t = yy[:, :1] / H
coast = W * (0.64 - 0.07 * t + 0.05 * np.sin(t * np.pi * 1.6 + 0.4))
coast = coast + W * 0.05 * fbm(np.zeros_like(t), t * 4.0, 4, seed=1)
edge_warp = fbm(u * 1.4, v * 1.4, 5, seed=2) * 46 + fbm(u * 5, v * 5, 3, seed=3) * 10
dist = (xx - coast + edge_warp) / W  # > 0 : mer, < 0 : terre

# Terre : roche mouillée sombre, grain fin.
rock = 0.085 + 0.035 * fbm(u * 6, v * 6, 4, seed=4) + 0.02 * fbm(u * 22, v * 22, 2, seed=5)

# Mer : dentelle d'écume obtenue par un bruit « en crêtes » déformé.
wx = fbm(u * 0.6, v * 0.6, 3, seed=6)
wy = fbm(u * 0.6 + 7.3, v * 0.6 + 2.1, 3, seed=7)
lace = fbm(u * 2.4 + 1.7 * wx, v * 2.4 + 1.7 * wy, 5, ridged=True, seed=8)
lace_fine = fbm(u * 6.5 + 1.2 * wx, v * 6.5 + 1.2 * wy, 3, ridged=True, seed=9)
foam = smoothstep(0.56, 0.86, lace * 0.8 + lace_fine * 0.3)

near = np.exp(-np.clip(dist, 0, None) / 0.13)       # écume dense près du bord
mid = np.exp(-np.clip(dist, 0, None) / 0.45)
bands = 0.5 + 0.5 * np.sin(dist * 34.0 + 5.0 * fbm(u * 0.8, v * 0.8, 3, seed=10))
crests = smoothstep(0.55, 0.95, bands) * mid       # lignes de vagues plus au large
water = 0.02 + 0.025 * fbm(u * 3, v * 3, 3, seed=11)
sea = water + foam * (0.05 + 0.95 * near) + foam * crests * 0.45

# Liseré d'écume sur le rivage.
rim = np.exp(-((dist - 0.006) ** 2) / (2 * 0.007 ** 2)) * (0.45 + 0.55 * lace)
img = np.where(dist < 0, rock, sea) + rim * 0.75
img = np.clip(img, 0, 1)

# Bords légèrement assombris, rendu photo.
vy = np.linspace(-1, 1, H)[:, None]
vx = np.linspace(-1, 1, W)[None, :]
img *= 1.0 - 0.22 * (vx ** 2 * 0.5 + vy ** 2 * 0.5)

out = Image.fromarray((np.clip(img, 0, 1) ** 0.95 * 255).astype(np.uint8), mode="L")
out = out.filter(ImageFilter.GaussianBlur(0.7))
out.convert("RGB").save(sys.argv[1], quality=86, optimize=True, progressive=True)
print("fond d'écran enregistré :", sys.argv[1], out.size)
