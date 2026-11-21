"""Question bank engine: parses fact files, generates MCQs from templates, merges
verified computed questions from per-topic generators, and exports JSON assets."""
import json, random, re, importlib, sys, itertools
from pathlib import Path

ROOT = Path(__file__).parent
FACT_DIR = ROOT / "facts"
TARGET = 3300
MASK = "▢▢▢"

# ----------------------------------------------------------------- parsing

def unescape(s):
    return s.replace("\\n", "\n").replace("\\t", "    ")


def parse_facts(path):
    facts, cats, seqs, hand = [], [], [], []
    cur = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.rstrip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("@ "):
            term, lesson = line[2:].rsplit(" | ", 1)
            cur = {"term": term.strip(), "lesson": int(lesson), "D": "", "U": [], "T": [], "F": [], "C": "", "B": []}
            facts.append(cur)
        elif line.startswith("% "):
            parts = [p.strip() for p in line[2:].split(" | ")]
            label, lesson, members = parts[0], int(parts[1]), [m.strip() for m in parts[2].split(";") if m.strip()]
            contrast = [c.strip() for c in parts[3].split(",")] if len(parts) > 3 else []
            cats.append({"label": label, "lesson": lesson, "members": members, "contrast": contrast})
            cur = None
        elif line.startswith("> "):
            parts = [p.strip() for p in line[2:].split(" | ")]
            seqs.append({"title": parts[0], "lesson": int(parts[1]), "steps": [s.strip() for s in parts[2].split(" ; ")]})
            cur = None
        elif line.startswith("Q: "):
            text, lesson = line[3:].rsplit(" | ", 1)
            cur = {"hq": unescape(text.strip()), "lesson": int(lesson), "O": "", "X": [], "E": "", "C": "", "H": ""}
            hand.append(cur)
        elif len(line) > 2 and line[1] == ":" and cur is not None:
            key, val = line[0], line[3:].strip() if line[2:3] == " " else line[2:].strip()
            if "hq" in cur:
                if key == "O": cur["O"] = val
                elif key == "X": cur["X"].append(val)
                elif key == "E": cur["E"] = val
                elif key == "C": cur["C"] = unescape(val)
                elif key == "H": cur["H"] = val
            else:
                if key == "D": cur["D"] = val
                elif key == "U": cur["U"].append(val)
                elif key == "T": cur["T"].append(val)
                elif key == "F": cur["F"].append(val)
                elif key == "C": cur["C"] = unescape(val)
                elif key == "B":
                    bits = [b.strip() for b in val.split(" ;; ")]
                    cur["B"].append({"code": unescape(bits[0]), "ans": bits[1], "dis": bits[2:5]})
        else:
            raise ValueError(f"{path.name}: cannot parse line: {line[:80]}")
    return facts, cats, seqs, hand

# ----------------------------------------------------------------- helpers

def mask(text, term):
    if not text:
        return text
    variants = {term}
    base = re.sub(r"\s*\(.*?\)\s*$", "", term).strip()
    if base and len(base) > 2:
        variants.add(base)
    out = text
    for v in sorted(variants, key=len, reverse=True):
        out = re.sub(re.escape(v), MASK, out, flags=re.I)
    return out


def make_q(rng, stem, correct, wrong, lesson, kind, hint="", hint_code="", expl="", expl_code="", code="", diff=1, src=""):
    opts = [correct] + list(wrong)
    if len(set(o.strip().lower() for o in opts)) != len(opts) or len(opts) != 4:
        return None
    if any(not o.strip() for o in opts):
        return None
    order = list(range(4)); rng.shuffle(order)
    shuffled = [opts[i] for i in order]
    return {"q": stem, "c": code, "o": shuffled, "a": order.index(0), "h": hint, "hc": hint_code,
            "e": expl, "ec": expl_code, "l": lesson - 1, "k": kind, "d": diff, "src": src}


def qkey(q):
    return (q["q"].strip(), q.get("c", "").strip(), tuple(sorted(o.strip() for o in q["o"])))

# ----------------------------------------------------------------- templates

