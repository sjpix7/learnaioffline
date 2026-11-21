"""Verified Python code-output questions: every answer comes from executing the snippet."""
from .common import code_q, Collector, run
import string

W = ["rag", "agent", "graph", "chain", "embed", "token", "vector", "index", "model", "prompt", "query", "tool"]


def r_list(rng, n=None, lo=1, hi=9):
    n = n or rng.randint(4, 7)
    return [rng.randint(lo, hi) * 10 for _ in range(n)]


def t_slice(rng):
    xs = r_list(rng)
    a, b = sorted(rng.sample(range(0, len(xs) + 1), 2))
    step = rng.choice([1, 1, 2])
    s = f"{a}:{b}" + (f":{step}" if step != 1 else "")
    code = f"nums = {xs}\nprint(nums[{s}])"
    alts = [f"nums = {xs}\nprint(nums[{a}:{b+1}])", f"nums = {xs}\nprint(nums[{a+1}:{b}])",
            f"nums = {xs}\nprint(nums[{max(a-1,0)}:{b}])", f"nums = {xs}\nprint(nums[{a}:{b}][::-1])",
            f"nums = {xs}\nprint(nums[{a+1}:{b+1}])"]
    return code_q(rng, code, 1, alts, hint="Slices include the start index and exclude the stop index.",
                  expl=f"nums[{s}] starts at index {a} and stops before index {b}.")


def t_neg_index(rng):
    w = rng.choice(["LangGraph", "Embedding", "Pipeline", "Retriever", "Reranker", "Workflow"])
    i = rng.randint(1, 4)
    j = rng.randint(2, 5)
    code = f"s = \"{w}\"\nprint(s[-{i}], s[-{j}:])"
    alts = [f"s = \"{w}\"\nprint(s[-{i+1}], s[-{j}:])", f"s = \"{w}\"\nprint(s[{i}], s[-{j}:])",
            f"s = \"{w}\"\nprint(s[-{i}], s[:-{j}])", f"s = \"{w}\"\nprint(s[-{i-1 or 2}], s[-{j+1}:])"]
    return code_q(rng, code, 1, alts, hint="Negative indices count from the end: -1 is the last character.")


def t_step_slice(rng):
    w = rng.choice(["abcdefghij", "0123456789", "retrieval", "embeddings", "transformer"])
    st = rng.choice([2, 3, -1, -2])
    code = f"print(\"{w}\"[::{st}])"
    alts = [f"print(\"{w}\"[1::{abs(st)}])", f"print(\"{w}\"[::{-st}])", f"print(\"{w}\"[::{st}][::-1])", f"print(\"{w}\"[1::{st}])"]
    return code_q(rng, code, 1, alts, hint="A negative step walks the sequence backwards.")


def t_divmod(rng):
    a = rng.randint(5, 40) * rng.choice([1, -1])
    b = rng.randint(2, 7)
    code = f"print({a} // {b}, {a} % {b})"
    alts = [f"print(int({a} / {b}), {a} % {b})", f"print({a} // {b}, -({abs(a)} % {b}))", f"print({a} // {b} + 1, {a} % {b})",
            f"print(int({a} / {b}), {a} - int({a} / {b}) * {b})"]
    return code_q(rng, code, 1, alts, hint="// floors toward negative infinity; % takes the sign of the divisor.")


def t_true_div(rng):
    a, b = rng.randint(2, 9) * 2, 2
    code = f"x = {a} / {b}\nprint(x, type(x).__name__)"
    alts = [f"print({a//b}, 'int')", f"print({a//b}.0, 'int')", f"print({a//b}, 'float')"]
    return code_q(rng, code, 1, alts, hint="In Python 3, / always returns a float.")


def t_round(rng):
    v = rng.choice([0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 2.675, 7.5])
    code = f"print(round({v}))" if v != 2.675 else "print(round(2.675, 2))"
    alts = [f"print(int({v}) + 1)", f"print(int({v}))", f"print(int({v}) + 2)"] if v != 2.675 else ["print(2.68)", "print(2.7)", "print(2.6)"]
    return code_q(rng, code, 1, alts, hint="round() uses banker's rounding (halves go to the even neighbour) and floats are binary.")


def t_bool_sum(rng):
    vals = [rng.choice(["True", "False", "0", "1", "''", "'x'", "[]", "[0]", "None", "2"]) for _ in range(5)]
    code = f"vals = [{', '.join(vals)}]\nprint(sum(bool(v) for v in vals))"
    alts = [f"print({sum(1 for v in vals if v in ('True','1','2'))})", f"print({len(vals)})", f"print({sum(1 for v in vals if v not in ('None',))})",
            f"print({sum(1 for v in vals if v in ('True',))})"]
    return code_q(rng, code, 1, alts, hint="Empty containers, 0, '' and None are falsy; non-empty values are truthy.")


