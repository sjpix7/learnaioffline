"""Computed Ragas metric questions using the metrics' documented formulas."""
import math
from .common import Collector, make_q, pick_distractors, fmt


def t_faith(rng):
    n = rng.randint(2, 8); s = rng.randint(0, n)
    val = fmt(round(s / n, 3))
    wrong = pick_distractors(val, [fmt(round((n - s) / n, 3)), fmt(round(s / (n + 1), 3)), fmt(round(min(1, (s + 1) / n), 3)), str(s), fmt(round(s / (2 * n), 3))])
    if not wrong:
        return None
    return make_q(rng, f"A response contains {n} claims; {s} of them can be inferred from the retrieved context. What is the faithfulness score?",
                  val, wrong, 2, "calculation", hint="Faithfulness = supported claims / total claims in the response.", expl=f"{s}/{n} = {val}", diff=1, src="faith")


def t_recall(rng):
    n = rng.randint(2, 8); s = rng.randint(0, n)
    val = fmt(round(s / n, 3))
    wrong = pick_distractors(val, [fmt(round((n - s) / n, 3)), fmt(round(s / (n + 2), 3)), fmt(round(min(1, (s + 1) / n), 3)), fmt(round(n / max(1, s + n), 3))])
    if not wrong:
        return None
    return make_q(rng, f"The reference answer has {n} claims; {s} of them are supported by the retrieved contexts. What is context recall?", val, wrong, 3,
                  "calculation", hint="Context recall = reference claims attributable to the context / total reference claims.", expl=f"{s}/{n} = {val}", diff=1, src="crecall")


def t_cprec(rng):
    rel = [rng.choice([0, 1]) for _ in range(rng.randint(3, 6))]
    if not any(rel):
        return None
    total, hits = 0.0, 0
    for k, v in enumerate(rel, 1):
        hits += v
        if v:
            total += hits / k
    cp = total / sum(rel)
    val = fmt(round(cp, 3))
    wrong = pick_distractors(val, [fmt(round(sum(rel) / len(rel), 3)), fmt(round(total, 3)), fmt(round(cp * 0.8, 3)), fmt(round(min(1, cp + 0.1), 3)), "1"])
    if not wrong:
        return None
    return make_q(rng, f"Relevance of retrieved chunks in rank order is {rel} (1 = relevant). What is context precision@{len(rel)}?", val, wrong, 3, "calculation",
                  hint="Average precision@k over the ranks k where the chunk is relevant.",
                  expl=f"Sum of precision@k at relevant ranks = {fmt(round(total, 3))}; divided by {sum(rel)} relevant = {val}", diff=3, src="cprec")


def t_relevancy(rng):
    sims = [round(rng.uniform(0.55, 0.98), 2) for _ in range(3)]
    v = sum(sims) / 3
    val = fmt(round(v, 3))
    wrong = pick_distractors(val, [fmt(round(max(sims), 3)), fmt(round(min(sims), 3)), fmt(round(sum(sims), 3)), fmt(round(sorted(sims)[1], 3))])
    if not wrong:
        return None
    return make_q(rng, f"For answer relevancy, the judge generated 3 questions from the response; their cosine similarities to the user input are {sims}. What is the score?",
                  val, wrong, 3, "calculation", hint="Answer relevancy is the mean of the cosine similarities.", expl=f"mean({sims}) = {val}", diff=1, src="arel")


def t_f1(rng):
    tp, fp, fn = rng.randint(1, 6), rng.randint(0, 4), rng.randint(0, 4)
    p, r = tp / (tp + fp), tp / (tp + fn)
    f1 = 2 * p * r / (p + r)
    kind = rng.choice(["precision", "recall", "F1"])
    v = {"precision": p, "recall": r, "F1": f1}[kind]
    val = fmt(round(v, 3))
    wrong = pick_distractors(val, [fmt(round(x, 3)) for x in (p, r, f1, (p + r) / 2, tp / (tp + fp + fn))])
    if not wrong:
        return None
    return make_q(rng, f"Factual correctness: {tp} response claims are supported by the reference (TP), {fp} are not (FP), and {fn} reference claims are missing from the response (FN). What is the {kind}?",
                  val, wrong, 4, "calculation", hint="precision = TP/(TP+FP); recall = TP/(TP+FN); F1 = 2PR/(P+R).",
                  expl=f"P = {fmt(round(p, 3))}, R = {fmt(round(r, 3))}, F1 = {fmt(round(f1, 3))}.", diff=2, src="f1")


