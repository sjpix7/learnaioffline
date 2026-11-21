"""Build all app content: lessons JSON, question banks, glossary and topic index."""
import json, sys, time
from pathlib import Path
import build_lessons, qbank, enrich_hints

ROOT = Path(__file__).parent
OUT = ROOT.parent / "android" / "app" / "src" / "main" / "assets" / "content"
SHARE = {"python": 0.6, "fastapi": 0.4, "langchain": 0.35, "langgraph": 0.35, "vector_rag": 0.6, "hybrid_rag": 0.6,
         "embeddings": 0.55, "llm_models": 0.55, "ragas": 0.55, "graph_rag": 0.5, "langfuse": 0.35,
         "llamaindex": 0.25, "agentic_ai": 0.25, "rag_architectures": 0.3, "ai_frameworks": 0.2, "mcp": 0.35}


def main(only=None):
    (OUT / "lessons").mkdir(parents=True, exist_ok=True)
    (OUT / "questions").mkdir(parents=True, exist_ok=True)
    (OUT / "glossary").mkdir(parents=True, exist_ok=True)
    topics = build_lessons.build()
    index = []
    for t in topics:
        m = t["meta"]
        tid = m["id"]
        titles = [l["title"] for l in t["lessons"]]
        if only and tid not in only:
            continue
        t0 = time.time()
        qs, stats, facts, cats = qbank.assemble(t, titles, computed_share=SHARE.get(tid, 0.4))
        glossary = qbank.export(tid, qs, facts, titles, OUT / "questions")
        (OUT / "glossary" / f"{tid}.json").write_text(json.dumps(glossary, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        lessons_json = [{"t": l["title"], "m": l["minutes"], "b": l["blocks"]} for l in t["lessons"]]
        (OUT / "lessons" / f"{tid}.json").write_text(json.dumps(lessons_json, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        per_lesson = [0] * len(titles)
        for q in qs:
            per_lesson[q["l"]] += 1
        index.append({"id": tid, "title": m["title"], "short": m["short"], "emoji": m["emoji"], "color": m["color"],
                      "lessons": [{"t": l["title"], "m": l["minutes"], "s": l["sections"], "q": per_lesson[i]}
                                  for i, l in enumerate(t["lessons"])],
                      "qcount": len(qs), "gcount": len(glossary)})
        kinds = {}
        for q in qs:
            kinds[q["k"]] = kinds.get(q["k"], 0) + 1
        print(f"{tid:18} {stats} kinds={kinds} {time.time()-t0:.1f}s", flush=True)
    tpath = OUT / "topics.json"
    if only and tpath.exists():
        # Partial build: replace/insert only the rebuilt topics and keep the others, in lesson-file order.
        merged = {e["id"]: e for e in json.loads(tpath.read_text(encoding="utf-8"))}
        merged.update({e["id"]: e for e in index})
        index = [merged[t["meta"]["id"]] for t in topics if t["meta"]["id"] in merged]
    tpath.write_text(json.dumps(index, ensure_ascii=False, indent=1), encoding="utf-8")
    # Point every hint at a lesson section and attach study code (also refreshes per-lesson counts).
    enrich_hints.main(only)


if __name__ == "__main__":
    main(set(sys.argv[1:]) or None)
