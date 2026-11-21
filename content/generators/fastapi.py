"""Verified FastAPI questions: each app is built and called with TestClient at build time."""
import json
from .common import Collector, make_q, pick_distractors

STATUS = ["200", "201", "204", "400", "401", "403", "404", "405", "409", "422", "429", "500"]
HEAD = "from fastapi import FastAPI, HTTPException, Query, Path, Depends, Header\nfrom pydantic import BaseModel, Field\nfrom typing import Annotated\napp = FastAPI()\n"


def call(app_code, method, url, **kw):
    from fastapi.testclient import TestClient
    g = {}
    exec(compile(HEAD + app_code, "<app>", "exec"), g)
    client = TestClient(g["app"], raise_server_exceptions=False)
    r = client.request(method, url, **kw)
    try:
        body = r.json()
    except Exception:
        body = r.text
    return r.status_code, body


def compact(body):
    return json.dumps(body, separators=(", ", ": ")) if not isinstance(body, str) else body


def status_q(rng, app_code, method, url, lesson, hint, expl="", kw=None):
    code, body = call(app_code, method, url, **(kw or {}))
    correct = str(code)
    wrong = pick_distractors(correct, rng.sample(STATUS, len(STATUS)))
    req = f"{method} {url}" + (f"  body={json.dumps(kw['json'])}" if kw and "json" in kw else "") + \
          (f"  headers={kw['headers']}" if kw and "headers" in kw else "")
    shown = app_code.strip() + f"\n\n# client sends: {req}"
    return make_q(rng, "What HTTP status code does the client receive?", correct, wrong, lesson, "api-behaviour",
                  code=shown, hint=hint, expl=(expl + " " if expl else "") + f"Status {code}; response body: {compact(body)[:160]}",
                  expl_code=shown, diff=2, src="fastapi")


def body_q(rng, app_code, method, url, lesson, hint, wrongs, kw=None, expl=""):
    code, body = call(app_code, method, url, **(kw or {}))
    if code >= 400:
        return None
    correct = compact(body)
    wrong = pick_distractors(correct, wrongs)
    if not wrong:
        return None
    req = f"{method} {url}" + (f"  body={json.dumps(kw['json'])}" if kw and "json" in kw else "")
    shown = app_code.strip() + f"\n\n# client sends: {req}"
    return make_q(rng, "What JSON body does the client receive?", correct, wrong, lesson, "api-behaviour", code=shown, hint=hint,
                  expl=(expl + " " if expl else "") + f"Status {code}; body {correct}", expl_code=shown, diff=2, src="fastapi")


def t_path_int(rng):
    val = rng.choice(["42", "7", "abc", "3.5", "-2", "0x10", "1e3"])
    name = rng.choice(["doc_id", "item_id", "user_id", "chunk_id"])
    app = f"@app.get(\"/docs/{{{name}}}\")\ndef read({name}: int):\n    return {{\"{name}\": {name}}}"
    return status_q(rng, app, "GET", f"/docs/{val}", 2, "Path parameters are converted to the annotated type; invalid values fail validation.")


def t_path_body(rng):
    val = rng.choice(["42", "7", "-3", "10"])
    name = rng.choice(["doc_id", "item_id", "n"])
    app = f"@app.get(\"/docs/{{{name}}}\")\ndef read({name}: int):\n    return {{\"{name}\": {name}, \"type\": type({name}).__name__}}"
    wr = [f'{{"{name}": "{val}", "type": "str"}}', f'{{"{name}": {val}, "type": "str"}}', f'{{"{name}": "{val}", "type": "int"}}',
          f'{{"{name}": {val}.0, "type": "float"}}']
    return body_q(rng, app, "GET", f"/docs/{val}", 2, "The string from the URL is converted to int before your function runs.", wr)


