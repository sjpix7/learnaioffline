"""Computed RAG-architecture questions: CRAG/adaptive routing rules, hierarchy sizes, cost arithmetic and fusion."""
import math
from functools import partial
from .common import Collector, make_q, pick_distractors, fmt
from . import vecmath as vm
from .hybrid_rag import t_rrf_winner, t_rrf_score


def t_crag(rng):
    grades = [rng.choice(["Correct", "Incorrect", "Ambiguous"]) for _ in range(rng.randint(2, 4))]
    if "Correct" in grades:
        val = "Correct: refine the relevant documents into knowledge strips"
    elif all(g == "Incorrect" for g in grades):
        val = "Incorrect: discard them, rewrite the query and use web search"
    else:
        val = "Ambiguous: combine refined documents with web search results"
    opts = ["Correct: refine the relevant documents into knowledge strips", "Incorrect: discard them, rewrite the query and use web search",
            "Ambiguous: combine refined documents with web search results", "Stop and return an empty answer"]
    wrong = [o for o in opts if o != val][:3]
    return make_q(rng, f"In Corrective RAG, the evaluator grades the retrieved documents as {grades}. Which action follows (using the rule: any Correct → Correct; all Incorrect → Incorrect; otherwise Ambiguous)?",
                  val, wrong, 6, "calculation", hint="Apply the CRAG decision rule to the set of grades.", expl=f"Grades {grades} → {val.split(':')[0]}.", diff=2, src="crag")


def t_adaptive(rng):
    kind = rng.choice(["simple", "moderate", "complex"])
    q = {"simple": rng.choice(["What does RAG stand for?", "Who wrote 'Attention Is All You Need'?"]),
         "moderate": rng.choice(["What is our refund window for digital goods?", "Which port does our staging DB use?"]),
         "complex": rng.choice(["Compare Q2 churn drivers across our three regions and link them to support incidents.",
                                "Which suppliers of the parts in the recalled model also supply our EU factory?"])}[kind]
    val = {"simple": "answer directly without retrieval", "moderate": "single-step retrieval", "complex": "multi-step iterative retrieval"}[kind]
    wrong = [v for v in ["answer directly without retrieval", "single-step retrieval", "multi-step iterative retrieval", "refuse to answer"] if v != val]
    return make_q(rng, f"An Adaptive RAG classifier labels this query as {kind}: “{q}” Which strategy is used?", val, wrong[:3], 7, "calculation",
                  hint="Adaptive RAG maps simple → no retrieval, moderate → single-step, complex → multi-step.", expl=f"{kind} → {val}.", diff=1, src="adaptive")


