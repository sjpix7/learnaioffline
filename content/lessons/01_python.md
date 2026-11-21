---
id: python
title: Python for AI Engineers
short: The language behind every AI stack — from basics to async, typing and Pydantic
emoji: 🐍
color: #3776AB
---
## Python Basics: Values, Types and Variables
Python is the default language of AI engineering: every framework in this app (LangChain, LangGraph, LlamaIndex, FastAPI, RAGAS, CrewAI) is Python-first. This lesson builds the foundation.

### Dynamic but strong typing
A **variable** is a name bound to an object. Python is *dynamically typed* (names have no fixed type) but *strongly typed* (it will not silently mix incompatible types).
```python
x = 10          # int
x = "ten"       # now a str — allowed, the name is re-bound
print(type(x))  # <class 'str'>
"5" + 5         # TypeError: can only concatenate str (not "int") to str
```

### Core built-in types
| Type | Example | Mutable? |
|---|---|---|
| int | 42, 10**20 (arbitrary precision) | No |
| float | 3.14, 1e-3 | No |
| bool | True, False (subclass of int) | No |
| str | "hello" (Unicode) | No |
| list | [1, 2, 3] | Yes |
| tuple | (1, 2) | No |
| dict | {"k": "v"} | Yes |
| set | {1, 2} | Yes |
| NoneType | None | No |

### Numbers and operators
```python
7 / 2    # 3.5  true division always returns float
7 // 2   # 3    floor division
-7 // 2  # -4   floors toward negative infinity
7 % 3    # 1    remainder
2 ** 10  # 1024 power
round(2.5), round(3.5)  # (2, 4) banker's rounding
0.1 + 0.2 == 0.3        # False — floating point error; use math.isclose
```

### Strings
Strings are immutable sequences of Unicode characters. Indexing starts at 0; negative indices count from the end.
```python
s = "LangGraph"
s[0], s[-1]      # ('L', 'h')
s[4:]            # 'Graph'   slicing: [start:stop:step], stop excluded
s[::-1]          # 'hparGgnaL'
s.lower(), s.upper(), s.replace("Graph", "Chain")
"a,b,c".split(",")        # ['a', 'b', 'c']
"-".join(["a", "b"])      # 'a-b'
name, n = "RAG", 3
f"{name} x{n} = {n*2:.1f}"  # 'RAG x3 = 6.0'
```

### Truthiness and identity
Empty containers, `0`, `0.0`, `""` and `None` are *falsy*; everything else is truthy. `==` compares values, `is` compares identity — use `is` only for `None`, `True`, `False`.
```python
if not []: print("empty list is falsy")
a = [1]; b = [1]
a == b, a is b   # (True, False)
```

> 💡 Tip: `bool` is a subclass of `int`, so `True + True == 2`. This is why `sum(flags)` counts True values.

### Key takeaways
- Names point to objects; types belong to objects, not names.
- `/` always gives a float, `//` floors, `%` gives the remainder with the sign of the divisor.
- Slices exclude the stop index; negative indices count from the end.
- Use f-strings for formatting and `is None` for None checks.

## Control Flow and Functions
### Conditionals and loops
```python
score = 0.82
if score >= 0.9:
    label = "excellent"
elif score >= 0.7:
    label = "good"
else:
    label = "needs work"

for i, doc in enumerate(["a.pdf", "b.pdf"], start=1):
    print(i, doc)          # 1 a.pdf / 2 b.pdf

n = 0
while n < 3:
    n += 1
else:
    print("runs when loop ends without break")
```
`range(start, stop, step)` excludes `stop`: `list(range(2, 10, 3))` → `[2, 5, 8]`. `break` exits a loop, `continue` skips to the next iteration, and a loop's `else` block runs only when no `break` happened.

### match statements (Python 3.10+)
```python
def route(event: dict) -> str:
    match event:
        case {"type": "tool_call", "name": name}:
            return f"call {name}"
        case {"type": "message"}:
            return "chat"
        case _:
            return "unknown"
```

