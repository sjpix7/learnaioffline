"""Post-process exported question banks so every hint points at a specific lesson section and,
where the lesson (or its key concepts) has code, carries a sample-code snippet to study.

Runs on the exported assets (after build_all.py) and is deterministic and idempotent:
  * questions that already have hint code keep it;
  * generic "Revisit Lesson N: ..." hints are rewritten to name the most relevant section;
  * a snippet is never chosen if it contains the correct option verbatim (that would give the answer away)
    or if it is the question's own code;
  * "category" questions inherit the lesson of their category, so when another lesson's section is a far
    better match for the asked-about item, the question is re-linked to that lesson (topics.json counts are
    updated to match).
"""
import json
import math
import re
import sys
from pathlib import Path

ASSETS = Path(__file__).parent.parent / "android" / "app" / "src" / "main" / "assets" / "content"
STOP = set("""the a an and or of to in on for with by is are be as at it its this that these those which what
when where who how why from into than then not no yes can will would should could may might must does do did
has have had was were been being their there them they you your our we i his her him she he true false following
statement correct best describes about using use used uses value values example code output prints print return
returns line lines result run runs given each other more most less also only just one two three after before
between both all any some such same like key takeaways""".split())
WORD = re.compile(r"[A-Za-z_][A-Za-z0-9_]{2,}")
REVISIT = re.compile(r"^Revisit Lesson (\d+): (.*)\.$")


def tokens(text):
    out = set()
    for w in WORD.findall(text or ""):
        lw = w.lower()
        if lw in STOP:
            continue
        out.add(lw)
        for part in lw.split("_"):
            if len(part) > 2 and part not in STOP:
                out.add(part)
    return out


def reveals(code, answer):
    a = answer.strip()
    if len(a) < 4 or a.lower() in ("true", "false", "none", "yes"):
        return False
    return a.lower() in code.lower()


class TopicIndex:
    def __init__(self, lessons, glossary):
        self.lessons = lessons
        self.sections = []          # (lesson, title, tokens)
        self.cands = {}             # lesson -> [(code, section_title, tokens)]
        for li, lesson in enumerate(lessons):
            current = lesson["t"]
            cands = self.cands.setdefault(li, [])
            for b in lesson["b"]:
                if b["k"] == "h":
                    current = b["t"]
                    if current.lower() != "key takeaways":
                        self.sections.append((li, current, tokens(current)))
                elif b["k"] == "c":
                    code = b["t"].strip("\n")
                    if len(code) >= 20:
                        cands.append((code, current, tokens(code) | tokens(current)))
        for g in glossary:
            code = (g.get("c") or "").strip("\n")
            if code and len(code) >= 12:
                li = g.get("l", 0)
                self.cands.setdefault(li, []).append((code, g["t"], tokens(code) | tokens(g["t"]) | tokens(g.get("d", ""))))
        docs = [s[2] for s in self.sections] + [c[2] for cs in self.cands.values() for c in cs]
        df = {}
        for d in docs:
            for t in d:
                df[t] = df.get(t, 0) + 1
        n = max(1, len(docs))
        self.idf = {t: math.log((n + 1) / (c + 0.5)) for t, c in df.items()}

    def w(self, toks):
        return sum(self.idf.get(t, 0.0) for t in toks)

    def best_section(self, qtok, lesson, allow_other):
        best = (None, None, 0.0)   # lesson, title, score
        own_best = (None, 0.0)
        for li, title, stok in self.sections:
            sc = self.w(qtok & stok)
            if li == lesson:
                if sc > own_best[1]:
                    own_best = (title, sc)
                sc *= 1.5
            elif not allow_other:
                continue
            if sc > best[2]:
                best = (li, title, sc)
        return best, own_best

    def best_code(self, q, own_code, qtok, lesson, section):
        best, best_score = None, 0.0
        answer = q["o"][q["a"]]
        # For short answers (a term or name), code that mentions the answer's distinctive words gives it away.
        short = len(answer.split()) <= 4 and q["k"] in ("concept", "scenario", "matching", "category", "code-reading")
        ans_tok = {t for t in tokens(answer) if len(t) >= 4}
        for code, sec, ctok in self.cands.get(lesson, []):
            if code == own_code or reveals(code, answer) or sec.lower() == answer.lower():
                continue
            if short and ans_tok & ctok:
                continue
            sc = self.w(qtok & ctok) + (1.5 if section and sec == section else 0.0)
            if sc > best_score:
                best, best_score = (code, sec), sc
        return best


def enrich_topic(tid):
    qpath = ASSETS / "questions" / f"{tid}.json"
    bank = json.loads(qpath.read_text(encoding="utf-8"))
    lessons = json.loads((ASSETS / "lessons" / f"{tid}.json").read_text(encoding="utf-8"))
    glossary = json.loads((ASSETS / "glossary" / f"{tid}.json").read_text(encoding="utf-8"))
    idx = TopicIndex(lessons, glossary)
    snippets = bank["s"]
    sidx = {s: i for i, s in enumerate(snippets)}
    added = rewritten = moved = 0
    for q in bank["q"]:
        answer = q["o"][q["a"]]
        first_expl = (q.get("e") or "").split(". ")[0]
        qtok = tokens(q["q"]) | tokens(answer) | tokens(first_expl)
        lesson = q["l"]
        (blesson, bsec, bscore), (section, own_score) = idx.best_section(qtok, lesson, q["k"] == "category")
        if (q["k"] == "category" and blesson is not None and blesson != lesson
                and bscore >= 4.0 and bscore > 2 * max(own_score, 0.5)):
            q["l"] = lesson = blesson
            section = bsec
            moved += 1
        if "hc" not in q:
            own_code = snippets[q["c"]] if "c" in q else None
            best = idx.best_code(q, own_code, qtok, lesson, section)
            if best is not None:
                code, code_sec = best
                if code not in sidx:
                    sidx[code] = len(snippets)
                    snippets.append(code)
                q["hc"] = sidx[code]
                added += 1
                if section is None and code_sec in {s[1] for s in idx.sections if s[0] == lesson}:
                    section = code_sec
        m = REVISIT.match(q["h"]) if "Study the example" not in q["h"] else None
        if m:
            n, title = lesson + 1, lessons[lesson]["t"]
            where = f"Lesson {n} › “{section}”" if section and section != title else f"Lesson {n}: {title}"
            q["h"] = f"Revisit {where}." + (" Study the example below." if "hc" in q else "")
            rewritten += 1
    qpath.write_text(json.dumps(bank, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    per_lesson = [0] * len(lessons)
    for q in bank["q"]:
        per_lesson[q["l"]] += 1
    with_code = sum(1 for q in bank["q"] if "hc" in q)
    return per_lesson, added, rewritten, moved, with_code, len(bank["q"])


def main(only=None):
    tpath = ASSETS / "topics.json"
    topics = json.loads(tpath.read_text(encoding="utf-8"))
    for t in topics:
        if only and t["id"] not in only:
            continue
        per_lesson, added, rewritten, moved, with_code, total = enrich_topic(t["id"])
        for i, l in enumerate(t["lessons"]):
            l["q"] = per_lesson[i]
        print(f"{t['id']:18} +{added:5d} hint snippets, {rewritten:5d} hints targeted, {moved:4d} re-linked, "
              f"{with_code}/{total} ({100 * with_code / total:.0f}%) with hint code")
    tpath.write_text(json.dumps(topics, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main(set(sys.argv[1:]) or None)
