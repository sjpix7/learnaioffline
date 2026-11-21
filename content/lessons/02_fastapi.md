---
id: fastapi
title: FastAPI
short: Serve models, RAG pipelines and agents as fast, typed, documented APIs
emoji: ⚡
color: #009688
---
## Introduction: Your First FastAPI App
FastAPI is a modern Python web framework for building APIs from **type hints**. It sits on **Starlette** (the ASGI web toolkit) and **Pydantic** (validation). It is the most common way to put an LLM app, RAG pipeline or agent behind an HTTP endpoint.

### Why FastAPI?
- Automatic request **validation** and conversion from type hints.
- Automatic **OpenAPI** schema with interactive docs at `/docs` (Swagger UI) and `/redoc`.
- Native `async` support — ideal for I/O-bound LLM calls.
- Dependency injection, security utilities, WebSockets, Server-Sent Events and background tasks built in.

### Hello world
```python
# main.py
from fastapi import FastAPI

app = FastAPI(title="RAG Service", version="1.0.0")

@app.get("/")
def root():
    return {"message": "Hello, FastAPI"}      # dicts are serialized to JSON

@app.get("/health")
async def health():
    return {"status": "ok"}
```
Run it:
```bash
pip install "fastapi[standard]"
fastapi dev main.py          # auto-reload dev server (uses Uvicorn)
fastapi run main.py          # production mode
# or directly: uvicorn main:app --reload --port 8000
```
Open `http://127.0.0.1:8000/docs` to try endpoints from the browser.

### ASGI in one paragraph
**ASGI** (Asynchronous Server Gateway Interface) is the async successor of WSGI. An ASGI server such as **Uvicorn** or Hypercorn speaks HTTP and calls your app. FastAPI apps are ASGI apps, so they support async handlers, WebSockets and streaming.

### Path operations
A *path operation* = HTTP method + path + function. Decorators exist for each method: `@app.get`, `@app.post`, `@app.put`, `@app.patch`, `@app.delete`. Routes are matched **in the order they are declared**, so put `/users/me` before `/users/{user_id}`.

### def vs async def
- `async def` handlers run on the event loop — use them with async libraries (`httpx.AsyncClient`, `AsyncOpenAI`).
- Plain `def` handlers run in a **threadpool**, so blocking code doesn't freeze the server.
- Never call blocking code (e.g. `time.sleep`, sync DB drivers) inside `async def`.

### Key takeaways
- FastAPI = Starlette (web) + Pydantic (data) + type hints.
- Docs are generated automatically at `/docs` and `/redoc` from the OpenAPI schema.
- Use `fastapi dev` while developing and `fastapi run`/Uvicorn in production.

## Path Parameters, Query Parameters and Validation
### Path parameters
```python
@app.get("/docs/{doc_id}")
def get_doc(doc_id: int):            # "42" in the URL is converted to int 42
    return {"doc_id": doc_id}
```
`GET /docs/abc` returns **422 Unprocessable Entity** with a JSON error explaining that `doc_id` must be an integer. Use an `Enum` to restrict a path parameter to fixed values.

### Query parameters
Function parameters that are not part of the path are read from the query string.
```python
@app.get("/search")
def search(q: str, k: int = 5, rerank: bool = False):
    return {"q": q, "k": k, "rerank": rerank}
# GET /search?q=rag&k=3&rerank=true
```
- No default → **required** (missing → 422).
- A default value → optional.
- `bool` accepts `true/false/1/0/yes/no/on/off`.

### Adding constraints with Annotated
```python
from typing import Annotated
from fastapi import Query, Path

@app.get("/items/{item_id}")
def read_item(
    item_id: Annotated[int, Path(ge=1)],
    q: Annotated[str | None, Query(min_length=3, max_length=50)] = None,
    tags: Annotated[list[str], Query()] = [],       # ?tags=a&tags=b
):
    return {"item_id": item_id, "q": q, "tags": tags}
```
Numeric checks: `gt`, `ge`, `lt`, `le`; string checks: `min_length`, `max_length`, `pattern`. Violations return 422.

