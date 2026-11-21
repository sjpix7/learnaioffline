---
id: langgraph
title: LangGraph
short: Stateful, controllable agents as graphs — persistence, interrupts, multi-agent
emoji: 🕸️
color: #7B1FA2
---
## Why LangGraph? Agents as State Machines
LangGraph is a low-level orchestration framework for **long-running, stateful agents**. Instead of a hidden loop, you describe your application as a **graph**: nodes do work, edges decide what runs next, and a shared **state** flows through it.

### What LangGraph gives you
- **Cycles** — loops such as "think → act → observe → think" (classic DAG pipelines can't loop).
- **Durable execution** — every step is checkpointed, so runs can resume after failures or restarts.
- **Human-in-the-loop** — pause anywhere with `interrupt()`, inspect or edit state, then resume.
- **Memory** — short-term (thread state) and long-term (stores).
- **Streaming** — of state updates, LLM tokens and custom events.
- **Fine control** — you decide exactly which steps are LLM-driven and which are deterministic.

LangGraph 1.0 was released in October 2025 (alongside LangChain 1.0) with no breaking changes to the core graph API; 1.1 (Mar 2026) added type-safe `version="v2"` streaming/invoke output, and 1.2 (May 2026) added per-node timeouts and error handlers. LangChain's `create_agent` is itself built on LangGraph.

### Mental model
| Concept | Meaning |
|---|---|
| State | A typed dict (or Pydantic/dataclass) shared by all nodes |
| Node | A function `state -> partial state update` |
| Edge | Fixed transition from one node to another |
| Conditional edge | A function that picks the next node at runtime |
| START / END | Virtual entry and exit nodes |
| Super-step | One "tick" in which all scheduled nodes run (possibly in parallel) |
| Checkpointer | Saves state after every super-step |

LangGraph's execution model is inspired by Google's **Pregel**: nodes run in super-steps and communicate via state channels.

```bash
pip install -U langgraph
```

### Key takeaways
- LangGraph models agents as graphs of nodes and edges over shared state.
- Cycles, persistence, interrupts and streaming are first-class.
- Use LangChain's `create_agent` for a standard agent; drop to LangGraph for custom control flow.

## State, Nodes and Edges
### Your first graph
```python
from typing import TypedDict
from langgraph.graph import StateGraph, START, END

class State(TypedDict):
    question: str
    answer: str

def think(state: State) -> dict:
    return {"answer": f"Thinking about: {state['question']}"}

def polish(state: State) -> dict:
    return {"answer": state["answer"] + " ✓"}

builder = StateGraph(State)
builder.add_node("think", think)
builder.add_node("polish", polish)
builder.add_edge(START, "think")
builder.add_edge("think", "polish")
builder.add_edge("polish", END)
graph = builder.compile()

graph.invoke({"question": "What is RAG?"})
# {'question': 'What is RAG?', 'answer': 'Thinking about: What is RAG? ✓'}
```

### Rules worth memorising
- A node receives the **current state** and returns a **partial update** (only the keys it changes).
- Without a reducer, a returned key **overwrites** the old value.
- You must call `.compile()` before `invoke`/`stream`; compilation validates the graph and attaches a checkpointer if you pass one.
- `add_sequence([a, b, c])` is shorthand for a chain of nodes.
- `graph.get_graph().draw_mermaid()` renders the graph.

### Input/output schemas and private state
```python
class InputState(TypedDict):  question: str
class OutputState(TypedDict): answer: str
class OverallState(InputState, OutputState): scratch: list[str]

builder = StateGraph(OverallState, input_schema=InputState, output_schema=OutputState)
```
The caller only sees `answer`, while nodes can use internal keys like `scratch`.

### Runtime context
Static, per-run configuration (user id, DB handle, model name) that is **not** part of state can be passed as context:
```python
from dataclasses import dataclass
from langgraph.runtime import Runtime

@dataclass
class Ctx:
    user_id: str

def node(state: State, runtime: Runtime[Ctx]):
    return {"answer": f"hello {runtime.context.user_id}"}

builder = StateGraph(State, context_schema=Ctx)
...
graph.invoke({"question": "hi"}, context={"user_id": "u1"})
```

### Key takeaways
- Nodes return partial updates; un-reduced keys are overwritten.
- `compile()` is mandatory and is where you attach a checkpointer.
- Separate input/output schemas hide internal state; `context` carries run-level config.

## Reducers and Message State
### Reducers merge updates
When several nodes (or several steps) write to the same key, a **reducer** decides how values combine. Declare it with `Annotated[type, reducer]`.
```python
import operator
from typing import Annotated, TypedDict

class State(TypedDict):
    steps: Annotated[list[str], operator.add]   # append instead of overwrite
    count: int                                  # no reducer -> overwrite

def a(state): return {"steps": ["a"], "count": 1}
def b(state): return {"steps": ["b"], "count": 2}
# START -> a -> b -> END with input {"steps": [], "count": 0}
# result: {'steps': ['a', 'b'], 'count': 2}
```
Any binary function `(old, new) -> merged` works as a reducer: `operator.add` for lists/ints, a custom `merge_dicts`, etc. To replace a reduced value entirely, return `Overwrite(value)` (from `langgraph.types`).

### add_messages: the chat reducer
```python
from langgraph.graph.message import add_messages
from langchain_core.messages import AnyMessage

class ChatState(TypedDict):
    messages: Annotated[list[AnyMessage], add_messages]
```
`add_messages`:
- **appends** new messages,
- **updates** a message in place if a new message has the same `id`,
- accepts tuples/dicts like `("user", "hi")` and converts them to message objects,
- deletes messages when given `RemoveMessage(id=...)`.

Because this pattern is so common, LangGraph ships `MessagesState`:
```python
from langgraph.graph import MessagesState
class AgentState(MessagesState):
    documents: list[str]          # add your own keys
```

### Parallel writes need reducers
If two nodes run in the same super-step and both write a key **without** a reducer, LangGraph raises `InvalidUpdateError` — it cannot know which value to keep.

### Key takeaways
- `Annotated[list, operator.add]` appends; keys without reducers overwrite.
- `add_messages` appends, de-duplicates by id and supports `RemoveMessage`.
- Concurrent writes to the same key require a reducer.

## Conditional Edges, Routing and Loops
### Routing functions
```python
from typing import Literal

def route(state: State) -> Literal["search", "answer"]:
    return "search" if needs_more_info(state) else "answer"

builder.add_conditional_edges("classify", route)
# or with an explicit path map:
builder.add_conditional_edges("classify", route, {"search": "web_search", "answer": "respond"})
```
A routing function reads the state and returns the **name** of the next node (or `END`, or a list of names to fan out).

### A loop with an exit condition
```python
class S(TypedDict):
    draft: str
    revisions: int

def write(state):  return {"draft": improve(state["draft"]), "revisions": state["revisions"] + 1}
def should_continue(state):
    return END if state["revisions"] >= 3 or good_enough(state["draft"]) else "write"

builder = StateGraph(S)
builder.add_node("write", write)
builder.add_edge(START, "write")
builder.add_conditional_edges("write", should_continue)
graph = builder.compile()
```

### Recursion limit
Each super-step counts against `recursion_limit`. When it is exceeded LangGraph raises `GraphRecursionError`, which protects you from infinite loops:
```python
graph.invoke(inputs, {"recursion_limit": 50})
```
Older releases defaulted to 25 steps; current 1.x releases use a much higher default, so **set an explicit limit** for agent loops in production.

### Command: update state and route in one return
```python
from langgraph.types import Command

def triage(state) -> Command[Literal["billing", "tech"]]:
    dept = classify(state["messages"][-1].content)
    return Command(update={"department": dept}, goto=dept)
```
`Command(goto=..., update=...)` replaces a separate conditional edge; `graph=Command.PARENT` navigates to a node in a parent graph (used for multi-agent handoffs).

### Key takeaways
- Conditional edges call a routing function that returns the next node name.
- Loops are just edges pointing backwards plus an exit condition.
- `recursion_limit` caps super-steps; `Command` combines an update with a `goto`.

## Building a Tool-Calling (ReAct) Agent from Scratch
The ReAct pattern: the model **reasons**, **acts** (calls a tool), **observes** the result, and repeats.

```python
from langchain.chat_models import init_chat_model
from langchain.tools import tool
from langgraph.graph import StateGraph, MessagesState, START, END
from langgraph.prebuilt import ToolNode, tools_condition

@tool
def multiply(a: int, b: int) -> int:
    """Multiply two integers."""
    return a * b

tools = [multiply]
model = init_chat_model("openai:gpt-5.5").bind_tools(tools)

def call_model(state: MessagesState):
    return {"messages": [model.invoke(state["messages"])]}

builder = StateGraph(MessagesState)
builder.add_node("agent", call_model)
builder.add_node("tools", ToolNode(tools))         # executes tool_calls, returns ToolMessages
builder.add_edge(START, "agent")
builder.add_conditional_edges("agent", tools_condition)   # -> "tools" or END
builder.add_edge("tools", "agent")                 # loop back after tools run
graph = builder.compile()

graph.invoke({"messages": [("user", "What is 6 times 7?")]})
```
How it runs:
1. `agent` calls the model; the AIMessage contains a `tool_call` for `multiply`.
2. `tools_condition` sees tool calls → routes to `tools`.
3. `ToolNode` runs `multiply(6, 7)` and appends a `ToolMessage("42")`.
4. Back to `agent`; the model now answers "42" with no tool calls → `tools_condition` routes to `END`.

### Writing tools_condition yourself
```python
def should_continue(state: MessagesState):
    last = state["messages"][-1]
    return "tools" if getattr(last, "tool_calls", None) else END
```

### Prebuilt vs custom
- `create_agent` (LangChain) — the batteries-included version of this graph with middleware.
- Custom graph — when you need extra nodes (retrieval, validation, reflection), custom routing or special state.

### Key takeaways
- Agent node ↔ ToolNode loop, routed by whether the last AIMessage has `tool_calls`.
- `ToolNode` executes requested tools (in parallel when several are requested) and returns `ToolMessage`s.
- The loop ends when the model replies without tool calls.

## Persistence, Threads and Memory
### Checkpointers
Compile with a checkpointer and LangGraph saves a **checkpoint** (state snapshot) after every super-step.
```python
from langgraph.checkpoint.memory import InMemorySaver
graph = builder.compile(checkpointer=InMemorySaver())

config = {"configurable": {"thread_id": "conversation-1"}}
graph.invoke({"messages": [("user", "Hi, I'm Ravi")]}, config)
graph.invoke({"messages": [("user", "What's my name?")]}, config)   # state restored from thread
```
| Checkpointer | Package | Use |
|---|---|---|
| InMemorySaver | langgraph | tests, notebooks |
| SqliteSaver | langgraph-checkpoint-sqlite | local apps |
| PostgresSaver | langgraph-checkpoint-postgres | production |

A **thread** (`thread_id`) is one conversation / one run history. Different thread ids are fully isolated.

### Inspecting and time-travelling
```python
snap = graph.get_state(config)
snap.values          # current state
snap.next            # nodes scheduled next
for s in graph.get_state_history(config): print(s.config["configurable"]["checkpoint_id"])

graph.update_state(config, {"messages": [("user", "correction")]})   # edit state as if a node wrote it
graph.invoke(None, past_checkpoint_config)                          # replay/fork from an older checkpoint
```

### Durability modes
`graph.invoke(..., durability="sync" | "async" | "exit")` controls when checkpoints are written: synchronously before the next step, asynchronously in the background, or only when the run exits (fastest, least durable).

### Long-term memory with stores
```python
from langgraph.store.memory import InMemoryStore
store = InMemoryStore()
graph = builder.compile(checkpointer=InMemorySaver(), store=store)

def remember(state, runtime):
    ns = ("users", runtime.context.user_id, "prefs")
    runtime.store.put(ns, "lang", {"value": "Hindi"})
    items = runtime.store.search(ns)          # also supports semantic search when configured with embeddings
```
Checkpointers = memory **within** a thread. Stores = memory **across** threads (profiles, preferences, learned facts).

### Key takeaways
- `compile(checkpointer=...)` + `thread_id` gives resumable, multi-turn state.
- `get_state`, `get_state_history` and `update_state` enable debugging and time travel.
- Use a store for cross-thread long-term memory.

## Human-in-the-Loop: interrupt and Command(resume)
Real agents need approval before risky actions (sending emails, payments, deleting data).

### interrupt()
```python
from langgraph.types import interrupt, Command

def send_email(state):
    decision = interrupt({                    # pauses the graph and surfaces this payload
        "action": "send_email",
        "draft": state["draft"],
        "question": "Approve sending?",
    })
    if decision["approved"]:
        deliver(state["draft"])
        return {"status": "sent"}
    return {"status": "cancelled"}

graph = builder.compile(checkpointer=InMemorySaver())   # interrupts REQUIRE a checkpointer
config = {"configurable": {"thread_id": "t1"}}

result = graph.invoke({"draft": "Hi team..."}, config)
result["__interrupt__"]                      # the payload shown to the human

graph.invoke(Command(resume={"approved": True}), config)   # continue from the pause
```

### Important semantics
- When resumed, the interrupted **node restarts from its beginning**; `interrupt()` then returns the resume value. Keep side effects *after* the interrupt or make them idempotent.
- A node can call `interrupt()` several times; resume values are matched by order.
- Static breakpoints are also available: `compile(interrupt_before=["tools"])` / `interrupt_after=[...]`.

### Common HITL patterns
| Pattern | Example |
|---|---|
| Approve / reject | confirm a tool call before it runs |
| Edit state | fix the agent's plan or draft |
| Review tool calls | change arguments before execution |
| Provide input | ask the user for missing info (e.g. account id) |

In LangChain, `HumanInTheLoopMiddleware(interrupt_on={"send_email": True})` gives the same behaviour for `create_agent` with approve/edit/reject decisions.

### Key takeaways
- `interrupt(payload)` pauses; `Command(resume=value)` continues the same thread.
- Interrupts need a checkpointer and a `thread_id`.
- The paused node re-runs from the top on resume, so place side effects carefully.

## Multi-Agent Systems, Subgraphs and the Send API
### Subgraphs
A compiled graph can be used as a node in another graph. Subgraphs keep complex systems modular (a "research team" graph inside a "company" graph).
```python
research_graph = research_builder.compile()
parent = StateGraph(State)
parent.add_node("research", research_graph)     # shares keys with the parent state
```
If schemas differ, call the subgraph inside a normal node and map inputs/outputs yourself.

### Multi-agent architectures
| Architecture | How it works |
|---|---|
| Supervisor | a central LLM routes work to specialist agents and decides when done |
| Hierarchical | supervisors of supervisors (teams of teams) |
| Network / swarm | agents hand off directly to each other |
| Custom workflow | deterministic edges between agents |

Handoffs are implemented with `Command(goto="other_agent", update={...}, graph=Command.PARENT)`. Prebuilt helpers exist in `langgraph-supervisor` and `langgraph-swarm`.

### Map-reduce with Send
`Send` creates **dynamic parallel branches** — one per item — even when the number isn't known in advance.
```python
import operator
from typing import Annotated
from langgraph.types import Send

class S(TypedDict):
    topics: list[str]
    summaries: Annotated[list[str], operator.add]

def fan_out(state: S):
    return [Send("summarize", {"topic": t}) for t in state["topics"]]

def summarize(sub: dict):
    return {"summaries": [f"summary of {sub['topic']}"]}

builder = StateGraph(S)
builder.add_node("summarize", summarize)
builder.add_conditional_edges(START, fan_out, ["summarize"])
builder.add_edge("summarize", END)
builder.compile().invoke({"topics": ["a", "b", "c"], "summaries": []})
# summaries has 3 items — the reducer combines the parallel writes
```

### Key takeaways
- Compiled graphs can be nodes (subgraphs) for modular multi-agent systems.
- Supervisor, hierarchical and swarm are the main multi-agent topologies.
- `Send(node, payload)` fans out dynamic parallel work; a reducer gathers results.

## Streaming, the Functional API and Deployment
### Stream modes
```python
for mode, chunk in graph.stream(inputs, config, stream_mode=["updates", "messages"]):
    ...
```
| Mode | Emits |
|---|---|
| values | the full state after each super-step |
| updates | only the node name + its update |
| messages | LLM tokens `(message_chunk, metadata)` from inside nodes |
| custom | anything you emit with `get_stream_writer()` |
| checkpoints / tasks / debug | detailed execution events |

```python
from langgraph.config import get_stream_writer
def node(state):
    writer = get_stream_writer()
    writer({"progress": "fetched 10 docs"})
    return {}
```
Add `subgraphs=True` to stream events from nested graphs. LangGraph 1.1+ supports `version="v2"` for typed stream parts.

### The Functional API
For code-centric workflows without explicit graphs:
```python
from langgraph.func import entrypoint, task

@task
def fetch(url: str) -> str: ...

@entrypoint(checkpointer=InMemorySaver())
def workflow(urls: list[str]) -> list[str]:
    futures = [fetch(u) for u in urls]        # tasks run in parallel and are checkpointed
    return [f.result() for f in futures]
```
`@entrypoint` defines a durable workflow; `@task` marks units of work whose results are saved so they are not recomputed on resume. Interrupts work here too.

### Reliability features
- `RetryPolicy` per node: `builder.add_node("call_api", fn, retry_policy=RetryPolicy(max_attempts=3))`.
- Node caching: `cache_policy=CachePolicy(ttl=...)` with a cache on compile.
- Deferred nodes (`defer=True`) wait until all parallel branches finish.

### Deployment
- **LangGraph CLI**: `langgraph dev` runs a local Agent Server with **LangGraph Studio** (visual debugger).
- **langgraph.json** declares graphs, dependencies and env.
- **LangSmith Deployment** (formerly LangGraph Platform) hosts graphs as APIs with threads, runs, cron jobs, double-texting handling and a built-in persistence layer; self-hosted options exist.

### Key takeaways
- Use `updates` for progress, `messages` for tokens, `custom` for your own events.
- The functional API (`@entrypoint`, `@task`) gives durability without drawing a graph.
- `langgraph dev` + Studio for local debugging; LangSmith Deployment for production hosting.