class FactTemplates:
    def __init__(self, topic_title, lesson_titles, facts, cats, seqs, hand, rng):
        self.tt = topic_title
        self.lt = lesson_titles
        self.facts = facts
        self.cats = cats
        self.seqs = seqs
        self.hand = hand
        self.rng = rng
        self.by_lesson = {}
        for f in facts:
            self.by_lesson.setdefault(f["lesson"], []).append(f)

    def lref(self, lesson):
        return f"Revisit Lesson {lesson}: {self.lt[lesson - 1]}."

    def others(self, fact, need, attr, k=3, near=True):
        """Pick k other facts (with the needed attribute) preferring the same lesson."""
        same = [f for f in self.by_lesson.get(fact["lesson"], []) if f is not fact and need(f)]
        rest = [f for f in self.facts if f is not fact and need(f) and f not in same]
        self.rng.shuffle(same); self.rng.shuffle(rest)
        pool = (same + rest) if near else (rest + same)
        chosen, seen = [], {str(fact[attr]).lower()}
        for f in pool:
            val = str(f[attr]).lower()
            if val in seen:
                continue
            chosen.append(f); seen.add(val)
            if len(chosen) == k:
                break
        return chosen if len(chosen) == k else None

    def gen(self, variants):
        out = {}  # tier -> list
        def add(tier, q):
            if q: out.setdefault(tier, []).append(q)
        rng = self.rng
        for f in self.facts:
            L = f["lesson"]
            term, D = f["term"], f["D"]
            code = f["C"]
            for v in range(variants):
                near = v % 2 == 0
                # 1. definition -> term
                o = self.others(f, lambda x: True, "term", near=near)
                if o:
                    add(v, make_q(rng, f"Which {self.tt} concept matches this description?\n\n“{mask(D, term)}”",
                                  term, [x["term"] for x in o], L, "concept",
                                  hint=self.lref(L) + (" Look at the masked name in the sample code." if code else ""),
                                  hint_code=mask(code, term),
                                  expl=f"{term}: {D}" + (f" {f['T'][0]}" if f["T"] else ""), expl_code=code,
                                  src=f"def2term:{term}"))
                # 2. term -> definition
                o = self.others(f, lambda x: bool(x["D"]), "D", near=near)
                if o and D:
                    add(v, make_q(rng, f"What best describes {term}?", D, [x["D"] for x in o], L, "concept",
                                  hint=self.lref(L) + (f" Scenario: {f['U'][0]}" if f["U"] else ""), hint_code=code,
                                  expl=f"{term}: {D}", expl_code=code, src=f"term2def:{term}"))
                # 3. scenario -> term
                for u in f["U"]:
                    o = self.others(f, lambda x: True, "term", near=near)
                    if o:
                        add(v, make_q(rng, f"Scenario: {u}\n\nWhich is the best fit?", term, [x["term"] for x in o], L,
                                      "scenario", hint=self.lref(L), hint_code=mask(code, term),
                                      expl=f"{term} fits: {D}", expl_code=code, diff=2, src=f"use:{term}:{u[:20]}"))
                # 4. which TRUE (1 true + 3 false from other facts)
                for t in f["T"]:
                    o = self.others(f, lambda x: bool(x["F"]) and bool(x["T"]), "term", near=near)
                    if o:
                        wrong = [rng.choice(x["F"]) for x in o]
                        why = " ".join(f"✗ “{w}” — actually: {x['T'][0]}" for w, x in zip(wrong, o))
                        add(v, make_q(rng, f"Which statement about {self.tt} is TRUE?", t, wrong, L, "true-false",
                                      hint=self.lref(L) + f" One option is about {term}.", hint_code=code,
                                      expl=f"✓ {t} {why}", expl_code=code, diff=2, src=f"true:{t[:30]}"))
                # 5. which FALSE (1 false + 3 true)
                for fl in f["F"]:
                    o = self.others(f, lambda x: bool(x["T"]), "term", near=near)
                    if o:
                        rights = [rng.choice(x["T"]) for x in o]
                        corr = f["T"][0] if f["T"] else D
                        add(v, make_q(rng, f"Which statement about {self.tt} is FALSE?", fl, rights, L, "true-false",
                                      hint=self.lref(L) + f" Check the claim about {term}.", hint_code=code,
                                      expl=f"✗ False: “{fl}” Correct version: {corr}", expl_code=code, diff=2,
                                      src=f"false:{fl[:30]}"))
                # 6. term-specific correct statement
                if f["T"] and f["F"] and v < 2:
                    o = self.others(f, lambda x: bool(x["F"]), "term", k=2, near=True)
                    if o:
                        t = f["T"][v % len(f["T"])]
                        wrong = [f["F"][v % len(f["F"])]] + [rng.choice(x["F"]) for x in o]
                        add(v, make_q(rng, f"Which statement about {term} is correct?", t, wrong, L, "true-false",
                                      hint=self.lref(L), hint_code=code,
                                      expl=f"✓ {t} — {term}: {D}", expl_code=code, diff=2, src=f"tterm:{term}:{v}"))
                # 7. code -> what it illustrates
                if code and v < 3:
                    o = self.others(f, lambda x: bool(x["D"]), "D", near=near)
                    if o:
                        add(v, make_q(rng, "What concept does this code illustrate?", D, [x["D"] for x in o], L,
                                      "code-reading", code=code, hint=self.lref(L),
                                      expl=f"The code demonstrates {term}: {D}", expl_code=code, diff=2,
                                      src=f"code:{term}"))
                # 8. fill in the blank (fixed distractors -> one variant only)
                if v == 0:
                    for b in f["B"]:
                        add(0, make_q(rng, "Fill in the blank (____) so the code does what the comment says.", b["ans"],
                                      b["dis"], L, "fill-code", code=b["code"],
                                      hint=self.lref(L) + " " + mask(D, b["ans"]), hint_code="",
                                      expl=f"Answer: {b['ans']} — {term}: {D}", expl_code=code or b["code"].replace("____", b["ans"]),
                                      diff=2, src=f"blank:{term}"))
                # 9. correct pairing
                o = self.others(f, lambda x: bool(x["D"]), "D", near=near)
                if o and D:
                    terms = [x["term"] for x in o]
                    defs = [x["D"] for x in o]
                    if v % 2 == 0:
                        wrongs = [f"{terms[0]} → {defs[1]}", f"{terms[1]} → {defs[2]}", f"{term} → {defs[0]}"]
                    else:
                        wrongs = [f"{terms[0]} → {defs[2]}", f"{terms[1]} → {defs[0]}", f"{term} → {defs[1]}"]
                    add(v, make_q(rng, "Which pairing of term and description is correct?", f"{term} → {D}", wrongs, L,
                                  "matching", hint=self.lref(L), hint_code=code,
                                  expl=f"{term} → {D}", expl_code=code, diff=2, src=f"pair:{term}"))
        # categories
        for c in self.cats:
            L = c["lesson"]
            label = c["label"]
            contrast = [x for x in self.cats if x["label"] in c["contrast"]]
            other = [x for x in self.cats if x is not c and x not in contrast]
            same_label_members = {m.lower() for x in self.cats if x["label"] == label for m in x["members"]}
            def outsiders(k, prefer_contrast=True):
                pool_c = [(m, x) for x in contrast for m in x["members"] if m.lower() not in same_label_members]
                pool_o = [(m, x) for x in other for m in x["members"] if m.lower() not in same_label_members]
                rng.shuffle(pool_c); rng.shuffle(pool_o)
                pool = (pool_c + pool_o) if prefer_contrast else (pool_o + pool_c)
                seen, res = set(), []
                for m, x in pool:
                    if m.lower() in seen: continue
                    seen.add(m.lower()); res.append((m, x))
                    if len(res) == k: break
                return res if len(res) == k else None
            for v in range(variants):
                for m in c["members"]:
                    o = outsiders(3, prefer_contrast=(v % 2 == 0))
                    if o:
                        expl = f"{m} is a {label}. " + " ".join(f"{om} → {ox['label']}." for om, ox in o)
                        add(v, make_q(rng, f"Which of the following is a {label}?", m, [om for om, _ in o], L, "category",
                                      hint=self.lref(L), expl=expl, src=f"cat:{label}:{m}"))
                if len(c["members"]) >= 3:
                    for _ in range(min(len(c["members"]), 4)):
                        ms = rng.sample(c["members"], 3)
                        o = outsiders(1, prefer_contrast=(v % 2 == 0))
                        if o:
                            om, ox = o[0]
                            add(v, make_q(rng, f"Which of the following is NOT a {label}?", om, ms, L, "category",
                                          hint=self.lref(L),
                                          expl=f"{om} is a {ox['label']}, not a {label}. The others ({', '.join(ms)}) are each a {label}.",
                                          src=f"notcat:{label}:{om}"))
                labels_pool = [x["label"] for x in contrast] + [x["label"] for x in other]
                labels_pool = list(dict.fromkeys(l for l in labels_pool if l != label))
                if len(labels_pool) >= 3:
                    for m in c["members"]:
                        if v > 1: break
                        wrong = rng.sample(labels_pool[:6] if v == 0 else labels_pool, 3)
                        add(v, make_q(rng, f"“{m}” is best classified as which of the following?", f"a {label}",
                                      [f"a {w}" for w in wrong], L, "category", hint=self.lref(L),
                                      expl=f"{m} is a {label}.", src=f"belongs:{m}"))
        # sequences
        for s in self.seqs:
            L = s["lesson"]
            steps = s["steps"]
            correct = " → ".join(steps)
            for v in range(variants * 2):
                perms = set()
                tries = 0
                while len(perms) < 3 and tries < 200:
                    tries += 1
                    p = steps[:]
                    if rng.random() < 0.5 and len(p) > 2:
                        i = rng.randrange(len(p) - 1); p[i], p[i + 1] = p[i + 1], p[i]
                    else:
                        rng.shuffle(p)
                    if p != steps:
                        perms.add(" → ".join(p))
                if len(perms) == 3:
                    add(v, make_q(rng, f"What is the correct order? {s['title']}", correct, sorted(perms), L, "ordering",
                                  hint=self.lref(L), expl="Correct order: " + correct, diff=2, src=f"seq:{s['title']}:{v}"))
        # handwritten
        for h in self.hand:
            add(0, make_q(rng, h["hq"], h["O"], h["X"][:3], h["lesson"], "concept", code=h["C"],
                          hint=h["H"] or self.lref(h["lesson"]), expl=h["E"], diff=2, src="hand"))
        return out