def t_range(rng):
    a, b, s = rng.randint(0, 5), rng.randint(8, 20), rng.choice([2, 3, 4])
    code = f"print(list(range({a}, {b}, {s})))"
    alts = [f"print(list(range({a}, {b}+1, {s})))", f"print(list(range({a}+1, {b}, {s})))", f"print(list(range({a}, {b}, {s}))[:-1])",
            f"print(list(range({a}, {b}+{s}, {s})))"]
    return code_q(rng, code, 2, alts, hint="range(start, stop, step) never includes stop.")


def t_sum_range(rng):
    n = rng.randint(4, 12)
    code = f"print(sum(range({n})))"
    alts = [f"print(sum(range({n}+1)))", f"print(sum(range(1, {n})) - 1)", f"print({n})", f"print(sum(range({n}-1)))"]
    return code_q(rng, code, 2, alts, hint="range(n) yields 0..n-1.")


def t_loop_break_else(rng):
    xs = r_list(rng, 5)
    target = rng.choice(xs + [999])
    code = f"for x in {xs}:\n    if x == {target}:\n        print('found')\n        break\nelse:\n    print('not found')"
    alts = ["print('found')\nprint('not found')", "print('not found')" if target in xs else "print('found')", "print(None)"]
    return code_q(rng, code, 2, alts, hint="A loop's else runs only when the loop wasn't ended by break.")


def t_default_mut(rng):
    n = rng.randint(2, 4)
    calls = "\n".join(f"r = add({i})" for i in range(n))
    code = f"def add(item, bucket=[]):\n    bucket.append(item)\n    return bucket\n{calls}\nprint(r)"
    alts = [f"print([{n-1}])", f"print({list(range(n-1))})", f"print({list(range(1, n))})", f"print([])"]
    return code_q(rng, code, 2, alts, hint="Default values are created once, when def runs.",
                  expl="The same default list is reused across calls, so it accumulates items.")


def t_closure(rng):
    n = rng.randint(2, 5)
    code = "def counter():\n    count = 0\n    def inc():\n        nonlocal count\n        count += 1\n        return count\n    return inc\nc = counter()\n" + "\n".join("c()" for _ in range(n - 1)) + "\nprint(c())"
    alts = ["print(1)", f"print({n-1})", f"print({n+1})", "print(0)"]
    return code_q(rng, code, 2, alts, hint="nonlocal lets inc() rebind the enclosing count.")


def t_late_binding(rng):
    n = rng.randint(3, 5)
    code = f"fs = [lambda: i for i in range({n})]\nprint([f() for f in fs])"
    alts = [f"print({list(range(n))})", f"print({[n]*n})", f"print({[0]*n})"]
    return code_q(rng, code, 2, alts, hint="Closures look up i when called, not when created.",
                  expl="All lambdas share the loop variable i, which ends at its last value.")


def t_args_kwargs(rng):
    pos = rng.randint(1, 4); kw = rng.randint(0, 3)
    args = ", ".join(str(i) for i in range(pos)) + ("," if pos and kw else "") + " ".join(f" k{i}={i}," for i in range(kw)).rstrip(",")
    code = f"def f(*args, **kwargs):\n    return len(args), len(kwargs), type(args).__name__\nprint(f({args}))"
    alts = [f"print(({pos}, {kw}, 'list'))", f"print(({pos+kw}, 0, 'tuple'))", f"print(({kw}, {pos}, 'tuple'))", f"print(({pos}, {kw}, 'dict'))"]
    return code_q(rng, code, 2, alts, hint="*args is a tuple of positionals; **kwargs is a dict of keywords.")


def t_kwonly_error(rng):
    code = "def f(a, *, b):\n    return a + b\ntry:\n    print(f(1, 2))\nexcept TypeError:\n    print('TypeError')"
    if rng.random() < 0.5:
        code = "def f(a, *, b):\n    return a + b\nprint(f(1, b=2))"
    alts = ["print(3)", "print('TypeError')", "print('SyntaxError')", "print(12)"]
    return code_q(rng, code, 2, alts, hint="Parameters after * must be passed by keyword.")


