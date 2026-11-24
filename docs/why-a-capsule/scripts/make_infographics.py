"""Six infographics for the page, light and dark, written as plain SVG.
Every number is typed here from the simulation-capsule repository at 15e95b8:
capsule_ledger.py on examples/capsule_cylinder_re0100, SPEC 3.5, and the probe
records 7a, 7b, 7c, 8a and 8b. Run: python3 scripts/make_infographics.py"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = "Inter, -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif"
MONO = "'DejaVu Sans Mono', ui-monospace, Menlo, Consolas, monospace"
THEMES = {
    "light": dict(bg="#fcfcfb", panel="#f1f0ec", line="#d6d4cd", text="#0b0b0b", muted="#52514e",
                  mark="#2a78d6", soft="#dbe8f9", ref="#8a8983"),
    "dark": dict(bg="#1a1a19", panel="#262624", line="#3f3f3b", text="#f0efec", muted="#c3c2b7",
                 mark="#3987e5", soft="#1f3350", ref="#8f8e86"),
}


def t(x, y, s, size=13, fill="{text}", w=400, anchor="start", font=FONT):
    return ('<text x="%s" y="%s" font-family="%s" font-size="%s" font-weight="%s" fill="%s" '
            'text-anchor="%s">%s</text>' % (x, y, font, size, w, fill, anchor, s))


def chain(c):
    o = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 980 430" role="img" '
         'aria-label="From a solved case to a capsule to the context window, cylinder at Re 100">',
         '<rect width="980" height="430" fill="{bg}"/>']
    # 1 solved case
    o.append('<rect x="20" y="40" width="215" height="330" rx="10" fill="{panel}" stroke="{line}"/>')
    o.append(t(127, 68, "SOLVED CASE", 11, "{muted}", 700, "middle"))
    o.append(t(127, 90, "cylinder at Re 100", 13, "{muted}", 400, "middle"))
    o.append(t(127, 138, "179,040", 30, "{text}", 700, "middle"))
    o.append(t(127, 158, "cells", 13, "{muted}", 400, "middle"))
    o.append(t(127, 206, "15,000", 30, "{text}", 700, "middle"))
    o.append(t(127, 226, "time steps", 13, "{muted}", 400, "middle"))
    gx, gy = 47, 252
    for i in range(7):
        o.append('<line x1="%d" y1="%d" x2="%d" y2="%d" stroke="{line}"/>' % (gx + i * 27, gy, gx + i * 27, gy + 54))
    for j in range(3):
        o.append('<line x1="%d" y1="%d" x2="%d" y2="%d" stroke="{line}"/>' % (gx, gy + j * 27, gx + 162, gy + j * 27))
    o.append('<path d="M47,279 C74,246 101,312 128,279 S182,246 209,279" fill="none" stroke="{mark}" stroke-width="2"/>')
    o.append(t(127, 336, "fields, mesh, scenes,", 12, "{muted}", 400, "middle"))
    o.append(t(127, 353, "solver history", 12, "{muted}", 400, "middle"))
    # 2 extraction
    o.append('<polygon points="247,52 407,110 407,300 247,358" fill="{soft}" stroke="{mark}" stroke-dasharray="5 4"/>')
    o.append(t(327, 150, "EXTRACTION", 11, "{text}", 700, "middle"))
    for k, s in enumerate(["one artifact, one question", "nondimensional by default",
                           "token budget stated", "sealed to a commit"]):
        o.append(t(327, 178 + k * 22, s, 12, "{text}", 400, "middle"))
    o.append(t(327, 278, "extraction card + chain", 11, "{muted}", 400, "middle"))
    # 3 capsule
    o.append('<rect x="419" y="40" width="320" height="330" rx="10" fill="{bg}" stroke="{mark}" stroke-width="2"/>')
    o.append(t(435, 68, "capsule_cylinder_re0100/", 13, "{text}", 700, "start", MONO))
    o.append(t(723, 68, "tokens", 11, "{muted}", 400, "end"))
    rows = [("setup.txt", "the case setup", "7,056"),
            ("summary.json", "global scalars", "960"),
            ("planes/", "sections, CSV + image", "13,705"),
            ("views/", "one question each", "1,200"),
            ("signals/", "one cycle + spectrum", "10,532"),
            ("manifest.json", "build record", "1,796")]
    for k, (name, gloss, tok) in enumerate(rows):
        y = 84 + k * 40
        o.append('<rect x="433" y="%d" width="292" height="32" rx="6" fill="{panel}"/>' % y)
        o.append(t(443, y + 21, name, 12, "{text}", 700, "start", MONO))
        o.append(t(556, y + 21, gloss, 11, "{muted}"))
        o.append(t(717, y + 21, tok, 12, "{text}", 700, "end"))
    o.append('<line x1="433" y1="328" x2="725" y2="328" stroke="{line}"/>')
    o.append(t(443, 352, "whole capsule", 12, "{muted}"))
    o.append(t(717, 352, "35,249", 15, "{text}", 700, "end"))
    # 4 window
    o.append('<rect x="751" y="40" width="209" height="330" rx="10" fill="{panel}" stroke="{line}"/>')
    o.append(t(855, 68, "CONTEXT WINDOW", 11, "{muted}", 700, "middle"))
    o.append(t(855, 86, "200,000 tokens", 12, "{muted}", 400, "middle"))
    bx, by, bw, bh = 771, 102, 46, 250
    cap = bh * 35249 / 200000.0
    bud = bh * 60000 / 200000.0
    o.append('<rect x="%d" y="%d" width="%d" height="%d" rx="4" fill="{bg}" stroke="{line}"/>' % (bx, by, bw, bh))
    o.append('<rect x="%d" y="%d" width="%d" height="%.1f" rx="4" fill="{mark}"/>' % (bx, by, bw, cap))
    o.append('<line x1="%d" y1="%.1f" x2="%d" y2="%.1f" stroke="{text}" stroke-dasharray="4 3"/>' % (bx - 6, by + bud, bx + bw + 6, by + bud))
    o.append(t(831, by + 20, "this capsule", 12, "{text}", 700))
    o.append(t(831, by + 37, "35,249, 18%", 12, "{muted}"))
    o.append(t(831, by + bud + 4, "design budget", 12, "{text}", 700))
    o.append(t(831, by + bud + 21, "60,000, 30%", 12, "{muted}"))
    o.append(t(831, by + 172, "left for the", 12, "{text}", 700))
    o.append(t(831, by + 189, "conversation", 12, "{text}", 700))
    o.append(t(831, by + 206, "164,751", 12, "{muted}"))
    # arrows and footer
    o.append('<path d="M739,205 L749,205" stroke="{mark}" stroke-width="2"/>')
    o.append(t(490, 405, "A Simulation Capsule is to a CFD case what llms.txt is to a website.", 13, "{muted}", 400, "middle"))
    o.append("</svg>")
    return "\n".join(o).format(**c)


def score(c):
    rows = [("7a", "Predict a capsule before the run starts", (12, 13), (12, 13), None),
            ("7b", "Side force signs at an instant no file stores", (8, 8), (6, 8), (3, 8)),
            ("7c", "Stagnation value behind a misleading label", (4, 4), (3, 4), (0, 3)),
            ("8a", "Six facts about a history never seen", (6, 6), (5, 6), None),
            ("8b", "Six questions on phase and spectrum", (6, 6), (4, 6), (0, 6)),
            ("8b", "Hand back 36 samples of that history", (36, 36), (34, 36), (0, 36))]
    x0, x1 = 380, 760
    o = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 980 400" role="img" '
         'aria-label="Probe results: two readers and the naive reading, share of answers right">',
         '<rect width="980" height="400" fill="{bg}"/>']
    o.append(t(20, 30, "Graded probes: share of answers right", 14, "{text}", 700))
    o.append('<circle cx="392" cy="26" r="6" fill="{mark}"/>')
    o.append(t(404, 30, "a reader with the capsule", 12, "{muted}"))
    o.append('<rect x="585" y="20" width="11" height="11" fill="none" stroke="{ref}" stroke-width="2" transform="rotate(45 590.5 25.5)"/>')
    o.append(t(606, 30, "naive reading of the same capsule", 12, "{muted}"))
    for f, lab in ((0, "0"), (0.5, "half"), (1, "all")):
        x = x0 + f * (x1 - x0)
        o.append('<line x1="%.1f" y1="52" x2="%.1f" y2="352" stroke="{line}"/>' % (x, x))
        o.append(t(x, 372, lab, 11, "{muted}", 400, "middle"))
    for k, (part, what, a, b, n) in enumerate(rows):
        y = 78 + k * 50
        o.append(t(20, y + 4, part, 12, "{text}", 700, "start", MONO))
        o.append(t(52, y + 4, what, 12, "{text}"))
        o.append('<line x1="%d" y1="%d" x2="%d" y2="%d" stroke="{line}" stroke-width="3" stroke-linecap="round"/>' % (x0, y, x1, y))
        if n is not None:
            xn = x0 + (x1 - x0) * n[0] / float(n[1])
            o.append('<rect x="%.1f" y="%.1f" width="11" height="11" fill="{bg}" stroke="{ref}" stroke-width="2" transform="rotate(45 %.1f %.1f)"/>' % (xn - 5.5, y - 5.5, xn, y))
        for r, dy in ((a, -7), (b, 7)):
            xr = x0 + (x1 - x0) * r[0] / float(r[1])
            o.append('<circle cx="%.1f" cy="%d" r="6.5" fill="{mark}" stroke="{bg}" stroke-width="2"/>' % (xr, y + dy))
        o.append(t(785, y - 3, "%d and %d of %d" % (a[0], b[0], a[1]), 12, "{text}", 700))
        o.append(t(785, y + 13, "naive: %d of %d" % n if n else "naive: not scored", 11, "{muted}"))
    o.append("</svg>")
    return "\n".join(o).format(**c)


def box(x, y, w, h, fill="{panel}", stroke="{line}", sw=1, rx=10):
    return '<rect x="%s" y="%s" width="%s" height="%s" rx="%s" fill="%s" stroke="%s" stroke-width="%s"/>' % (x, y, w, h, rx, fill, stroke, sw)


def arrow(x1, y, x2, col="{muted}"):
    return ('<line x1="%s" y1="%s" x2="%s" y2="%s" stroke="%s" stroke-width="2"/>'
            '<polygon points="%s,%s %s,%s %s,%s" fill="%s"/>' % (x1, y, x2 - 7, y, col, x2, y, x2 - 9, y - 5, x2 - 9, y + 5, col))


def halves(c):
    o = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 980 290" role="img" '
         'aria-label="The two halves of AI-assisted CFD: writing the simulation and reading the result">',
         '<rect width="980" height="290" fill="{bg}"/>']
    o.append(t(20, 34, "THE HALF THAT WRITES", 11, "{muted}", 700))
    o.append(t(20, 52, "starccm-javadoc-mcp", 12, "{muted}", 400, "start", MONO))
    o.append('<line x1="20" y1="62" x2="440" y2="62" stroke="{line}" stroke-width="3"/>')
    o.append(t(960, 34, "THE HALF THAT READS", 11, "{text}", 700, "end"))
    o.append(t(960, 52, "simulation-capsule", 12, "{text}", 700, "end", MONO))
    o.append('<line x1="520" y1="62" x2="960" y2="62" stroke="{mark}" stroke-width="3"/>')
    names = [(20, "Language model", "{panel}", "{line}", 1), (280, "Solver", "{panel}", "{line}", 1),
             (540, "Solved case", "{panel}", "{line}", 1), (800, "Simulation Capsule", "{soft}", "{mark}", 2)]
    for x, n, f, st, sw in names:
        o.append(box(x, 96, 160, 64, f, st, sw))
        o.append(t(x + 80, 133, n, 14, "{text}", 700, "middle"))
    for x1, lab, col in ((180, "writes macros", "{muted}"), (440, "solves", "{muted}"), (700, "extraction", "{mark}")):
        o.append(arrow(x1 + 8, 128, x1 + 94, col))
        o.append(t(x1 + 50, 116, lab, 11, "{muted}", 400, "middle"))
    o.append('<path d="M880,160 L880,226 L100,226 L100,170" fill="none" stroke="{mark}" stroke-width="2"/>')
    o.append('<polygon points="100,161 95,171 105,171" fill="{mark}"/>')
    o.append('<rect x="372" y="212" width="236" height="28" rx="14" fill="{bg}" stroke="{mark}"/>')
    o.append(t(490, 231, "reads, audits, questions", 13, "{text}", 700, "middle"))
    o.append(t(490, 272, "Without the lower path, the model writes a simulation and never sees what came out.", 12, "{muted}", 400, "middle"))
    o.append("</svg>")
    return "\n".join(o).format(**c)


def ways(c):
    o = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 980 250" role="img" '
         'aria-label="Three ways to hand a solved case to a model: raw export, screenshot, capsule">',
         '<rect width="980" height="250" fill="{bg}"/>']
    cards = [("RAW EXPORT", "grows with the mesh", ["Spends the budget on structure", "the model cannot use."], False),
             ("SCREENSHOT", "keeps no numbers", ["Leaves the model guessing", "magnitudes from pixel colors."], False),
             ("CAPSULE", "small files, one question each", ["Sized to fit the window, with", "room left for the conversation."], True)]
    for k, (head, big, lines, hi) in enumerate(cards):
        x = 20 + k * 320
        o.append(box(x, 20, 300, 200, "{soft}" if hi else "{panel}", "{mark}" if hi else "{line}", 2 if hi else 1))
        o.append(t(x + 22, 52, head, 11, "{text}" if hi else "{muted}", 700))
        gx, gy = x + 22, 70
        if k == 0:
            for i in range(9):
                o.append('<line x1="%d" y1="%d" x2="%d" y2="%d" stroke="{ref}"/>' % (gx + i * 8, gy, gx + i * 8, gy + 40))
            for j in range(6):
                o.append('<line x1="%d" y1="%d" x2="%d" y2="%d" stroke="{ref}"/>' % (gx, gy + j * 8, gx + 64, gy + j * 8))
        elif k == 1:
            o.append('<rect x="%d" y="%d" width="64" height="40" rx="3" fill="none" stroke="{ref}" stroke-width="2"/>' % (gx, gy))
            o.append('<path d="M%d,%d l16,-18 l12,12 l10,-8 l18,14" fill="none" stroke="{ref}" stroke-width="2"/>' % (gx + 4, gy + 34))
        else:
            for j in range(3):
                o.append('<rect x="%d" y="%d" width="64" height="10" rx="3" fill="{mark}"/>' % (gx, gy + j * 15))
        o.append(t(x + 22, 146, big, 17, "{text}", 700))
        for j, l in enumerate(lines):
            o.append(t(x + 22, 172 + j * 18, l, 12, "{muted}"))
    o.append("</svg>")
    return "\n".join(o).format(**c)


def cards(c):
    o = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 980 240" role="img" '
         'aria-label="Four probe results: read, audit, question, refuse">',
         '<rect width="980" height="240" fill="{bg}"/>']
    data = [("READ", "36 of 36", ["samples of a time history", "the capsule does not store,", "rebuilt within 1% of the swing"], "probe 8b"),
            ("AUDIT", "1.068", ["the stagnation value, found", "behind a label that reads", "2.394"], "probe 7c"),
            ("QUESTION", "5,000", ["iterations, predicted before", "the run started; interpolation", "gives 9,243"], "probe 7a"),
            ("DECLINE", "4 of 4", ["questions the files cannot", "answer, refused by both", "readers"], "probe 6b")]
    for k, (head, big, lines, src) in enumerate(data):
        x = 20 + k * 240
        o.append(box(x, 16, 220, 208))
        o.append('<rect x="%d" y="16" width="220" height="6" rx="3" fill="{mark}"/>' % x)
        o.append(t(x + 18, 52, head, 11, "{muted}", 700))
        o.append(t(x + 18, 100, big, 34, "{text}", 700))
        for j, l in enumerate(lines):
            o.append(t(x + 18, 132 + j * 18, l, 12, "{text}"))
        o.append(t(x + 18, 206, src, 11, "{muted}", 400, "start", MONO))
    o.append("</svg>")
    return "\n".join(o).format(**c)


def strip(c):
    o = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 980 150" role="img" '
         'aria-label="How a graded probe is run, in five steps">',
         '<rect width="980" height="150" fill="{bg}"/>']
    steps = [("Freeze the kit", ["sha256 of each file,", "before any reader runs"]),
             ("Fresh chat", ["the capsule attached", "whole, no other context"]),
             ("Answer", ["a value and an interval;", "expected answers unseen"]),
             ("Grade", ["against the files or what", "the solver recorded"]),
             ("Audit", ["what traces to the files,", "what to training"])]
    for k, (head, lines) in enumerate(steps):
        x = 20 + k * 192
        o.append(box(x, 20, 172, 110))
        o.append('<circle cx="%d" cy="46" r="12" fill="{mark}"/>' % (x + 26))
        o.append(t(x + 26, 51, str(k + 1), 13, "#ffffff", 700, "middle"))
        o.append(t(x + 46, 51, head, 13, "{text}", 700))
        for j, l in enumerate(lines):
            o.append(t(x + 14, 84 + j * 18, l, 12, "{muted}"))
        if k < 4:
            o.append(arrow(x + 174, 75, x + 191, "{ref}"))
    o.append("</svg>")
    return "\n".join(o).format(**c)


for name, c in THEMES.items():
    for stem, fn in (("chain", chain), ("probes", score), ("halves", halves), ("ways", ways), ("cards", cards), ("strip", strip)):
        with open(os.path.join(ROOT, "figures", "%s_%s.svg" % (stem, name)), "w", encoding="utf-8") as f:
            f.write(fn(c))