### Headers and cookies
```python
from fastapi import Header, Cookie
@app.get("/whoami")
def whoami(user_agent: Annotated[str | None, Header()] = None):
    return {"ua": user_agent}        # header "User-Agent" -> user_agent (underscores converted)
```

### Key takeaways
- Path params come from `{...}` in the route; other simple params come from the query string.
- Parameters without defaults are required; invalid or missing values produce 422.
- `Annotated[type, Query(...)]` / `Path(...)` add validation and documentation.

## Request Bodies with Pydantic Models
### Declaring a body
When a parameter is a Pydantic model, FastAPI reads it from the JSON **request body**.
```python
from pydantic import BaseModel, Field

class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    session_id: str | None = None
    temperature: float = Field(0.2, ge=0, le=2)

@app.post("/chat")
async def chat(req: ChatRequest):
    return {"echo": req.message, "temp": req.temperature}
```
FastAPI will: read the body as JSON, validate it, convert types, give you a typed object, and document the schema in OpenAPI. Invalid bodies return **422** with details for every error.

### Mixing body, path and query
```python
@app.put("/sessions/{session_id}/messages")
def add_message(session_id: str, req: ChatRequest, notify: bool = False):
    ...
```
Rules: declared in the path → path param; a singular type (int, str, bool...) → query param; a Pydantic model → body. Use `Body()` to force a singular value into the body, and `Body(embed=True)` to expect `{"req": {...}}`.

### Nested models and lists
```python
class Source(BaseModel):
    title: str
    url: str

class Answer(BaseModel):
    text: str
    sources: list[Source] = []
```

### Forms and files
```python
from fastapi import UploadFile, File, Form

@app.post("/ingest")
async def ingest(file: UploadFile = File(...), collection: str = Form("default")):
    content = await file.read()
    return {"filename": file.filename, "bytes": len(content), "collection": collection}
```
Form and file handling requires the `python-multipart` package (included in `fastapi[standard]`).

### Key takeaways
- Pydantic models in the signature become JSON request bodies.
- Validation errors return 422 with a list of problems.
- `UploadFile` streams uploaded documents for ingestion pipelines.

## Responses, Status Codes and Errors
### response_model
`response_model` (or the return type annotation) validates and **filters** what you send back, so secrets never leak.
```python
class UserIn(BaseModel):
    username: str
    password: str

class UserOut(BaseModel):
    username: str

@app.post("/users", response_model=UserOut, status_code=201)
def create_user(user: UserIn):
    return user                  # password is filtered out by UserOut
```
Options: `response_model_exclude_none=True`, `response_model_exclude_unset=True`.

### Status codes
```python
from fastapi import status
@app.delete("/docs/{doc_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_doc(doc_id: int): ...
```
| Code | Meaning |
|---|---|
| 200 | OK (default) |
| 201 | Created |
| 204 | No Content |
| 400 | Bad Request |
| 401 | Unauthorized (not authenticated) |
| 403 | Forbidden (authenticated, not allowed) |
| 404 | Not Found |
| 405 | Method Not Allowed |
| 422 | Unprocessable Entity (validation failed) |
| 429 | Too Many Requests |
| 500 | Internal Server Error |

### Raising errors
```python
from fastapi import HTTPException

@app.get("/docs/{doc_id}")
def get_doc(doc_id: int):
    if doc_id not in DB:
        raise HTTPException(status_code=404, detail="Document not found")
    return DB[doc_id]
```
The client receives `{"detail": "Document not found"}` with status 404. You `raise` HTTPException — returning it would just serialize it.