def t_unpack(rng):
    xs = list(range(rng.randint(1, 3), rng.randint(5, 8)))
    form = rng.choice(["first, *rest", "*init, last", "a, b, *rest", "a, *mid, z"])
    code = f"{form} = {xs}\nprint({form.replace('*', '')})"
    alts = [f"print({xs[0]}, {xs[1:]})", f"print({xs[:-1]}, {xs[-1]})", f"print({xs[0]}, {tuple(xs[1:])})",
            f"print({xs[0]}, {xs[1]}, {xs[2:]})", f"print({xs[0]}, {xs[1:-1]}, {xs[-1]})", f"print({xs[0]}, {xs[2:]}, {xs[-1]})"]
    return code_q(rng, code, 3, alts, hint="The starred name collects the remaining items into a list.")


def t_list_ops(rng):
    xs = r_list(rng, 4)
    v1, v2 = rng.randint(1, 9), rng.randint(1, 9)
    ops = rng.sample([f"nums.append({v1})", f"nums.insert(0, {v2})", "nums.pop()", f"nums.extend([{v1}, {v2}])", "nums.pop(0)", f"nums.remove({xs[1]})"], 3)
    code = f"nums = {xs}\n" + "\n".join(ops) + "\nprint(nums)"
    alts = [f"nums = {xs}\n" + "\n".join(ops[:-1]) + "\nprint(nums)", f"nums = {xs}\n" + "\n".join(reversed(ops)) + "\nprint(nums)",
            f"nums = {xs}\n" + "\n".join(ops[1:]) + "\nprint(nums)", f"nums = {xs}\n" + "\n".join(ops) + "\nprint(nums[::-1])"]
    return code_q(rng, code, 3, alts, hint="append adds one item at the end; extend adds each item; pop() removes the last.")


def t_sort_none(rng):
    xs = r_list(rng, 4)
    code = rng.choice([f"nums = {xs}\nprint(nums.sort())", f"nums = {xs}\nr = sorted(nums)\nprint(r, nums)",
                       f"nums = {xs}\nnums.sort(reverse=True)\nprint(nums[0])"])
    alts = [f"print({sorted(xs)})", f"print({sorted(xs)}, {sorted(xs)})", f"print({min(xs)})", "print(None)",
            f"print({sorted(xs)}, {xs})", f"print({max(xs)})", f"print({xs}, {sorted(xs)})"]
    return code_q(rng, code, 3, alts, hint="list.sort() sorts in place and returns None; sorted() returns a new list.")


def t_dict_ops(rng):
    k = rng.sample(W, 3)
    code = rng.choice([
        f"d = {{'{k[0]}': 1, '{k[1]}': 2}}\nprint(d.get('{k[2]}', 0) + d.get('{k[0]}'))",
        f"d = {{'{k[0]}': 1}}\nd.setdefault('{k[1]}', 5)\nd.setdefault('{k[0]}', 9)\nprint(d)",
        f"a = {{'{k[0]}': 1, '{k[1]}': 2}}\nb = {{'{k[1]}': 3, '{k[2]}': 4}}\nprint(a | b)",
        f"d = {{'{k[0]}': 1, '{k[1]}': 2}}\nprint(list(d.items())[-1])",
        f"d = dict.fromkeys(['{k[0]}', '{k[1]}'], 0)\nd['{k[0]}'] += 1\nprint(d)",
    ])
    alts = [f"print(1)", f"print(2)", f"print({{'{k[0]}': 9, '{k[1]}': 5}})", f"print({{'{k[0]}': 1, '{k[1]}': 2, '{k[2]}': 4}})",
            f"print(('{k[0]}', 1))", f"print({{'{k[0]}': 1, '{k[1]}': 1}})", "print('KeyError')", f"print({{'{k[0]}': 1}})",
            f"print({{'{k[0]}': 0, '{k[1]}': 0}})", f"print(('{k[1]}', 2))[0]" ]
    return code_q(rng, code, 3, alts, hint="get() returns a default for missing keys; | merges with the right side winning; setdefault only sets missing keys.")


def t_set_ops(rng):
    a = set(rng.sample(range(1, 9), 4)); b = set(rng.sample(range(1, 9), 4))
    op = rng.choice(["|", "&", "-", "^"])
    code = f"a = {sorted(a)}\nb = {sorted(b)}\nprint(sorted(set(a) {op} set(b)))"
    alts = [f"a = {sorted(a)}\nb = {sorted(b)}\nprint(sorted(set(a) {o} set(b)))" for o in "|&-^" if o != op] + [f"print(sorted(set({sorted(b)}) - set({sorted(a)})))"]
    return code_q(rng, code, 3, alts, hint="| union, & intersection, - difference, ^ symmetric difference.")


