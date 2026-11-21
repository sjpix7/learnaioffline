"""Computed agent questions: simulated agent loops (call counting), budgets, cost and success-rate arithmetic."""
from .common import Collector, make_q, pick_distractors, fmt, code_q


def t_loop_calls(rng):
    rounds = [rng.randint(0, 3) for _ in range(rng.randint(1, 4))]  # tool calls requested in each model turn before the final answer
    model_calls = len(rounds) + 1
    tool_msgs = sum(rounds)
    turns = [r for r in rounds if r > 0]
    model_calls = len(turns) + 1
    tool_msgs = sum(turns)
    desc = "; ".join(f"turn {i + 1}: the model requests {n} tool call(s)" for i, n in enumerate(turns)) or "the model answers immediately"
    ask = rng.choice(["model", "tool"])
    val = str(model_calls if ask == "model" else tool_msgs)
    wrong = pick_distractors(val, [str(model_calls - 1), str(model_calls + 1), str(tool_msgs + 1), str(tool_msgs), str(model_calls + tool_msgs), str(len(turns))])
    if not wrong:
        return None
    what = "model (LLM) calls" if ask == "model" else "ToolMessages (tool results)"
    return make_q(rng, f"In a tool-calling agent loop, {desc}; then the model gives a final answer with no tool calls. How many {what} occur in total?",
                  val, wrong, 1, "calculation", hint="Each model turn is one LLM call (including the final answer); each requested tool call produces one ToolMessage.",
                  expl=f"Model calls = {len(turns)} tool turns + 1 final = {model_calls}; tool results = {tool_msgs}.", diff=2, src="ag-loop")


def t_sim(rng):
    plan = [rng.choice(["search", "calc", "lookup"]) for _ in range(rng.randint(1, 4))]
    limit = rng.randint(1, 5)
    code = ("def run_agent(plan, max_model_calls):\n    messages, calls = [], 0\n    while calls < max_model_calls:\n        calls += 1\n"
            "        if not plan:\n            return f\"answered after {calls} calls\"\n        tool = plan.pop(0)\n        messages.append(f\"result of {tool}\")\n"
            "    return f\"stopped: limit {max_model_calls} reached, {len(messages)} tool results\"\n"
            f"print(run_agent({plan}, max_model_calls={limit}))")
    n = len(plan)
    alts = [f"print('answered after {n} calls')", f"print('answered after {n + 1} calls')", f"print('stopped: limit {limit} reached, {limit} tool results')",
            f"print('stopped: limit {limit} reached, {max(0, limit - 1)} tool results')", f"print('answered after {limit} calls')"]
    return code_q(rng, code, 10, alts, hint="Each loop iteration is one model call; the final answer also costs a call.")


def t_budget_cost(rng):
    steps = rng.randint(3, 15); inp = rng.choice([2000, 4000, 8000, 15000]); out = rng.choice([200, 400, 800])
    growth = rng.choice([0, 500, 1000])
    pin, pout = rng.choice([(1.25, 10.0), (3.0, 15.0), (0.4, 1.6), (2.0, 8.0)])
    total_in = sum(inp + growth * i for i in range(steps))
    cost = (total_in * pin + steps * out * pout) / 1e6
    val = "$" + fmt(round(cost, 4), 4)
    wrong = pick_distractors(val, ["$" + fmt(round((steps * inp * pin + steps * out * pout) / 1e6, 4), 4) if growth else "$" + fmt(round(cost * 1.5, 4), 4),
                                   "$" + fmt(round(cost / steps, 4), 4), "$" + fmt(round(cost * 2, 4), 4), "$" + fmt(round((total_in + steps * out) * pin / 1e6, 4), 4)])
    if not wrong:
        return None
    return make_q(rng, f"An agent makes {steps} model calls. The first call sends {inp:,} input tokens and each later call sends {growth:,} more (history grows); every call outputs {out} tokens. "
                       f"At ${pin}/1M input and ${pout}/1M output, what does the run cost?", val, wrong, 10, "calculation",
                  hint="Sum input tokens over all calls (they grow), add output tokens, then apply prices.",
                  expl=f"Input tokens = {total_in:,}; output = {steps * out:,}; cost = {val}.", diff=3, src="ag-cost")


def t_success(rng):
    n = rng.choice([20, 50, 100, 200]); ok = rng.randint(n // 4, n)
    val = fmt(round(100 * ok / n, 1), 1) + "%"
    wrong = pick_distractors(val, [fmt(round(100 * (n - ok) / n, 1), 1) + "%", fmt(round(ok / n, 3)), fmt(round(100 * ok / (n + ok), 1), 1) + "%", f"{ok}%"])
    if not wrong:
        return None
    return make_q(rng, f"An agent is evaluated on {n} benchmark tasks and fully solves {ok}. What is its task success rate?", val, wrong, 10, "calculation",
                  hint="Success rate = solved / total.", expl=f"{ok}/{n} = {val}", diff=1, src="ag-sr")


def t_parallel_latency(rng):
    lat = [rng.randint(1, 9) for _ in range(rng.randint(2, 4))]
    mode = rng.choice(["sequential", "parallel"])
    v = sum(lat) if mode == "sequential" else max(lat)
    val = f"{v} s"
    wrong = pick_distractors(val, [f"{sum(lat)} s", f"{max(lat)} s", f"{min(lat)} s", f"{round(sum(lat) / len(lat), 1)} s"])
    if not wrong:
        return None
    return make_q(rng, f"Sub-agents take {lat} seconds respectively. Ignoring overhead, how long does the step take if they run in {mode}?", val, wrong, 5,
                  "calculation", hint="Sequential = sum of durations; parallel = the longest one.", expl=f"{mode} → {val}", diff=1, src="ag-lat")


def t_handoff_trace(rng):
    agents = ["Triage", "Billing", "Tech", "Refunds"]
    chain = ["Triage"] + rng.sample(agents[1:], rng.randint(1, 3))
    val = chain[-1]
    wrong = pick_distractors(val, [a for a in agents if a != val])
    desc = " → ".join(chain)
    return make_q(rng, f"In an OpenAI Agents SDK run, control passes by handoffs: {desc}. Which agent produces result.final_output?", val, wrong, 7,
                  "calculation", hint="After a handoff, the receiving agent takes over the conversation.", expl=f"The last agent in the handoff chain ({val}) answers.",
                  diff=1, src="ag-hand")


def generate(rng, lesson_titles, limit=900):
    return Collector(rng, limit, per_src_cap=260).run_templates([t_loop_calls, t_sim, t_budget_cost, t_success, t_parallel_latency, t_handoff_trace])
