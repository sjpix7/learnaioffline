"""Verified LangGraph questions: each graph is built and executed with langgraph at build time."""
from .common import code_q, Collector

IMP = "import operator\nfrom typing import Annotated, TypedDict\nfrom langgraph.graph import StateGraph, START, END\n"
NAMES = ["plan", "search", "grade", "write", "review", "fetch", "rank", "draft"]


def t_linear_reducer(rng):
    k = rng.randint(2, 4)
    names = rng.sample(NAMES, k)
    reducer = rng.random() < 0.65
    ann = "Annotated[list[str], operator.add]" if reducer else "list[str]"
    nodes = "\n".join(f"b.add_node(\"{n}\", lambda s: {{\"steps\": [\"{n}\"]}})" for n in names)
    edges = "\n".join(f"b.add_edge(\"{a}\", \"{c}\")" for a, c in zip(names, names[1:]))
    code = (IMP + f"class S(TypedDict):\n    steps: {ann}\nb = StateGraph(S)\n{nodes}\nb.add_edge(START, \"{names[0]}\")\n{edges}\n"
            f"b.add_edge(\"{names[-1]}\", END)\nprint(b.compile().invoke({{\"steps\": [\"start\"]}})[\"steps\"])")
    alts = [f"print({['start'] + names})", f"print({names})", f"print({[names[-1]]})", f"print({['start', names[-1]]})", f"print({names[::-1]})",
            f"print({['start'] + names[::-1]})"]
    return code_q(rng, code, 3, alts, hint="With operator.add, updates are appended; without a reducer each update overwrites the key.")


def t_overwrite(rng):
    a, b2 = rng.randint(1, 9), rng.randint(10, 20)
    code = (IMP + f"class S(TypedDict):\n    x: int\n    total: Annotated[int, operator.add]\nb = StateGraph(S)\n"
            f"b.add_node(\"one\", lambda s: {{\"x\": {a}, \"total\": {a}}})\nb.add_node(\"two\", lambda s: {{\"x\": {b2}, \"total\": {b2}}})\n"
            "b.add_edge(START, \"one\")\nb.add_edge(\"one\", \"two\")\nb.add_edge(\"two\", END)\n"
            "out = b.compile().invoke({\"x\": 0, \"total\": 0})\nprint(out[\"x\"], out[\"total\"])")
    alts = [f"print({a + b2}, {a + b2})", f"print({b2}, {b2})", f"print({a}, {a + b2})", f"print({b2}, {a})", f"print({a + b2}, {b2})"]
    return code_q(rng, code, 3, alts, hint="Keys without a reducer are overwritten; operator.add on ints sums the updates.")


def t_loop_counter(rng):
    step = rng.randint(1, 4); limit = rng.randint(5, 14); start = rng.randint(0, 3)
    code = (IMP + "class S(TypedDict):\n    n: int\n    runs: Annotated[list[int], operator.add]\n"
            f"def inc(s):\n    return {{\"n\": s[\"n\"] + {step}, \"runs\": [1]}}\n"
            f"def route(s):\n    return \"inc\" if s[\"n\"] < {limit} else END\n"
            "b = StateGraph(S)\nb.add_node(\"inc\", inc)\nb.add_edge(START, \"inc\")\nb.add_conditional_edges(\"inc\", route)\n"
            f"out = b.compile().invoke({{\"n\": {start}, \"runs\": []}})\nprint(out[\"n\"], len(out[\"runs\"]))")
    import math
    runs = max(1, math.ceil((limit - start) / step))
    final = start + runs * step
    alts = [f"print({final - step}, {runs - 1})", f"print({final}, {runs + 1})", f"print({limit}, {runs})", f"print({final + step}, {runs + 1})", f"print({limit - 1}, {runs})"]
    return code_q(rng, code, 4, alts, hint="The node runs, then the router checks the condition; the loop ends when route returns END.")


def t_router(rng):
    v = rng.randint(-10, 30)
    t1, t2 = sorted(rng.sample(range(-5, 25), 2))
    code = (IMP + "class S(TypedDict):\n    v: int\n    path: Annotated[list[str], operator.add]\n"
            f"def route(s):\n    if s[\"v\"] < {t1}:\n        return \"low\"\n    if s[\"v\"] < {t2}:\n        return \"mid\"\n    return \"high\"\n"
            "b = StateGraph(S)\nfor n in [\"low\", \"mid\", \"high\"]:\n    b.add_node(n, lambda s, n=n: {\"path\": [n]})\n    b.add_edge(n, END)\n"
            "b.add_node(\"classify\", lambda s: {\"path\": [\"classify\"]})\nb.add_edge(START, \"classify\")\n"
            "b.add_conditional_edges(\"classify\", route)\n" + f"print(b.compile().invoke({{\"v\": {v}, \"path\": []}})[\"path\"])")
    alts = ["print(['classify', 'low'])", "print(['classify', 'mid'])", "print(['classify', 'high'])", "print(['low'])", "print(['classify'])",
            "print(['classify', 'low', 'mid', 'high'])"]
    return code_q(rng, code, 4, alts, hint="The routing function returns the name of the next node.")