def t_comprehension(rng):
    n = rng.randint(5, 9); m = rng.choice([2, 3])
    form = rng.choice([f"[x * x for x in range({n}) if x % {m} == 0]", f"[x for x in range({n}) if x % {m}]",
                       f"{{x: x % {m} for x in range({n // 2 + 1})}}", f"[(i, c) for i, c in enumerate('ab{('c' * (n % 3 + 1))}') if i % 2 == 0]",
                       f"sum(x for x in range({n}) if x % {m} == 0)", f"[w[0] for w in {rng.sample(W, 4)} if len(w) > {m+2}]"])
    code = f"print({form})"
    alts = [f"print({form.replace(f'% {m} == 0', f'% {m} != 0')})", f"print({form.replace(str(n), str(n + 1), 1)})",
            f"print({form.replace(f'% {m}', f'% {m + 1}')})", f"print({form.replace('x * x', 'x')})", f"print({form.replace('i % 2 == 0', 'i % 2 == 1')})",
            f"print({form.replace(f'> {m+2}', f'>= {m+2}')})"]
    return code_q(rng, code, 3, alts, hint="Read the comprehension as: output expression, for loop, then filter.")


def t_zip_enum(rng):
    a = rng.sample(W, 3); b = [rng.randint(1, 9) for _ in range(rng.choice([2, 3]))]
    code = rng.choice([f"print(list(zip({a}, {b})))", f"print(list(enumerate({a}, start={rng.randint(0, 2)})))",
                       f"print(dict(zip({a}, {b})))"])
    alts = [f"print(list(zip({a[:len(b)]}, {b})) + [('{a[-1]}', None)])", f"print(list(enumerate({a})))", f"print(list(enumerate({a}, start=1)))",
            f"print(list(zip({b}, {a})))", f"print(dict(zip({b}, {a})))", f"print(list(enumerate({a}, start=2)))"]
    return code_q(rng, code, 3, alts, hint="zip stops at the shortest input; enumerate's start sets the first index.")


def t_copy(rng):
    v = rng.randint(1, 9)
    code = rng.choice([f"a = [[1], [2]]\nb = a.copy()\nb[0].append({v})\nb.append([3])\nprint(a)",
                       f"import copy\na = [[1], [2]]\nb = copy.deepcopy(a)\nb[0].append({v})\nprint(a)",
                       f"a = [1, 2]\nb = a\nb.append({v})\nprint(a)", f"a = [1, 2]\nb = a[:]\nb.append({v})\nprint(a)"])
    alts = ["print([[1], [2]])", f"print([[1, {v}], [2]])", f"print([[1, {v}], [2], [3]])", "print([1, 2])", f"print([1, 2, {v}])"]
    return code_q(rng, code, 3, alts, hint="Assignment never copies; .copy() and [:] are shallow; deepcopy copies nested objects.")


def t_is_eq(rng):
    code = rng.choice(["a = [1, 2]\nb = [1, 2]\nprint(a == b, a is b)", "a = [1, 2]\nb = a\nprint(a == b, a is b)",
                       "x = None\nprint(x is None, x == None)", "a = (1, 2)\nb = tuple([1, 2])\nprint(a == b)"])
    alts = ["print(True, True)", "print(True, False)", "print(False, False)", "print(False, True)", "print(False)", "print(True)"]
    return code_q(rng, code, 1, alts, hint="== compares values; is compares identity.")


def t_strings(rng):
    w = rng.sample(W, 3)
    s = f"{w[0]},{w[1]},,{w[2]}"
    code = rng.choice([f"print('{s}'.split(','))", f"print(len('{s}'.split(',')))", f"print('-'.join({w}))",
                       f"print('  {w[0]}  '.strip().upper())", f"print('{w[0]}{w[1]}'.find('{w[1][0]}'))",
                       f"print('{w[0]} {w[0]} {w[1]}'.count('{w[0]}'))", f"print('{w[0]}'.replace('{w[0][0]}', 'X'))",
                       f"print('{w[1]}'.title(), '{w[1]}'.startswith('{w[1][:2]}'))", f"print('{w[0]}' * 2 + '!')"])
    alts = [f"print({[w[0], w[1], w[2]]})", "print(3)", "print(4)", "print(5)", f"print('{w[0]}-{w[1]}{w[2]}')", f"print('{w[0].upper()}  ')",
            f"print({len(w[0])})", f"print({len(w[0]) + 1})", "print(1)", "print(2)", f"print('{w[0]}'.upper())", f"print('{w[1].capitalize()}', False)",
            f"print('{w[0]}!' * 2)", f"print('{w[0]} {w[0]}!')", "print(-1)", f"print({[w[0], w[1], '', w[2]][::-1]})"]
    return code_q(rng, code, 1, alts, hint="split keeps empty strings between consecutive separators; find returns the first index or -1.")


