"""Computed MCP questions: JSON-RPC message handling, pagination, stdio framing, annotations defaults,
version negotiation, URI templates, tool results, elicitation and logging. Every answer is produced
by running the snippet with plain Python (json/base64 only), so correctness is verified at build time."""
import json
from .common import Collector, code_q, calc_q

METHODS_REQ = ["tools/list", "tools/call", "resources/read", "resources/list", "prompts/get", "prompts/list",
               "ping", "initialize", "sampling/createMessage", "roots/list", "elicitation/create", "completion/complete"]
METHODS_NOTE = ["notifications/initialized", "notifications/progress", "notifications/cancelled",
                "notifications/tools/list_changed", "notifications/resources/updated", "notifications/message",
                "notifications/roots/list_changed"]
KINDS = ["request", "notification", "error response", "result response"]
CLASSIFY = ('if "method" in msg and "id" in msg:\n    kind = "request"\nelif "method" in msg:\n    kind = "notification"\n'
            'elif "error" in msg:\n    kind = "error response"\nelse:\n    kind = "result response"\nprint(kind)')


def _rid(rng):
    return rng.choice([rng.randint(1, 99), f'"req-{rng.randint(1, 50)}"'])


def t_kind(rng):
    k = rng.choice(KINDS)
    rid = _rid(rng)
    if k == "request":
        m = f'{{"jsonrpc": "2.0", "id": {rid}, "method": "{rng.choice(METHODS_REQ)}"}}'
    elif k == "notification":
        m = f'{{"jsonrpc": "2.0", "method": "{rng.choice(METHODS_NOTE)}"}}'
    elif k == "error response":
        code = rng.choice([-32700, -32600, -32601, -32602, -32603, -32002])
        m = f'{{"jsonrpc": "2.0", "id": {rid}, "error": {{"code": {code}, "message": "failed"}}}}'
    else:
        body = rng.choice(['{}', '{"tools": []}', '{"content": [], "isError": false}', '{"contents": []}', '{"messages": []}'])
        m = f'{{"jsonrpc": "2.0", "id": {rid}, "result": {body}}}'
    code = f"import json\nmsg = json.loads('{m}')\n{CLASSIFY}"
    return code_q(rng, code, 3, extra=KINDS, stem="This client code classifies an incoming JSON-RPC message. What does it print?",
                  hint="Requests have id + method, notifications have method but no id, responses have id + result or error.",
                  expl="The message is classified by which of id, method, result and error it contains.")


ERRS = {-32700: "Parse error", -32600: "Invalid Request", -32601: "Method not found", -32602: "Invalid params",
        -32603: "Internal error", -32002: "Resource not found"}
ERR_CAUSE = {-32700: "the server received text that is not valid JSON",
             -32600: "the message is not a valid JSON-RPC request object",
             -32601: "the client called a method the server does not support",
             -32602: "the client called tools/call with an unknown tool name",
             -32603: "the server hit an unexpected internal failure",
             -32002: "the client called resources/read for a URI that does not exist"}


def t_err(rng):
    c = rng.choice(list(ERRS))
    wrong = [ERRS[x] for x in ERRS if x != c]
    rng.shuffle(wrong)
    return calc_q(rng, f"An MCP response contains \"error\": {{\"code\": {c}}}. What is this error called?", ERRS[c], wrong, 3,
                  hint="-32700 parse, -32600 invalid request, -32601 method not found, -32602 invalid params, -32603 internal; MCP adds -32002.",
                  expl=f"{c} = {ERRS[c]}.")


def t_err_cause(rng):
    c = rng.choice(list(ERR_CAUSE))
    wrong = [f"{x}" for x in ERRS if x != c]
    rng.shuffle(wrong)
    return calc_q(rng, f"Which error code should be returned when {ERR_CAUSE[c]}?", f"{c}", wrong, 3,
                  hint="Match the failure to the JSON-RPC error codes (MCP adds -32002 for missing resources).",
                  expl=f"{ERR_CAUSE[c].capitalize()} → {c} ({ERRS[c]}).")


