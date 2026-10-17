"""The research feature's item textures (art style §5): the Commonplace Book (Stage 1) and the Choir
page (Stage 3). Usage: python3 research_items.py <repo root>. Deterministic: no randomness at all.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib import canvas, outline, put, save  # noqa: E402


def commonplace_book():
    """An upright leather book: copper cover lit from the upper left, a darker spine, a cream page
    edge on the right, a brass clasp and an engraved resonance mark; the teal tick outside, top right."""
    img = canvas()
    for y in range(2, 14):
        put(img, 3, y, "copper1")  # spine, the shadowed fold
        for x in range(4, 11):
            lit = x == 4 or y == 2
            put(img, x, y, "copper3" if lit else "copper2")
        put(img, 11, y, "brass5" if y % 2 else "brass4")  # page edge
    for x in range(4, 11):
        put(img, x, 13, "copper1")  # bottom board edge
    put(img, 10, 2, "copper2")
    # brass corners and clasp
    for x, y in ((4, 2), (4, 13), (10, 7), (11, 7), (10, 8), (11, 8)):
        put(img, x, y, "brass3")
    put(img, 10, 7, "brass4")  # one specular tick on the clasp
    # engraved mark on the cover: a small upright fork, in ink grooves
    for y in range(5, 10):
        put(img, 6, y, "ink2")
        put(img, 8, y, "ink2")
    put(img, 7, 10, "ink2")
    put(img, 7, 11, "ink2")
    outline(img)
    # teal tick: a little wavefront off the top-right corner, outside the outline
    for x, y in ((13, 1), (14, 2), (14, 3)):
        put(img, x, y, "teal2")
    return img


def choir_page():
    """A torn sheet of old paper: cream with mauve creeping into the shadowed edges, script lines in
    ink, and a dim teal groove (Stage 3: teal1, in grooves, no outside tick)."""
    img = canvas()
    for y in range(2, 14):
        for x in range(4, 12):
            if x + y >= 23:  # the torn lower-right corner
                continue
            shade = "mauve2" if x == 11 or y == 13 else ("brass5" if x < 8 else "brass4")
            put(img, x, y, shade)
    put(img, 4, 2, "brass5")
    for y in (4, 6, 8, 10):  # script, broken into words
        for x in range(5, 11):
            if (x + y) % 4 != 0 and x + y < 21:
                put(img, x, y, "ink2")
    for x in range(5, 9):  # the resonance groove, a ruled line under the script
        put(img, x, 12, "teal1")
    put(img, 11, 3, "mauve1")  # the curl at the top-right corner
    outline(img)
    return img


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    out = os.path.join(root, "src/main/resources/assets/vftv/textures/item")
    save(commonplace_book(), os.path.join(out, "commonplace_book.png"))
    save(choir_page(), os.path.join(out, "choir_page.png"))
    print("research items ok")
