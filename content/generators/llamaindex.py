"""Verified LlamaIndex questions: Workflows are executed with llama-index-workflows; retrieval arithmetic is computed."""
import math
from .common import code_q, Collector, make_q, pick_distractors, fmt

IMP = "import asyncio\nfrom workflows import Workflow, step, Context\nfrom workflows.events import StartEvent, StopEvent, Event\n"
RUN = "\nasync def main():\n    return await W(timeout=10).run({kw})\nprint(asyncio.run(main()))"


def t_wf_chain(rng):
    a, b, c = rng.randint(1, 5), rng.randint(2, 4), rng.randint(1, 9)
    n = rng.randint(1, 6)
    code = (IMP + "class Mid(Event):\n    x: int\nclass W(Workflow):\n    @step\n    async def first(self, ev: StartEvent) -> Mid:\n"
            f"        return Mid(x=ev.n + {a})\n    @step\n    async def second(self, ev: Mid) -> StopEvent:\n        return StopEvent(result=ev.x * {b} - {c})" + RUN.format(kw=f"n={n}"))
    alts = [IMP + f"print({(n * b + a) - c})", IMP + f"print({(n + a) * b})", IMP + f"print({n * b - c})", IMP + f"print({(n + a) * (b - c)})",
            IMP + f"print({n + a})"]
    return code_q(rng, code, 8, alts, hint="StartEvent carries run() kwargs; each step is triggered by the event type in its signature.")


def t_wf_branch(rng):
    t = rng.randint(3, 8); n = rng.randint(0, 12)
    code = (IMP + "class Small(Event):\n    v: int\nclass Big(Event):\n    v: int\nclass W(Workflow):\n    @step\n    async def route(self, ev: StartEvent) -> Small | Big:\n"
            f"        return Big(v=ev.n) if ev.n >= {t} else Small(v=ev.n)\n    @step\n    async def small(self, ev: Small) -> StopEvent:\n        return StopEvent(result=f\"small:{{ev.v}}\")\n"
            "    @step\n    async def big(self, ev: Big) -> StopEvent:\n        return StopEvent(result=f\"big:{ev.v}\")" + RUN.format(kw=f"n={n}"))
    alts = [f"print('small:{n}')", f"print('big:{n}')", f"print('small:{t}')", f"print('big:{t}')", "print('None')"]
    return code_q(rng, code, 8, alts, hint="A step returning a union of events branches; the matching step handles the emitted event.")


def t_wf_loop(rng):
    step_ = rng.randint(2, 5); limit = rng.randint(6, 20)
    code = (IMP + "class Again(Event):\n    v: int\nclass W(Workflow):\n    @step\n    async def start(self, ev: StartEvent) -> Again:\n        return Again(v=ev.n)\n"
            f"    @step\n    async def grow(self, ctx: Context, ev: Again) -> Again | StopEvent:\n        loops = await ctx.store.get(\"loops\", default=0)\n"
            f"        await ctx.store.set(\"loops\", loops + 1)\n        if ev.v >= {limit}:\n            return StopEvent(result=(ev.v, loops + 1))\n"
            f"        return Again(v=ev.v + {step_})" + RUN.format(kw="n=1"))
    loops = 1 + max(0, math.ceil((limit - 1) / step_))
    final = 1 + (loops - 1) * step_
    alts = [f"print(({final}, {loops - 1}))", f"print(({final - step_}, {loops}))", f"print(({limit}, {loops}))", f"print(({final + step_}, {loops + 1}))"]
    return code_q(rng, code, 8, alts, hint="A step can emit the same event type it consumes to create a loop; ctx.store keeps state between iterations.")


def t_wf_store(rng):
    a, b = rng.sample(["rag", "graph", "agent", "query"], 2)
    code = (IMP + "class Next(Event):\n    pass\nclass W(Workflow):\n    @step\n    async def one(self, ctx: Context, ev: StartEvent) -> Next:\n"
            f"        await ctx.store.set(\"topic\", ev.topic)\n        return Next()\n    @step\n    async def two(self, ctx: Context, ev: Next) -> StopEvent:\n"
            f"        topic = await ctx.store.get(\"topic\")\n        return StopEvent(result=topic.upper() + \"-{b}\")" + RUN.format(kw=f"topic=\"{a}\""))
    alts = [f"print('{a}-{b}')", f"print('{b.upper()}-{a}')", f"print('{a.upper()}')", "print('None')", f"print('TOPIC-{b}')"]
    return code_q(rng, code, 8, alts, hint="ctx.store shares values between steps of the same run.")


