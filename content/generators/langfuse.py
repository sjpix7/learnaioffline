"""Computed Langfuse questions: trace-tree structure, prompt label resolution, sampling and cost arithmetic."""
from .common import Collector, make_q, pick_distractors, fmt, num_distractors

TYPES = ["span", "generation", "retriever", "tool", "agent", "embedding", "chain", "guardrail"]
NAMES = ["handle-request", "retrieve", "rerank", "llm-call", "search", "embed-query", "grade", "answer", "guard", "plan", "tool-call", "format"]


def build_tree(rng):
    names = rng.sample(NAMES, rng.randint(3, 6))
    nodes = [{"name": names[0], "type": "span", "parent": None, "depth": 1, "children": []}]
    for n in names[1:]:
        parent = rng.choice([x for x in nodes if x["depth"] < 3])
        node = {"name": n, "type": rng.choice(TYPES), "parent": parent["name"], "depth": parent["depth"] + 1, "children": []}
        parent["children"].append(node); nodes.append(node)
    return nodes


def render(node, indent=0):
    pad = "    " * indent
    head = f'{pad}with langfuse.start_as_current_observation(as_type="{node["type"]}", name="{node["name"]}"):'
    if not node["children"]:
        return head + f"\n{pad}    pass"
    return head + "\n" + "\n".join(render(c, indent + 1) for c in node["children"])


def t_tree(rng):
    nodes = build_tree(rng)
    code = "langfuse = get_client()\n" + render(nodes[0])
    kind = rng.randint(0, 3)
    if kind == 0:
        stem, val = "How many observations does this trace contain?", str(len(nodes))
        wrong = [str(len(nodes) + d) for d in (-1, 1, 2, -2) if len(nodes) + d > 0]
        expl = f"Each with-block creates one observation: {', '.join(n['name'] for n in nodes)}."
    elif kind == 1:
        target = rng.choice(nodes[1:])
        stem, val = f"Which observation is the parent of \"{target['name']}\"?", target["parent"]
        wrong = [n["name"] for n in nodes if n["name"] not in (target["parent"], target["name"])] + ["none (it is a root)"]
        expl = f"\"{target['name']}\" is created inside the with-block of \"{target['parent']}\", so that observation is its parent."
    elif kind == 2:
        stem, val = f"How many direct children does \"{nodes[0]['name']}\" have?", str(len(nodes[0]["children"]))
        wrong = [str(len(nodes[0]["children"]) + d) for d in (1, -1, 2, 3) if len(nodes[0]["children"]) + d >= 0]
        expl = "Only observations created directly inside its with-block (not deeper) are direct children."
    else:
        depth = max(n["depth"] for n in nodes)
        stem, val = "What is the maximum nesting depth of this trace (root = 1)?", str(depth)
        wrong = [str(d) for d in (1, 2, 3, 4, 5) if d != depth]
        expl = "Depth increases by one for each nested with-block."
    wrong = pick_distractors(val, rng.sample(wrong, len(wrong)))
    if not wrong:
        return None
    return make_q(rng, stem, val, wrong, 3, "calculation", code=code, hint="start_as_current_observation makes the new observation the parent of anything created inside its block.",
                  expl=expl, expl_code=code, diff=2, src="lf-tree")


def t_observe_tree(rng):
    fns = rng.sample(["pipeline", "retrieve", "rerank", "generate", "embed", "grade"], rng.randint(3, 4))
    root = fns[0]
    calls = {f: [] for f in fns}
    for f in fns[1:]:
        parent = rng.choice([p for p in fns if p != f and fns.index(p) < fns.index(f)])
        calls[parent].append(f)
    code = "from langfuse import observe\n" + "\n".join(
        f"@observe()\ndef {f}():\n" + ("\n".join(f"    {c}()" for c in calls[f]) if calls[f] else "    return 'ok'") for f in reversed(fns)) + f"\n\n{root}()"
    target = rng.choice(fns[1:])
    parent = next(p for p, cs in calls.items() if target in cs)
    wrong = [f for f in fns if f not in (parent, target)] + ["it is a separate trace"]
    wrong = pick_distractors(parent, rng.sample(wrong, len(wrong)))
    if not wrong:
        return None
    return make_q(rng, f"In the resulting trace, which observation is the parent of \"{target}\"?", parent, wrong, 3, "calculation", code=code,
                  hint="@observe nests observations following the call stack.", expl=f"{target}() is called from inside {parent}(), so its observation nests under {parent}.",
                  expl_code=code, diff=2, src="lf-obs")


