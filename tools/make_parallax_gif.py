#!/usr/bin/env python3
"""Assemble les images de ScreenshotTest.parallaxe en une animation (GIF ou WebP).

Utilisation :
    ./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true --tests '*ScreenshotTest.parallaxe'
    python3 tools/make_parallax_gif.py app/build/parallaxe docs/screenshots/parallaxe.webp
"""
import sys
from pathlib import Path

from PIL import Image


def main(frames_dir: str, output: str, width: int = 360) -> None:
    paths = sorted(Path(frames_dir).glob("frame-*.png"))
    if not paths:
        sys.exit(f"Aucune image dans {frames_dir}")
    frames = []
    for path in paths:
        image = Image.open(path).convert("RGB")
        height = round(image.height * width / image.width)
        frames.append(image.resize((width, height), Image.LANCZOS))
    if output.lower().endswith(".webp"):
        # WebP animé : bien plus léger qu'un GIF, lu par les navigateurs et GitHub.
        frames[0].save(output, save_all=True, append_images=frames[1:], duration=70, loop=0, quality=72, method=6)
        print(f"{output} : {len(frames)} images, {Path(output).stat().st_size // 1024} Ko")
        return
    # Une palette commune évite le scintillement des couleurs d'une image à l'autre.
    palette = frames[0].quantize(colors=200, method=Image.Quantize.MEDIANCUT)
    quantized = [frame.quantize(palette=palette, dither=Image.Dither.FLOYDSTEINBERG) for frame in frames]
    quantized[0].save(
        output,
        save_all=True,
        append_images=quantized[1:],
        duration=70,
        loop=0,
        optimize=True,
    )
    print(f"{output} : {len(frames)} images, {Path(output).stat().st_size // 1024} Ko")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 360)
