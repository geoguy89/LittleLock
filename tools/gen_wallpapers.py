"""Draws LittleLock's cartoon-animal wallpapers.

One drawing description per animal (flat, friendly shapes on a 600 x 600 canvas) becomes
  - an Android vector drawable  app/src/main/res/drawable/wp_<key>.xml   (sharp on any screen)
  - a PNG preview for a quick look (--preview DIR)

    python tools/gen_wallpapers.py                  # rewrite the vector drawables
    python tools/gen_wallpapers.py --preview out    # also write out/<key>.png and out/_sheet.png

The animal sits in the middle ~260 units, so the picture still reads when a tall phone or a wide tablet crops it
(the launcher scales it to cover the screen). Run it again after changing a drawing; the XML files are generated.
"""

from __future__ import annotations

import argparse
import math
import os
from pathlib import Path

W = 600
OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "drawable"


# ---------------------------------------------------------------------------------------- drawing model
class Canvas:
    def __init__(self) -> None:
        self.items: list[tuple] = []

    def grad(self, top: str, bottom: str) -> None:
        self.items.append(("grad", top, bottom))

    def poly(self, pts, fill: str, alpha: float = 1.0) -> None:
        self.items.append(("poly", [(float(x), float(y)) for x, y in pts], fill, alpha))

    def ellipse(self, cx, cy, rx, ry, fill: str, rot: float = 0.0, alpha: float = 1.0) -> None:
        self.items.append(("ell", float(cx), float(cy), float(rx), float(ry), fill, float(rot), alpha))

    def circle(self, cx, cy, r, fill: str, alpha: float = 1.0) -> None:
        self.ellipse(cx, cy, r, r, fill, 0.0, alpha)

    def rect(self, x, y, w, h, fill: str, alpha: float = 1.0) -> None:
        self.poly([(x, y), (x + w, y), (x + w, y + h), (x, y + h)], fill, alpha)

    def line(self, pts, color: str, width: float, alpha: float = 1.0) -> None:
        self.items.append(("line", [(float(x), float(y)) for x, y in pts], color, float(width), alpha))

    def curve(self, p0, p1, p2, color: str, width: float) -> None:
        """A quadratic curve p0 -> p2 bent towards p1 (a smile, a whisker)."""
        self.items.append(("quad", p0, p1, p2, color, float(width)))


def star(cx, cy, r, points=5, inner=0.45):
    out = []
    for i in range(points * 2):
        a = -math.pi / 2 + i * math.pi / points
        rr = r if i % 2 == 0 else r * inner
        out.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return out


def heart(cx, cy, s):
    pts = []
    for i in range(41):
        t = i / 40 * 2 * math.pi
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((cx + x * s, cy + y * s))
    return pts


def cloud(c: Canvas, x, y, s=1.0, color="#FFFFFF", alpha=0.9):
    for dx, dy, r in ((0, 0, 26), (28, -12, 32), (62, 0, 26), (30, 8, 30)):
        c.circle(x + dx * s, y + dy * s, r * s, color, alpha)


def flower(c: Canvas, x, y, s=1.0, petal="#FFFFFF", mid="#FFD54F"):
    for i in range(5):
        a = i * 2 * math.pi / 5
        c.circle(x + 9 * s * math.cos(a), y + 9 * s * math.sin(a), 7 * s, petal)
    c.circle(x, y, 6 * s, mid)


def eyes(c: Canvas, y, gap, r=17, iris="#2B2B2B"):
    for sx in (-1, 1):
        x = 300 + sx * gap
        c.circle(x, y, r, "#FFFFFF")
        c.circle(x + sx * -1, y + 1, r * 0.66, iris)
        c.circle(x + r * 0.2, y - r * 0.25, r * 0.26, "#FFFFFF")


def cheeks(c: Canvas, y, gap, r=16, color="#FF8FA3"):
    for sx in (-1, 1):
        c.ellipse(300 + sx * gap, y, r, r * 0.7, color, 0, 0.55)


def smile(c: Canvas, y, w=22, depth=14, color="#4A2C2A", width=5):
    c.curve((300 - w, y), (300, y + depth * 2), (300 + w, y), color, width)