def t_fstring(rng):
    x = rng.choice([3.14159, 2.71828, 1234.5678, 0.12345, 42.0])
    spec = rng.choice([".2f", ".1f", ".0f", "8.2f", ",.2f", ".1%", ".3e"])
    code = f"x = {x}\nprint(f'[{{x:{spec}}}]')"
    alts = [f"x = {x}\nprint(f'[{{x:{s}}}]')" for s in [".2f", ".1f", ".3f", ".2%", "10.2f", ".2e"] if s != spec]
    return code_q(rng, code, 1, alts, hint="Format spec: [width][,][.precision][type] — f fixed, % percent, e scientific.")


def t_generator(rng):
    n = rng.randint(3, 5)
    code = rng.choice([f"g = (x * 2 for x in range({n}))\nprint(sum(g), sum(g))",
                       f"def gen():\n    for i in range({n}):\n        yield i\ng = gen()\nnext(g)\nprint(list(g))",
                       f"def gen():\n    yield 1\n    yield from range(2, {n})\nprint(list(gen()))",
                       f"it = iter([{', '.join(str(i) for i in range(n))}])\nprint(next(it), next(it, 'end'))"])
    alts = [f"print({sum(x*2 for x in range(n))}, {sum(x*2 for x in range(n))})", f"print({list(range(n))})", f"print({list(range(1, n))})",
            f"print([1, {list(range(2, n))}])", f"print({list(range(1, n+1))})", "print(0, 1)", "print(0, 'end')", f"print({sum(x*2 for x in range(n))}, None)", "print(1, 2)"]
    return code_q(rng, code, 5, alts, hint="Generators are lazy and single-use; yield from delegates to another iterable.")


def t_decorator_order(rng):
    a, b = rng.sample(["A", "B", "C", "D"], 2)
    code = f"def deco(tag):\n    def wrap(fn):\n        def inner():\n            return tag + fn()\n        return inner\n    return wrap\n@deco('{a}')\n@deco('{b}')\ndef f():\n    return 'x'\nprint(f())"
    alts = [f"print('{b}{a}x')", f"print('x{a}{b}')", f"print('{a}x')", f"print('{a}{b}')"]
    return code_q(rng, code, 5, alts, hint="Decorators apply bottom-up: f = deco(a)(deco(b)(f)).")


def t_ctx(rng):
    raise_it = rng.random() < 0.5
    code = ("from contextlib import contextmanager\n@contextmanager\ndef span():\n    print('start')\n    try:\n        yield\n    finally:\n        print('end')\n"
            "try:\n    with span():\n        print('body')\n" + ("        raise ValueError()\n" if raise_it else "") + "except ValueError:\n    print('caught')")
    return code_q(rng, code, 5, [], extra=["start | body | caught | end", "start | body | caught", "start | body", "body | end",
                  "start | end | body", "start | body | end | caught", "start | body | end"], oneline=True,
                  hint="finally inside a context manager runs even when the with-block raises; output lines are shown separated by |.")


def t_try_flow(rng):
    exc = rng.choice(["ValueError", "KeyError", None])
    body = {"ValueError": "int('x')", "KeyError": "{}['k']", None: "int('7')"}[exc]
    code = f"try:\n    {body}\n    print('A', end=' ')\nexcept ValueError:\n    print('B', end=' ')\nexcept KeyError:\n    print('C', end=' ')\nelse:\n    print('D', end=' ')\nfinally:\n    print('E')"
    alts = ["print('A D E')", "print('B E')", "print('C E')", "print('A E')", "print('B D E')", "print('E')", "print('A B E')"]
    return code_q(rng, code, 7, alts, hint="else runs only when no exception occurred; finally always runs.")


def t_exception_type(rng):
    snippet, name = rng.choice([("[1, 2][5]", "IndexError"), ("{'a': 1}['b']", "KeyError"), ("'5' + 5", "TypeError"),
                                ("int('abc')", "ValueError"), ("1 / 0", "ZeroDivisionError"), ("None.upper()", "AttributeError"),
                                ("undefined_name", "NameError"), ("next(iter([]))", "StopIteration"), ("[].pop()", "IndexError"),
                                ("{'a'} + {'b'}", "TypeError"), ("float('1e')", "ValueError"), ("(1, 2)[0] = 5", "TypeError")])
    code = f"try:\n    {snippet}\nexcept Exception as e:\n    print(type(e).__name__)"
    others = ["IndexError", "KeyError", "TypeError", "ValueError", "ZeroDivisionError", "AttributeError", "NameError", "LookupError"]
    return code_q(rng, code, 7, [], extra=[o for o in others if o != name], stem="Which exception type does this code print?",
                  hint="Match the operation to the error it raises.")