def t_hierarchy(rng):
    doc = rng.choice([4096, 8192, 10000, 16384, 20000])
    sizes = rng.choice([[2048, 512, 128], [1024, 256], [2048, 512]])
    level = rng.randrange(len(sizes))
    n = math.ceil(doc / sizes[level])
    val = str(n)
    wrong = pick_distractors(val, [str(n * 2), str(max(1, n // 2)), str(n + 1), str(math.ceil(doc / sizes[0])), str(sum(math.ceil(doc / s) for s in sizes))])
    if not wrong:
        return None
    return make_q(rng, f"A {doc:,}-token document is split hierarchically with chunk sizes {sizes} (no overlap). How many nodes are at the {sizes[level]}-token level?",
                  val, wrong, 4, "calculation", hint="Each level has ceil(document tokens / level chunk size) nodes.", expl=f"ceil({doc}/{sizes[level]}) = {n}", diff=1, src="hier")


def t_contextual_cost(rng):
    docs = rng.choice([100, 500, 2000]); chunks = rng.choice([10, 20, 40]); doc_tokens = rng.choice([4000, 8000, 20000])
    calls = docs * chunks
    val = f"{calls:,}"
    wrong = pick_distractors(val, [f"{docs:,}", f"{chunks:,}", f"{calls * 2:,}", f"{docs * doc_tokens:,}"])
    return make_q(rng, f"Contextual retrieval is applied to {docs:,} documents with {chunks} chunks each. How many LLM calls are needed to generate chunk contexts (one per chunk)?",
                  val, wrong, 5, "calculation", hint="One context-generation call per chunk; prompt caching makes each call cheaper, not fewer.",
                  expl=f"{docs:,} × {chunks} = {val} calls.", diff=1, src="ctxcost")


def t_failure_reduction(rng):
    base = rng.choice([5.7, 4.0, 8.0, 10.0, 6.5]); red = rng.choice([35, 49, 67])
    v = base * (1 - red / 100)
    val = fmt(round(v, 2), 2) + "%"
    wrong = pick_distractors(val, [fmt(round(base - red / 10, 2), 2) + "%", fmt(round(base * red / 100, 2), 2) + "%", fmt(round(base / 2, 2), 2) + "%",
                                   fmt(round(base * (1 + red / 100), 2), 2) + "%"])
    if not wrong:
        return None
    return make_q(rng, f"A baseline retriever fails on {base}% of queries (top-20). A technique reduces the failure rate by {red}% (relative). What is the new failure rate?",
                  val, wrong, 5, "calculation", hint="A relative reduction multiplies the rate by (1 − reduction).", expl=f"{base}% × (1 − {red / 100}) = {val}", diff=2, src="fail")


def t_long_vs_rag(rng):
    corpus = rng.choice([200_000, 500_000, 900_000]); rag_tokens = rng.choice([3_000, 6_000, 10_000]); price = rng.choice([1.0, 2.5, 3.0, 5.0])
    q = rng.choice([1_000, 10_000])
    lc = q * corpus * price / 1e6; rg = q * rag_tokens * price / 1e6
    val = fmt(round(lc / rg, 1), 1) + "×"
    wrong = pick_distractors(val, [fmt(round(rg / lc, 3), 3) + "×", fmt(round(corpus / 1000, 1), 1) + "×", fmt(round(lc / rg / 2, 1), 1) + "×", fmt(round(lc / rg * 2, 1), 1) + "×"])
    if not wrong:
        return None
    return make_q(rng, f"Answering {q:,} questions by sending a {corpus:,}-token corpus every time vs. RAG sending {rag_tokens:,} retrieved tokens (same ${price}/1M input price, no caching): how many times more expensive is the long-context approach?",
                  val, wrong, 10, "calculation", hint="Cost scales with input tokens per request.", expl=f"{corpus:,} / {rag_tokens:,} = {val}", diff=2, src="lc")


def t_parent_children(rng):
    parent = rng.choice([1000, 1500, 2000, 2048, 4000]); child = rng.choice([100, 128, 200, 250, 256, 400, 512])
    if child >= parent:
        return None
    n = math.ceil(parent / child)
    val = str(n)
    wrong = pick_distractors(val, [str(n + 1), str(max(1, n - 1)), str(parent // child), str(n * 2), str(math.ceil(parent / (child * 2)))])
    if not wrong:
        return None
    return make_q(rng, f"In parent-document retrieval, parents are {parent} tokens and children are {child} tokens (no overlap). How many child chunks does one full parent produce?",
                  val, wrong, 4, "calculation", hint="children per parent = ceil(parent size / child size).", expl=f"ceil({parent}/{child}) = {n}", diff=1, src="pc")


def t_window(rng):
    n = rng.randint(8, 30); i = rng.randint(1, n); w = rng.randint(1, 4)
    lo, hi = max(1, i - w), min(n, i + w)
    val = f"sentences {lo}–{hi}"
    wrong = pick_distractors(val, [f"sentences {i - w}–{i + w}", f"sentences {lo}–{i}", f"sentences {i}–{hi}", f"sentences {max(1, i - 2 * w)}–{min(n, i + 2 * w)}",
                                   f"sentence {i} only"])
    if not wrong:
        return None
    return make_q(rng, f"A document has {n} sentences. Sentence {i} matches the query and the sentence window size is {w} on each side. Which text is sent to the LLM?",
                  val, wrong, 4, "calculation", hint="The window covers i−w … i+w, clipped to the document boundaries.", expl=f"Window = {val}.", diff=2, src="win")


def t_multiquery(rng):
    v = rng.randint(2, 5); k = rng.choice([3, 4, 5, 8, 10])
    total = (v + 1) * k
    val = str(total)
    wrong = pick_distractors(val, [str(v * k), str(k), str(total + k), str(v + k)])
    return make_q(rng, f"Multi-query retrieval generates {v} rephrasings and also searches the original question, retrieving top-{k} for each. What is the maximum number of candidate chunks before de-duplication?",
                  val, wrong, 2, "calculation", hint="(variants + original) × k.", expl=f"({v} + 1) × {k} = {total}", diff=1, src="mq")


def t_automerge(rng):
    total = rng.choice([4, 6, 8]); got = rng.randint(1, total); th = rng.choice([0.5, 0.6, 0.75])
    merge = got / total > th
    val = "merge them into the parent chunk" if merge else "keep the individual child chunks"
    wrong = ["keep the individual child chunks" if merge else "merge them into the parent chunk", "drop all of them", "re-run retrieval with a larger k"]
    return make_q(rng, f"Auto-merging retrieval merges children into their parent when more than {int(th * 100)}% of a parent's {total} children are retrieved. {got} children were retrieved. What happens?",
                  val, wrong, 4, "calculation", hint="Compare retrieved/total with the threshold.", expl=f"{got}/{total} = {got / total:.2f} {'>' if merge else '≤'} {th} → {val}.",
                  diff=1, src="am")


def t_selfrag(rng):
    isrel = rng.choice([True, False]); issup = rng.choice(["fully supported", "partially supported", "not supported"])
    if not isrel:
        val = "discard the passage and try another one (or retrieve again)"
    elif issup == "fully supported":
        val = "keep the generation as a strong candidate"
    else:
        val = "regenerate or pick a better-supported candidate"
    opts = ["discard the passage and try another one (or retrieve again)", "keep the generation as a strong candidate", "regenerate or pick a better-supported candidate",
            "stop and return no answer"]
    return make_q(rng, f"In a Self-RAG-style loop, the passage relevance check (ISREL) says {'relevant' if isrel else 'irrelevant'} and the support check (ISSUP) says {issup}. What should happen?",
                  val, [o for o in opts if o != val][:3], 7, "calculation", hint="Irrelevant passages are discarded; only fully supported generations are kept.",
                  expl=f"ISREL={'relevant' if isrel else 'irrelevant'}, ISSUP={issup} → {val}.", diff=2, src="selfrag")


def t_max_k(rng):
    win = rng.choice([8192, 16384, 32768, 128000]); prompt = rng.choice([500, 1000, 2000]); out = rng.choice([500, 1000, 2000, 4000]); ch = rng.choice([256, 512, 800, 1000])
    k = (win - prompt - out) // ch
    if k <= 0:
        return None
    val = str(k)
    wrong = pick_distractors(val, [str(k + 1), str(max(1, k - 1)), str(win // ch), str((win - prompt) // ch)])
    if not wrong:
        return None
    return make_q(rng, f"With a {win:,}-token window, a {prompt}-token prompt template and {out} tokens reserved for the answer, what is the maximum number of {ch}-token chunks you can include?",
                  val, wrong, 10, "calculation", hint="k = floor((window − prompt − reserved output) / chunk size).", expl=f"floor(({win} − {prompt} − {out}) / {ch}) = {k}", diff=2, src="maxk")


def generate(rng, lesson_titles, limit=1500):
    T = [t_crag, t_adaptive, t_hierarchy, t_contextual_cost, t_failure_reduction, t_long_vs_rag, partial(vm.t_chunks, lesson=4), t_parent_children, t_window, t_multiquery, t_automerge, t_selfrag, t_max_k, partial(t_rrf_score)]
    col = Collector(rng, limit - 250)
    col.run_templates(T)
    col.limit = len(col.items) + 250
    out = col.run_templates([t_rrf_winner])
    for q in out:
        if q["src"] in ("rrf-win", "rrf-score"):
            q["l"] = 1   # RAG-Fusion lives in lesson 2 (0-based 1)
    return out