def scene_day(c: Canvas, top="#BDE8FF", bottom="#E8F7FF", ground="#9BDB8A", ground2="#7CC96E"):
    c.grad(top, bottom)
    c.circle(500, 105, 46, "#FFE27A")
    c.circle(500, 105, 62, "#FFF3B0", 0.45)
    cloud(c, 70, 120, 1.0)
    cloud(c, 380, 190, 0.7)
    cloud(c, 120, 330, 0.55)
    c.ellipse(120, 640, 360, 150, ground)
    c.ellipse(520, 650, 330, 170, ground2)
    for x, y in ((70, 520), (160, 560), (440, 545), (535, 510), (355, 585), (255, 575)):
        flower(c, x, y, 1.2, "#FFFFFF", "#FFC93C")
    for x, y, r in ((545, 300, 11), (65, 250, 9), (520, 400, 8)):
        c.poly(star(x, y, r), "#FFFFFF", 0.9)


def scene_night(c: Canvas, top="#2C3E8F", bottom="#7C8CDB", ground="#E8F1FF", ground2="#CFE0FF"):
    c.grad(top, bottom)
    c.circle(95, 110, 40, "#FFF4C2")
    c.circle(112, 100, 36, top)                      # a crescent
    for x, y, r in ((200, 70, 12), (330, 130, 9), (470, 80, 13), (540, 210, 10), (60, 260, 9), (520, 340, 8), (150, 190, 7), (400, 50, 8)):
        c.poly(star(x, y, r), "#FFFFFF", 0.95)
    c.ellipse(120, 650, 360, 150, ground)
    c.ellipse(520, 655, 330, 170, ground2)
    for x, y in ((90, 520), (500, 530), (300, 585)):
        c.poly(star(x, y, 12, 6, 0.5), "#FFFFFF", 0.8)


def scene_pond(c: Canvas):
    c.grad("#C9F0E0", "#EFFFF8")
    cloud(c, 60, 110, 1.0)
    cloud(c, 400, 160, 0.8)
    c.circle(500, 90, 40, "#FFE27A")
    c.ellipse(300, 600, 420, 190, "#7ED3F0")
    c.ellipse(300, 600, 360, 150, "#9BE0F7", 0, 0.7)
    c.ellipse(120, 540, 60, 22, "#4CAF50")
    c.ellipse(480, 560, 70, 24, "#43A047")
    flower(c, 485, 538, 1.3, "#FFB3C7", "#FFE27A")
    flower(c, 120, 520, 1.1, "#FFFFFF", "#FFE27A")
    for x, y in ((500, 280), (70, 300)):
        c.poly(heart(x, y, 1.0), "#FF8FA3", 0.8)


# ---------------------------------------------------------------------------------------- the animals
def cat(c: Canvas):
    scene_day(c, "#FFE5D0", "#FFF6EC", "#A8DF8E", "#86CC76")
    fur, dark = "#F2A65A", "#D9843B"
    c.poly([(205, 245), (215, 150), (285, 215)], fur)
    c.poly([(395, 245), (385, 150), (315, 215)], fur)
    c.poly([(222, 222), (226, 176), (264, 212)], "#FFB6C1")
    c.poly([(378, 222), (374, 176), (336, 212)], "#FFB6C1")
    c.ellipse(300, 310, 122, 104, fur)
    for dx in (-22, 0, 22):
        c.line([(300 + dx, 212), (300 + dx * 0.8, 236)], dark, 7)
    eyes(c, 300, 48, 21, "#2B2B2B")
    cheeks(c, 340, 80)
    c.poly([(288, 330), (312, 330), (300, 344)], "#FF7A93")
    c.curve((300, 344), (300, 350), (300, 352), "#4A2C2A", 4)
    c.curve((300, 352), (284, 372), (268, 354), "#4A2C2A", 4)
    c.curve((300, 352), (316, 372), (332, 354), "#4A2C2A", 4)
    for sy in (-1, 0, 1):
        c.line([(238, 340 + sy * 12), (172, 330 + sy * 22)], "#FFFFFF", 3, 0.95)
        c.line([(362, 340 + sy * 12), (428, 330 + sy * 22)], "#FFFFFF", 3, 0.95)