def t_pages(rng):
    n = rng.randint(2, 4)
    sizes = [rng.randint(1, 5) for _ in range(n)]
    names = iter("abcdefghijklmnopqrstuvwxyz")
    lines, total = [], 0
    for i, s in enumerate(sizes):
        key = "None" if i == 0 else f'"c{i}"'
        nxt = "None" if i == n - 1 else f'"c{i + 1}"'
        items = [next(names) for _ in range(s)]
        total += s
        lines.append(f"    {key}: ({items!r}, {nxt}),")
    code = ("pages = {  # cursor -> (tools on that page, nextCursor)\n" + "\n".join(lines) + "\n}\n"
            "cursor, tools, calls = None, [], 0\nwhile True:\n    items, cursor = pages[cursor]\n    tools += items\n"
            "    calls += 1\n    if cursor is None:\n        break\nprint(len(tools), calls)")
    extra = [f"{total} {n - 1}", f"{total - sizes[-1]} {n - 1}", f"{sizes[0]} 1", f"{total} {n + 1}", f"{total + 1} {n}", f"{n} {total}"]
    return code_q(rng, code, 3, extra=extra, stem="This loop pages through tools/list results. What does it print?",
                  hint="Keep requesting with nextCursor until it is absent (None).",
                  expl=f"{n} pages are fetched ({' + '.join(map(str, sizes))} tools).")


def t_stdio(rng):
    msgs = []
    for i in range(rng.randint(3, 6)):
        r = rng.random()
        if r < 0.4:
            msgs.append(f'{{"jsonrpc":"2.0","id":{i + 1},"method":"{rng.choice(METHODS_REQ)}"}}')
        elif r < 0.75:
            msgs.append(f'{{"jsonrpc":"2.0","method":"{rng.choice(METHODS_NOTE)}"}}')
        else:
            msgs.append(f'{{"jsonrpc":"2.0","id":{i + 1},"result":{{}}}}')
    what = rng.choice(["notifications", "requests", "responses"])
    cond = {"notifications": '"id" not in m', "requests": '"id" in m and "method" in m', "responses": '"method" not in m'}[what]
    buf = "\\n".join(msgs) + "\\n"
    code = (f"import json\n# bytes read from a stdio server's stdout\nbuf = '{buf}'\n"
            f"msgs = [json.loads(line) for line in buf.splitlines() if line.strip()]\n"
            f"print(len(msgs), sum(1 for m in msgs if {cond}))")
    out = code_q(rng, code, 7, extra=[f"{len(msgs)} {k}" for k in range(len(msgs) + 1)] + [f"{len(msgs) + 1} 0"],
                 stem=f"stdio messages are newline-delimited. This counts all messages and the {what}. What does it print?",
                 hint="Each line is one JSON-RPC message; notifications have no id, responses have no method.",
                 expl="Split on newlines, parse each line, then count by message kind.")
    return out


DEFAULTS = {"readOnlyHint": False, "destructiveHint": True, "idempotentHint": False, "openWorldHint": True}


def t_annot(rng):
    keys = list(DEFAULTS)
    given = {k: rng.choice([True, False]) for k in rng.sample(keys, rng.randint(0, 3))}
    a, b = rng.sample(keys, 2)
    code = (f"ann = {json.dumps(given).replace('true', 'True').replace('false', 'False')}  # tool annotations from tools/list\n"
            f"DEFAULTS = {{\"readOnlyHint\": False, \"destructiveHint\": True,\n            \"idempotentHint\": False, \"openWorldHint\": True}}\n"
            f"print(ann.get(\"{a}\", DEFAULTS[\"{a}\"]), ann.get(\"{b}\", DEFAULTS[\"{b}\"]))")
    return code_q(rng, code, 4, extra=["True True", "True False", "False True", "False False"],
                  stem="Missing annotations take their spec defaults. What does this print?",
                  hint="Defaults: readOnlyHint false, destructiveHint true, idempotentHint false, openWorldHint true.",
                  expl="Values present in ann win; missing ones fall back to the defaults.")