### Defining functions
```python
def chunk(text: str, size: int = 100, *, overlap: int = 0) -> list[str]:
    """Split text into fixed-size chunks."""
    step = size - overlap
    return [text[i:i + size] for i in range(0, len(text), step)]

chunk("abcdef", 4, overlap=2)   # ['abcd', 'cdef', 'ef']
```
- Parameters after `*` are **keyword-only**; parameters before `/` are **positional-only**.
- `*args` collects extra positional arguments into a tuple; `**kwargs` collects keyword arguments into a dict.
- A function without `return` returns `None`.

### The mutable default trap
Default values are evaluated **once**, when the function is defined.
```python
def add(item, bucket=[]):      # BUG: shared list
    bucket.append(item)
    return bucket
add(1); add(2)                 # [1, 2]  — surprise!

def add_fixed(item, bucket=None):
    if bucket is None:
        bucket = []
    bucket.append(item)
    return bucket
```

### Scope, closures and lambdas
Python resolves names using **LEGB**: Local → Enclosing → Global → Built-in. Use `nonlocal` to rebind a variable of an enclosing function and `global` for module-level names.
```python
def counter():
    count = 0
    def inc():
        nonlocal count
        count += 1
        return count
    return inc
c = counter(); c(); c()   # 2

square = lambda x: x * x
sorted(["bb", "a", "ccc"], key=len)   # ['a', 'bb', 'ccc']
```

### Key takeaways
- `range` and slices exclude the stop value.
- Never use a mutable object (list/dict) as a default argument.
- `*args` → tuple, `**kwargs` → dict; `*` marks keyword-only params.
- Closures capture variables, and `nonlocal` lets you rebind them.

## Data Structures and Comprehensions
### Lists
```python
nums = [5, 2, 9]
nums.append(1)        # [5, 2, 9, 1]
nums.extend([7, 8])   # adds each item
nums.insert(0, 0)     # insert at index
nums.pop()            # removes & returns last item
sorted(nums)          # new sorted list; nums.sort() sorts in place and returns None
nums[1:3] = [4, 4]    # slice assignment
```

### Tuples and unpacking
```python
point = (3, 4)
x, y = point
first, *rest = [1, 2, 3, 4]     # first=1, rest=[2, 3, 4]
a, b = b, a                      # swap
single = (1,)                    # a one-element tuple needs the comma
```

### Dictionaries
Dicts preserve insertion order (guaranteed since 3.7).
```python
cfg = {"model": "gpt", "temperature": 0.2}
cfg.get("top_p", 1.0)            # 1.0 default when key missing
cfg["max_tokens"] = 512
for key, value in cfg.items(): ...
merged = cfg | {"temperature": 0.7}   # 3.9+ merge; right side wins
from collections import defaultdict, Counter
counts = Counter("banana")            # Counter({'a': 3, 'n': 2, 'b': 1})
groups = defaultdict(list); groups["docs"].append("a.txt")
```
Dict keys must be **hashable** (immutable): strings, numbers, tuples of immutables — not lists.

### Sets
```python
a, b = {1, 2, 3}, {2, 3, 4}
a | b, a & b, a - b, a ^ b   # union, intersection, difference, symmetric difference
```
Membership tests on sets and dicts are O(1) on average; on lists they are O(n).

### Comprehensions
```python
squares = [x * x for x in range(5)]                 # [0, 1, 4, 9, 16]
evens = [x for x in range(10) if x % 2 == 0]
lengths = {w: len(w) for w in ["rag", "agent"]}     # dict comprehension
unique = {c.lower() for c in "AaB"}                 # {'a', 'b'}
total = sum(x * x for x in range(5))                # generator expression, no list built
matrix = [[r * c for c in range(3)] for r in range(2)]
```

### Copying
Assignment never copies. `list(x)`, `x[:]` and `x.copy()` make **shallow** copies (nested objects are shared); `copy.deepcopy` copies recursively.
```python
import copy
a = [[1], [2]]
b = a.copy(); b[0].append(9)    # a is now [[1, 9], [2]]
c = copy.deepcopy(a)            # fully independent
```

### Key takeaways
- `list.sort()` returns `None`; `sorted()` returns a new list.
- Use sets/dicts for fast membership; dict keys must be hashable.
- Comprehensions are concise and fast; generator expressions avoid building lists.
- Shallow copies share nested objects.

