"""Verified LangChain questions: each snippet is executed with langchain-core at build time."""
from .common import code_q, Collector

IMP = "from langchain_core.runnables import RunnableLambda, RunnableParallel, RunnablePassthrough, RunnableBranch\n"
OPS = [("+", lambda a, b: a + b), ("*", lambda a, b: a * b), ("-", lambda a, b: a - b)]


def lam(rng):
    op, _ = rng.choice(OPS)
    n = rng.randint(1, 5)
    return f"lambda x: x {op} {n}"


def t_chain(rng):
    k = rng.randint(2, 3)
    lams = [lam(rng) for _ in range(k)]
    x = rng.randint(1, 6)
    steps = " | ".join(f"RunnableLambda({l})" for l in lams)
    code = IMP + f"chain = {steps}\nprint(chain.invoke({x}))"
    rev = " | ".join(f"RunnableLambda({l})" for l in reversed(lams))
    alts = [IMP + f"print(({rev}).invoke({x}))", IMP + f"print(({' | '.join(f'RunnableLambda({l})' for l in lams[:-1])}).invoke({x}))",
            IMP + f"print(({steps}).invoke({x + 1}))", IMP + f"print(({' | '.join(f'RunnableLambda({l})' for l in lams[1:])}).invoke({x}))"]
    return code_q(rng, code, 4, alts, hint="Data flows left to right through |: each step receives the previous step's output.")


def t_parallel(rng):
    a, b = lam(rng), lam(rng)
    x = rng.randint(1, 6)
    ka, kb = rng.sample(["a", "b", "ctx", "q", "double", "plus"], 2)
    code = IMP + f"par = RunnableParallel({ka}=RunnableLambda({a}), {kb}=RunnableLambda({b}))\nprint(par.invoke({x}))"
    alts = [IMP + f"print({{'{ka}': ({a})({x}), '{kb}': ({b})(({a})({x}))}})", IMP + f"print({{'{ka}': ({b})({x}), '{kb}': ({a})({x})}})",
            IMP + f"print(({b})(({a})({x})))", IMP + f"print([({a})({x}), ({b})({x})])"]
    return code_q(rng, code, 4, alts, hint="RunnableParallel gives the SAME input to every branch and returns a dict.")


def t_dict_coercion(rng):
    a, b = lam(rng), lam(rng)
    x = rng.randint(1, 6)
    code = IMP + f"chain = RunnableLambda({a}) | {{\"left\": RunnableLambda({b}), \"right\": RunnablePassthrough()}}\nprint(chain.invoke({x}))"
    ax = eval(f"({a})({x})")
    alts = [IMP + f"print({{'left': ({b})({x}), 'right': {x}}})", IMP + f"print({{'left': ({b})({ax}), 'right': {x}}})",
            IMP + f"print({{'left': {ax}, 'right': ({b})({ax})}})", IMP + f"print(({b})({ax}))"]
    return code_q(rng, code, 4, alts, hint="A dict inside a chain becomes a RunnableParallel; RunnablePassthrough forwards its input unchanged.")


def t_assign(rng):
    text = rng.choice(["abc", "graph", "agent", "rerank", "hi"])
    k = rng.choice(["n", "length", "size"])
    extra = rng.choice([f"lambda d: len(d['text'])", f"lambda d: d['text'].upper()", f"lambda d: d['text'][::-1]"])
    code = IMP + f"step = RunnablePassthrough.assign({k}={extra})\nprint(step.invoke({{'text': '{text}'}}))"
    val = eval(f"({extra})({{'text': '{text}'}})")
    alts = [IMP + f"print({{'{k}': {val!r}}})", IMP + f"print({{'text': '{text}'}})", IMP + f"print({{'text': {val!r}}})",
            IMP + f"print({{'text': '{text}', '{k}': '{text}'}})"]
    return code_q(rng, code, 4, alts, hint="assign() keeps the input keys and adds the new computed keys.")


def t_itemgetter(rng):
    q = rng.choice(["What is RAG?", "Define MRR", "hi"])
    code = IMP + "from operator import itemgetter\nchain = itemgetter('question') | RunnableLambda(lambda s: len(s.split()))\n" + f"print(chain.invoke({{'question': '{q}', 'k': 3}}))"
    n = len(q.split())
    alts = [IMP + f"print({len(q)})", IMP + f"print({n + 1})", IMP + "print(3)", IMP + f"print({{'question': {n}}})"]
    return code_q(rng, code, 4, alts, hint="itemgetter('question') extracts that key from the input dict.")