def t_query_constraint(rng):
    lo, hi = rng.randint(1, 3), rng.randint(5, 20)
    v = rng.choice([lo - 1, lo, hi, hi + 1, (lo + hi) // 2, -1])
    app = f"@app.get(\"/search\")\ndef search(q: str, k: Annotated[int, Query(ge={lo}, le={hi})] = {lo}):\n    return {{\"q\": q, \"k\": k}}"
    return status_q(rng, app, "GET", f"/search?q=rag&k={v}", 2, f"ge/le are inclusive bounds; values outside [{lo}, {hi}] fail validation.")


def t_query_missing(rng):
    has = rng.random() < 0.5
    default = rng.choice(["", " = 5"])
    app = f"@app.get(\"/search\")\ndef search(q: str, k: int{default}):\n    return {{\"q\": q, \"k\": k}}"
    url = "/search?q=rag" + ("&k=3" if has else "")
    return status_q(rng, app, "GET", url, 2, "A parameter without a default is required.")


def t_query_defaults(rng):
    k = rng.randint(2, 9)
    send = rng.choice(["", f"&k={k + 1}", "&rerank=true", f"&k={k - 1}&rerank=1", "&rerank=no"])
    app = f"@app.get(\"/search\")\ndef search(q: str, k: int = {k}, rerank: bool = False):\n    return {{\"q\": q, \"k\": k, \"rerank\": rerank}}"
    wr = [f'{{"q": "rag", "k": {k}, "rerank": false}}', f'{{"q": "rag", "k": {k + 1}, "rerank": false}}', f'{{"q": "rag", "k": {k}, "rerank": true}}',
          f'{{"q": "rag", "k": {k - 1}, "rerank": true}}', f'{{"q": "rag", "k": "{k}", "rerank": "true"}}', f'{{"q": "rag", "k": {k - 1}, "rerank": false}}',
          f'{{"q": "rag", "k": {k + 1}, "rerank": true}}']
    return body_q(rng, app, "GET", "/search?q=rag" + send, 2, "Unspecified query params use their defaults; bool accepts true/1/yes/no.", wr)


def t_status_code(rng):
    sc = rng.choice([200, 201, 202, 204])
    raise_404 = rng.random() < 0.35
    body = "    raise HTTPException(status_code=404, detail=\"missing\")\n" if raise_404 else ""
    ret = "    return None" if sc == 204 else "    return {\"ok\": True}"
    app = f"@app.post(\"/items\", status_code={sc})\ndef create():\n{body}{ret}"
    return status_q(rng, app, "POST", "/items", 4, "status_code= sets the success code; a raised HTTPException overrides it.")


def t_http_exception(rng):
    sc = rng.choice([400, 401, 403, 404, 409, 429])
    doc = rng.choice([1, 2, 3, 9])
    app = f"DB = {{1: \"intro\", 2: \"rag\"}}\n@app.get(\"/docs/{{doc_id}}\")\ndef get_doc(doc_id: int):\n    if doc_id not in DB:\n        raise HTTPException(status_code={sc}, detail=\"Document not found\")\n    return {{\"title\": DB[doc_id]}}"
    if rng.random() < 0.5:
        return status_q(rng, app, "GET", f"/docs/{doc}", 4, "HTTPException sets the status code and a detail message.")
    wr = ['{"title": "intro"}', '{"title": "rag"}', '{"detail": "Document not found"}', '{"error": "Document not found"}', '{"title": null}',
          f'{{"status": {sc}, "detail": "Document not found"}}']
    code, body = call(app, "GET", f"/docs/{doc}")
    correct = compact(body)
    wrong = pick_distractors(correct, wr)
    if not wrong:
        return None
    shown = app + f"\n\n# client sends: GET /docs/{doc}"
    return make_q(rng, "What JSON body does the client receive?", correct, wrong, 4, "api-behaviour", code=shown,
                  hint="HTTPException(detail=...) is returned as {\"detail\": ...}.", expl=f"Status {code}; body {correct}",
                  expl_code=shown, diff=2, src="fastapi")


def t_method(rng):
    defined = rng.choice(["get", "post", "put", "delete"])
    sent = rng.choice(["GET", "POST", "PUT", "DELETE", "PATCH"])
    path = rng.choice(["/items", "/chat", "/docs"])
    url = path if rng.random() < 0.8 else path + "/extra"
    app = f"@app.{defined}(\"{path}\")\ndef handler():\n    return {{\"ok\": True}}"
    return status_q(rng, app, sent, url, 4, "Existing path + unsupported method → 405; unknown path → 404.")


def t_response_model(rng):
    extra = rng.sample(["password", "api_key", "internal_score", "email"], rng.randint(1, 2))
    keep = rng.choice([["username"], ["username", "email"]])
    keep = [k for k in keep if k not in extra] or ["username"]
    fields = "\n".join(f"    {k}: str" for k in keep)
    data = {k: k[:3] for k in keep + extra}
    app = f"class UserOut(BaseModel):\n{fields}\n@app.get(\"/me\", response_model=UserOut)\ndef me():\n    return {json.dumps(data)}"
    wr = [compact(data), compact({k: data[k] for k in extra}), compact({**{k: data[k] for k in keep}, extra[0]: None}), compact({"detail": "filtered"}),
          compact({k: data[k] for k in (keep + extra)[1:]})]
    return body_q(rng, app, "GET", "/me", 4, "response_model filters out fields it does not declare.", wr)


def t_route_order(rng):
    first_me = rng.random() < 0.5
    me = "@app.get(\"/users/me\")\ndef me():\n    return {\"who\": \"me\"}\n"
    uid = "@app.get(\"/users/{user_id}\")\ndef user(user_id: str):\n    return {\"who\": user_id}\n"
    app = (me + uid) if first_me else (uid + me)
    target = rng.choice(["me", "42"])
    wr = ['{"who": "me"}', '{"who": "42"}', '{"who": "user_id"}', '{"detail": "Not Found"}']
    return body_q(rng, app, "GET", f"/users/{target}", 1, "Routes are matched in declaration order.", wr)


def t_dep_cache(rng):
    use_cache = rng.random() < 0.6
    app = (f"calls = {{\"n\": 0}}\ndef get_db():\n    calls[\"n\"] += 1\n    return \"db\"\n"
           f"def repo(db: str = Depends(get_db{', use_cache=False' if not use_cache else ''})):\n    return db\n"
           f"@app.get(\"/x\")\ndef x(db: str = Depends(get_db), r: str = Depends(repo)):\n    return {{\"calls\": calls[\"n\"]}}")
    wr = ['{"calls": 1}', '{"calls": 2}', '{"calls": 0}', '{"calls": 3}']
    return body_q(rng, app, "GET", "/x", 5, "Within one request, the same dependency runs once unless use_cache=False.", wr)


def t_list_query(rng):
    tags = rng.sample(["a", "b", "rag", "llm", "ml"], rng.randint(1, 3))
    url = "/items?" + "&".join(f"tags={t}" for t in tags)
    app = "@app.get(\"/items\")\ndef items(tags: Annotated[list[str], Query()] = []):\n    return {\"tags\": tags, \"n\": len(tags)}"
    wr = [compact({"tags": tags[-1:], "n": 1}), compact({"tags": [",".join(tags)], "n": 1}), compact({"tags": [], "n": 0}),
          compact({"tags": tags, "n": len(tags) + 1}), compact({"tags": tags[:1], "n": 1})]
    return body_q(rng, app, "GET", url, 2, "Repeated query parameters are collected into a list.", wr)


def t_body_validation(rng):
    payload = rng.choice([{"message": "hi"}, {"message": ""}, {}, {"message": "hi", "temperature": 3}, {"message": "hi", "temperature": 0.5},
                          {"message": "hi", "extra": 1}, {"message": 123}, {"message": "hi", "temperature": "0.7"}])
    app = ("class ChatRequest(BaseModel):\n    message: str = Field(min_length=1)\n    temperature: float = Field(0.2, ge=0, le=2)\n"
           "@app.post(\"/chat\")\ndef chat(req: ChatRequest):\n    return {\"echo\": req.message, \"t\": req.temperature}")
    return status_q(rng, app, "POST", "/chat", 3, "Bodies are validated by the Pydantic model; constraint violations give 422.", kw={"json": payload})


def t_body_echo(rng):
    payload = rng.choice([{"message": "hi"}, {"message": "hi", "temperature": 0.5}, {"message": "hi", "extra": 1}, {"message": "hi", "temperature": "1.5"}])
    app = ("class ChatRequest(BaseModel):\n    message: str\n    temperature: float = 0.2\n"
           "@app.post(\"/chat\")\ndef chat(req: ChatRequest):\n    return req.model_dump()")
    wr = ['{"message": "hi", "temperature": 0.2}', '{"message": "hi", "temperature": 0.5}', '{"message": "hi", "extra": 1, "temperature": 0.2}',
          '{"message": "hi", "temperature": "1.5"}', '{"message": "hi", "temperature": 1.5}', '{"message": "hi"}']
    return body_q(rng, app, "POST", "/chat", 3, "Missing fields take defaults; unknown fields are ignored by default; compatible strings are coerced.", wr,
                  kw={"json": payload})


def t_header(rng):
    send = rng.random() < 0.6
    req = rng.random() < 0.5
    app = f"@app.get(\"/whoami\")\ndef whoami(x_api_key: Annotated[str{'' if req else ' | None'}, Header()]{'' if req else ' = None'}):\n    return {{\"key\": x_api_key}}"
    kw = {"headers": {"X-API-Key": "k1"}} if send else {}
    if rng.random() < 0.5:
        return status_q(rng, app, "GET", "/whoami", 2, "Header() converts x_api_key to the X-Api-Key header; required headers must be sent.", kw=kw)
    wr = ['{"key": "k1"}', '{"key": null}', '{"key": "x_api_key"}', '{"key": "X-API-Key"}']
    return body_q(rng, app, "GET", "/whoami", 2, "Header() reads the X-Api-Key header for parameter x_api_key.", wr, kw=kw)


def t_unknown_path(rng):
    app = "@app.get(\"/health\")\ndef health():\n    return {\"status\": \"ok\"}"
    url = rng.choice(["/health", "/healthz", "/health/", "/Health"])
    return status_q(rng, app, "GET", url, 1, "Paths are case-sensitive; unknown paths return 404 (a trailing slash may redirect).",
                    kw={"follow_redirects": False})


def t_exclude_none(rng):
    flag = rng.random() < 0.5
    app = (f"class Doc(BaseModel):\n    title: str\n    summary: str | None = None\n@app.get(\"/doc\", response_model=Doc{', response_model_exclude_none=True' if flag else ''})\n"
           "def doc():\n    return {\"title\": \"RAG\"}")
    wr = ['{"title": "RAG", "summary": null}', '{"title": "RAG"}', '{"title": "RAG", "summary": ""}', '{"summary": null}']
    return body_q(rng, app, "GET", "/doc", 4, "response_model_exclude_none drops fields whose value is None.", wr)


TEMPLATES = [t_path_int, t_path_body, t_query_constraint, t_query_missing, t_query_defaults, t_status_code, t_http_exception, t_method,
             t_response_model, t_route_order, t_dep_cache, t_list_query, t_body_validation, t_body_echo, t_header, t_unknown_path, t_exclude_none]


def generate(rng, lesson_titles, limit=1400):
    items = Collector(rng, limit + 700).run_templates(TEMPLATES)
    out, n200 = [], 0
    for q in items:
        if q["q"].startswith("What HTTP status") and q["o"][q["a"]] == "200":
            n200 += 1
            if n200 > 220:
                continue
        out.append(q)
    return out[:limit]