def t_class_attr(rng):
    v = rng.randint(1, 9)
    code = rng.choice([f"class A:\n    n = 0\n    def __init__(self):\n        A.n += 1\nA(); A(); a = A()\nprint(A.n, a.n)",
                       f"class A:\n    tags = []\na, b = A(), A()\na.tags.append({v})\nprint(b.tags)",
                       f"class A:\n    x = 1\na = A()\na.x = {v}\nprint(A.x, a.x)",
                       f"class A:\n    def hi(self):\n        return 'A'\nclass B(A):\n    def hi(self):\n        return 'B' + super().hi()\nprint(B().hi())",
                       f"class A:\n    def __init__(self, x):\n        self.x = x\n    def __eq__(self, o):\n        return self.x == o.x\nprint(A({v}) == A({v}), A({v}) is A({v}))"])
    alts = ["print(3, 3)", "print(1, 3)", "print(3, 0)", "print([])", f"print([{v}])", f"print({v}, {v})", f"print(1, {v})", "print(1, 1)",
            "print('B')", "print('AB')", "print('BA')", "print(True, True)", "print(True, False)", "print(False, False)"]
    return code_q(rng, code, 4, alts, hint="Class attributes are shared; assigning through an instance creates an instance attribute.")


def t_dataclass(rng):
    a, b = rng.randint(1, 5), rng.randint(1, 5)
    code = rng.choice([f"from dataclasses import dataclass\n@dataclass\nclass P:\n    x: int\n    y: int = 0\nprint(P({a}) == P({a}, 0), P({a}, {b}))",
                       f"from dataclasses import dataclass\n@dataclass(order=True)\nclass P:\n    x: int\nprint(P({a}) < P({a + b}))",
                       f"from dataclasses import dataclass, field\n@dataclass\nclass C:\n    tags: list = field(default_factory=list)\nc1, c2 = C(), C()\nc1.tags.append({a})\nprint(c2.tags)"])
    alts = [f"print(False, P(x={a}, y={b}))", f"print(True, P({a}, {b}))", f"print(True, <P object>)", "print(False)", "print(True)", f"print([{a}])", "print([])", "print(None)"]
    return code_q(rng, code, 4, alts, hint="@dataclass generates __init__, __repr__ and __eq__; order=True adds comparisons.")


def t_counter(rng):
    w = "".join(rng.choice("aabbbcd") for _ in range(rng.randint(6, 9)))
    code = rng.choice([f"from collections import Counter\nprint(Counter('{w}').most_common(1))",
                       f"from collections import Counter\nc = Counter('{w}')\nprint(c['b'], c['z'])",
                       f"from collections import defaultdict\nd = defaultdict(int)\nfor ch in '{w}':\n    d[ch] += 1\nprint(len(d))"])
    from collections import Counter
    c = Counter(w)
    alts = [f"print([('a', {c['a']})])", f"print([('b', {c['b']})])", f"print([('c', {c['c']+1})])", f"print({c['b']}, 'KeyError')", f"print({c['b']+1}, 0)",
            f"print({len(c)+1})", f"print({len(w)})", f"print({c['a']}, 0)", f"print({len(c)-1})"]
    return code_q(rng, code, 3, alts, hint="Counter returns 0 for missing keys; defaultdict(int) starts counts at 0.")


def t_sorted_key(rng):
    ws = rng.sample(W, 4)
    code = rng.choice([f"print(sorted({ws}, key=len))", f"print(sorted({ws}, key=lambda w: w[-1]))", f"print(sorted({ws}, reverse=True)[0])",
                       f"print(max({ws}, key=len), min({ws}))"])
    alts = [f"print({sorted(ws)})", f"print({sorted(ws, key=len, reverse=True)})", f"print({ws})", f"print('{sorted(ws)[0]}')",
            f"print('{max(ws)}')", f"print('{min(ws, key=len)}', '{max(ws)}')", f"print({sorted(ws, key=lambda w: w[0])})"]
    return code_q(rng, code, 3, alts, hint="key= transforms each item for comparison; sorting is stable.")