def t_cutoff(rng):
    scores = sorted([round(rng.uniform(0.4, 0.95), 2) for _ in range(rng.randint(4, 8))], reverse=True)
    cut = rng.choice([0.6, 0.65, 0.7, 0.75, 0.8])
    kept = sum(1 for s in scores if s >= cut)
    val = str(kept)
    wrong = pick_distractors(val, [str(len(scores) - kept), str(kept + 1), str(max(0, kept - 1)), str(len(scores))])
    if not wrong:
        return None
    return make_q(rng, f"A retriever returns nodes with scores {scores}. After SimilarityPostprocessor(similarity_cutoff={cut}), how many nodes remain?",
                  val, wrong, 5, "calculation", hint="Nodes scoring below the cutoff are dropped.", expl=f"Scores ≥ {cut}: {kept} nodes.", diff=1, src="li-cut")


def t_topk(rng):
    n = rng.randint(2, 30); k = rng.randint(2, 12)
    val = str(min(n, k))
    wrong = pick_distractors(val, [str(max(n, k)), str(k + 1), str(n - 1 if n > 1 else 3), str(k * 2)])
    if not wrong:
        return None
    return make_q(rng, f"An index contains {n} nodes. How many nodes does index.as_retriever(similarity_top_k={k}).retrieve(q) return?",
                  val, wrong, 5, "calculation", hint="top-k can't return more nodes than exist.", expl=f"min({n}, {k}) = {val}", diff=1, src="li-topk")


def t_calls(rng):
    n = rng.randint(2, 10)
    mode, calls = rng.choice([("refine", n), ("accumulate", n), ("simple_summarize", 1), ("no_text", 0)])
    val = str(calls)
    wrong = pick_distractors(val, [str(n), "1", "0", str(n + 1), str(2 * n)])
    if not wrong:
        return None
    return make_q(rng, f"A query engine with response_mode=\"{mode}\" retrieves {n} small chunks (each fits in the context window). "
                       "How many LLM calls does synthesis make?", val, wrong, 5, "calculation",
                  hint="refine and accumulate call the LLM per chunk; simple_summarize makes one call; no_text makes none.",
                  expl=f"{mode} with {n} chunks → {calls} LLM call(s).", diff=2, src="li-calls")


def t_hit_mrr(rng):
    k = rng.choice([3, 5]); m = rng.randint(3, 6)
    ranks = [rng.choice([1, 2, 3, 4, 5, 6, None]) for _ in range(m)]
    if rng.random() < 0.5:
        hits = sum(1 for r in ranks if r is not None and r <= k)
        val = fmt(round(hits / m, 3))
        wrong = [fmt(round((hits + 1) / m, 3)), fmt(round(max(0, hits - 1) / m, 3)), fmt(round(sum(1 for r in ranks if r) / m, 3)), fmt(round(hits / k, 3))]
        stem = f"For {m} queries the rank of the relevant chunk was {['miss' if r is None else r for r in ranks]}. What is hit rate@{k}?"
        expl = f"{hits} of {m} queries have the relevant chunk within the top {k}: {val}."
    else:
        rr = [0 if r is None else 1 / r for r in ranks]
        val = fmt(round(sum(rr) / m, 3))
        wrong = [fmt(round(sum(rr) / max(1, sum(1 for r in ranks if r)), 3)), fmt(round(sum(r or 0 for r in ranks) / m, 3)),
                 fmt(round(sum(rr), 3)), fmt(round(sum(rr) / m + 0.1, 3))]
        stem = f"For {m} queries the rank of the first relevant chunk was {['miss' if r is None else r for r in ranks]}. What is the MRR?"
        expl = "MRR = mean of 1/rank (a miss counts as 0): " + " + ".join(fmt(round(x, 3)) for x in rr) + f" over {m} = {val}."
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, stem, val, wrong, 9, "calculation", hint="Hit rate counts queries with a relevant result in top-k; MRR averages 1/rank.", expl=expl,
                  diff=2, src="li-eval")


TEMPLATES = [t_wf_chain, t_wf_branch, t_wf_loop, t_wf_store, t_cutoff, t_topk, t_calls, t_hit_mrr]


def generate(rng, lesson_titles, limit=1000):
    return Collector(rng, limit).run_templates(TEMPLATES)
