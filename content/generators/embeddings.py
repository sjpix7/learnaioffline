"""Computed embedding questions: similarity math, storage, Matryoshka and quantization arithmetic."""
from functools import partial
from .common import Collector, make_q, pick_distractors, fmt
from . import vecmath as vm


def t_mrl(rng):
    full = rng.choice([3072, 1536, 1024, 768]); small = rng.choice([d for d in [64, 128, 256, 512, 768, 1024] if d < full])
    v = 100 * (1 - small / full)
    val = fmt(round(v, 1), 1) + "%"
    wrong = pick_distractors(val, [fmt(round(100 * small / full, 1), 1) + "%", fmt(round(v / 2, 1), 1) + "%", "50%", fmt(round(100 - full / small, 1), 1) + "%"])
    if not wrong:
        return None
    return make_q(rng, f"A Matryoshka embedding of {full} dimensions is truncated to the first {small}. By what percentage does vector storage shrink?",
                  val, wrong, 7, "calculation", hint="Savings = 1 − new_dims / old_dims.", expl=f"1 − {small}/{full} = {val}", diff=1, src="mrl")


def t_quant_factor(rng):
    a, b = rng.sample(["float32", "float16", "int8", "binary"], 2)
    bits = {"float32": 32, "float16": 16, "int8": 8, "binary": 1}
    if bits[a] <= bits[b]:
        a, b = b, a
    f = bits[a] / bits[b]
    val = f"{int(f)}×"
    wrong = pick_distractors(val, [f"{int(f * 2)}×", f"{max(2, int(f // 2))}×", "4×", "8×", "16×", "32×"])
    if not wrong:
        return None
    return make_q(rng, f"How many times smaller are {b} embeddings than {a} embeddings of the same dimension?", val, wrong, 7, "calculation",
                  hint="Compare bits per dimension: float32 = 32, float16 = 16, int8 = 8, binary = 1.", expl=f"{bits[a]} / {bits[b]} = {val}", diff=1, src="qfac")


def t_combo(rng):
    N = rng.choice([1_000_000, 5_000_000, 20_000_000]); d0 = rng.choice([1024, 1536, 3072]); d1 = rng.choice([d for d in [256, 512, 768] if d < d0])
    prec = rng.choice(["int8", "binary"])
    bpv = 1 if prec == "int8" else 1 / 8
    before, after = N * d0 * 4, N * d1 * bpv
    def h(x):
        return f"{x / 1e9:.2f} GB" if x >= 1e9 else f"{x / 1e6:.1f} MB"
    val = h(after)
    wrong = pick_distractors(val, [h(N * d1 * 4), h(N * d0 * bpv), h(after * 8), h(after / 4), h(before)])
    if not wrong:
        return None
    return make_q(rng, f"You store {N:,} embeddings. You truncate them from {d0} to {d1} dimensions (Matryoshka) and quantize to {prec}. What is the raw storage? (1 GB = 10⁹ bytes)",
                  val, wrong, 7, "calculation", hint="bytes = N × dims × bytes per value; int8 = 1 byte, binary = 1/8 byte.",
                  expl=f"{N:,} × {d1} × {bpv} = {val} (was {h(before)} at {d0}-dim float32).", diff=3, src="combo")


def t_prefix(rng):
    model, q, d = rng.choice([("E5", "query: ", "passage: "), ("nomic-embed-text", "search_query: ", "search_document: ")])
    side = rng.choice(["query", "document"])
    val = (q if side == "query" else d).strip()
    pool = ["query:", "passage:", "search_query:", "search_document:", "Represent this sentence:", "question:", "doc:"]
    wrong = pick_distractors(val, [p for p in pool if p != val])
    return make_q(rng, f"Which prefix should you prepend to a {side} when embedding with {model}?", val, wrong, 8, "calculation",
                  hint="Asymmetric models expect different prefixes for queries and documents.", expl=f"{model}: queries use “{q.strip()}”, documents use “{d.strip()}”.",
                  diff=1, src="prefix")


def t_api_dims(rng):
    model, dims = rng.choice([("text-embedding-3-small", "1536"), ("text-embedding-3-large", "3072"), ("all-MiniLM-L6-v2", "384"),
                              ("BGE-M3 (dense)", "1024"), ("gemini-embedding-001", "3072")])
    wrong = pick_distractors(dims, ["384", "768", "1024", "1536", "3072", "4096"])
    return make_q(rng, f"What is the default output dimension of {model}?", dims, wrong, 5, "calculation", hint="Recall each model's native vector size.",
                  expl=f"{model} outputs {dims} dimensions by default.", diff=2, src="dims")


def generate(rng, lesson_titles, limit=1800):
    T = [partial(vm.t_cosine, lesson=4), partial(vm.t_norm, lesson=4), partial(vm.t_unit_l2, lesson=4), partial(vm.t_dot, lesson=4),
         partial(vm.t_nearest, lesson=1), partial(vm.t_l2, lesson=4),
         partial(vm.t_memory, lesson=7, kinds=("float32", "float16", "int8", "binary")), t_mrl, t_quant_factor, t_combo, t_prefix, t_api_dims]
    return Collector(rng, limit, per_src_cap=330).run_templates(T)
