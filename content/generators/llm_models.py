"""Computed LLM questions: memory, KV cache, cost, softmax/temperature, top-p/top-k, LoRA, MoE and context budgets."""
import math
from .common import Collector, make_q, pick_distractors, fmt, num_distractors


def t_weights(rng):
    p = rng.choice([1, 3, 7, 8, 13, 14, 32, 70, 405]); prec = rng.choice(["FP32", "FP16", "INT8", "4-bit"])
    b = {"FP32": 4, "FP16": 2, "INT8": 1, "4-bit": 0.5}[prec]
    v = p * b
    val = fmt(round(v, 1), 1) + " GB"
    wrong = pick_distractors(val, [fmt(round(p * x, 1), 1) + " GB" for x in (4, 2, 1, 0.5, 8) if x != b])
    if not wrong:
        return None
    return make_q(rng, f"Approximately how much memory do the weights of a {p}B-parameter model need in {prec}?", val, wrong, 8, "calculation",
                  hint="memory ≈ parameters × bytes per parameter (FP32 4, FP16 2, INT8 1, 4-bit 0.5).", expl=f"{p}B × {b} bytes = {val}", diff=1, src="w")


def t_kv(rng):
    L = rng.choice([24, 32, 40, 48, 64, 80]); h = rng.choice([4, 8, 16]); d = rng.choice([64, 128]); tok = rng.choice([4096, 8192, 32768, 131072])
    per = 2 * L * h * d * 2
    total = per * tok
    def gib(x):
        return f"{x / 2**30:.2f} GiB" if x >= 2**30 else f"{x / 2**20:.0f} MiB"
    val = gib(total)
    wrong = pick_distractors(val, [gib(total / 2), gib(total * 2), gib(total * 8), gib(per * 1000), gib(total / 8)])
    if not wrong:
        return None
    return make_q(rng, f"A model has {L} layers, {h} KV heads and head_dim {d}, with an FP16 KV cache. How much KV cache does one {tok:,}-token sequence need?",
                  val, wrong, 5, "calculation", hint="bytes/token = 2 (K and V) × layers × KV heads × head_dim × 2 bytes.",
                  expl=f"{per:,} bytes per token × {tok:,} tokens = {val}", diff=3, src="kv")


def t_cost(rng):
    inp = rng.choice([500, 2000, 8000, 12000, 50000, 120000]); out = rng.choice([100, 300, 800, 2000, 4000])
    pin, pout = rng.choice([(0.15, 0.6), (1.25, 10), (2.5, 10), (3, 15), (5, 25), (0.4, 1.6)])
    v = inp * pin / 1e6 + out * pout / 1e6
    val = "$" + fmt(round(v, 5), 5)
    wrong = pick_distractors(val, ["$" + fmt(round((inp + out) * pin / 1e6, 5), 5), "$" + fmt(round((inp + out) * pout / 1e6, 5), 5),
                                   "$" + fmt(round(inp * pout / 1e6 + out * pin / 1e6, 5), 5), "$" + fmt(round(v * 1000, 5), 5)])
    if not wrong:
        return None
    return make_q(rng, f"A request uses {inp:,} input tokens and {out:,} output tokens. Prices are ${pin} per 1M input and ${pout} per 1M output tokens. What does it cost?",
                  val, wrong, 2, "calculation", hint="Price input and output tokens separately.", expl=f"{inp:,}×{pin}/1e6 + {out:,}×{pout}/1e6 = {val}", diff=1, src="cost")


def t_words_tokens(rng):
    w = rng.choice([300, 750, 1500, 3000, 7500, 15000])
    v = round(w / 0.75)
    val = f"about {v:,}"
    wrong = pick_distractors(val, [f"about {round(w * 0.75):,}", f"about {w:,}", f"about {w * 4:,}", f"about {round(w / 4):,}"])
    return make_q(rng, f"Using the rule of thumb of ~0.75 English words per token, about how many tokens is a {w:,}-word document?", val, wrong, 2, "calculation",
                  hint="tokens ≈ words / 0.75.", expl=f"{w:,} / 0.75 ≈ {v:,} tokens", diff=1, src="wt")