def t_approval(rng):
    keys = ["readOnlyHint", "destructiveHint"]
    given = {k: rng.choice([True, False]) for k in rng.sample(keys, rng.randint(0, 2))}
    if rng.random() < 0.5:
        given["openWorldHint"] = rng.choice([True, False])
    lit = json.dumps(given).replace("true", "True").replace("false", "False")
    code = (f"ann = {lit}\nread_only = ann.get(\"readOnlyHint\", False)\ndestructive = ann.get(\"destructiveHint\", True)\n"
            "if read_only:\n    print(\"auto-approve\")\nelif destructive:\n    print(\"ask user (destructive)\")\n"
            "else:\n    print(\"ask user (non-destructive)\")")
    return code_q(rng, code, 4, extra=["auto-approve", "ask user (destructive)", "ask user (non-destructive)", "block"],
                  stem="A host decides how to approve a tool call from its annotations. What does it print?",
                  hint="readOnlyHint defaults to false; destructiveHint defaults to true.",
                  expl="Read-only tools are auto-approved; otherwise destructiveHint (default true) decides.")


def t_progress(rng):
    total = rng.choice([4, 5, 8, 10, 20, 40, 50, 200])
    steps = sorted(rng.sample(range(1, total + 1), rng.randint(2, 4)))
    notes = ",\n    ".join(f'{{"progressToken": "t1", "progress": {s}, "total": {total}}}' for s in steps)
    code = (f"updates = [\n    {notes},\n]\np = updates[-1]\nprint(f\"{{100 * p['progress'] // p['total']}}%\")")
    extra = [f"{100 * s // total}%" for s in steps] + [f"{100 * steps[-1] // total + 10}%", f"{steps[-1]}%", "100%", f"{len(steps)}%"]
    return code_q(rng, code, 3, extra=extra, stem="A host renders the latest notifications/progress update. What does it print?",
                  hint="Use the last update: progress / total × 100.", expl="Only the most recent update is shown.")


VERSIONS = ["2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05"]


def t_version(rng):
    server = sorted(rng.sample(VERSIONS, rng.randint(1, 3)), reverse=True)
    client = rng.choice(VERSIONS)
    code = (f"SERVER_SUPPORTED = {server!r}  # newest first\n\ndef negotiate(requested):\n"
            "    return requested if requested in SERVER_SUPPORTED else SERVER_SUPPORTED[0]\n\n"
            f"print(negotiate(\"{client}\"))")
    return code_q(rng, code, 2, extra=VERSIONS + ["error"], stem="The client's initialize request asks for a protocol version. What does the server reply with?",
                  hint="Same version if supported, otherwise the server's latest supported version.",
                  expl="The server echoes a supported version or offers its newest one.")


TEMPLATES = [("weather://forecast/{city}/{day}", {"city": ["pune", "oslo", "lima"], "day": ["mon", "tue", "sat"]}),
             ("notes://{folder}/{name}", {"folder": ["work", "home"], "name": ["todo", "ideas", "plan"]}),
             ("postgres://{db}/tables/{table}/schema", {"db": ["shop", "crm"], "table": ["orders", "users", "items"]}),
             ("github://{owner}/{repo}/issues/{number}", {"owner": ["acme", "sam"], "repo": ["api", "web"], "number": ["7", "42", "108"]})]


