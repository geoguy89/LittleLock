#!/usr/bin/env python3
"""
Draws the profile pictures (cartoon faces) as Android vector drawables: app/src/main/res/drawable/av_<key>.xml.
Run from the repository root:  python tools/gen_avatars.py            (add  --preview sheet.png  for a contact sheet)
Edit the drawings here, not the generated XML. Every picture is a 200 x 200 round badge.
"""
import os
import sys

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res', 'drawable')

DARK = '#2B2233'


# ---------------------------------------------------------------- primitives: ('kind', ..., colour)

def circle(cx, cy, r, c): return ('ellipse', cx, cy, r, r, c)
def ellipse(cx, cy, rx, ry, c): return ('ellipse', cx, cy, rx, ry, c)
def poly(pts, c): return ('poly', pts, c)
def curve(p0, ctrl, p1, c, w=5): return ('curve', p0, ctrl, p1, c, w)
def line(p0, p1, c, w=4): return ('curve', p0, ((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2), p1, c, w)


def rrect(x, y, w, h, r, c):
    return ('rrect', x, y, w, h, r, c)


def eyes(y, dx, r=11, pupil=6, cx=100):
    out = []
    for s in (-1, 1):
        x = cx + s * dx
        out += [circle(x, y, r, '#FFFFFF'), circle(x + s * -1, y + 1, pupil, DARK), circle(x - 2, y - 2, 2.2, '#FFFFFF')]
    return out


def dot_eyes(y, dx, r=6, cx=100):
    out = []
    for s in (-1, 1):
        x = cx + s * dx
        out += [circle(x, y, r, DARK), circle(x - 2, y - 2, 2, '#FFFFFF')]
    return out


def cheeks(y, dx, c='#FF9FB2', r=9):
    return [ellipse(100 - dx, y, r, r * 0.72, c), ellipse(100 + dx, y, r, r * 0.72, c)]


def smile(y, w=16, h=12, cx=100, c=DARK, wd=4.5):
    return [curve((cx - w, y), (cx, y + h * 2), (cx + w, y), c, wd)]


# ---------------------------------------------------------------- the pictures

def cat():
    return [circle(100, 100, 100, '#FFE0A8'),
            poly([(40, 80), (52, 22), (96, 56)], '#EE9A2E'), poly([(160, 80), (148, 22), (104, 56)], '#EE9A2E'),
            poly([(52, 70), (57, 38), (82, 58)], '#FFB4BE'), poly([(148, 70), (143, 38), (118, 58)], '#FFB4BE'),
            ellipse(100, 112, 68, 60, '#F7B84B'),
            curve((100, 54), (100, 66), (100, 76), '#E08B1E', 6), curve((80, 58), (84, 68), (88, 76), '#E08B1E', 5), curve((120, 58), (116, 68), (112, 76), '#E08B1E', 5),
            ] + eyes(100, 26, 12, 7) + [
            poly([(92, 116), (108, 116), (100, 125)], '#FF7C93'),
            curve((100, 125), (100, 134), (88, 134), DARK, 3.5), curve((100, 125), (100, 134), (112, 134), DARK, 3.5),
            line((66, 118), (26, 108), '#8A5A2A', 3), line((66, 126), (26, 130), '#8A5A2A', 3),
            line((134, 118), (174, 108), '#8A5A2A', 3), line((134, 126), (174, 130), '#8A5A2A', 3)] + cheeks(124, 42, '#FFA5A5', 8)


def dog():
    return [circle(100, 100, 100, '#CFE8FF'),
            ellipse(36, 108, 19, 40, '#7A4A22'), ellipse(164, 108, 19, 40, '#7A4A22'),
            circle(100, 106, 64, '#C98B4B'),
            ellipse(100, 66, 26, 14, '#B27538'),
            ellipse(100, 132, 36, 30, '#F6E2BD')] + eyes(94, 26, 11, 6) + [
            ellipse(100, 116, 14, 9, DARK), circle(96, 113, 3, '#FFFFFF'),
            curve((100, 124), (100, 138), (84, 138), DARK, 3.5), curve((100, 124), (100, 138), (116, 138), DARK, 3.5),
            ellipse(100, 150, 10, 12, '#FF7C93'), line((100, 140), (100, 152), '#E0566F', 3)]


def bunny():
    return [circle(100, 100, 100, '#FFDDF0'),
            ellipse(70, 44, 17, 46, '#FFFFFF'), ellipse(130, 44, 17, 46, '#FFFFFF'),
            ellipse(70, 48, 8, 32, '#FFB1CB'), ellipse(130, 48, 8, 32, '#FFB1CB'),
            circle(100, 118, 58, '#FFFFFF')] + eyes(106, 24, 11, 6) + [
            poly([(92, 126), (108, 126), (100, 134)], '#FF7C93'),
            curve((100, 134), (100, 144), (88, 143), DARK, 3.5), curve((100, 134), (100, 144), (112, 143), DARK, 3.5),
            rrect(94, 144, 12, 12, 3, '#FFFFFF'), line((100, 144), (100, 156), '#DDD', 2)] + cheeks(132, 40, '#FFB1CB', 10)


def bear():
    return [circle(100, 100, 100, '#D9F2D0'),
            circle(48, 56, 24, '#8B5E3C'), circle(152, 56, 24, '#8B5E3C'),
            circle(48, 56, 12, '#D8AE7E'), circle(152, 56, 12, '#D8AE7E'),
            circle(100, 108, 64, '#A9764B'),
            ellipse(100, 130, 30, 24, '#EBCDA3')] + eyes(94, 26, 10, 6) + [
            ellipse(100, 118, 12, 8, '#3B2A1E'), circle(97, 115, 2.5, '#FFFFFF'),
            curve((100, 126), (100, 138), (88, 138), '#3B2A1E', 3.5), curve((100, 126), (100, 138), (112, 138), '#3B2A1E', 3.5)] + cheeks(122, 46, '#F4A28C', 8)


def panda():
    return [circle(100, 100, 100, '#E3F0FF'),
            circle(48, 54, 24, '#2B2B33'), circle(152, 54, 24, '#2B2B33'),
            circle(100, 110, 66, '#FFFFFF'),
            ellipse(72, 98, 18, 23, '#2B2B33'), ellipse(128, 98, 18, 23, '#2B2B33'),
            circle(74, 97, 8, '#FFFFFF'), circle(126, 97, 8, '#FFFFFF'),
            circle(75, 98, 5, DARK), circle(125, 98, 5, DARK), circle(73, 96, 1.8, '#FFFFFF'), circle(123, 96, 1.8, '#FFFFFF'),
            ellipse(100, 126, 12, 8, '#2B2B33'),
            curve((100, 134), (100, 146), (86, 144), '#2B2B33', 3.5), curve((100, 134), (100, 146), (114, 144), '#2B2B33', 3.5)] + cheeks(130, 44, '#FFB1B8', 8)


def fox():
    return [circle(100, 100, 100, '#FFE8D1'),
            poly([(34, 84), (46, 18), (96, 54)], '#E8742A'), poly([(166, 84), (154, 18), (104, 54)], '#E8742A'),
            poly([(48, 62), (50, 30), (74, 50)], '#3B2A22'), poly([(152, 62), (150, 30), (126, 50)], '#3B2A22'),
            ellipse(100, 104, 68, 56, '#F08A3A'),
            poly([(38, 108), (100, 172), (162, 108), (100, 132)], '#FFF4E6')] + eyes(94, 28, 10, 6) + [
            circle(100, 160, 9, DARK), circle(97, 157, 2.5, '#FFFFFF'),
            curve((100, 168), (100, 178), (88, 176), DARK, 3), curve((100, 168), (100, 178), (112, 176), DARK, 3)] + cheeks(128, 50, '#FFB48F', 7)


def penguin():
    return [circle(100, 100, 100, '#CFE8FF'),
            ellipse(36, 124, 14, 40, '#26324F'), ellipse(164, 124, 14, 40, '#26324F'),
            ellipse(100, 108, 62, 78, '#26324F'),
            ellipse(100, 124, 44, 56, '#FFFFFF'),
            ellipse(72, 88, 20, 24, '#FFFFFF'), ellipse(128, 88, 20, 24, '#FFFFFF')] + eyes(88, 24, 10, 6) + [
            poly([(84, 108), (116, 108), (100, 130)], '#FF9F1C'), poly([(88, 108), (112, 108), (100, 116)], '#FFB84D')] + cheeks(112, 44, '#FFB1B8', 8)


def owl():
    return [circle(100, 100, 100, '#F3E5CF'),
            poly([(46, 70), (40, 24), (84, 52)], '#7B4F29'), poly([(154, 70), (160, 24), (116, 52)], '#7B4F29'),
            ellipse(100, 112, 66, 72, '#A5713F'),
            ellipse(100, 140, 40, 44, '#F2D9B0'),
            circle(72, 92, 28, '#F2D9B0'), circle(128, 92, 28, '#F2D9B0'),
            circle(72, 92, 22, '#FFFFFF'), circle(128, 92, 22, '#FFFFFF'),
            circle(74, 93, 12, DARK), circle(126, 93, 12, DARK), circle(70, 88, 4, '#FFFFFF'), circle(122, 88, 4, '#FFFFFF'),
            poly([(90, 108), (110, 108), (100, 130)], '#FF9F1C')] + [
            curve((78, 148), (86, 158), (94, 148), '#C9A06A', 3), curve((106, 148), (114, 158), (122, 148), '#C9A06A', 3),
            curve((92, 164), (100, 174), (108, 164), '#C9A06A', 3)]


def frog():
    return [circle(100, 100, 100, '#E4F7C8'),
            circle(62, 66, 26, '#5CC15A'), circle(138, 66, 26, '#5CC15A'),
            ellipse(100, 120, 72, 56, '#6ED36A'),
            circle(62, 64, 18, '#FFFFFF'), circle(138, 64, 18, '#FFFFFF'),
            circle(64, 66, 9, DARK), circle(136, 66, 9, DARK), circle(61, 62, 3, '#FFFFFF'), circle(133, 62, 3, '#FFFFFF'),
            circle(92, 108, 3, '#3B8A3A'), circle(108, 108, 3, '#3B8A3A'),
            curve((52, 122), (100, 168), (148, 122), DARK, 5)] + cheeks(130, 54, '#FF9FB2', 10)


def monkey():
    return [circle(100, 100, 100, '#FFF0C9'),
            circle(34, 108, 22, '#7A4A22'), circle(166, 108, 22, '#7A4A22'),
            circle(34, 108, 12, '#F2CFA2'), circle(166, 108, 12, '#F2CFA2'),
            circle(100, 98, 62, '#8B5A2B'),
            circle(80, 98, 26, '#F6D8AE'), circle(120, 98, 26, '#F6D8AE'),
            ellipse(100, 128, 44, 36, '#F6D8AE')] + eyes(98, 20, 10, 6) + [
            circle(93, 120, 3, '#8B5A2B'), circle(107, 120, 3, '#8B5A2B'),
            curve((82, 134), (100, 156), (118, 134), DARK, 4)] + cheeks(124, 52, '#F4A28C', 7)


def lion():
    mane = [circle(100 + 70 * __import__('math').cos(i * 3.14159 / 6), 104 + 70 * __import__('math').sin(i * 3.14159 / 6), 26, '#B8651F') for i in range(12)]
    return [circle(100, 100, 100, '#FFF1B8')] + mane + [
            circle(100, 104, 70, '#C8742B'),
            circle(58, 62, 14, '#F5C15C'), circle(142, 62, 14, '#F5C15C'),
            circle(100, 108, 54, '#F5C15C'),
            ellipse(100, 128, 28, 22, '#FFE7A8')] + eyes(96, 24, 10, 6) + [
            poly([(91, 114), (109, 114), (100, 124)], '#7A3E12'),
            curve((100, 124), (100, 136), (88, 136), DARK, 3.5), curve((100, 124), (100, 136), (112, 136), DARK, 3.5)] + cheeks(120, 44, '#FFB48F', 7)


def pig():
    return [circle(100, 100, 100, '#FFE0E6'),
            poly([(44, 84), (40, 30), (92, 54)], '#FF8FAB'), poly([(156, 84), (160, 30), (108, 54)], '#FF8FAB'),
            circle(100, 108, 66, '#FFB3C7'),
            ellipse(100, 126, 32, 24, '#FF8FAB'),
            ellipse(90, 126, 6, 9, '#D9567B'), ellipse(110, 126, 6, 9, '#D9567B')] + eyes(94, 28, 10, 6) + [
            curve((84, 150), (100, 162), (116, 150), DARK, 3.5)] + cheeks(122, 56, '#FF7C93', 8)


def robot():
    return [circle(100, 100, 100, '#D8E6F2'),
            line((100, 46), (100, 28), '#7B8794', 6), circle(100, 24, 9, '#FF5C5C'), circle(97, 21, 3, '#FFFFFF'),
            circle(34, 104, 12, '#7B8794'), circle(166, 104, 12, '#7B8794'),
            rrect(40, 46, 120, 108, 26, '#B8C4D0'),
            rrect(52, 62, 96, 70, 18, '#34445A'),
            circle(78, 90, 13, '#7CFFB2'), circle(122, 90, 13, '#7CFFB2'), circle(78, 90, 6, '#1B6B45'), circle(122, 90, 6, '#1B6B45'),
            circle(74, 86, 3, '#FFFFFF'), circle(118, 86, 3, '#FFFFFF'),
            rrect(74, 112, 52, 10, 5, '#7CFFB2'),
            line((86, 112), (86, 122), '#34445A', 2), line((100, 112), (100, 122), '#34445A', 2), line((114, 112), (114, 122), '#34445A', 2),
            circle(52, 146, 4, '#8995A3'), circle(148, 146, 4, '#8995A3')]


def alien():
    return [circle(100, 100, 100, '#E1D9FF'),
            line((70, 44), (58, 20), '#4FAF3C', 4), circle(56, 17, 8, '#FFD43B'),
            line((130, 44), (142, 20), '#4FAF3C', 4), circle(144, 17, 8, '#FFD43B'),
            ellipse(100, 108, 62, 70, '#7ED957'),
            ellipse(72, 98, 20, 28, DARK), ellipse(128, 98, 20, 28, DARK),
            circle(66, 88, 5, '#FFFFFF'), circle(122, 88, 5, '#FFFFFF'), circle(76, 106, 2.5, '#FFFFFF'), circle(132, 106, 2.5, '#FFFFFF'),
            circle(94, 128, 2.5, '#3F9A2E'), circle(106, 128, 2.5, '#3F9A2E'),
            curve((86, 146), (100, 160), (114, 146), '#3F9A2E', 4)] + cheeks(134, 46, '#B7F08F', 7)


def dino():
    spikes = [poly([(x - 10, 56), (x, 30), (x + 10, 56)], '#F2A93B') for x in (60, 84, 108, 132)]
    return [circle(100, 100, 100, '#D8F5E2')] + spikes + [
            ellipse(100, 112, 66, 56, '#5DBB63'),
            ellipse(100, 138, 50, 30, '#8FD88F'),
            circle(88, 124, 3.5, '#3B8A3A'), circle(112, 124, 3.5, '#3B8A3A')] + eyes(94, 28, 11, 6) + [
            curve((72, 144), (100, 164), (128, 144), DARK, 4),
            poly([(84, 150), (90, 150), (87, 158)], '#FFFFFF'), poly([(110, 150), (116, 150), (113, 158)], '#FFFFFF'),
            circle(56, 112, 7, '#4AA550'), circle(144, 112, 7, '#4AA550'), circle(64, 78, 6, '#4AA550'), circle(136, 78, 6, '#4AA550')]


def unicorn():
    rainbow = ['#FF6B6B', '#FFA94D', '#FFE066', '#69DB7C', '#4DABF7', '#9775FA']
    mane = [circle(40 + (i % 2) * 8, 66 + i * 17, 17, c) for i, c in enumerate(rainbow)]
    return [circle(100, 100, 100, '#F3E1FF')] + mane + [
            poly([(58, 70), (64, 30), (90, 56)], '#FFFFFF'), poly([(142, 70), (136, 30), (110, 56)], '#FFFFFF'),
            poly([(64, 62), (66, 42), (80, 56)], '#FFB1CB'), poly([(136, 62), (134, 42), (120, 56)], '#FFB1CB'),
            ellipse(104, 118, 52, 58, '#FFFFFF'),
            poly([(92, 62), (100, 10), (108, 62)], '#FFD43B'),
            line((94, 50), (106, 46), '#F0A500', 3), line((96, 36), (104, 32), '#F0A500', 3),
            circle(80, 98, 11, DARK), circle(128, 98, 11, DARK), circle(77, 94, 3.5, '#FFFFFF'), circle(125, 94, 3.5, '#FFFFFF'),
            line((70, 90), (64, 84), DARK, 3), line((138, 90), (144, 84), DARK, 3),
            ellipse(104, 142, 24, 17, '#FFD6E5'), circle(95, 142, 3, '#E8789C'), circle(113, 142, 3, '#E8789C'),
            curve((92, 154), (104, 162), (116, 154), DARK, 3)] + cheeks(124, 38, '#FFB1CB', 8)


AVATARS = [('cat', 'Cat', cat), ('dog', 'Puppy', dog), ('bunny', 'Bunny', bunny), ('bear', 'Bear', bear), ('panda', 'Panda', panda),
           ('fox', 'Fox', fox), ('penguin', 'Penguin', penguin), ('owl', 'Owl', owl), ('frog', 'Frog', frog), ('monkey', 'Monkey', monkey),
           ('lion', 'Lion', lion), ('pig', 'Piggy', pig), ('robot', 'Robot', robot), ('alien', 'Alien', alien), ('dino', 'Dino', dino),
           ('unicorn', 'Unicorn', unicorn)]


# ---------------------------------------------------------------- output

def f(v): return ('%.1f' % v).rstrip('0').rstrip('.')


def path_of(p):
    k = p[0]
    if k == 'ellipse':
        _, cx, cy, rx, ry, c = p
        d = 'M%s,%sa%s,%s 0 1,0 %s,0a%s,%s 0 1,0 %s,0Z' % (f(cx - rx), f(cy), f(rx), f(ry), f(2 * rx), f(rx), f(ry), f(-2 * rx))
        return '    <path android:fillColor="%s" android:pathData="%s"/>' % (c, d)
    if k == 'poly':
        _, pts, c = p
        d = 'M' + 'L'.join('%s,%s' % (f(x), f(y)) for x, y in pts) + 'Z'
        return '    <path android:fillColor="%s" android:pathData="%s"/>' % (c, d)
    if k == 'rrect':
        _, x, y, w, h, r, c = p
        d = ('M%s,%sH%sQ%s,%s %s,%sV%sQ%s,%s %s,%sH%sQ%s,%s %s,%sV%sQ%s,%s %s,%sZ' %
             (f(x + r), f(y), f(x + w - r), f(x + w), f(y), f(x + w), f(y + r), f(y + h - r), f(x + w), f(y + h), f(x + w - r), f(y + h),
              f(x + r), f(x), f(y + h), f(x), f(y + h - r), f(y + r), f(x), f(y), f(x + r), f(y)))
        return '    <path android:fillColor="%s" android:pathData="%s"/>' % (c, d)
    _, p0, c1, p1, col, w = p
    d = 'M%s,%sQ%s,%s %s,%s' % (f(p0[0]), f(p0[1]), f(c1[0]), f(c1[1]), f(p1[0]), f(p1[1]))
    return '    <path android:strokeColor="%s" android:strokeWidth="%s" android:strokeLineCap="round" android:pathData="%s"/>' % (col, f(w), d)


def write_all():
    for key, _, fn in AVATARS:
        lines = ['<?xml version="1.0" encoding="utf-8"?>',
                 '<!-- Generated by tools/gen_avatars.py - edit the drawing there, not this file. -->',
                 '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
                 '    android:width="200dp" android:height="200dp" android:viewportWidth="200" android:viewportHeight="200">']
        lines += [path_of(p) for p in fn()]
        lines.append('</vector>')
        with open(os.path.join(OUT, 'av_%s.xml' % key), 'w', encoding='utf-8', newline='\n') as fh:
            fh.write('\n'.join(lines) + '\n')


def preview(png):
    from PIL import Image, ImageDraw
    S = 4
    cell = 200 * S
    cols = 4
    rows = (len(AVATARS) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * 220 * S // 2, rows * 220 * S // 2), 'white')
    for i, (key, _, fn) in enumerate(AVATARS):
        img = Image.new('RGBA', (cell, cell), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        for p in fn():
            k = p[0]
            col = p[-1] if k != 'curve' else p[4]
            if k == 'ellipse':
                _, cx, cy, rx, ry, c = p
                d.ellipse([(cx - rx) * S, (cy - ry) * S, (cx + rx) * S, (cy + ry) * S], fill=c)
            elif k == 'poly':
                d.polygon([(x * S, y * S) for x, y in p[1]], fill=p[2])
            elif k == 'rrect':
                _, x, y, w, h, r, c = p
                d.rounded_rectangle([x * S, y * S, (x + w) * S, (y + h) * S], radius=r * S, fill=c)
            else:
                _, p0, c1, p1, c, w = p
                pts = []
                for t in range(0, 21):
                    u = t / 20
                    pts.append((((1 - u) ** 2 * p0[0] + 2 * (1 - u) * u * c1[0] + u * u * p1[0]) * S, ((1 - u) ** 2 * p0[1] + 2 * (1 - u) * u * c1[1] + u * u * p1[1]) * S))
                d.line(pts, fill=c, width=int(w * S), joint='curve')
                for q in (pts[0], pts[-1]):
                    d.ellipse([q[0] - w * S / 2, q[1] - w * S / 2, q[0] + w * S / 2, q[1] + w * S / 2], fill=c)
        mask = Image.new('L', (cell, cell), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, cell, cell], fill=255)
        img.putalpha(Image.composite(img.getchannel('A'), Image.new('L', (cell, cell), 0), mask))
        small = img.resize((cell // 2, cell // 2))
        sheet.paste(small, ((i % cols) * 220 * S // 2 + 4 * S, (i // cols) * 220 * S // 2 + 4 * S), small)
    sheet.resize((sheet.width // 2, sheet.height // 2)).save(png)


if __name__ == '__main__':
    write_all()
    if '--preview' in sys.argv:
        preview(sys.argv[sys.argv.index('--preview') + 1])
    print('ok', len(AVATARS))