def t_softmax(rng):
    l1, l2 = rng.choice([(2.0, 1.0), (3.0, 1.0), (1.5, 0.5), (4.0, 2.0), (2.0, 0.0)])
    T = rng.choice([0.5, 1.0, 2.0])
    p = math.exp(l1 / T) / (math.exp(l1 / T) + math.exp(l2 / T))
    val = fmt(round(p, 3))
    wrong = pick_distractors(val, [fmt(round(l1 / (l1 + l2), 3)), fmt(round(math.exp(l1) / (math.exp(l1) + math.exp(l2)), 3)) if T != 1 else fmt(round(1 - p, 3)),
                                   fmt(round(1 - p, 3)), fmt(round(min(0.999, p + 0.1), 3)), "0.5"])
    if not wrong:
        return None
    return make_q(rng, f"A model has only two candidate tokens with logits {l1} and {l2}. With temperature {T}, what probability does softmax assign to the first token? (3 decimals)",
                  val, wrong, 4, "calculation", hint="p₁ = e^(l₁/T) / (e^(l₁/T) + e^(l₂/T)).", expl=f"e^({l1}/{T}) / (e^({l1}/{T}) + e^({l2}/{T})) = {val}", diff=3, src="softmax")


def t_temp_effect(rng):
    T = rng.choice([0.2, 1.0, 1.8])
    val = {0.2: "the most likely token becomes even more dominant (sharper distribution)", 1.0: "the model's original distribution is used unchanged",
           1.8: "probabilities become flatter, so unlikely tokens are chosen more often"}[T]
    opts = ["the most likely token becomes even more dominant (sharper distribution)", "the model's original distribution is used unchanged",
            "probabilities become flatter, so unlikely tokens are chosen more often", "the vocabulary size changes"]
    return make_q(rng, f"What happens to the next-token distribution at temperature {T}?", val, [o for o in opts if o != val], 4, "calculation",
                  hint="Temperature divides the logits before softmax.", expl=f"T = {T}: {val}.", diff=1, src="temp")


def t_top_p(rng):
    probs = sorted([round(x, 2) for x in rng.sample([0.4, 0.25, 0.15, 0.1, 0.05, 0.03, 0.02, 0.35, 0.2, 0.3], 5)], reverse=True)
    s = sum(probs); probs = [round(p / s, 2) for p in probs]
    toks = ["A", "B", "C", "D", "E"]
    p = rng.choice([0.5, 0.7, 0.8, 0.9, 0.95])
    cum, n = 0, 0
    for x in probs:
        cum += x; n += 1
        if cum >= p - 1e-9:
            break
    val = "{" + ", ".join(toks[:n]) + "}"
    wrong = pick_distractors(val, ["{" + ", ".join(toks[:k]) + "}" for k in range(1, 6) if k != n])
    if not wrong:
        return None
    return make_q(rng, f"Next-token probabilities are {dict(zip(toks, probs))}. With top_p = {p}, which tokens remain in the nucleus?", val, wrong, 4,
                  "calculation", hint="Add probabilities from the most likely downward until the sum reaches p.", expl=f"Cumulative sum reaches {p} after {n} token(s): {val}.",
                  diff=2, src="topp")


def t_lora(rng):
    d = rng.choice([1024, 2048, 4096, 5120, 8192]); k = rng.choice([d, d * 4 if d <= 2048 else d]); r = rng.choice([4, 8, 16, 32, 64])
    v = r * (d + k)
    val = f"{v:,}"
    wrong = pick_distractors(val, [f"{d * k:,}", f"{r * d:,}", f"{r * d * k:,}", f"{2 * v:,}", f"{r * r:,}"])
    return make_q(rng, f"LoRA with rank r = {r} adapts a {d}×{k} weight matrix. How many trainable parameters does this adapter add?", val, wrong, 9, "calculation",
                  hint="ΔW = B·A with B: d×r and A: r×k → r×(d+k) parameters.", expl=f"{r} × ({d} + {k}) = {val} (vs {d * k:,} for full fine-tuning).", diff=2, src="lora")


