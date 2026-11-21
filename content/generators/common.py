"""Shared helpers for computed-question generators. Every answer is produced by running
real code (or exact arithmetic), so correctness is verified at build time."""
import io, contextlib, random, math, sys
sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1]))
from qbank import make_q, qkey


def run(code, env=None):
    """Execute code and return its printed output (stripped) or the exception class name."""
    buf = io.StringIO()
    g = {"__name__": "__qgen__"}
    if env:
        g.update(env)
    try:
        with contextlib.redirect_stdout(buf):
            exec(compile(code, "<q>", "exec"), g)
    except BaseException as e:  # noqa: BLE001 - uncaught errors are marked with "!"
        return f"!{type(e).__name__}"
    return buf.getvalue().strip()


def pick_distractors(correct, candidates, k=3, rng=None):
    out, seen = [], {correct.strip()}
    for c in candidates:
        c = str(c).strip()
        if not c or c in seen or "\n" in c and "\n" not in correct:
            continue
        seen.add(c); out.append(c)
        if len(out) == k:
            break
    return out if len(out) == k else None


def fmt(x, nd=3):
    if isinstance(x, float):
        s = f"{x:.{nd}f}".rstrip("0").rstrip(".")
        return s if s not in ("-0", "") else "0"
    return str(x)


def num_distractors(value, rng, nd=3, spread=None, positive=True):
    """Plausible numeric distractors around a value."""
    cands = []
    base = value if value != 0 else 1
    factors = [0.5, 2, 0.8, 1.25, 1.5, 0.67, 1.1, 0.9, 3, 0.33]
    rng.shuffle(factors)
    for f in factors:
        v = value * f
        if positive and v < 0:
            continue
        cands.append(fmt(round(v, nd), nd))
    if spread:
        for s in spread:
            cands.append(fmt(round(value + s, nd), nd))
    return cands


class Collector:
    def __init__(self, rng, limit, per_src_cap=None):
        self.rng, self.limit, self.items, self.seen = rng, limit, [], set()
        self.cap, self.counts = per_src_cap, {}

    def add(self, q):
        if q is None:
            return False
        k = qkey(q)
        if k in self.seen:
            return False
        src = q.get("src", "")
        if self.cap and self.counts.get(src, 0) >= self.cap and src != "gen":
            return False
        self.counts[src] = self.counts.get(src, 0) + 1
        self.seen.add(k); self.items.append(q); return True

    def full(self):
        return len(self.items) >= self.limit

    def run_templates(self, templates, max_attempts=None):
        """Round-robin over template functions until the limit is reached."""
        attempts = 0
        max_attempts = max_attempts or self.limit * 25
        fails = {t: 0 for t in templates}
        active = list(templates)
        while not self.full() and active and attempts < max_attempts:
            for t in list(active):
                attempts += 1
                ok = False
                try:
                    ok = self.add(t(self.rng))
                except Exception as e:  # template bug -> skip this attempt
                    ok = False
                if not ok:
                    fails[t] += 1
                    if fails[t] > 60:
                        active.remove(t)
                else:
                    fails[t] = 0
                if self.full():
                    break
        return self.items


def code_q(rng, code, lesson, alts=(), extra=(), stem="What does this code print?", hint="", expl="", diff=2, kind="code-output", env=None, oneline=False):
    correct = run(code, env)
    if oneline:
        correct = " | ".join(correct.splitlines())
    if not correct or correct.startswith("!") or len(correct) > 90:
        return None
    cands = [run(a, env) for a in alts] + list(extra)
    cands = [(" | ".join(c.splitlines()) if oneline else c) for c in cands if not str(c).startswith("!")]
    rng.shuffle(cands)
    wrong = pick_distractors(correct, cands, rng=rng)
    if not wrong:
        return None
    e = (expl + " " if expl else "") + f"Output: {correct}"
    return make_q(rng, stem, correct, wrong, lesson, kind, hint=hint, expl=e, expl_code=code, code=code, diff=diff,
                  src="gen")


def calc_q(rng, stem, value, wrong, lesson, hint="", expl="", code="", diff=2):
    wrong = pick_distractors(value, wrong, rng=rng)
    if not wrong:
        return None
    return make_q(rng, stem, value, wrong, lesson, "calculation", hint=hint, expl=expl, expl_code=code, diff=diff,
                  src="calc")