def dog(c: Canvas):
    scene_day(c, "#D8EEFF", "#F2FAFF", "#9BDB8A", "#7CC96E")
    fur, ear = "#C98A5A", "#8B5A36"
    c.ellipse(205, 290, 40, 82, ear, -0.35)
    c.ellipse(395, 290, 40, 82, ear, 0.35)
    c.ellipse(300, 305, 118, 108, fur)
    c.ellipse(300, 345, 76, 58, "#F7E3CC")
    c.ellipse(300, 300, 20, 30, "#F7E3CC", 0, 0.0)
    c.ellipse(255, 275, 28, 26, "#8B5A36", 0, 0.9)         # an eye patch
    eyes(c, 280, 46, 19, "#2B2B2B")
    c.ellipse(300, 325, 26, 19, "#2B2B2B")
    c.circle(292, 320, 5, "#FFFFFF", 0.7)
    c.line([(300, 344), (300, 356)], "#2B2B2B", 4)
    c.curve((300, 356), (278, 376), (258, 354), "#2B2B2B", 4)
    c.curve((300, 356), (322, 376), (342, 354), "#2B2B2B", 4)
    c.ellipse(300, 372, 17, 22, "#FF7A93")
    cheeks(c, 335, 92, 15)


def bunny(c: Canvas):
    scene_day(c, "#FFE0EC", "#FFF5F8", "#A8DF8E", "#86CC76")
    fur = "#F4F0F2"
    c.ellipse(248, 195, 34, 88, fur, -0.12)
    c.ellipse(352, 195, 34, 88, fur, 0.12)
    c.ellipse(248, 198, 18, 66, "#FFB6C9", -0.12)
    c.ellipse(352, 198, 18, 66, "#FFB6C9", 0.12)
    c.ellipse(300, 330, 112, 100, fur)
    eyes(c, 318, 44, 19, "#4A3B52")
    cheeks(c, 355, 76, 16)
    c.poly([(290, 345), (310, 345), (300, 357)], "#FF7A93")
    c.curve((300, 357), (300, 362), (300, 366), "#7A5A66", 4)
    c.curve((300, 366), (284, 384), (270, 368), "#7A5A66", 4)
    c.curve((300, 366), (316, 384), (330, 368), "#7A5A66", 4)
    c.rect(292, 372, 8, 14, "#FFFFFF")
    c.rect(301, 372, 8, 14, "#FFFFFF")
    c.line([(292, 386), (308, 386)], "#CFC4C9", 2)


def bear(c: Canvas):
    scene_day(c, "#FFE9C7", "#FFF8EA", "#A8DF8E", "#86CC76")
    fur = "#B07A4F"
    for sx in (-1, 1):
        c.circle(300 + sx * 92, 218, 40, fur)
        c.circle(300 + sx * 92, 222, 22, "#E3B589")
    c.ellipse(300, 305, 122, 108, fur)
    c.ellipse(300, 345, 58, 44, "#E8C49C")
    eyes(c, 283, 50, 17, "#2B2B2B")
    c.ellipse(300, 330, 22, 15, "#3A2418")
    c.circle(293, 326, 4, "#FFFFFF", 0.6)
    c.line([(300, 345), (300, 357)], "#3A2418", 4)
    c.curve((300, 357), (282, 374), (266, 358), "#3A2418", 4)
    c.curve((300, 357), (318, 374), (334, 358), "#3A2418", 4)
    cheeks(c, 340, 98, 14)


def panda(c: Canvas):
    scene_day(c, "#DFF5E1", "#F4FFF4", "#9BDB8A", "#7CC96E")
    for sx in (-1, 1):
        c.circle(300 + sx * 96, 215, 38, "#2B2B2B")
    c.ellipse(300, 305, 126, 108, "#FFFFFF")
    for sx in (-1, 1):
        c.ellipse(300 + sx * 50, 292, 28, 38, "#2B2B2B", sx * 0.5)
        c.circle(300 + sx * 50, 290, 13, "#FFFFFF")
        c.circle(300 + sx * 50 - sx * 1, 291, 8, "#2B2B2B")
        c.circle(300 + sx * 50 + 3, 286, 3, "#FFFFFF")
    c.ellipse(300, 336, 16, 11, "#2B2B2B")
    c.curve((300, 346), (300, 352), (300, 358), "#2B2B2B", 4)
    c.curve((300, 358), (284, 376), (268, 360), "#2B2B2B", 4)
    c.curve((300, 358), (316, 376), (332, 360), "#2B2B2B", 4)
    cheeks(c, 350, 90, 15)