def t_entities(rng):
    ref = rng.randint(2, 8); found = rng.randint(0, ref)
    val = fmt(round(found / ref, 3))
    wrong = pick_distractors(val, [fmt(round((ref - found) / ref, 3)), fmt(round(found / (ref + 2), 3)), fmt(round(min(1, (found + 1) / ref), 3)), str(found)])
    if not wrong:
        return None
    return make_q(rng, f"The reference mentions {ref} entities; {found} of them also appear in the retrieved contexts. What is context entities recall?", val, wrong, 4,
                  "calculation", hint="Entities in both context and reference / entities in the reference.", expl=f"{found}/{ref} = {val}", diff=1, src="ent")


def t_noise(rng):
    n = rng.randint(3, 10); bad = rng.randint(0, n)
    val = fmt(round(bad / n, 3))
    wrong = pick_distractors(val, [fmt(round((n - bad) / n, 3)), fmt(round(bad / (n + 1), 3)), fmt(round(min(1, (bad + 1) / n), 3)), str(bad)])
    if not wrong:
        return None
    return make_q(rng, f"For noise sensitivity, {bad} of the response's {n} claims are incorrect. What is the score (and is lower or higher better)?",
                  f"{val} (lower is better)", [f"{w} (lower is better)" for w in wrong[:2]] + [f"{val} (higher is better)"], 4, "calculation",
                  hint="Noise sensitivity = incorrect claims / total claims; it measures errors, so lower is better.", expl=f"{bad}/{n} = {val}; lower is better.",
                  diff=2, src="noise")


def t_mean_gate(rng):
    scores = [round(rng.uniform(0.5, 1.0), 2) for _ in range(rng.randint(4, 6))]
    th = rng.choice([0.7, 0.75, 0.8, 0.85])
    m = sum(scores) / len(scores)
    val = f"{'passes' if m >= th else 'fails'} (mean {m:.3f})"
    other = f"{'fails' if m >= th else 'passes'} (mean {m:.3f})"
    wrong = pick_distractors(val, [other, f"passes (mean {max(scores):.3f})", f"fails (mean {min(scores):.3f})", f"passes (mean {sorted(scores)[len(scores)//2]:.3f})"])
    if not wrong:
        return None
    return make_q(rng, f"A CI gate requires mean faithfulness ≥ {th}. Sample scores: {scores}. Does the build pass?", val, wrong, 1, "calculation",
                  hint="Compare the mean of the scores with the threshold.", expl=f"mean = {m:.3f} → {val.split(' ')[0]}", diff=1, src="gate")


def t_metric_choice(rng):
    sym, metric = rng.choice([("answers contain facts not found in the retrieved chunks", "faithfulness"),
                              ("relevant chunks exist in the results but are ranked below irrelevant ones", "context precision"),
                              ("key facts from the reference are missing from the retrieved chunks", "context recall"),
                              ("answers are accurate but drift away from what was asked", "answer relevancy"),
                              ("the agent calls tools in the wrong order", "tool call accuracy")])
    opts = ["faithfulness", "context precision", "context recall", "answer relevancy", "tool call accuracy"]
    return make_q(rng, f"Which Ragas metric best detects this problem: {sym}?", metric, rng.sample([o for o in opts if o != metric], 3), 3, "calculation",
                  hint="Map each symptom to retrieval (precision/recall) or generation (faithfulness/relevancy).", expl=f"Use {metric}.", diff=1, src="choice")


def generate(rng, lesson_titles, limit=1800):
    return Collector(rng, limit, per_src_cap=330).run_templates([t_faith, t_recall, t_cprec, t_relevancy, t_f1, t_entities, t_noise, t_mean_gate, t_metric_choice])
