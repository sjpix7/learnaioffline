"""Computed hybrid search questions: RRF, weighted fusion, BM25 IDF/saturation and metrics."""
import math
from functools import partial
from .common import Collector, make_q, pick_distractors, fmt
from . import vecmath as vm

DOCS = list("ABCDEFG")


def rrf(lists, k=60):
    s = {}
    for lst in lists:
        for r, d in enumerate(lst, 1):
            s[d] = s.get(d, 0) + 1 / (k + r)
    return s


def t_rrf_score(rng):
    k = rng.choice([60, 60, 10, 1])
    r1, r2 = rng.randint(1, 6), rng.choice([None, 1, 2, 3, 4, 5, 8])
    v = 1 / (k + r1) + (0 if r2 is None else 1 / (k + r2))
    val = fmt(round(v, 5), 5)
    wrong = [fmt(round(1 / r1 + (0 if r2 is None else 1 / r2), 5), 5), fmt(round(1 / (k + r1 + (r2 or 0)), 5), 5),
             fmt(round(2 / (k + r1), 5), 5), fmt(round(v / 2, 5), 5), fmt(round((1 / (k + r1)) * (1 if r2 is None else 1 / (k + r2)) * 100, 5), 5)]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    where = f"rank {r1} in the BM25 list and " + ("absent from the vector list" if r2 is None else f"rank {r2} in the vector list")
    return make_q(rng, f"A document is {where}. What is its Reciprocal Rank Fusion score with k = {k}? (5 decimals)", val, wrong, 3, "calculation",
                  hint="RRF(d) = Σ 1/(k + rank) over the lists that contain d.",
                  expl=f"1/({k}+{r1})" + ("" if r2 is None else f" + 1/({k}+{r2})") + f" = {val}", diff=2, src="rrf-score")


def t_rrf_winner(rng):
    a = rng.sample(DOCS[:5], rng.randint(3, 4)); b = rng.sample(DOCS[:5], rng.randint(3, 4))
    k = rng.choice([60, 1])
    s = rrf([a, b], k)
    order = sorted(s, key=lambda d: (-s[d], d))
    top = order[0]
    if len(order) > 1 and abs(s[order[0]] - s[order[1]]) < 1e-9:
        return None
    val = f"Doc {top}"
    wrong = pick_distractors(val, [f"Doc {a[0]}", f"Doc {b[0]}", f"Doc {order[-1]}", f"Doc {order[1]}"] + [f"Doc {d}" for d in order])
    if not wrong:
        return None
    return make_q(rng, f"BM25 ranks {a} and vector search ranks {b} (best first). Which document ranks first after RRF with k = {k}?", val, wrong, 3,
                  "calculation", hint="Documents that rank well in BOTH lists usually win under RRF.",
                  expl="RRF scores: " + ", ".join(f"{d}={s[d]:.4f}" for d in order), diff=2, src="rrf-win",
                  code=f"def rrf(lists, k={k}):\n    s = {{}}\n    for lst in lists:\n        for r, d in enumerate(lst, 1):\n            s[d] = s.get(d, 0) + 1 / (k + r)\n    return sorted(s, key=s.get, reverse=True)\nprint(rrf([{a}, {b}])[0])")


def t_weighted(rng):
    alpha = rng.choice([0.0, 0.25, 0.3, 0.5, 0.7, 0.75, 1.0])
    dense = round(rng.uniform(0, 1), 2); sparse = round(rng.uniform(0, 1), 2)
    v = alpha * dense + (1 - alpha) * sparse
    val = fmt(round(v, 3))
    wrong = [fmt(round((1 - alpha) * dense + alpha * sparse, 3)), fmt(round(dense + sparse, 3)), fmt(round((dense + sparse) / 2, 3)),
             fmt(round(alpha * dense, 3)), fmt(round(max(dense, sparse), 3))]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"With α = {alpha}, a chunk has normalized dense score {dense} and normalized sparse score {sparse}. What is its hybrid score α·dense + (1−α)·sparse?",
                  val, wrong, 3, "calculation", hint="α weights the dense (vector) score; 1 − α weights the keyword score.",
                  expl=f"{alpha}×{dense} + {round(1 - alpha, 2)}×{sparse} = {val}", diff=1, src="weighted")