def t_fanout(rng):
    k = rng.randint(2, 3)
    names = rng.sample(NAMES, k)
    reducer = rng.random() < 0.7
    ann = "Annotated[list[str], operator.add]" if reducer else "list[str]"
    nodes = "\n".join(f"b.add_node(\"{n}\", lambda s: {{\"found\": [\"{n}\"]}})\nb.add_edge(START, \"{n}\")\nb.add_edge(\"{n}\", END)" for n in names)
    code = (IMP + f"class S(TypedDict):\n    found: {ann}\nb = StateGraph(S)\n{nodes}\ntry:\n    out = b.compile().invoke({{\"found\": []}})\n"
            "    print(sorted(out[\"found\"]))\nexcept Exception as e:\n    print(type(e).__name__)")
    alts = [f"print({sorted(names)})", f"print({[sorted(names)[0]]})", "print('InvalidUpdateError')", "print([])", f"print({[sorted(names)[-1]]})",
            "print('GraphRecursionError')"]
    return code_q(rng, code, 3, alts, hint="Nodes in the same super-step that write one key need a reducer to combine updates.")


def t_send(rng):
    items = rng.sample(["a", "b", "c", "d", "e", "f"], rng.randint(2, 5))
    code = (IMP + "from langgraph.types import Send\nclass S(TypedDict):\n    topics: list[str]\n    out: Annotated[list[str], operator.add]\n"
            "def fan_out(s):\n    return [Send(\"work\", {\"t\": t}) for t in s[\"topics\"]]\n"
            "b = StateGraph(S)\nb.add_node(\"work\", lambda p: {\"out\": [p[\"t\"].upper()]})\n"
            "b.add_conditional_edges(START, fan_out, [\"work\"])\nb.add_edge(\"work\", END)\n"
            f"out = b.compile().invoke({{\"topics\": {items}, \"out\": []}})\nprint(len(out[\"out\"]), sorted(out[\"out\"])[0])")
    up = sorted(x.upper() for x in items)
    alts = [f"print(1, '{up[0]}')", f"print({len(items)}, '{sorted(items)[0]}')", f"print({len(items) + 1}, '{up[0]}')", f"print({len(items)}, '{up[-1]}')",
            "print(0, '')"]
    return code_q(rng, code, 8, alts, hint="Each Send launches one parallel run of the target node with its own payload.")


def t_messages(rng):
    n0 = rng.randint(1, 3)
    same_id = rng.random() < 0.5
    msg = "AIMessage(content=\"edited\", id=\"m0\")" if same_id else "AIMessage(content=\"new\")"
    init = ", ".join(f"HumanMessage(content=\"q{i}\", id=\"m{i}\")" for i in range(n0))
    code = ("from typing import Annotated, TypedDict\nfrom langchain_core.messages import AIMessage, HumanMessage\n"
            "from langgraph.graph import StateGraph, START, END\nfrom langgraph.graph.message import add_messages\n"
            "class S(TypedDict):\n    messages: Annotated[list, add_messages]\nb = StateGraph(S)\n"
            f"b.add_node(\"bot\", lambda s: {{\"messages\": [{msg}]}})\nb.add_edge(START, \"bot\")\nb.add_edge(\"bot\", END)\n"
            f"out = b.compile().invoke({{\"messages\": [{init}]}})\nprint(len(out[\"messages\"]), out[\"messages\"][0].content)")
    alts = [f"print({n0 + 1}, 'q0')", f"print({n0}, 'edited')", f"print(1, 'new')", f"print({n0 + 1}, 'edited')", f"print({n0}, 'q0')", "print(1, 'edited')"]
    return code_q(rng, code, 3, alts, hint="add_messages appends new messages but replaces a message that has the same id.")


def t_output_schema(rng):
    code = (IMP + "class In(TypedDict):\n    question: str\nclass Out(TypedDict):\n    answer: str\nclass All(In, Out):\n    scratch: str\n"
            "b = StateGraph(All, input_schema=In, output_schema=Out)\n"
            "b.add_node(\"think\", lambda s: {\"scratch\": \"notes\", \"answer\": s[\"question\"].upper()})\n"
            "b.add_edge(START, \"think\")\nb.add_edge(\"think\", END)\n" + f"print(b.compile().invoke({{\"question\": \"{rng.choice(['hi', 'rag', 'why'])}\"}}))")
    q = code.split('"question": "')[-1].split('"')[0]
    alts = [f"print({{'question': '{q}', 'answer': '{q.upper()}'}})", f"print({{'question': '{q}', 'scratch': 'notes', 'answer': '{q.upper()}'}})",
            f"print({{'answer': '{q.upper()}', 'scratch': 'notes'}})", f"print({{'scratch': 'notes'}})"]
    return code_q(rng, code, 2, alts, hint="output_schema limits what the caller receives; internal keys stay hidden.")