def t_batch(rng):
    l = lam(rng)
    xs = [rng.randint(1, 9) for _ in range(3)]
    code = IMP + f"r = RunnableLambda({l})\nprint(r.batch({xs}))"
    vals = [eval(f"({l})({x})") for x in xs]
    alts = [IMP + f"print({sorted(vals)})", IMP + f"print({vals[::-1]})", IMP + f"print({vals[0]})", IMP + f"print({xs})"]
    return code_q(rng, code, 2, alts, hint="batch() returns one output per input, in input order.")


def t_prompt_format(rng):
    n = rng.randint(5, 30); topic = rng.choice(["GraphRAG", "LCEL", "Langfuse", "reranking"])
    code = f"from langchain_core.prompts import PromptTemplate\npt = PromptTemplate.from_template('Explain {{topic}} in {{n}} words.')\nprint(pt.format(topic='{topic}', n={n}))"
    alts = [f"print('Explain {{topic}} in {{n}} words.')", f"print('Explain {topic} in {{n}} words.')", f"print('Explain {n} in {topic} words.')",
            f"print(['topic', 'n'])"]
    return code_q(rng, code, 3, alts, hint="format() substitutes each {variable} with the given keyword argument.")


def t_input_vars(rng):
    vs = rng.sample(["question", "context", "lang", "n", "style", "history_summary"], rng.randint(2, 3))
    tmpl = " ".join(f"{{{v}}}" for v in vs)
    lit = rng.random() < 0.5
    t = ("Return JSON {{\"a\": 1}} " if lit else "") + tmpl
    code = f"from langchain_core.prompts import PromptTemplate\nprint(PromptTemplate.from_template('{t}').input_variables)"
    alts = [f"print({vs})", f"print({sorted(vs) + ['a']})", f"print({vs[::-1] + ['\"a\"']})", f"print({sorted(vs)[:-1]})", f"print({len(vs)})"]
    return code_q(rng, code, 3, alts, hint="Double braces {{ }} are literal and are not variables; input_variables are sorted.")


def t_chat_prompt(rng):
    hist = rng.randint(0, 3)
    hlist = ", ".join(rng.choice(['("human", "hi")', '("ai", "hello")']) for _ in range(hist))
    code = ("from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder\n"
            "p = ChatPromptTemplate.from_messages([(\"system\", \"Be brief.\"), MessagesPlaceholder(\"history\"), (\"human\", \"{q}\")])\n"
            f"msgs = p.invoke({{\"history\": [{hlist}], \"q\": \"What is RAG?\"}}).to_messages()\nprint(len(msgs), type(msgs[-1]).__name__)")
    alts = [f"print({hist + 1}, 'HumanMessage')", f"print({hist + 2}, 'AIMessage')", f"print(3, 'HumanMessage')", f"print({hist + 3}, 'HumanMessage')",
            f"print({hist + 2}, 'SystemMessage')", f"print({hist + 2}, 'str')"]
    return code_q(rng, code, 3, alts, hint="MessagesPlaceholder expands to however many messages are passed in history.")


def t_partial(rng):
    d = rng.choice(["2026-11-21", "Monday", "today"]); q = rng.choice(["hi", "status?", "help"])
    code = ("from langchain_core.prompts import PromptTemplate\npt = PromptTemplate.from_template('[{date}] {q}').partial(date='" + d + "')\n"
            f"print(pt.input_variables, pt.format(q='{q}'))")
    alts = [f"print(['date', 'q'], '[{d}] {q}')", f"print(['q'], '[date] {q}')", f"print(['date'], '[{d}] {q}')", f"print(['q'], '{q}')"]
    return code_q(rng, code, 3, alts, hint="partial() pre-fills variables, removing them from input_variables.")


def t_fallbacks(rng):
    fail = rng.random() < 0.6
    a, b = rng.choice(["'primary'", "'gpt'", "'fast-model'"]), rng.choice(["'backup'", "'claude'", "'big-model'"])
    prim = "def primary(x):\n    raise TimeoutError('slow')\n" if fail else f"def primary(x):\n    return {a}\n"
    code = IMP + prim + f"chain = RunnableLambda(primary).with_fallbacks([RunnableLambda(lambda x: {b})])\nprint(chain.invoke('q'))"
    alts = [f"print({a})", f"print({b})", "print('TimeoutError')", f"print([{a}, {b}])", "print(None)"]
    return code_q(rng, code, 2, alts, hint="Fallbacks run only when the primary raises an exception.")