def t_any_all(rng):
    xs = [rng.choice([0, 1, 2, -1, 0]) for _ in range(4)]
    code = f"xs = {xs}\nprint(any(xs), all(xs), any(x < 0 for x in xs))"
    alts = [f"print({not any(xs)}, {all(xs)}, {any(x < 0 for x in xs)})", f"print({any(xs)}, {not all(xs)}, {any(x < 0 for x in xs)})",
            f"print({any(xs)}, {all(xs)}, {not any(x < 0 for x in xs)})", "print(True, True, True)", "print(False, False, False)"]
    return code_q(rng, code, 3, alts, hint="any is True if at least one truthy item; all requires every item truthy (all([]) is True).")


def t_map_filter(rng):
    xs = list(range(1, rng.randint(5, 8)))
    code = rng.choice([f"print(list(map(lambda x: x * 3, filter(lambda x: x % 2, {xs}))))",
                       f"from functools import reduce\nprint(reduce(lambda a, b: a * b, {xs[:4]}))",
                       f"print(list(filter(None, [0, 1, '', 'a', None, []])))"])
    alts = [f"print({[x*3 for x in xs if x % 2 == 0]})", f"print({[x*3 for x in xs]})", f"print({[x for x in xs if x % 2]})",
            f"print({sum(xs[:4])})", f"print({xs[3]})", "print([1, 'a'])" if 'None' not in code else "print([0, 1, 'a'])", "print([0, '', None, []])", "print([])"]
    return code_q(rng, code, 3, alts, hint="filter keeps items where the function is truthy; map applies a function to each item.")


def t_tuple_single(rng):
    code = rng.choice(["print(type((1)).__name__, type((1,)).__name__)", "t = 1, 2\nprint(type(t).__name__, len(t))",
                       "print(len((('a', 'b'))), len((('a', 'b'),)))"])
    alts = ["print('tuple', 'tuple')", "print('int', 'int')", "print('int', 'tuple')", "print('tuple', 'int')", "print('list', 2)", "print('tuple', 2)",
            "print(2, 2)", "print(2, 1)", "print(1, 1)"]
    return code_q(rng, code, 3, alts, hint="The comma makes the tuple, not the parentheses.")


def t_chain_cmp(rng):
    a, b, c = rng.randint(1, 5), rng.randint(1, 5), rng.randint(1, 5)
    code = f"print({a} < {b} < {c}, {a} == {b} or {c})"
    alts = [f"print({a < b and b < c}, True)", f"print({not (a < b < c)}, {a == b or c})", f"print(False, {c})", f"print(True, {c})", f"print({a < b < c}, False)"]
    return code_q(rng, code, 1, alts, hint="a < b < c means a < b and b < c; 'or' returns the first truthy operand.")


def t_scope_error(rng):
    code = rng.choice(["x = 1\ndef f():\n    x += 1\n    return x\ntry:\n    print(f())\nexcept Exception as e:\n    print(type(e).__name__)",
                       "x = 1\ndef f():\n    global x\n    x += 1\n    return x\nf()\nprint(x)",
                       "x = 'g'\ndef f():\n    x = 'l'\n    return x\nprint(f(), x)"])
    alts = ["print(2)", "print('UnboundLocalError')", "print('NameError')", "print(1)", "print('l', 'l')", "print('g', 'g')", "print('l', 'g')"]
    return code_q(rng, code, 2, alts, hint="Assigning to a name inside a function makes it local unless declared global/nonlocal.")


def t_batched(rng):
    s = "".join(string.ascii_lowercase[: rng.randint(4, 7)])
    n = rng.choice([2, 3])
    code = rng.choice([f"from itertools import batched\nprint(len(list(batched('{s}', {n}))))",
                       f"from itertools import islice\nprint(list(islice(range(100), {n}, {n + 3})))",
                       f"from itertools import chain\nprint(list(chain([1, 2], [{n}], ())))"])
    import math
    alts = [f"print({len(s)//n})", f"print({math.ceil(len(s)/n)+1})", f"print({list(range(n, n+4))})", f"print({list(range(n+1, n+4))})", f"print({list(range(0, 3))})",
            f"print([[1, 2], [{n}]])", f"print([1, 2, [{n}]])", f"print({len(s)})"]
    return code_q(rng, code, 5, alts, hint="batched groups items into tuples of size n (last may be shorter); islice is like slicing for iterators.")


def t_async_gather(rng):
    delays = [rng.choice([0.03, 0.01, 0.02]) for _ in range(3)]
    names = rng.sample(["a", "b", "c", "d"], 3)
    code = ("import asyncio\nasync def job(name, d):\n    await asyncio.sleep(d)\n    return name\nasync def main():\n"
            f"    return await asyncio.gather(job('{names[0]}', {delays[0]}), job('{names[1]}', {delays[1]}), job('{names[2]}', {delays[2]}))\nprint(asyncio.run(main()))")
    by_time = [n for _, n in sorted(zip(delays, names))]
    alts = [f"print({by_time})", f"print({names[::-1]})", f"print({sorted(names)})", f"print('{names[0]}')"]
    return code_q(rng, code, 8, alts, hint="gather returns results in argument order, regardless of which finished first.")