## Object-Oriented Python and Dataclasses
### Classes
```python
class Document:
    kind = "text"                      # class attribute, shared

    def __init__(self, content: str, source: str):
        self.content = content         # instance attributes
        self.source = source

    def __repr__(self) -> str:
        return f"Document(source={self.source!r})"

    def __len__(self) -> int:
        return len(self.content)

doc = Document("hello", "a.txt")
len(doc)            # 5 — calls __len__
```
**Dunder methods** (`__init__`, `__repr__`, `__len__`, `__eq__`, `__iter__`, `__call__`...) plug your objects into Python syntax.

### Inheritance and super()
```python
class PDFDocument(Document):
    def __init__(self, content, source, pages: int):
        super().__init__(content, source)
        self.pages = pages
```
Python resolves methods with the **MRO** (method resolution order); see `PDFDocument.__mro__`.

### Methods: instance, class and static
```python
class Embedder:
    registry = {}
    @classmethod
    def from_name(cls, name): return cls()      # alternative constructor
    @staticmethod
    def normalize(v): ...                         # no self/cls
    @property
    def dim(self): return 768                     # attribute-style access
```

### Abstract base classes and protocols
```python
from abc import ABC, abstractmethod
class Retriever(ABC):
    @abstractmethod
    def retrieve(self, query: str) -> list[str]: ...
# Retriever() -> TypeError: can't instantiate abstract class
```
`typing.Protocol` gives *structural* typing: any object with the right methods matches, without inheriting.

### Dataclasses
```python
from dataclasses import dataclass, field

@dataclass
class Chunk:
    text: str
    score: float = 0.0
    tags: list[str] = field(default_factory=list)   # never a bare [] default

@dataclass(frozen=True, slots=True)
class Point:
    x: int
    y: int
```
`@dataclass` auto-generates `__init__`, `__repr__` and `__eq__`. `frozen=True` makes instances immutable (and hashable); `order=True` adds comparison methods.

### Key takeaways
- Dunder methods integrate classes with built-in syntax.
- Use `@classmethod` for alternative constructors and `@property` for computed attributes.
- `@dataclass` removes boilerplate; use `field(default_factory=list)` for mutable defaults.
- ABCs enforce interfaces; Protocols match by shape.

## Iterators, Generators, Decorators and Context Managers
These four features appear constantly in AI frameworks: streaming tokens (generators), `@tool` / `@observe` / `@step` (decorators) and tracing spans (context managers).

### Iterators and generators
An **iterable** has `__iter__`; an **iterator** has `__next__` and raises `StopIteration` when exhausted. A **generator** function uses `yield` and produces values lazily.
```python
def stream_tokens(text):
    for word in text.split():
        yield word

g = stream_tokens("hello agent world")
next(g)        # 'hello'
list(g)        # ['agent', 'world'] — generators are consumed once
list(g)        # []
```
`yield from other_gen()` delegates to another generator. Generators keep memory flat for huge files or streamed LLM output.

### itertools essentials
```python
from itertools import islice, chain, batched   # batched is 3.12+
list(islice(range(100), 3))           # [0, 1, 2]
list(chain([1, 2], [3]))              # [1, 2, 3]
list(batched("abcde", 2))             # [('a','b'), ('c','d'), ('e',)]
```

### Decorators
A decorator is a function that takes a function and returns a new one.
```python
import functools, time

def timed(fn):
    @functools.wraps(fn)                 # keep __name__ and docstring
    def wrapper(*args, **kwargs):
        start = time.perf_counter()
        try:
            return fn(*args, **kwargs)
        finally:
            print(f"{fn.__name__} took {time.perf_counter() - start:.3f}s")
    return wrapper

@timed
def embed(text): ...
# same as: embed = timed(embed)
```
Decorators with arguments add one more layer: `def retry(times): def deco(fn): ...; return deco`. `functools.lru_cache` / `functools.cache` memoize pure functions.