def fox(c: Canvas):
    scene_day(c, "#FFE0C2", "#FFF4E8", "#A8DF8E", "#86CC76")
    fur = "#EE7D2B"
    c.poly([(196, 250), (206, 140), (284, 214)], fur)
    c.poly([(404, 250), (394, 140), (316, 214)], fur)
    c.poly([(207, 174), (206, 140), (232, 160)], "#2B2B2B")
    c.poly([(393, 174), (394, 140), (368, 160)], "#2B2B2B")
    c.ellipse(300, 305, 124, 100, fur)
    c.ellipse(238, 346, 56, 36, "#FFFFFF", -0.3)
    c.ellipse(362, 346, 56, 36, "#FFFFFF", 0.3)
    c.ellipse(300, 366, 38, 24, "#FFFFFF")
    eyes(c, 292, 50, 17, "#2B2B2B")
    c.ellipse(300, 336, 17, 13, "#2B2B2B")
    c.circle(295, 332, 4, "#FFFFFF", 0.6)
    c.curve((300, 349), (300, 355), (300, 360), "#2B2B2B", 4)
    c.curve((300, 360), (284, 376), (270, 362), "#2B2B2B", 4)
    c.curve((300, 360), (316, 376), (330, 362), "#2B2B2B", 4)


def penguin(c: Canvas):
    scene_night(c)
    c.ellipse(300, 330, 108, 138, "#26324D")
    c.ellipse(300, 352, 74, 104, "#FFFFFF")
    for sx in (-1, 1):
        c.ellipse(300 + sx * 112, 340, 24, 70, "#26324D", sx * 0.35)
        c.ellipse(300 + sx * 46, 462, 38, 15, "#FFA63C")
    eyes(c, 270, 40, 20, "#1B1B1B")
    c.poly([(278, 296), (322, 296), (300, 326)], "#FFA63C")
    cheeks(c, 304, 70, 13)
    for x in (255, 345):
        c.poly(star(x, 395, 6, 6, 0.4), "#C9D7F2", 0.0)


def owl(c: Canvas):
    scene_night(c, "#3B2F77", "#9F8BE0", "#E7DEFF", "#D2C6FA")
    c.rect(60, 452, 480, 26, "#7A5230")
    c.circle(90, 452, 13, "#7A5230")
    body = "#A9754A"
    c.ellipse(300, 335, 112, 132, body)
    c.poly([(206, 252), (200, 182), (262, 226)], body)
    c.poly([(394, 252), (400, 182), (338, 226)], body)
    c.ellipse(300, 372, 72, 88, "#F1D9B5")
    for i, (x, y) in enumerate(((270, 350), (300, 350), (330, 350), (285, 380), (315, 380), (300, 410))):
        c.curve((x - 10, y), (x, y + 9), (x + 10, y), "#C9A16F", 3)
    for sx in (-1, 1):
        c.circle(300 + sx * 52, 285, 42, "#F6EBD6")
        c.circle(300 + sx * 52, 285, 42, "#8A5A32", 0.0)
        c.circle(300 + sx * 52, 287, 26, "#FFFFFF")
        c.circle(300 + sx * 52 - sx * 2, 289, 17, "#2B2B2B")
        c.circle(300 + sx * 52 + 4, 282, 6, "#FFFFFF")
    c.poly([(288, 318), (312, 318), (300, 342)], "#FFB020")
    for sx in (-1, 1):
        c.ellipse(300 + sx * 48, 474, 22, 12, "#FFB020")