### Custom exception handlers
```python
from fastapi import Request
from fastapi.responses import JSONResponse

class RateLimited(Exception): ...

@app.exception_handler(RateLimited)
async def rate_limited_handler(request: Request, exc: RateLimited):
    return JSONResponse(status_code=429, content={"detail": "slow down"})
```

### Other response classes
`JSONResponse`, `HTMLResponse`, `PlainTextResponse`, `RedirectResponse`, `FileResponse`, `StreamingResponse` and `EventSourceResponse` (Server-Sent Events).

### Key takeaways
- `response_model` validates output and hides fields you didn't declare.
- Set success codes with `status_code=`; raise `HTTPException` for errors.
- 422 means the request didn't match your declared types/constraints.

## Dependency Injection
FastAPI's `Depends` lets endpoints declare what they need — DB sessions, the current user, settings, an LLM client — and FastAPI builds and injects it.

### A simple dependency
```python
from typing import Annotated
from fastapi import Depends

def pagination(skip: int = 0, limit: int = 10):
    return {"skip": skip, "limit": limit}

Page = Annotated[dict, Depends(pagination)]

@app.get("/docs")
def list_docs(page: Page):
    return page
```
Dependencies can have their own parameters (query, headers, other dependencies), forming a **dependency graph**. Within one request, a dependency used several times is executed **once** and cached (`use_cache=True` by default).

### Dependencies with yield (setup/teardown)
```python
def get_db():
    db = SessionLocal()
    try:
        yield db            # value injected into the endpoint
    finally:
        db.close()          # runs after the response is sent

@app.get("/users/{uid}")
def read_user(uid: int, db: Annotated[Session, Depends(get_db)]):
    return db.get(User, uid)
```

### Class and settings dependencies
```python
from functools import lru_cache
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    openai_api_key: str
    top_k: int = 4

@lru_cache
def get_settings() -> Settings:
    return Settings()          # read env once

@app.get("/config")
def config(settings: Annotated[Settings, Depends(get_settings)]):
    return {"top_k": settings.top_k}
```

### Router-level and global dependencies
```python
app = FastAPI(dependencies=[Depends(verify_api_key)])          # every route
router = APIRouter(dependencies=[Depends(verify_api_key)])     # a group of routes
```

### Overriding in tests
```python
app.dependency_overrides[get_settings] = lambda: Settings(openai_api_key="test")
```

### Key takeaways
- `Depends` builds reusable, testable building blocks; `Annotated` aliases keep signatures short.
- `yield` dependencies give setup/teardown around a request.
- Dependencies are cached per request and can be overridden in tests.

## Async, Lifespan, Background Tasks and Middleware
### Lifespan: load models once
Heavy resources — embedding models, vector-store clients, HTTP clients — should be created once at startup.
```python
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request

@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.http = httpx.AsyncClient(timeout=30)
    app.state.embedder = load_embedding_model()
    yield                                  # app serves requests here
    await app.state.http.aclose()          # shutdown cleanup

app = FastAPI(lifespan=lifespan)

@app.get("/embed")
async def embed(text: str, request: Request):
    return {"dims": len(request.app.state.embedder(text))}
```
`lifespan` replaces the deprecated `@app.on_event("startup")`/`("shutdown")` handlers.

### Background tasks
Run work *after* the response is sent — logging, sending emails, writing traces.
```python
from fastapi import BackgroundTasks

def log_feedback(session_id: str, rating: int): ...

@app.post("/feedback")
def feedback(session_id: str, rating: int, tasks: BackgroundTasks):
    tasks.add_task(log_feedback, session_id, rating)
    return {"ok": True}
```
For long jobs (document ingestion, batch evals) use a real queue: Celery, RQ, Arq, Dramatiq or a workflow engine.

### Middleware
```python
import time
@app.middleware("http")
async def add_timing(request: Request, call_next):
    start = time.perf_counter()
    response = await call_next(request)
    response.headers["X-Process-Time"] = f"{time.perf_counter() - start:.4f}"
    return response
```
Built-ins include `CORSMiddleware`, `GZipMiddleware`, `TrustedHostMiddleware` and `HTTPSRedirectMiddleware`.