### Context managers
`with` guarantees setup and cleanup, even when exceptions happen.
```python
from contextlib import contextmanager

@contextmanager
def span(name):
    print("start", name)
    try:
        yield name
    finally:
        print("end", name)

with span("retrieve") as s:
    ...
```
A class works too by implementing `__enter__` and `__exit__`. Files, locks, DB sessions and tracing spans (Langfuse's `start_as_current_observation`) all use this pattern.

### Key takeaways
- Generators are lazy and single-use; perfect for streaming.
- Always use `functools.wraps` inside decorators.
- `@decorator` is syntax sugar for `fn = decorator(fn)`.
- Context managers make cleanup reliable via `try/finally`.

## Type Hints and Pydantic
Type hints don't change runtime behaviour, but they power editors, static checkers (mypy, pyright) — and frameworks like FastAPI, LangChain tools and Pydantic, which *read* hints at runtime to validate data and build JSON schemas.

### Type hint basics
```python
from typing import Literal, Optional, TypedDict, Annotated, Callable, Any

def search(query: str, k: int = 5) -> list[str]: ...
def maybe(x: int | None = None) -> None: ...          # same as Optional[int]
Mode = Literal["dense", "sparse", "hybrid"]

class State(TypedDict):          # used by LangGraph state
    question: str
    docs: list[str]

Score = Annotated[float, "between 0 and 1"]           # metadata attached to a type
Handler = Callable[[str], str]
```
Generics (3.12+ syntax): `def first[T](items: list[T]) -> T: return items[0]`.

### Pydantic v2 models
Pydantic validates and converts data using type hints.
```python
from pydantic import BaseModel, Field, field_validator, ValidationError

class Answer(BaseModel):
    text: str
    confidence: float = Field(ge=0, le=1)
    sources: list[str] = []

    @field_validator("text")
    @classmethod
    def not_empty(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("empty answer")
        return v

a = Answer(text="Paris", confidence="0.9")   # "0.9" coerced to 0.9 (lax mode)
a.model_dump()          # {'text': 'Paris', 'confidence': 0.9, 'sources': []}
a.model_dump_json()     # JSON string
Answer.model_json_schema()   # JSON Schema — used for LLM structured output
try:
    Answer(text="x", confidence=2)
except ValidationError as e:
    print(e.error_count())    # 1
```
Pydantic v2 (Rust core) replaced v1's `.dict()`/`.json()`/`parse_obj` with `model_dump()`, `model_dump_json()` and `model_validate()`. Use `model_config = ConfigDict(strict=True)` to disable coercion and `extra="forbid"` to reject unknown fields.

### pydantic-settings
```python
from pydantic_settings import BaseSettings
class Settings(BaseSettings):
    openai_api_key: str
    model_name: str = "gpt-5-mini"
settings = Settings()   # reads environment variables / .env
```

> 💡 Tip: `with_structured_output(Answer)` in LangChain and `response_model` in FastAPI both rely on Pydantic schemas like the one above.

### Key takeaways
- Hints are ignored by the interpreter but read by tools and frameworks.
- `X | None` is the modern Optional; `Literal` restricts values; `TypedDict` types dict shapes.
- Pydantic validates, coerces and serializes; `model_json_schema()` feeds LLM structured outputs.

## Errors, Logging and Files
### Exceptions
```python
try:
    value = int("42a")
except ValueError as e:
    print("bad number:", e)
except (KeyError, IndexError):
    ...
else:
    print("runs only if no exception")
finally:
    print("always runs")
```
Raise with `raise ValueError("msg")`; chain with `raise RuntimeError("wrapped") from e`. Create custom errors by subclassing `Exception`. Catch specific exceptions — a bare `except:` also catches `KeyboardInterrupt` and `SystemExit`.

Python 3.11 added **exception groups** and `except*`, used by `asyncio.TaskGroup` when several tasks fail.

### Retrying flaky LLM calls
```python
import random, time

def with_retry(fn, attempts=4, base=0.5):
    for i in range(attempts):
        try:
            return fn()
        except TimeoutError:
            if i == attempts - 1:
                raise
            time.sleep(base * 2 ** i + random.random() * 0.1)   # exponential backoff + jitter
```
Libraries such as `tenacity` provide this as a decorator.

### Logging
```python
import logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger(__name__)
log.info("retrieved %d docs", 5)     # lazy formatting
log.exception("failed")              # inside except: logs traceback
```
Levels: DEBUG < INFO < WARNING < ERROR < CRITICAL. Default level is WARNING.

### Files, paths and JSON
```python
from pathlib import Path
import json

p = Path("data") / "docs.jsonl"
p.parent.mkdir(parents=True, exist_ok=True)
with p.open("w", encoding="utf-8") as f:
    for row in [{"id": 1}, {"id": 2}]:
        f.write(json.dumps(row) + "\n")      # JSON Lines: one object per line

rows = [json.loads(line) for line in p.read_text(encoding="utf-8").splitlines()]
list(Path("data").glob("*.jsonl"))
```
`json.dumps` → string, `json.dump` → file; `json.loads` ← string, `json.load` ← file.

### Key takeaways
- `else` runs when no exception occurred; `finally` always runs.
- Use exponential backoff with jitter for rate limits and timeouts.
- Prefer `logging` over `print` in services; use `%s` lazy formatting.
- `pathlib` is the modern way to work with files; JSONL is ideal for datasets.

## Async Python and Concurrency
LLM apps are **I/O bound**: most time is spent waiting on model APIs, vector DBs and HTTP. `asyncio` lets one thread juggle thousands of waits.

### async / await
```python
import asyncio

async def call_llm(prompt: str) -> str:
    await asyncio.sleep(1)          # stands in for a network call
    return prompt.upper()

async def main():
    # run three calls concurrently: ~1s total, not 3s
    results = await asyncio.gather(call_llm("a"), call_llm("b"), call_llm("c"))
    print(results)                  # ['A', 'B', 'C'] — order matches inputs

asyncio.run(main())
```
- Calling an `async def` function returns a **coroutine**; nothing runs until it is awaited or scheduled.
- `asyncio.create_task()` schedules a coroutine to run concurrently.
- `asyncio.TaskGroup` (3.11+) is structured concurrency: if one task fails the others are cancelled.
- `asyncio.wait_for(coro, timeout=5)` / `asyncio.timeout(5)` enforce deadlines.

### Limiting concurrency
```python
sem = asyncio.Semaphore(5)          # at most 5 in-flight requests

async def limited(prompt):
    async with sem:
        return await call_llm(prompt)

async def run_all(prompts):
    async with asyncio.TaskGroup() as tg:
        tasks = [tg.create_task(limited(p)) for p in prompts]
    return [t.result() for t in tasks]
```

### Don't block the event loop
`time.sleep()` or CPU-heavy code inside a coroutine freezes *every* task. Use `await asyncio.sleep()`, async clients (`httpx.AsyncClient`, `AsyncOpenAI`) and `await asyncio.to_thread(blocking_fn)` for blocking libraries.

### Threads vs processes vs async
| Tool | Best for | Notes |
|---|---|---|
| asyncio | many concurrent I/O waits | single thread, cooperative |
| threading / ThreadPoolExecutor | blocking I/O libraries | GIL limits CPU parallelism (in standard builds) |
| multiprocessing / ProcessPoolExecutor | CPU-bound work | separate interpreters, true parallelism |

Python 3.13 introduced an experimental **free-threaded** (no-GIL) build, and Python 3.14 made free-threading officially supported (PEP 779), though it remains a separate build.

### Async generators
```python
async def stream():
    for tok in ["Hel", "lo"]:
        await asyncio.sleep(0.1)
        yield tok

async def consume():
    async for tok in stream():
        print(tok, end="")
```
This is exactly how token streaming works in FastAPI endpoints and LangChain's `astream()`.

### Key takeaways
- `gather` runs coroutines concurrently and returns results in input order.
- Use semaphores to respect API rate limits.
- Never call blocking functions inside `async def`; offload with `asyncio.to_thread`.
- Async is for I/O; processes are for CPU-bound work.

## Environments, Packaging and Testing
### Virtual environments and uv
Each project should have an isolated environment so dependency versions don't clash.
```bash
python -m venv .venv && source .venv/bin/activate
pip install "langchain>=1.0" fastapi

# uv: a fast Rust-based package & project manager
uv init rag-app && cd rag-app
uv add langgraph fastapi "uvicorn[standard]"
uv run python main.py
uv lock              # writes uv.lock with exact pinned versions
```
`pyproject.toml` (PEP 621) is the standard place to declare a project's metadata and dependencies.

### Modules, packages and imports
```text
rag_app/
  __init__.py
  retrieval.py
  api/
    __init__.py
    routes.py
```
`from rag_app.retrieval import search` (absolute) or `from ..retrieval import search` (relative). Code under `if __name__ == "__main__":` runs only when the file is executed directly, not when imported.

### Testing with pytest
```python
# test_chunking.py
import pytest
from rag_app.chunking import chunk

def test_chunk_sizes():
    assert chunk("abcdef", 4) == ["abcd", "ef"]

@pytest.mark.parametrize("size", [1, 2, 3])
def test_no_loss(size):
    assert "".join(chunk("hello", size)) == "hello"

@pytest.fixture
def fake_llm():
    return lambda prompt: "stub answer"

def test_answer(fake_llm):
    assert fake_llm("q") == "stub answer"
```
Mock LLMs in unit tests (deterministic, free); keep a separate, smaller suite of evaluation tests (RAGAS, LangSmith, Langfuse experiments) for real model behaviour. `unittest.mock.patch` replaces objects during a test.

### Code quality tools
- **ruff** — very fast linter + formatter.
- **mypy / pyright** — static type checking.
- **pre-commit** — run checks before each commit.

### Key takeaways
- One virtual environment per project; `uv` makes it fast and reproducible.
- `pyproject.toml` declares dependencies; lock files pin exact versions.
- pytest fixtures and parametrize keep tests short; mock the LLM in unit tests.

## Modern Python: 3.10 to 3.14 Features
Knowing recent features helps you read modern framework code.

### Python 3.10–3.12 highlights
- **3.10**: structural pattern matching (`match/case`), `X | Y` union types, better error messages.
- **3.11**: big speedups (Faster CPython), `ExceptionGroup` and `except*`, `asyncio.TaskGroup`, `tomllib`.
- **3.12**: type-parameter syntax `def f[T](x: T) -> T`, the `type` statement for aliases, more flexible f-strings, `itertools.batched`.

```python
type Vector = list[float]          # 3.12 type alias statement

def top_k[T](items: list[T], k: int) -> list[T]:
    return items[:k]
```

### Python 3.13 (Oct 2024)
- New interactive REPL with multiline editing and colour.
- Experimental free-threaded build (PEP 703) and an experimental JIT compiler.

### Python 3.14 (Oct 2025)
- **PEP 750 template strings (t-strings)**: `t"Hello {name}"` returns a `Template` object (static parts + interpolations) instead of a `str`, so libraries can safely escape values — useful against injection in SQL, HTML or prompts.
- **PEP 649/749 deferred evaluation of annotations**: annotations are evaluated lazily; forward references no longer need quotes. The new `annotationlib` module inspects them.
- **PEP 779**: free-threaded Python is officially supported.
- **PEP 734**: multiple interpreters in the standard library (`concurrent.interpreters`).
- `compression.zstd` module, syntax highlighting in the REPL, and remote attaching for `pdb`.

```python
# Python 3.14+
from string.templatelib import Template
name = "Ada"
tpl = t"Hello {name}"
isinstance(tpl, Template)   # True — not a str
tpl.strings                 # ('Hello ', '')
tpl.values                  # ('Ada',)
```

### Useful standard-library tools for AI work
| Module | Use |
|---|---|
| functools | cache, lru_cache, partial, reduce |
| itertools | batched, islice, chain, groupby |
| collections | Counter, defaultdict, deque, OrderedDict |
| dataclasses | lightweight data containers |
| pathlib / json / csv | data loading |
| asyncio | concurrency for API calls |
| concurrent.futures | thread and process pools |
| re | regex for cleaning text |

### Key takeaways
- Pattern matching (3.10), TaskGroup (3.11) and generic syntax (3.12) appear in modern code.
- 3.14 brings t-strings, deferred annotations and official free-threading.
- The standard library covers most data-wrangling needs before you reach for packages.
