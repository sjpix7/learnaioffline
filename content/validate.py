"""Validate generated assets: structure, counts, uniqueness, answer validity, lesson references."""
import json, random, sys
from pathlib import Path
from collections import Counter

A = Path(__file__).parent.parent / "android" / "app" / "src" / "main" / "assets" / "content"
topics = json.loads((A / "topics.json").read_text())
problems, total = [], 0
for t in topics:
    d = json.loads((A / "questions" / f"{t['id']}.json").read_text())
    lessons = json.loads((A / "lessons" / f"{t['id']}.json").read_text())
    qs, snips = d["q"], d["s"]
    total += len(qs)
    if len(qs) != 3300: problems.append(f"{t['id']}: {len(qs)} questions")
    keys = set()
    for q in qs:
        k = (q["q"], snips[q["c"]] if "c" in q else "", tuple(sorted(q["o"])))
        if k in keys: problems.append(f"{t['id']} dup #{q['n']}")
        keys.add(k)
        if len(q["o"]) != 4 or len(set(q["o"])) != 4: problems.append(f"{t['id']} #{q['n']} bad options")
        if not (0 <= q["a"] < 4): problems.append(f"{t['id']} #{q['n']} bad answer")
        if not (0 <= q["l"] < len(lessons)): problems.append(f"{t['id']} #{q['n']} bad lesson {q['l']}")
        for f in ("c", "hc", "ec"):
            if f in q and not (0 <= q[f] < len(snips)): problems.append(f"{t['id']} #{q['n']} bad snippet")
        if any(len(o) > 400 for o in q["o"]): problems.append(f"{t['id']} #{q['n']} long option")
        if not q["q"].strip() or not q["e"].strip(): problems.append(f"{t['id']} #{q['n']} empty text")
    ans = Counter(q["a"] for q in qs)
    print(f"{t['id']:18} q={len(qs)} lessons={len(lessons)} answer-dist={dict(sorted(ans.items()))} per-lesson={[l['q'] for l in t['lessons']]}")
print("TOTAL", total, "problems", len(problems))
for p in problems[:20]: print(" -", p)
if "-s" in sys.argv:
    rng = random.Random(int(sys.argv[-1]) if sys.argv[-1].isdigit() else 0)
    for t in topics:
        d = json.loads((A / "questions" / f"{t['id']}.json").read_text())
        q = rng.choice(d["q"])
        print(f"\n=== {t['id']} #{q['n']} [{q['k']}] L{q['l']+1}\n{q['q']}")
        if "c" in q: print("```\n" + d["s"][q["c"]][:400] + "\n```")
        for i, o in enumerate(q["o"]): print((" ✔ " if i == q["a"] else "   ") + o[:160])
        print("  hint:", q["h"][:160]); print("  expl:", q["e"][:220])