### CORS
```python
from fastapi.middleware.cors import CORSMiddleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["https://myapp.com"],
    allow_methods=["*"],
    allow_headers=["*"],
)
```

### Key takeaways
- Use `lifespan` to load models and clients once and clean up on shutdown.
- `BackgroundTasks` run after the response; use a queue for long jobs.
- Middleware wraps every request — timing, CORS, compression, auth.

## Security: API Keys, OAuth2 and JWT
### API key header
```python
from fastapi.security import APIKeyHeader
from fastapi import Security, HTTPException

api_key_header = APIKeyHeader(name="X-API-Key")

def verify_api_key(key: str = Security(api_key_header)):
    if key != settings.service_key:
        raise HTTPException(status_code=401, detail="Invalid API key")
    return key
```

### OAuth2 password flow with JWT
```python
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
import jwt                          # PyJWT
from datetime import datetime, timedelta, timezone

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token")

@app.post("/token")
def login(form: Annotated[OAuth2PasswordRequestForm, Depends()]):
    user = authenticate(form.username, form.password)   # verify a password hash
    if not user:
        raise HTTPException(401, "Bad credentials", headers={"WWW-Authenticate": "Bearer"})
    payload = {"sub": user.username, "exp": datetime.now(timezone.utc) + timedelta(minutes=30)}
    return {"access_token": jwt.encode(payload, SECRET_KEY, algorithm="HS256"), "token_type": "bearer"}

def current_user(token: Annotated[str, Depends(oauth2_scheme)]):
    try:
        data = jwt.decode(token, SECRET_KEY, algorithms=["HS256"])
    except jwt.InvalidTokenError:
        raise HTTPException(401, "Invalid token")
    return data["sub"]

@app.get("/me")
def me(user: Annotated[str, Depends(current_user)]):
    return {"user": user}
```
`OAuth2PasswordBearer` reads `Authorization: Bearer <token>` and adds an "Authorize" button to `/docs`. Store password **hashes** (bcrypt / Argon2, e.g. via `pwdlib`), never plain text.

### Security checklist for LLM APIs
- Keep provider keys server-side; never ship them in mobile/web clients.
- Rate-limit per user/key (e.g. `slowapi`, or a gateway) to control cost.
- Validate and cap input length (`max_length`) — prompt size is cost.
- Log prompts carefully: mask PII before sending traces.
- Use HTTPS and restrictive CORS.

### Key takeaways
- `APIKeyHeader`, `HTTPBearer` and `OAuth2PasswordBearer` are security dependencies that also document auth in OpenAPI.
- JWTs carry signed claims like `sub` and `exp`; verify signature and expiry on every request.
- 401 = not authenticated, 403 = authenticated but not allowed.

## Streaming LLM Output: StreamingResponse, SSE and WebSockets
Users expect tokens to appear as they are generated. FastAPI supports three patterns.

### StreamingResponse
```python
from fastapi.responses import StreamingResponse

async def token_stream(prompt: str):
    async for chunk in llm.astream(prompt):          # e.g. a LangChain chat model
        yield chunk.content

@app.get("/generate")
async def generate(prompt: str):
    return StreamingResponse(token_stream(prompt), media_type="text/plain")
```

### Server-Sent Events (SSE)
SSE is a one-way, server → client stream over plain HTTP using the `text/event-stream` format. Recent FastAPI versions include native SSE support in `fastapi.sse`:
```python
from fastapi.sse import EventSourceResponse, ServerSentEvent

@app.post("/chat/stream", response_class=EventSourceResponse)
async def chat_stream(req: ChatRequest):
    async for chunk in agent.astream({"messages": [("user", req.message)]}, stream_mode="messages"):
        token, meta = chunk
        yield ServerSentEvent(data=token.content, event="token")
    yield ServerSentEvent(data="[DONE]", event="end")
```
On the wire each event looks like `event: token` / `data: "Hel"` followed by a blank line; `data` is JSON-encoded (use `raw_data` for plain text). Browsers consume SSE with `EventSource`; it auto-reconnects and sends `Last-Event-ID`.