def t_template(rng):
    tpl, space = rng.choice(TEMPLATES)
    args = {k: rng.choice(v) for k, v in space.items()}
    keys = list(args)
    code = f'template = "{tpl}"  # RFC 6570 level-1 template\nargs = {args!r}\nprint(template.format(**args))'
    sw = dict(args); sw[keys[0]], sw[keys[-1]] = args[keys[-1]], args[keys[0]]
    extra = [tpl.format(**sw), tpl, tpl.split("://")[1].format(**args), tpl.format(**{k: "{" + v + "}" for k, v in args.items()}),
             tpl.replace("{", "").replace("}", "")]
    return code_q(rng, code, 5, extra=extra, stem="What resource URI does this resource-template expansion print?",
                  hint="Each {variable} is replaced by its value.", expl="Variables are substituted into the URI template.")


TYPES = ["text", "image", "audio", "resource_link"]


def t_result(rng):
    blocks = [rng.choice(TYPES) for _ in range(rng.randint(1, 4))]
    is_err = rng.choice([True, False, None])
    parts = []
    for i, t in enumerate(blocks):
        if t == "text":
            parts.append(f'{{"type": "text", "text": "part {i}"}}')
        elif t == "resource_link":
            parts.append(f'{{"type": "resource_link", "uri": "file:///r{i}.txt"}}')
        else:
            parts.append(f'{{"type": "{t}", "data": "AAAA", "mimeType": "{t}/x"}}')
    res = '{"content": [' + ", ".join(parts) + "]" + ("" if is_err is None else f', "isError": {str(is_err)}') + "}"
    code = (f"result = {res}\ntexts = [c for c in result[\"content\"] if c[\"type\"] == \"text\"]\n"
            "print(len(result[\"content\"]), len(texts), result.get(\"isError\", False))")
    n, k = len(blocks), blocks.count("text")
    extra = [f"{n} {k} {not bool(is_err)}", f"{n} {n} {bool(is_err)}", f"{k} {n} {bool(is_err)}", f"{n} {k} None",
             f"{n + 1} {k} {bool(is_err)}", f"{n} {max(0, k - 1)} {bool(is_err)}"]
    return code_q(rng, code, 4, extra=extra, stem="What does this code print for this tool result?",
                  hint="Count all content blocks, then only text blocks; a missing isError means False.",
                  expl="isError defaults to false when absent.")


SCHEMAS = [({"city": "string", "units": "string", "days": "integer"}, ["city"]),
           ({"repo": "string", "title": "string", "body": "string", "labels": "array"}, ["repo", "title"]),
           ({"query": "string", "limit": "integer", "offset": "integer"}, ["query"]),
           ({"to": "string", "subject": "string", "text": "string"}, ["to", "subject", "text"]),
           ({"sql": "string", "database": "string", "timeout": "number"}, ["sql", "database"])]


def t_required(rng):
    props, req = rng.choice(SCHEMAS)
    given = {k: "x" for k in rng.sample(list(props), rng.randint(0, len(props)))}
    schema = {"type": "object", "properties": {k: {"type": v} for k, v in props.items()}, "required": req}
    code = (f"schema = {json.dumps(schema)}\nargs = {given!r}\n"
            "missing = [k for k in schema[\"required\"] if k not in args]\nprint(missing)")
    allp = list(props)
    extra = [repr([]), repr(req), repr([k for k in allp if k not in given]), repr(sorted(given)), repr(allp[:1]), repr(req[::-1])]
    return code_q(rng, code, 4, extra=extra, stem="A server validates tools/call arguments against the tool's inputSchema. What does it print?",
                  hint="Only the names listed in \"required\" must be present.",
                  expl="Optional properties may be omitted; required ones may not.")