def t_branch(rng):
    x = rng.randint(-5, 15)
    t1, t2 = rng.randint(0, 5), rng.randint(6, 12)
    code = IMP + (f"branch = RunnableBranch(\n    (lambda x: x < {t1}, RunnableLambda(lambda x: 'negative-ish')),\n    (lambda x: x < {t2}, RunnableLambda(lambda x: 'medium')),\n"
                  "    RunnableLambda(lambda x: 'large'),\n)\n" + f"print(branch.invoke({x}))")
    alts = ["print('negative-ish')", "print('medium')", "print('large')", "print(None)"]
    return code_q(rng, code, 4, alts, hint="RunnableBranch checks conditions in order and runs the first matching branch, else the default.")


def t_parsers(rng):
    choice = rng.randint(0, 3)
    items = rng.sample(["rag", "llm", "agent", "graph", "bm25"], 3)
    if choice == 0:
        code = "from langchain_core.output_parsers import CommaSeparatedListOutputParser\nprint(CommaSeparatedListOutputParser().parse('" + ", ".join(items) + "'))"
        alts = [f"print('{', '.join(items)}')", f"print({[', '.join(items)]})", f"print({items[::-1]})", f"print({len(items)})"]
    elif choice == 1:
        code = "from langchain_core.output_parsers import StrOutputParser\nfrom langchain_core.messages import AIMessage\nprint(StrOutputParser().invoke(AIMessage(content='" + items[0] + "')))"
        alts = [f"print(\"AIMessage(content='{items[0]}')\")", f"print(['{items[0]}'])", "print('content')", f"print({{'content': '{items[0]}'}})"]
    elif choice == 2:
        code = "from langchain_core.output_parsers import JsonOutputParser\nprint(JsonOutputParser().invoke('{\"answer\": \"" + items[0] + "\", \"score\": 0.9}'))"
        alts = [f"print('{{\"answer\": \"{items[0]}\", \"score\": 0.9}}')", f"print(['{items[0]}', 0.9])", f"print({{'answer': '{items[0]}'}})", "print('0.9')"]
    else:
        code = ("from langchain_core.tools import tool\n@tool\ndef search_docs(query: str, k: int = 3) -> str:\n    \"\"\"Search the docs.\"\"\"\n    return query\n"
                "print(search_docs.name, list(search_docs.args), search_docs.description)")
        alts = ["print('search_docs', ['query'], 'Search the docs.')", "print('search', ['query', 'k'], 'Search the docs.')",
                "print('search_docs', ['query', 'k'], None)", "print('SearchDocs', ['query', 'k'], 'search_docs')"]
    return code_q(rng, code, 5 if choice < 3 else 6, alts, hint="Parsers convert model output (messages/text) into Python data; @tool derives name, args and description from the function.")


def t_splitter(rng):
    words = rng.sample(["one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve"], rng.randint(6, 10))
    size = rng.choice([12, 15, 20, 25])
    text = " ".join(words)
    code = f"from langchain_text_splitters import RecursiveCharacterTextSplitter\ns = RecursiveCharacterTextSplitter(chunk_size={size}, chunk_overlap=0)\nprint(len(s.split_text('{text}')))"
    import math
    alts = [f"print({math.ceil(len(text)/size)})", f"print({math.ceil(len(text)/size) + 1})", f"print({max(1, math.ceil(len(text)/size) - 1)})",
            f"print({len(words)})", f"print({math.ceil(len(text)/size) + 2})"]
    return code_q(rng, code, 8, alts, hint="The splitter packs words up to chunk_size characters, splitting on spaces when needed.")


TEMPLATES = [t_chain, t_parallel, t_dict_coercion, t_assign, t_itemgetter, t_batch, t_prompt_format, t_input_vars, t_chat_prompt,
             t_partial, t_fallbacks, t_branch, t_parsers, t_splitter]


def generate(rng, lesson_titles, limit=1300):
    return Collector(rng, limit).run_templates(TEMPLATES)