### WebSockets
Bidirectional and long-lived — good for voice agents or chat UIs that send interrupts.
```python
from fastapi import WebSocket, WebSocketDisconnect

@app.websocket("/ws")
async def ws_chat(ws: WebSocket):
    await ws.accept()
    try:
        while True:
            msg = await ws.receive_text()
            async for chunk in llm.astream(msg):
                await ws.send_text(chunk.content)
    except WebSocketDisconnect:
        pass
```

### Choosing
| Need | Use |
|---|---|
| Simple token stream | StreamingResponse |
| Typed events, reconnection, browser-friendly | SSE |
| Two-way realtime (interrupts, audio) | WebSocket |

### Key takeaways
- Return a generator wrapped in `StreamingResponse` to stream bytes/text.
- SSE (`text/event-stream`) is the standard for streaming chat tokens to browsers.
- WebSockets are bidirectional; SSE is server-to-client only.

## Project Structure, Testing and Deployment
### Organising with APIRouter
```python
# app/routers/chat.py
from fastapi import APIRouter
router = APIRouter(prefix="/chat", tags=["chat"])

@router.post("/")
async def chat(req: ChatRequest): ...

# app/main.py
from fastapi import FastAPI
from app.routers import chat, ingest
app = FastAPI(lifespan=lifespan)
app.include_router(chat.router)
app.include_router(ingest.router, prefix="/v1")
```
A typical layout: `routers/`, `schemas/` (Pydantic), `services/` (RAG/agent logic), `deps.py`, `config.py`.

### Testing with TestClient
```python
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}

def test_validation():
    r = client.post("/chat", json={"message": ""})
    assert r.status_code == 422
```
TestClient is built on HTTPX. For async tests use `httpx.AsyncClient` with `ASGITransport(app=app)`. Override dependencies (`app.dependency_overrides`) to swap the real LLM for a fake one.

### Deployment
```dockerfile
FROM python:3.13-slim
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN pip install uv && uv sync --frozen --no-dev
COPY . .
CMD ["uv", "run", "fastapi", "run", "app/main.py", "--port", "8000", "--workers", "4"]
```
- Run several worker processes (`--workers`) to use multiple CPU cores.
- Put a reverse proxy/load balancer (Nginx, Traefik, cloud LB) in front for TLS.
- Add health checks, structured logging, tracing (OpenTelemetry/Langfuse) and timeouts on outbound LLM calls.
- Long-running inference belongs on GPU servers (vLLM, TGI) called over HTTP, not inside the API process.

### A complete mini RAG endpoint
```python
class Query(BaseModel):
    question: str = Field(min_length=3, max_length=1000)
    k: int = Field(4, ge=1, le=20)

class RAGAnswer(BaseModel):
    answer: str
    sources: list[str]

@router.post("/ask", response_model=RAGAnswer)
async def ask(q: Query, retriever: Annotated[Retriever, Depends(get_retriever)]):
    docs = await retriever.ainvoke(q.question)
    context = "\n\n".join(d.page_content for d in docs[: q.k])
    answer = await llm.ainvoke(f"Answer using only this context:\n{context}\n\nQ: {q.question}")
    return RAGAnswer(answer=answer.content, sources=[d.metadata["source"] for d in docs[: q.k]])
```

### Key takeaways
- Split large apps with `APIRouter(prefix=..., tags=[...])` and `include_router`.
- `TestClient` + dependency overrides make endpoints easy to test without real LLM calls.
- Deploy with multiple workers behind a proxy; keep GPU inference in dedicated servers.
