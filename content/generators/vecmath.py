"""Exact-arithmetic question templates shared by the RAG / embedding topics."""
import math
from .common import make_q, pick_distractors, fmt, num_distractors


def vec(rng, d=None, lo=-3, hi=4):
    d = d or rng.randint(2, 4)
    v = [rng.randint(lo, hi) for _ in range(d)]
    return v if any(v) else vec(rng, d, lo, hi)


def cosine(a, b):
    return sum(x * y for x, y in zip(a, b)) / (math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b)))


def t_cosine(rng, lesson):
    d = rng.randint(2, 3)
    a, b = vec(rng, d), vec(rng, d)
    c = cosine(a, b)
    val = fmt(round(c, 3))
    dot = sum(x * y for x, y in zip(a, b))
    wrong = [fmt(round(dot, 3)), fmt(round(-c, 3)), fmt(round(c * 0.8, 3)), fmt(round(min(1, abs(c) + 0.15), 3)), fmt(round(dot / (sum(x*x for x in a)), 3))]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"What is the cosine similarity of a = {a} and b = {b}? (3 decimals)", val, wrong, lesson, "calculation",
                  hint="cos = (a·b) / (‖a‖‖b‖).",
                  expl=f"a·b = {dot}; ‖a‖ = {math.sqrt(sum(x*x for x in a)):.3f}; ‖b‖ = {math.sqrt(sum(y*y for y in b)):.3f}; cos = {val}.",
                  expl_code="import numpy as np\na = np.array(%s); b = np.array(%s)\nprint(round(a @ b / (np.linalg.norm(a) * np.linalg.norm(b)), 3))" % (a, b), diff=2, src="cos")


def t_dot(rng, lesson):
    d = rng.randint(2, 4)
    a, b = vec(rng, d), vec(rng, d)
    dot = sum(x * y for x, y in zip(a, b))
    val = str(dot)
    wrong = [str(dot + rng.choice([1, 2, -1, -2])), str(sum(x + y for x, y in zip(a, b))), str(-dot), str(sum(abs(x * y) for x, y in zip(a, b)))]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"What is the dot product of {a} and {b}?", val, wrong, lesson, "calculation", hint="Multiply element-wise and sum.",
                  expl=" + ".join(f"({x}×{y})" for x, y in zip(a, b)) + f" = {dot}", diff=1, src="dot")


def t_l2(rng, lesson):
    d = rng.randint(2, 3)
    a, b = vec(rng, d), vec(rng, d)
    l2 = math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))
    val = fmt(round(l2, 3))
    wrong = [fmt(round(l2 ** 2, 3)), fmt(round(sum(abs(x - y) for x, y in zip(a, b)), 3)), fmt(round(l2 + 1, 3)), fmt(round(l2 / 2, 3))]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"What is the Euclidean (L2) distance between {a} and {b}? (3 decimals)", val, wrong, lesson, "calculation",
                  hint="L2 = sqrt(Σ (aᵢ − bᵢ)²).", expl=f"Squared differences sum to {sum((x - y) ** 2 for x, y in zip(a, b))}; sqrt = {val}.", diff=2, src="l2")


def t_norm(rng, lesson):
    a, b = rng.choice([(3, 4), (6, 8), (5, 12), (8, 15), (1, 0), (0, 2), (7, 24), (9, 12)])
    n = math.hypot(a, b)
    val = f"[{fmt(round(a / n, 3))}, {fmt(round(b / n, 3))}]"
    wrong = [f"[{fmt(round(a / (a + b), 3))}, {fmt(round(b / (a + b), 3))}]", f"[{a / 10}, {b / 10}]", f"[{fmt(round(b / n, 3))}, {fmt(round(a / n, 3))}]",
             f"[{a}, {b}]", f"[{fmt(round(a / n ** 2, 3))}, {fmt(round(b / n ** 2, 3))}]"]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"What is the unit-normalized form of v = [{a}, {b}]?", val, wrong, lesson, "calculation", hint="Divide each component by the L2 norm.",
                  expl=f"‖v‖ = {fmt(n)}, so v/‖v‖ = {val}.", diff=1, src="norm")


def t_nearest(rng, lesson):
    q = vec(rng, 2, -2, 3)
    docs = {name: vec(rng, 2, -3, 4) for name in rng.sample(["A", "B", "C", "D"], 3)}
    scores = {k: cosine(q, v) for k, v in docs.items()}
    best = max(scores, key=scores.get)
    if sorted(scores.values())[-1] - sorted(scores.values())[-2] < 0.02:
        return None
    val = f"Doc {best}"
    wrong = [f"Doc {k}" for k in docs if k != best] + ["They are all equally similar"]
    wrong = pick_distractors(val, wrong)
    return make_q(rng, f"Query vector q = {q}. Documents: " + ", ".join(f"{k} = {v}" for k, v in docs.items()) + ". Which document is most similar by cosine similarity?",
                  val, wrong, lesson, "calculation", hint="Compute cosine(q, d) for each document and take the largest.",
                  expl="Cosine scores: " + ", ".join(f"{k}: {fmt(round(s, 3))}" for k, s in scores.items()) + f" → Doc {best}.", diff=2, src="nearest")


def t_unit_l2(rng, lesson):
    c = rng.choice([0.2, 0.5, 0.6, 0.75, 0.8, 0.9, 0.95, 0.0, -0.5])
    l2 = math.sqrt(2 - 2 * c)
    val = fmt(round(l2, 3))
    wrong = [fmt(round(2 - 2 * c, 3)), fmt(round(1 - c, 3)), fmt(round(math.sqrt(1 - c), 3)), fmt(round(1 + c, 3))]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"Two unit-length embeddings have cosine similarity {c}. What is their Euclidean distance? (3 decimals)", val, wrong, lesson, "calculation",
                  hint="For unit vectors, ‖a − b‖² = 2 − 2·cos(a, b).", expl=f"sqrt(2 − 2×{c}) = {val}", diff=3, src="unitl2")