def elephant(c: Canvas):
    scene_day(c, "#D6ECFF", "#F1F8FF", "#A8DF8E", "#86CC76")
    gray, pink = "#9DB3C8", "#F4B6C6"
    for sx in (-1, 1):
        c.circle(300 + sx * 128, 285, 66, gray)
        c.circle(300 + sx * 128, 288, 44, pink, 0.85)
    c.ellipse(300, 300, 108, 104, gray)
    c.line([(300, 335), (300, 395), (326, 438), (352, 430)], gray, 54)
    c.ellipse(352, 432, 22, 20, gray)
    for sx in (-1, 1):
        c.poly([(300 + sx * 52, 366), (300 + sx * 70, 362), (300 + sx * 92, 404), (300 + sx * 80, 408)], "#FFFDF4")
    eyes(c, 277, 48, 17, "#2B2B2B")
    cheeks(c, 330, 78, 15)
    c.curve((276, 362), (300, 376), (324, 362), "#6C7F92", 3)


def frog(c: Canvas):
    scene_pond(c)
    green, light = "#59C55A", "#C9F2A8"
    c.ellipse(300, 345, 132, 92, green)
    c.ellipse(300, 372, 86, 52, light)
    for sx in (-1, 1):
        c.circle(300 + sx * 66, 262, 46, green)
        c.circle(300 + sx * 66, 262, 33, "#FFFFFF")
        c.circle(300 + sx * 66 - sx * 2, 264, 21, "#2B2B2B")
        c.circle(300 + sx * 66 + 6, 255, 7, "#FFFFFF")
    c.curve((222, 352), (300, 428), (378, 352), "#2D6B2F", 6)
    cheeks(c, 355, 118, 17)
    for sx in (-1, 1):
        c.circle(300 + sx * 12, 318, 3.5, "#2D6B2F")


ANIMALS = {"cat": ("Cat", cat), "dog": ("Puppy", dog), "bunny": ("Bunny", bunny), "bear": ("Bear", bear), "panda": ("Panda", panda),
           "fox": ("Fox", fox), "penguin": ("Penguin", penguin), "owl": ("Owl", owl), "elephant": ("Elephant", elephant), "frog": ("Frog", frog)}


# ---------------------------------------------------------------------------------------- outputs
def _f(v: float) -> str:
    s = f"{v:.1f}"
    return s[:-2] if s.endswith(".0") else s


def _ell_pts(cx, cy, rx, ry, rot, n=48):
    out = []
    for i in range(n):
        t = i / n * 2 * math.pi
        x, y = rx * math.cos(t), ry * math.sin(t)
        out.append((cx + x * math.cos(rot) - y * math.sin(rot), cy + x * math.sin(rot) + y * math.cos(rot)))
    return out


def to_xml(c: Canvas) -> str:
    o = ['<?xml version="1.0" encoding="utf-8"?>',
         '<!-- Generated by tools/gen_wallpapers.py - edit the drawing there, not this file. -->',
         '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
         '    xmlns:aapt="http://schemas.android.com/aapt"',
         f'    android:width="{W}dp" android:height="{W}dp" android:viewportWidth="{W}" android:viewportHeight="{W}">']

    def argb(col: str, a: float) -> str:
        return col if a >= 0.999 else "#" + f"{round(a * 255):02X}" + col[1:]

    for it in c.items:
        k = it[0]
        if k == "grad":
            o.append(f'    <path android:pathData="M0,0H{W}V{W}H0Z"><aapt:attr name="android:fillColor"><gradient android:startX="0" android:startY="0" '
                     f'android:endX="0" android:endY="{W}" android:startColor="{it[1]}" android:endColor="{it[2]}" android:type="linear"/></aapt:attr></path>')
        elif k == "poly":
            if it[3] <= 0.0:
                continue
            d = "M" + "L".join(f"{_f(x)},{_f(y)}" for x, y in it[1]) + "Z"
            o.append(f'    <path android:fillColor="{argb(it[2], it[3])}" android:pathData="{d}"/>')
        elif k == "ell":
            _, cx, cy, rx, ry, fill, rot, a = it
            if a <= 0.0:
                continue
            if abs(rot) < 1e-6:
                d = f"M{_f(cx - rx)},{_f(cy)}a{_f(rx)},{_f(ry)} 0 1,0 {_f(2 * rx)},0a{_f(rx)},{_f(ry)} 0 1,0 {_f(-2 * rx)},0Z"
            else:
                d = "M" + "L".join(f"{_f(x)},{_f(y)}" for x, y in _ell_pts(cx, cy, rx, ry, rot)) + "Z"
            o.append(f'    <path android:fillColor="{argb(fill, a)}" android:pathData="{d}"/>')
        elif k == "line":
            d = "M" + "L".join(f"{_f(x)},{_f(y)}" for x, y in it[1])
            o.append(f'    <path android:pathData="{d}" android:strokeColor="{argb(it[2], it[4])}" android:strokeWidth="{_f(it[3])}" '
                     'android:strokeLineCap="round" android:strokeLineJoin="round"/>')
        elif k == "quad":
            _, p0, p1, p2, col, wd = it
            d = f"M{_f(p0[0])},{_f(p0[1])}Q{_f(p1[0])},{_f(p1[1])} {_f(p2[0])},{_f(p2[1])}"
            o.append(f'    <path android:pathData="{d}" android:strokeColor="{col}" android:strokeWidth="{_f(wd)}" android:strokeLineCap="round"/>')
    o.append('</vector>')
    return "\n".join(o) + "\n"


