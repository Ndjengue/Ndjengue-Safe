#!/usr/bin/env python3
"""Gera o conjunto de ícones Android (legacy + adaptive) a partir do logo fornecido."""
import os
from PIL import Image, ImageDraw

SRC = "/mnt/user-data/uploads/Downloads/vpn_icone.png"
RES = "/root/controlo-parental/app/src/main/res"

# Densidades legacy (ic_launcher.png / ic_launcher_round.png)
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Tamanho base do adaptive icon foreground por densidade (108dp canvas)
ADAPTIVE_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

def circle_mask(img: Image.Image) -> Image.Image:
    size = img.size
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size[0], size[1]), fill=255)
    out = img.copy()
    out.putalpha(mask)
    return out

def square_crop(img: Image.Image) -> Image.Image:
    w, h = img.size
    s = min(w, h)
    left = (w - s) // 2
    top = (h - s) // 2
    return img.crop((left, top, left + s, top + s))

def main():
    src = Image.open(SRC).convert("RGBA")
    src_sq = square_crop(src)

    for density, px in DENSITIES.items():
        folder = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        resized = src_sq.resize((px, px), Image.LANCZOS)
        resized.save(os.path.join(folder, "ic_launcher.png"))
        round_icon = circle_mask(resized)
        round_icon.save(os.path.join(folder, "ic_launcher_round.png"))

    # Adaptive icon foreground: logo ocupa ~66% do canvas (zona segura), fundo transparente
    for density, canvas_px in ADAPTIVE_SIZES.items():
        folder = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        inner = int(canvas_px * 0.66)
        logo = src_sq.resize((inner, inner), Image.LANCZOS)
        canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        off = (canvas_px - inner) // 2
        canvas.paste(logo, (off, off), logo)
        canvas.save(os.path.join(folder, "ic_launcher_foreground.png"))

    # Ícone grande para Play Store / README (512x512)
    hi = src_sq.resize((512, 512), Image.LANCZOS)
    hi.save(os.path.join(RES, "..", "..", "..", "..", "icone_app_512.png"))

    print("Ícones gerados com sucesso.")

if __name__ == "__main__":
    main()
