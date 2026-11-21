"""Computed framework questions: autograd derivatives, dense-layer parameter counts, weight memory, throughput."""
from .common import Collector, make_q, pick_distractors, fmt
from .llm_models import t_weights


def t_grad(rng):
    a, b, c = rng.randint(1, 5), rng.randint(-4, 6), rng.randint(0, 9); x = rng.randint(-3, 5)
    lib = rng.choice(["torch", "jax"])
    g = 2 * a * x + b
    if lib == "torch":
        code = f"import torch\nx = torch.tensor({float(x)}, requires_grad=True)\ny = {a} * x ** 2 + {b} * x + {c}\ny.backward()\nprint(x.grad.item())"
    else:
        code = f"import jax\nf = lambda x: {a} * x ** 2 + {b} * x + {c}\nprint(jax.grad(f)({float(x)}))"
    val = f"{float(g)}"
    wrong = pick_distractors(val, [f"{float(a * x * x + b * x + c)}", f"{float(a * x + b)}", f"{float(2 * a * x)}", f"{float(g + b)}", f"{float(2 * a + b)}"])
    if not wrong:
        return None
    return make_q(rng, "What value is printed (the derivative of y with respect to x)?", val, wrong, 2, "calculation", code=code,
                  hint="d/dx (a·x² + b·x + c) = 2a·x + b.", expl=f"2×{a}×{x} + {b} = {g}", expl_code=code, diff=2, src="grad")


def t_dense_params(rng):
    inp = rng.choice([4, 8, 16, 64, 128, 768]); h = rng.choice([8, 16, 32, 64, 256]); out = rng.choice([1, 2, 3, 10])
    p = inp * h + h + h * out + out
    code = f"model = keras.Sequential([\n    keras.Input(shape=({inp},)),\n    keras.layers.Dense({h}, activation=\"relu\"),\n    keras.layers.Dense({out}),\n])\nmodel.count_params()"
    val = f"{p:,}"
    wrong = pick_distractors(val, [f"{inp * h + h * out:,}", f"{(inp + 1) * h:,}", f"{inp * h * out:,}", f"{p + h:,}", f"{inp + h + out:,}"])
    if not wrong:
        return None
    return make_q(rng, "How many trainable parameters does this Keras model have?", val, wrong, 2, "calculation", code=code,
                  hint="A Dense layer with n inputs and m units has n×m weights + m biases.",
                  expl=f"({inp}×{h} + {h}) + ({h}×{out} + {out}) = {val}", expl_code=code, diff=2, src="dense")


def t_torch_linear(rng):
    i, o = rng.choice([(768, 256), (512, 512), (1024, 4096), (4096, 4096), (128, 10)])
    bias = rng.choice([True, False])
    p = i * o + (o if bias else 0)
    code = f"import torch.nn as nn\nlayer = nn.Linear({i}, {o}, bias={bias})\nprint(sum(p.numel() for p in layer.parameters()))"
    val = f"{p}"
    wrong = pick_distractors(val, [f"{i * o + (0 if bias else o)}", f"{i + o}", f"{(i + 1) * (o + 1)}", f"{i * o * 2}"])
    if not wrong:
        return None
    return make_q(rng, "What does this PyTorch snippet print?", val, wrong, 2, "calculation", code=code, hint="nn.Linear(in, out) has in×out weights plus out biases (if bias=True).",
                  expl=f"{i}×{o}" + (f" + {o}" if bias else "") + f" = {p}", expl_code=code, diff=2, src="linear")


def t_throughput(rng):
    users = rng.choice([10, 50, 200, 1000]); tps = rng.choice([20, 40, 80]); out = rng.choice([200, 500, 1000])
    t = out / tps
    val = f"{fmt(round(t, 2), 2)} s"
    wrong = pick_distractors(val, [f"{fmt(round(out * tps / 1000, 2), 2)} s", f"{fmt(round(t * users, 2), 2)} s", f"{fmt(round(tps / out, 2), 2)} s", f"{fmt(round(t / 2, 2), 2)} s"])
    if not wrong:
        return None
    return make_q(rng, f"A server streams {tps} tokens/second to each of {users} concurrent users. Ignoring time to first token, how long does a {out}-token answer take for one user?",
                  val, wrong, 6, "calculation", hint="Per-user decode time = output tokens / per-user tokens per second.", expl=f"{out}/{tps} = {val}", diff=1, src="tps")


def generate(rng, lesson_titles, limit=700):
    out = Collector(rng, limit, per_src_cap=200).run_templates([t_grad, t_dense_params, t_torch_linear, t_throughput, t_weights])
    for q in out:
        if q["src"] == "w":
            q["l"] = 5  # inference lesson (0-based)
    return out