def preview(c: Canvas, size: int = 900):
    from PIL import Image, ImageDraw
    k = size / W
    img = Image.new("RGB", (size, size), "#FFFFFF")
    d = ImageDraw.Draw(img, "RGBA")

    def rgba(col: str, a: float):
        col = col.lstrip("#")
        return (int(col[0:2], 16), int(col[2:4], 16), int(col[4:6], 16), int(round(a * 255)))

    for it in c.items:
        kind = it[0]
        if kind == "grad":
            t, b = rgba(it[1], 1), rgba(it[2], 1)
            for y in range(size):
                f = y / (size - 1)
                d.line([(0, y), (size, y)], fill=tuple(int(t[i] + (b[i] - t[i]) * f) for i in range(3)) + (255,))
        elif kind == "poly":
            if it[3] > 0:
                d.polygon([(x * k, y * k) for x, y in it[1]], fill=rgba(it[2], it[3]))
        elif kind == "ell":
            _, cx, cy, rx, ry, fill, rot, a = it
            if a > 0:
                d.polygon([(x * k, y * k) for x, y in _ell_pts(cx, cy, rx, ry, rot, 96)], fill=rgba(fill, a))
        elif kind == "line":
            pts = [(x * k, y * k) for x, y in it[1]]
            d.line(pts, fill=rgba(it[2], it[4]), width=max(1, int(it[3] * k)), joint="curve")
            r = it[3] * k / 2
            for x, y in (pts[0], pts[-1]):
                d.ellipse([x - r, y - r, x + r, y + r], fill=rgba(it[2], it[4]))
        elif kind == "quad":
            _, p0, p1, p2, col, wd = it
            pts = []
            for i in range(25):
                t = i / 24
                x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
                y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
                pts.append((x * k, y * k))
            d.line(pts, fill=rgba(col, 1), width=max(1, int(wd * k)), joint="curve")
            r = wd * k / 2
            for x, y in (pts[0], pts[-1]):
                d.ellipse([x - r, y - r, x + r, y + r], fill=rgba(col, 1))
    return img


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", default="")
    a = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = []
    for key, (name, fn) in ANIMALS.items():
        c = Canvas()
        fn(c)
        (OUT / f"wp_{key}.xml").write_text(to_xml(c), encoding="utf-8")
        if a.preview:
            os.makedirs(a.preview, exist_ok=True)
            img = preview(c)
            img.save(Path(a.preview) / f"{key}.png")
            sheet.append((name, img))
    if a.preview and sheet:
        from PIL import Image, ImageDraw
        cell = 360
        cols = 5
        rows = (len(sheet) + cols - 1) // cols
        big = Image.new("RGB", (cols * cell, rows * (cell + 24)), "#222222")
        dd = ImageDraw.Draw(big)
        for i, (name, img) in enumerate(sheet):
            x, y = (i % cols) * cell, (i // cols) * (cell + 24)
            big.paste(img.resize((cell, cell)), (x, y + 24))
            dd.text((x + 8, y + 6), name, fill="#FFFFFF")
        big.save(Path(a.preview) / "_sheet.png")
    print(f"wrote {len(ANIMALS)} wallpapers to {OUT}")


if __name__ == "__main__":
    main()