def t_command(rng):
    dept = rng.choice(["billing", "tech"])
    code = (IMP + "from typing import Literal\nfrom langgraph.types import Command\nclass S(TypedDict):\n    text: str\n    dept: str\n    log: Annotated[list[str], operator.add]\n"
            "def triage(s) -> Command[Literal[\"billing\", \"tech\"]]:\n    d = \"billing\" if \"invoice\" in s[\"text\"] else \"tech\"\n    return Command(update={\"dept\": d, \"log\": [\"triage\"]}, goto=d)\n"
            "b = StateGraph(S)\nb.add_node(\"triage\", triage)\nfor n in [\"billing\", \"tech\"]:\n    b.add_node(n, lambda s, n=n: {\"log\": [n]})\n    b.add_edge(n, END)\n"
            "b.add_edge(START, \"triage\")\n" + f"out = b.compile().invoke({{\"text\": \"{'invoice is wrong' if dept == 'billing' else 'app crashes'}\", \"dept\": \"\", \"log\": []}})\n"
            "print(out[\"dept\"], out[\"log\"])")
    other = "tech" if dept == "billing" else "billing"
    alts = [f"print('{other}', ['triage', '{other}'])", f"print('{dept}', ['triage'])", f"print('', ['triage', '{dept}'])", f"print('{dept}', ['{dept}'])",
            f"print('{dept}', ['triage', 'billing', 'tech'])"]
    return code_q(rng, code, 4, alts, hint="Command(update=..., goto=...) both updates state and chooses the next node.")


def t_recursion(rng):
    needed = rng.randint(3, 12); limit = rng.randint(3, 14)
    code = (IMP + "from langgraph.errors import GraphRecursionError\nclass S(TypedDict):\n    n: int\n"
            "b = StateGraph(S)\nb.add_node(\"step\", lambda s: {\"n\": s[\"n\"] + 1})\nb.add_edge(START, \"step\")\n"
            f"b.add_conditional_edges(\"step\", lambda s: \"step\" if s[\"n\"] < {needed} else END)\n"
            f"try:\n    print(b.compile().invoke({{\"n\": 0}}, {{\"recursion_limit\": {limit}}})[\"n\"])\nexcept GraphRecursionError:\n    print(\"GraphRecursionError\")")
    alts = [f"print({needed})", "print('GraphRecursionError')", f"print({limit})", f"print({needed - 1})", f"print({min(needed, limit) - 1})"]
    return code_q(rng, code, 4, alts, hint="Each super-step counts toward recursion_limit; exceeding it raises GraphRecursionError.")


def t_interrupt(rng):
    approve = rng.random() < 0.5
    code = (IMP + "from langgraph.types import interrupt, Command\nfrom langgraph.checkpoint.memory import InMemorySaver\n"
            "class S(TypedDict):\n    status: str\n    calls: Annotated[int, operator.add]\n"
            "def send(s):\n    d = interrupt(\"approve?\")\n    return {\"status\": \"sent\" if d else \"cancelled\", \"calls\": 1}\n"
            "b = StateGraph(S)\nb.add_node(\"send\", send)\nb.add_edge(START, \"send\")\nb.add_edge(\"send\", END)\n"
            "g = b.compile(checkpointer=InMemorySaver())\ncfg = {\"configurable\": {\"thread_id\": \"t1\"}}\n"
            "first = g.invoke({\"status\": \"new\", \"calls\": 0}, cfg)\n"
            f"final = g.invoke(Command(resume={approve}), cfg)\nprint(first[\"status\"], final[\"status\"], final[\"calls\"])")
    alts = ["print('new', 'sent', 1)", "print('new', 'cancelled', 1)", "print('sent', 'sent', 2)", "print('approve?', 'sent', 1)", "print('new', 'sent', 2)",
            "print('cancelled', 'cancelled', 1)"]
    return code_q(rng, code, 7, alts, hint="The first invoke pauses at interrupt(); Command(resume=...) supplies interrupt()'s return value.")


TEMPLATES = [t_linear_reducer, t_overwrite, t_loop_counter, t_router, t_fanout, t_send, t_messages, t_output_schema, t_command, t_recursion, t_interrupt]


def generate(rng, lesson_titles, limit=1300):
    return Collector(rng, limit).run_templates(TEMPLATES)