def t_chunks(rng, lesson):
    L = rng.choice([500, 800, 1000, 1200, 2000, 2500, 3000, 4800, 10000])
    C = rng.choice([200, 256, 300, 400, 500, 512, 1000])
    O = rng.choice([0, 20, 50, 64, 100, 128])
    if O >= C or C >= L:
        return None
    S = C - O
    n = math.ceil((L - C) / S) + 1
    val = str(n)
    wrong = [str(math.ceil(L / C)), str(n + 1), str(max(1, n - 1)), str(math.ceil(L / S)), str(L // C)]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"A {L:,}-character document is split with a sliding window of chunk_size={C} and overlap={O}. How many chunks are produced?",
                  val, wrong, lesson, "calculation", hint="step = size − overlap; chunks = ceil((L − size)/step) + 1.",
                  expl=f"step = {C} − {O} = {S}; ceil(({L} − {C})/{S}) + 1 = {n}.", diff=2, src="chunks")


def t_precision_recall(rng, lesson):
    k = rng.choice([3, 5, 10]); total = rng.randint(2, 6)
    hits = rng.randint(0, min(k, total))
    which = rng.choice(["precision", "recall"])
    v = hits / k if which == "precision" else hits / total
    val = fmt(round(v, 3))
    other = hits / total if which == "precision" else hits / k
    wrong = [fmt(round(other, 3)), fmt(round((hits + 1) / k, 3)), fmt(round(hits / (k + total), 3)), fmt(round(min(1, v + 0.2), 3)), fmt(round(max(0, v - 0.1), 3))]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"A query has {total} relevant documents in the corpus. The top {k} results contain {hits} of them. What is {which}@{k}?",
                  val, wrong, lesson, "calculation", hint="precision@k = hits/k; recall@k = hits/total relevant.",
                  expl=f"{which}@{k} = {hits}/{k if which == 'precision' else total} = {val}.", diff=1, src="pr")


def t_mrr(rng, lesson):
    m = rng.randint(2, 5)
    ranks = [rng.choice([1, 1, 2, 3, 4, 5, None]) for _ in range(m)]
    rr = [0 if r is None else 1 / r for r in ranks]
    val = fmt(round(sum(rr) / m, 3))
    wrong = [fmt(round(sum(rr), 3)), fmt(round(sum(r or 0 for r in ranks) / m, 3)), fmt(round(sum(rr) / m + 0.05, 3)),
             fmt(round(1 / (sum(r or 10 for r in ranks) / m), 3))]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    shown = ["none" if r is None else r for r in ranks]
    return make_q(rng, f"Across {m} queries, the first relevant result appeared at ranks {shown} ('none' = not retrieved). What is the MRR?",
                  val, wrong, lesson, "calculation", hint="MRR = average of 1/rank, with 0 for queries where nothing relevant was retrieved.",
                  expl="Reciprocal ranks: " + ", ".join(fmt(round(x, 3)) for x in rr) + f"; mean = {val}.", diff=2, src="mrr")


def t_ndcg(rng, lesson):
    rels = [rng.choice([0, 0, 1, 1, 2, 3]) for _ in range(rng.randint(3, 4))]
    if not any(rels):
        return None
    dcg = sum(r / math.log2(i + 1) for i, r in enumerate(rels, start=1))
    ideal = sorted(rels, reverse=True)
    idcg = sum(r / math.log2(i + 1) for i, r in enumerate(ideal, start=1))
    v = dcg / idcg
    val = fmt(round(v, 3))
    wrong = [fmt(round(dcg, 3)), fmt(round(sum(rels) / (len(rels) * max(rels)), 3)), fmt(round(min(1, v + 0.1), 3)), fmt(round(v * 0.85, 3)), "1"]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"Graded relevance of the ranked results is {rels} (rank 1 first). What is nDCG@{len(rels)}? Use DCG = Σ rel/log2(rank+1).",
                  val, wrong, lesson, "calculation", hint="nDCG = DCG of your ranking / DCG of the ideal (sorted) ranking.",
                  expl=f"DCG = {fmt(round(dcg, 3))}, ideal DCG = {fmt(round(idcg, 3))}, nDCG = {val}.", diff=3, src="ndcg")


def t_memory(rng, lesson, kinds=("float32",)):
    N = rng.choice([100_000, 500_000, 1_000_000, 2_000_000, 10_000_000, 50_000_000])
    d = rng.choice([384, 512, 768, 1024, 1536, 3072])
    prec = rng.choice(kinds)
    bpv = {"float32": 4, "float16": 2, "int8": 1, "binary": 1 / 8}[prec]
    b = N * d * bpv
    def h(x):
        return f"{x / 1e9:.2f} GB" if x >= 1e9 else f"{x / 1e6:.1f} MB"
    val = h(b)
    wrong = [h(b * 4), h(b / 4), h(b * 8), h(b / 8), h(b * 2), h(b / 2), h(b * 1000)]
    wrong = pick_distractors(val, rng.sample(wrong, len(wrong)))
    if not wrong:
        return None
    return make_q(rng, f"How much raw memory do {N:,} vectors of {d} dimensions need in {prec}? (1 GB = 10⁹ bytes)", val, wrong, lesson, "calculation",
                  hint="bytes = N × d × bytes per value (float32 = 4, float16 = 2, int8 = 1, binary = 1/8).",
                  expl=f"{N:,} × {d} × {bpv} bytes = {val}.", diff=2, src="mem")