def t_minmax(rng):
    scores = sorted(rng.sample(range(2, 30), 4))
    x = rng.choice(scores)
    v = (x - scores[0]) / (scores[-1] - scores[0])
    val = fmt(round(v, 3))
    wrong = [fmt(round(x / scores[-1], 3)), fmt(round(1 - v, 3)), fmt(round(x / sum(scores), 3)), fmt(round((x - scores[0]) / scores[-1], 3))]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"BM25 scores for the candidates are {scores}. After min-max normalization, what is the normalized score of {x}?", val, wrong, 3,
                  "calculation", hint="(x − min) / (max − min).", expl=f"({x} − {scores[0]}) / ({scores[-1]} − {scores[0]}) = {val}", diff=1, src="minmax")


def t_idf(rng):
    N = rng.choice([100, 1000, 10000, 50000]); n = rng.choice([1, 5, 10, 50, 100, 500])
    if n >= N:
        return None
    v = math.log((N - n + 0.5) / (n + 0.5) + 1)
    val = fmt(round(v, 3))
    wrong = [fmt(round(math.log(N / n), 3)), fmt(round(math.log10((N - n + 0.5) / (n + 0.5) + 1), 3)), fmt(round(n / N, 3)), fmt(round(v * 2, 3))]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"A corpus has N = {N:,} documents and a term appears in n = {n:,} of them. Using IDF = ln((N − n + 0.5)/(n + 0.5) + 1), what is the IDF? (3 decimals)",
                  val, wrong, 2, "calculation", hint="Rarer terms (small n) get larger IDF.", expl=f"ln(({N} − {n} + 0.5)/({n} + 0.5) + 1) = {val}", diff=2, src="idf")


def t_rarer(rng):
    t1, t2 = rng.sample(["password", "the", "kubernetes", "ORA-00942", "invoice", "error", "reset"], 2)
    n1, n2 = rng.sample([2, 15, 120, 900, 4000], 2)
    val = t1 if n1 < n2 else t2
    wrong = pick_distractors(val, [t2 if val == t1 else t1, "They get the same IDF", "It depends only on query length"])
    return make_q(rng, f"In a 5,000-document corpus, \"{t1}\" appears in {n1} documents and \"{t2}\" appears in {n2}. Which term gets the higher BM25 IDF weight?",
                  val, wrong, 2, "calculation", hint="IDF rewards terms that appear in fewer documents.", expl=f"\"{val}\" is rarer, so its IDF is higher.", diff=1, src="rarer")


def t_saturation(rng):
    k1 = rng.choice([1.2, 1.5, 2.0]); f = rng.choice([1, 2, 3, 5, 10])
    v = f * (k1 + 1) / (f + k1)
    val = fmt(round(v, 3))
    wrong = [fmt(round(f * (k1 + 1), 3)), fmt(round(f / k1, 3)), fmt(round(k1 + 1, 3)), fmt(round(f, 3)), fmt(round(f / (f + k1), 3))]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"Ignoring length normalization (b = 0), BM25's term-frequency factor is f·(k1+1)/(f+k1). With k1 = {k1} and term frequency f = {f}, what is it? (3 decimals)",
                  val, wrong, 2, "calculation", hint="The factor approaches k1 + 1 as f grows — this is saturation.", expl=f"{f}×{k1 + 1}/({f}+{k1}) = {val}", diff=3, src="sat")


def t_alpha_meaning(rng):
    alpha = rng.choice([0, 1, 0.5])
    val = {0: "pure keyword (BM25) search", 1: "pure vector search", 0.5: "equal weight to keyword and vector scores"}[alpha]
    wrong = [v for v in ["pure keyword (BM25) search", "pure vector search", "equal weight to keyword and vector scores", "no results are returned"] if v != val]
    return make_q(rng, f"In Weaviate hybrid search, what does alpha = {alpha} mean?", val, wrong[:3], 4, "calculation",
                  hint="alpha is the weight on the vector side.", expl=f"alpha = {alpha} → {val}.", diff=1, src="alpha")


def generate(rng, lesson_titles, limit=2000):
    T = [t_rrf_score, t_rrf_winner, t_rrf_winner, t_weighted, t_minmax, t_idf, t_rarer, t_saturation, t_alpha_meaning,
         partial(vm.t_mrr, lesson=7), partial(vm.t_precision_recall, lesson=7), partial(vm.t_ndcg, lesson=7), partial(vm.t_cosine, lesson=1)]
    return Collector(rng, limit, per_src_cap=320).run_templates(T)