def t_async_coroutine(rng):
    code = rng.choice(["import asyncio\nasync def f():\n    return 42\nc = f()\nprint(type(c).__name__)\nc.close()",
                       "import asyncio\nasync def f():\n    return 42\nprint(asyncio.run(f()))",
                       "import asyncio\nasync def gen():\n    for i in range(3):\n        yield i\nasync def main():\n    return [i async for i in gen()]\nprint(asyncio.run(main()))"])
    alts = ["print('int')", "print('coroutine')", "print('function')", "print(42)", "print('None')", "print([0, 1, 2])", "print([1, 2, 3])", "print('generator')"]
    return code_q(rng, code, 8, alts, hint="Calling an async def function returns a coroutine object; asyncio.run executes it.")


def t_pydantic(rng):
    v = rng.choice(['"0.9"', '"7"', "3", '"abc"', "1.5", '"true"'])
    typ = rng.choice(["float", "int", "bool"])
    code = (f"from pydantic import BaseModel, ValidationError\nclass M(BaseModel):\n    x: {typ}\ntry:\n    m = M(x={v})\n    print(repr(m.x))\nexcept ValidationError as e:\n    print('ValidationError')")
    alts = ["print(\"'0.9'\")", "print(0.9)", "print(7)", "print(7.0)", "print(\"'7'\")", "print(3)", "print(3.0)", "print(True)", "print('ValidationError')",
            "print(1.5)", "print(1)", "print(2)", "print(\"'abc'\")", "print(None)"]
    return code_q(rng, code, 6, alts, hint="Pydantic v2 (lax mode) coerces compatible strings/numbers but rejects lossy or invalid conversions.")


def t_tstring(rng):
    name = rng.choice(["Ada", "Linus", "Grace"])
    code = rng.choice([f"name = '{name}'\ntpl = t'Hi {{name}}!'\nprint(type(tpl).__name__, tpl.values)",
                       f"name = '{name}'\ntpl = t'Hi {{name}}!'\nprint(tpl.strings)"])
    alts = ["print('str', ('Ada',))", f"print('Template', '{name}')", "print('str', 'Hi')", f"print(('Hi ', '!'), )", "print(('Hi', '!'))", f"print('Hi {name}!')", "print(('Hi ',))"]
    return code_q(rng, code, 10, alts, hint="t-strings (3.14) produce a Template with .strings (static parts) and .values (interpolations).")


def t_match(rng):
    ev = rng.choice(['{"type": "tool_call", "name": "search"}', '{"type": "message", "text": "hi"}', '{"kind": "other"}', '[1, 2, 3]', '[1]'])
    code = ("def route(e):\n    match e:\n        case {\"type\": \"tool_call\", \"name\": n}:\n            return f\"call {n}\"\n"
            "        case {\"type\": \"message\"}:\n            return \"chat\"\n        case [first, *rest]:\n            return f\"list {first} +{len(rest)}\"\n        case _:\n            return \"unknown\"\n"
            f"print(route({ev}))")
    alts = ["print('call search')", "print('chat')", "print('unknown')", "print('list 1 +2')", "print('list 1 +0')", "print('call n')", "print('list [1, 2, 3]')"]
    return code_q(rng, code, 2, alts, hint="Patterns are tried top to bottom; mapping patterns match if the listed keys match.")


TEMPLATES = [t_slice, t_neg_index, t_step_slice, t_divmod, t_true_div, t_round, t_bool_sum, t_range, t_sum_range, t_loop_break_else,
             t_default_mut, t_closure, t_late_binding, t_args_kwargs, t_kwonly_error, t_unpack, t_list_ops, t_sort_none, t_dict_ops,
             t_set_ops, t_comprehension, t_zip_enum, t_copy, t_is_eq, t_strings, t_fstring, t_generator, t_decorator_order, t_ctx,
             t_try_flow, t_exception_type, t_class_attr, t_dataclass, t_counter, t_sorted_key, t_any_all, t_map_filter, t_tuple_single,
             t_chain_cmp, t_scope_error, t_batched, t_async_gather, t_async_coroutine, t_pydantic, t_match]
import sys as _sys
if _sys.version_info >= (3, 14):
    TEMPLATES.append(t_tstring)


def generate(rng, lesson_titles, limit=2600):
    return Collector(rng, limit).run_templates(TEMPLATES)
