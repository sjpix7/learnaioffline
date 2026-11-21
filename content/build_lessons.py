"""Convert lesson markdown files into JSON block structures for the Android app."""
import json, re, sys, math
from pathlib import Path

ROOT = Path(__file__).parent
LESSON_DIR = ROOT / "lessons"


def parse_front_matter(text):
    m = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    meta = {}
    for line in m.group(1).splitlines():
        k, _, v = line.partition(":")
        meta[k.strip()] = v.strip()
    return meta, text[m.end():]


def parse_blocks(lines):
    blocks, i = [], 0
    para = []

    def flush_para():
        if para:
            blocks.append({"k": "p", "t": " ".join(s.strip() for s in para)})
            para.clear()

    while i < len(lines):
        line = lines[i]
        s = line.rstrip()
        if s.startswith("```"):
            flush_para()
            lang = s[3:].strip() or "text"
            code = []
            i += 1
            while i < len(lines) and not lines[i].startswith("```"):
                code.append(lines[i].rstrip("\n"))
                i += 1
            blocks.append({"k": "c", "l": lang, "t": "\n".join(code)})
            i += 1
            continue
        if not s.strip():
            flush_para(); i += 1; continue
        if s.startswith("### "):
            flush_para(); blocks.append({"k": "h", "t": s[4:].strip()}); i += 1; continue
        if s.startswith("|"):
            flush_para()
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                row = lines[i].strip()
                cells = [c.strip() for c in row.strip("|").split("|")]
                if not all(re.fullmatch(r":?-{2,}:?", c) for c in cells):
                    rows.append(cells)
                i += 1
            blocks.append({"k": "tb", "r": rows})
            continue
        if re.match(r"^\s*[-*] ", s) or s.startswith("✅") or s.startswith("❌"):
            flush_para()
            items = []
            while i < len(lines) and (re.match(r"^\s*[-*] ", lines[i]) or lines[i].startswith("✅") or lines[i].startswith("❌")):
                t = lines[i].strip()
                if t[:2] in ("- ", "* "):
                    t = t[2:]
                items.append(t.strip())
                i += 1
            blocks.append({"k": "ul", "i": items})
            continue
        if re.match(r"^\d+\. ", s):
            flush_para()
            items = []
            while i < len(lines) and (re.match(r"^\d+\. ", lines[i]) or re.match(r"^\s{2,}[-*] ", lines[i])):
                t = lines[i]
                if re.match(r"^\d+\. ", t):
                    items.append(re.sub(r"^\d+\. ", "", t).strip())
                else:
                    items[-1] += "\n   • " + re.sub(r"^\s*[-*] ", "", t).strip()
                i += 1
            blocks.append({"k": "ol", "i": items})
            continue
        if s.startswith("> "):
            flush_para()
            note = []
            while i < len(lines) and lines[i].startswith("> "):
                note.append(lines[i][2:].strip()); i += 1
            blocks.append({"k": "n", "t": " ".join(note)})
            continue
        para.append(s)
        i += 1
    flush_para()
    return blocks


def words_in(blocks):
    n = 0
    for b in blocks:
        if b["k"] in ("p", "h", "n"):
            n += len(b["t"].split())
        elif b["k"] == "c":
            n += len(b["t"].split()) // 2
        elif b["k"] in ("ul", "ol"):
            n += sum(len(x.split()) for x in b["i"])
        elif b["k"] == "tb":
            n += sum(len(" ".join(r).split()) for r in b["r"])
    return n


def build():
    topics = []
    for f in sorted(LESSON_DIR.glob("*.md")):
        meta, body = parse_front_matter(f.read_text(encoding="utf-8"))
        parts = re.split(r"^## (.+)$", body, flags=re.M)
        lessons = []
        for j in range(1, len(parts), 2):
            title = parts[j].strip()
            blocks = parse_blocks(parts[j + 1].split("\n"))
            sections = [b["t"] for b in blocks if b["k"] == "h"]
            w = words_in(blocks)
            lessons.append({"title": title, "blocks": blocks, "sections": sections,
                            "minutes": max(3, math.ceil(w / 160)), "words": w})
        topics.append({"meta": meta, "lessons": lessons, "order": len(topics)})
    return topics


if __name__ == "__main__":
    topics = build()
    total = 0
    for t in topics:
        ws = sum(l["words"] for l in t["lessons"])
        total += ws
        print(f'{t["meta"]["id"]:18} lessons={len(t["lessons"]):2} words={ws}')
    print("total words", total, "lessons", sum(len(t["lessons"]) for t in topics))