def t_blob(rng):
    data = rng.choice(["b'PNG'", "b'hello world'", "b'%PDF-1.7'", "b'\\x00\\x01\\x02\\x03'", "b'MCP'", "b'GIF89a'"])
    reps = rng.randint(1, 6)
    code = (f"import base64\nraw = {data} * {reps}\nblob = base64.b64encode(raw).decode()  # as sent in a resource 'blob'\n"
            "print(len(blob), len(base64.b64decode(blob)))")
    try:
        n = len(eval(data)) * reps
    except Exception:
        return None
    b = 4 * ((n + 2) // 3)
    extra = [f"{n} {b}", f"{b} {b}", f"{n} {n}", f"{b + 4} {n}", f"{b} {n + 1}", f"{2 * n} {n}"]
    return code_q(rng, code, 5, extra=extra, stem="Binary resource contents travel as base64. What does this print?",
                  hint="Base64 turns every 3 bytes into 4 characters (with padding); decoding restores the original length.",
                  expl="Encoded length = 4 × ceil(n / 3); decoded length = n.")


ELICIT = [("staging", "production"), ("eu", "us"), ("monthly", "yearly"), ("small", "large")]


def t_elicit(rng):
    a, b = rng.choice(ELICIT)
    action = rng.choice(["accept", "accept", "decline", "cancel"])
    choice = rng.choice([a, b])
    resp = {"action": action} if action != "accept" else {"action": "accept", "content": {"choice": choice}}
    code = (f"resp = {resp!r}  # elicitation/create result\nif resp[\"action\"] == \"accept\":\n"
            "    print(\"use\", resp[\"content\"][\"choice\"])\nelif resp[\"action\"] == \"decline\":\n"
            "    print(\"user refused\")\nelse:\n    print(\"dismissed\")")
    return code_q(rng, code, 6, extra=[f"use {a}", f"use {b}", "user refused", "dismissed"],
                  stem="How does this server handle the elicitation response? What does it print?",
                  hint="accept carries content; decline is an explicit no; cancel means dismissed.",
                  expl="The three actions are accept, decline and cancel.")


LEVELS = ["debug", "info", "notice", "warning", "error", "critical", "alert", "emergency"]


def t_levels(rng):
    msgs = [rng.choice(LEVELS) for _ in range(rng.randint(4, 7))]
    minimum = rng.choice(LEVELS[:6])
    code = (f"LEVELS = {LEVELS!r}\nminimum = \"{minimum}\"  # set via logging/setLevel\nincoming = {msgs!r}\n"
            "shown = [m for m in incoming if LEVELS.index(m) >= LEVELS.index(minimum)]\nprint(len(shown))")
    return code_q(rng, code, 3, extra=[str(i) for i in range(len(msgs) + 2)],
                  stem="Log messages below the level set with logging/setLevel are dropped. What does this print?",
                  hint="Severity increases left to right in the syslog list.", expl="Count messages at or above the minimum level.")


def t_session(rng):
    method = rng.choice(["initialize", "tools/list", "tools/call", "notifications/initialized", "notifications/cancelled", "ping"])
    is_note = method.startswith("notifications/")
    has_sid = rng.choice([True, False])
    expired = rng.choice([True, False]) if has_sid else False
    code = (f"msg = {{\"method\": \"{method}\"" + ("" if is_note else ", \"id\": 1") + "}\n"
            f"session_header = {repr('abc123') if has_sid else None}\nsession_known = {not expired}\n\n"
            "def status(msg):\n    if session_header and not session_known:\n        return 404\n"
            "    if msg[\"method\"] != \"initialize\" and not session_header:\n        return 400\n"
            "    return 200 if \"id\" in msg else 202\n\nprint(status(msg))")
    return code_q(rng, code, 7, extra=["200", "202", "400", "404", "401"],
                  stem="This sketch shows how a stateful Streamable HTTP server answers a POST. What does it print?",
                  hint="Expired session → 404; missing session (except initialize) → 400; notifications → 202.",
                  expl="Requests get 200 with a response; notifications get 202 Accepted.")


def generate(rng, lesson_titles, limit=1600):
    return Collector(rng, limit).run_templates([t_kind, t_err, t_err_cause, t_pages, t_stdio, t_annot, t_approval, t_progress,
                                                t_version, t_template, t_result, t_required, t_blob, t_elicit, t_levels,
                                                t_session])
