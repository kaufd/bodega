"""Надпись на splash -> VectorDrawable: контуры глифов Rubik SemiBold в круге 192dp.

Текста в splash-экране Android не бывает — только drawable, поэтому строки из
LINES переводятся в пути. Запуск из корня репозитория (нужен fonttools):

    python3 docs/design/make_splash_title.py > app/src/main/res/drawable/splash_title.xml

Кегль подбирается сам: на Android 12+ система обрезает splash-иконку кругом
192dp внутри канваса 288dp, под этот круг и вписывается блок строк.
"""
import math
import sys
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools.pens.svgPathPen import SVGPathPen

FONT = "app/src/main/res/font/rubik_variable.ttf"
LINES = ["С годовщиной,", "любимая!"]
CANVAS = 288.0   # dp, канвас splash-иконки
CIRCLE = 192.0   # dp, видимый круг внутри канваса
LINE_GAP = 1.18  # межстрочный интервал в кеглях

font = instancer.instantiateVariableFont(TTFont(FONT), {"wght": 600})
upem = font["head"].unitsPerEm
cmap = font.getBestCmap()
glyphs = font.getGlyphSet()
hmtx = font["hmtx"]


def line_paths(text):
    """Пути глифов строки в единицах шрифта плюс её полная ширина."""
    out, x = [], 0.0
    for ch in text:
        name = cmap.get(ord(ch))
        if name is None:
            sys.exit(f"в шрифте нет символа {ch!r}")
        pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.1f}")
        glyphs[name].draw(pen)
        d = pen.getCommands()
        if d:
            out.append((x, d))
        x += hmtx[name][0]
    return out, x


lines = [line_paths(t) for t in LINES]
widest = max(w for _, w in lines) / upem          # ширина блока в кеглях
tall = LINE_GAP * (len(lines) - 1) + 0.72         # высота блока в кеглях (0.72 — от базовой линии до верха прописных)

# Кегль подбираем так, чтобы прямоугольник блока вписался в круг с запасом 6%.
radius = CIRCLE / 2 * 0.94
size = 2 * radius / math.hypot(widest, tall)
scale = size / upem

# Верхняя базовая линия: блок центрирован по вертикали в канвасе.
top = CANVAS / 2 - tall * size / 2
groups = []
for i, (paths, width) in enumerate(lines):
    baseline = top + 0.72 * size + i * LINE_GAP * size
    left = CANVAS / 2 - width * scale / 2
    for x, d in paths:
        groups.append(
            f'    <group android:translateX="{left + x * scale:.2f}" '
            f'android:translateY="{baseline:.2f}" '
            f'android:scaleX="{scale:.5f}" android:scaleY="{-scale:.5f}">\n'
            f'        <path android:fillColor="#FF20324A" android:pathData="{d}" />\n'
            f"    </group>"
        )

print(
    '<?xml version="1.0" encoding="utf-8"?>\n'
    "<!-- Сгенерировано: контуры Rubik SemiBold, текст «"
    + " ".join(LINES)
    + "».\n"
    "     Правится не руками: docs/design/make_splash_title.py. -->\n"
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    f'    android:width="{CANVAS:.0f}dp" android:height="{CANVAS:.0f}dp"\n'
    f'    android:viewportWidth="{CANVAS:.0f}" android:viewportHeight="{CANVAS:.0f}">\n'
    + "\n".join(groups)
    + "\n</vector>"
)