# ----------------------------------------------------------------- assembly

def assemble(topic, lesson_titles, rng_seed=7, target=TARGET, computed_share=0.5):
    tid = topic["meta"]["id"]
    title = topic["meta"]["title"].replace(" for AI Engineers", "").replace(" & Frameworks", "")
    fpath = next(FACT_DIR.glob(f"*_{tid}.txt"))
    facts, cats, seqs, hand = parse_facts(fpath)
    rng = random.Random(rng_seed)
    tiers = FactTemplates(title, lesson_titles, facts, cats, seqs, hand, rng).gen(variants=6)
    computed = []
    try:
        mod = importlib.import_module(f"generators.{tid}")
        computed = mod.generate(random.Random(rng_seed + 1), lesson_titles)
    except ModuleNotFoundError as e:
        if f"generators.{tid}" not in str(e):
            raise
    seen, chosen = set(), []
    def take(q):
        if q is None: return False
        k = qkey(q)
        if k in seen: return False
        seen.add(k); chosen.append(q); return True

    for q in tiers.get(0, []):
        take(q)
    base = len(chosen)
    remaining = target - base
    comp_quota = int(max(0, remaining) * computed_share)
    ci = 0
    while ci < len(computed) and sum(1 for c in chosen if c["k"] in ("code-output", "calculation", "api-behaviour")) < comp_quota:
        take(computed[ci]); ci += 1
    tier = 1
    while len(chosen) < target and tier < 6:
        for q in tiers.get(tier, []):
            if len(chosen) >= target: break
            take(q)
        tier += 1
    while len(chosen) < target and ci < len(computed):
        take(computed[ci]); ci += 1
    stats = {"facts": len(facts), "cats": len(cats), "seqs": len(seqs), "hand": len(hand), "tier0": base,
             "computed_used": sum(1 for c in chosen if c["k"] in ("code-output", "calculation", "api-behaviour")),
             "computed_avail": len(computed), "total": len(chosen)}
    if len(chosen) < target:
        print(f"!! {tid}: only {len(chosen)} questions", stats)
    chosen = chosen[:target]
    # order by lesson, shuffled within each lesson for variety
    order_rng = random.Random(rng_seed + 2)
    order_rng.shuffle(chosen)
    chosen.sort(key=lambda q: q["l"])
    return chosen, stats, facts, cats


def export(tid, questions, facts, lesson_titles, out_dir):
    snippets, sidx = [], {}
    def sref(code):
        if not code: return -1
        if code not in sidx:
            sidx[code] = len(snippets); snippets.append(code)
        return sidx[code]
    qs = []
    for n, q in enumerate(questions, 1):
        item = {"n": n, "l": q["l"], "k": q["k"], "d": q["d"], "q": q["q"], "o": q["o"], "a": q["a"],
                "h": q["h"], "e": q["e"]}
        if q.get("c"): item["c"] = sref(q["c"])
        if q.get("hc"): item["hc"] = sref(q["hc"])
        if q.get("ec"): item["ec"] = sref(q["ec"])
        qs.append(item)
    glossary = [{"t": f["term"], "d": f["D"], "l": f["lesson"] - 1, "c": f["C"], "x": (f["T"][0] if f["T"] else "")} for f in facts]
    (out_dir / f"{tid}.json").write_text(json.dumps({"topic": tid, "s": snippets, "q": qs}, ensure_ascii=False,
                                                    separators=(",", ":")), encoding="utf-8")
    return glossary