def t_prompt_labels(rng):
    n = rng.randint(3, 7)
    prod = rng.randint(1, n - 1); stag = rng.randint(prod, n)
    ask = rng.choice(["default", "latest", "staging", "move"])
    setup = f"# prompt \"rag-answer\" has versions 1..{n}\n# labels: production -> v{prod}, staging -> v{stag}, latest -> v{n}\n"
    if ask == "default":
        call, val = 'langfuse.get_prompt("rag-answer")', prod
    elif ask == "latest":
        call, val = 'langfuse.get_prompt("rag-answer", label="latest")', n
    elif ask == "staging":
        call, val = 'langfuse.get_prompt("rag-answer", label="staging")', stag
    else:
        newv = rng.randint(1, n)
        setup += f"# you move the production label to v{newv}\n"
        call, val = 'langfuse.get_prompt("rag-answer")', newv
    code = setup + f"prompt = {call}\nprint(prompt.version)"
    cands = [str(x) for x in {prod, stag, n, 1, max(1, n - 1)} if x != val]
    wrong = pick_distractors(str(val), rng.sample(cands, len(cands)) + [str(val + 1), str(max(1, val - 1))])
    if not wrong:
        return None
    return make_q(rng, "Which prompt version is returned?", str(val), wrong, 6, "calculation", code=code,
                  hint="get_prompt without a label returns the version labelled production; latest is always the newest version.",
                  expl=f"The call resolves to version {val}.", expl_code=code, diff=2, src="lf-prompt")


def t_sampling(rng):
    n = rng.choice([1000, 2500, 5000, 10000, 40000, 120000]); rate = rng.choice([0.05, 0.1, 0.2, 0.25, 0.5])
    val = int(n * rate)
    wrong = [str(int(n * (1 - rate))), str(int(n * rate * 2)), str(int(n * rate / 2)), str(n)]
    code = f"langfuse = Langfuse(sample_rate={rate})\n# the app handles {n:,} requests, one trace each"
    wrong = pick_distractors(str(val), wrong)
    return make_q(rng, "About how many traces will be stored?", str(val), wrong, 5, "calculation", code=code,
                  hint="sample_rate is the fraction of traces kept.", expl=f"{n:,} × {rate} = {val:,} traces (sampling is per trace).", diff=1, src="lf-sample")


def t_cost(rng):
    inp = rng.choice([800, 1200, 2500, 4000, 12000, 30000]); out = rng.choice([150, 300, 600, 1000, 2000])
    pin = rng.choice([0.15, 0.4, 1.25, 2.5, 3.0, 5.0]); pout = rng.choice([0.6, 1.6, 10.0, 15.0, 25.0])
    calls = rng.choice([1, 10, 100, 1000])
    val = calls * (inp * pin + out * pout) / 1e6
    vs = fmt(round(val, 4), 4)
    wrong = num_distractors(val, rng, 4) + [fmt(round(calls * (inp + out) * pin / 1e6, 4), 4), fmt(round(calls * (inp * pout + out * pin) / 1e6, 4), 4)]
    wrong = pick_distractors(vs, wrong)
    if not wrong:
        return None
    return make_q(rng, f"A generation uses {inp:,} input and {out:,} output tokens. Prices: ${pin}/1M input, ${pout}/1M output. "
                       f"What is the total cost of {calls:,} such calls (USD)?", vs, wrong, 5, "calculation",
                  hint="cost = input/1e6 × input price + output/1e6 × output price, times the number of calls.",
                  expl=f"{calls} × ({inp}×{pin} + {out}×{pout}) / 1,000,000 = ${vs}", diff=2, src="lf-cost")


def t_sessions(rng):
    sess = [rng.choice(["s1", "s2", "s3", "s4"]) for _ in range(rng.randint(5, 9))]
    q = rng.choice(["count", "largest"])
    rows = "\n".join(f"trace {i + 1}: session_id={s}" for i, s in enumerate(sess))
    from collections import Counter
    c = Counter(sess)
    if q == "count":
        val, stem = str(len(c)), "How many sessions will Langfuse show?"
        wrong = [str(len(sess)), str(len(c) + 1), str(max(1, len(c) - 1)), "1"]
    else:
        top = max(c.values())
        val, stem = str(top), "How many traces does the largest session contain?"
        wrong = [str(top + 1), str(len(sess)), str(max(1, top - 1)), str(len(c))]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, stem, val, wrong, 5, "calculation", code=rows, hint="Traces sharing a session_id are grouped into one session.",
                  expl=f"Session sizes: {dict(sorted(c.items()))}.", diff=1, src="lf-sess")


def t_feedback(rng):
    n = rng.randint(8, 40); up = rng.randint(1, n)
    val = fmt(round(100 * up / n, 1), 1) + "%"
    wrong = [fmt(round(100 * (n - up) / n, 1), 1) + "%", fmt(round(100 * up / (n + up), 1), 1) + "%", fmt(round(100 * min(1, up / max(1, n - up)), 1), 1) + "%",
             fmt(round(100 * (up + 1) / n, 1), 1) + "%"]
    wrong = pick_distractors(val, wrong)
    if not wrong:
        return None
    return make_q(rng, f"{n} traces received a BOOLEAN \"thumbs\" score; {up} of them are 1 (thumbs up). What share of rated traces are positive?",
                  val, wrong, 7, "calculation", hint="Positive share = positive scores / all rated traces.", expl=f"{up}/{n} = {val}", diff=1, src="lf-fb")


TEMPLATES = [t_tree, t_tree, t_observe_tree, t_prompt_labels, t_sampling, t_cost, t_sessions, t_feedback]


def generate(rng, lesson_titles, limit=1300):
    return Collector(rng, limit, per_src_cap=420).run_templates(TEMPLATES)