def t_moe(rng):
    n, k = rng.choice([(8, 2), (16, 2), (64, 8), (128, 8), (32, 4)])
    expert = rng.choice([1, 2, 5, 7]); shared = rng.choice([1, 2, 5, 10])
    active = shared + k * expert; total = shared + n * expert
    ask = rng.choice(["active", "total"])
    v = active if ask == "active" else total
    val = f"{v}B"
    wrong = pick_distractors(val, [f"{total}B", f"{active}B", f"{n * expert}B", f"{k * expert}B", f"{shared + expert}B"])
    if not wrong:
        return None
    return make_q(rng, f"An MoE model has {shared}B shared (non-expert) parameters and {n} experts of {expert}B each; the router activates {k} experts per token. How many {ask} parameters does it have?",
                  val, wrong, 7, "calculation", hint="Total = shared + all experts; active = shared + experts used per token.", expl=f"total = {total}B, active = {active}B.", diff=2, src="moe")


def t_context_budget(rng):
    win = rng.choice([8192, 32768, 128000, 200000, 1_000_000]); sys = rng.choice([500, 1500, 4000]); docs = rng.choice([2000, 6000, 20000, 60000])
    out = rng.choice([1000, 4000, 8000])
    if sys + docs + out >= win:
        return None
    v = win - sys - docs - out
    val = f"{v:,}"
    wrong = pick_distractors(val, [f"{win - sys - docs:,}", f"{win - docs:,}", f"{v + out:,}", f"{win - sys:,}"])
    if not wrong:
        return None
    return make_q(rng, f"A model has a {win:,}-token context window. The system prompt uses {sys:,} tokens, retrieved documents {docs:,}, and you reserve {out:,} for the answer. How many tokens remain for chat history?",
                  val, wrong, 5, "calculation", hint="Input and output share the context window.", expl=f"{win:,} − {sys:,} − {docs:,} − {out:,} = {val}", diff=1, src="ctx")


def t_cache_saving(rng):
    prefix = rng.choice([10000, 20000, 50000]); q = rng.choice([200, 500, 1000]); reqs = rng.choice([100, 1000])
    price = rng.choice([1.0, 3.0, 5.0]); disc = rng.choice([0.1, 0.25])
    full = reqs * (prefix + q) * price / 1e6
    cached = (prefix * price + (reqs - 1) * prefix * price * disc + reqs * q * price) / 1e6
    val = "$" + fmt(round(cached, 3), 3)
    wrong = pick_distractors(val, ["$" + fmt(round(full, 3), 3), "$" + fmt(round(full * disc, 3), 3), "$" + fmt(round(reqs * q * price / 1e6, 3), 3),
                                   "$" + fmt(round(cached * 2, 3), 3)])
    if not wrong:
        return None
    return make_q(rng, f"{reqs} requests share a {prefix:,}-token prefix plus a {q}-token question. Input costs ${price}/1M; cached prefix reads cost {int(disc * 100)}% of that. "
                       "If the first request writes the cache at full price and the rest hit it, what is the total input cost?", val, wrong, 5, "calculation",
                  hint="First request pays full prefix price; later ones pay the discounted rate for the prefix; questions always pay full price.",
                  expl=f"Without caching it would be ${fmt(round(full, 3), 3)}; with caching {val}.", diff=3, src="cache")


def generate(rng, lesson_titles, limit=1900):
    return Collector(rng, limit, per_src_cap=320).run_templates([t_weights, t_kv, t_cost, t_words_tokens, t_softmax, t_temp_effect, t_top_p, t_lora, t_moe, t_context_budget,
                                                t_cache_saving])
