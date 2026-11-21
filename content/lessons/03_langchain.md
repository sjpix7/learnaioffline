---
id: langchain
title: LangChain
short: Models, prompts, LCEL, tools, agents with create_agent and middleware, retrieval
emoji: 🦜
color: #1C3C3C
---
## LangChain Overview and the v1 Architecture
LangChain is an open-source framework for building applications and **agents** on top of LLMs. It gives you one interface for hundreds of model providers, plus prompts, tools, retrieval components and a production-ready agent loop.

### What changed in LangChain 1.0 (Oct 2025)
LangChain and LangGraph both reached **1.0** in October 2025, and v1 is an LTS line (1.x stays active until 2.0). The 1.x line continued through 2026 (1.3 added v3 event streaming, 1.4 added built-in MCP support). Key changes:
- **`create_agent`** (in `langchain.agents`) is the standard way to build agents. It replaces `langgraph.prebuilt.create_react_agent`.
- **Middleware** lets you customise every step of the agent loop (summarisation, PII redaction, human approval, dynamic prompts...).
- **Standard content blocks** (`message.content_blocks`) give a provider-agnostic view of text, reasoning, images and tool calls.
- The namespace was slimmed down. Legacy chains (`LLMChain`, `RetrievalQA`, old agents, indexing API) moved to **`langchain-classic`**.

### The package layout
| Package | Contains |
|---|---|
| `langchain-core` | Base abstractions: messages, prompts, runnables (LCEL), tools, output parsers, vector store interfaces |
| `langchain` | `create_agent`, middleware, `init_chat_model`, re-exports of core pieces |
| `langchain-openai`, `langchain-anthropic`, `langchain-google-genai`, `langchain-ollama`... | Provider integration packages |
| `langchain-community` | Community-maintained integrations (loaders, tools, stores) |
| `langchain-text-splitters` | Text splitters |
| `langchain-classic` | Legacy v0.x chains and agents |
| `langgraph` | The low-level orchestration runtime agents run on |
| `langsmith` | Tracing, evaluation, deployment platform SDK |

### How the pieces relate
- **LangChain** — high-level building blocks and a prebuilt agent loop.
- **LangGraph** — low-level graph runtime with persistence, streaming and human-in-the-loop. `create_agent` is built on it.
- **Deep Agents** (`deepagents`) — an agent *harness* on top (planning, file systems, sub-agents).
- **LangSmith** — observability, evals and deployment.

```bash
pip install -U langchain langchain-openai langchain-anthropic
```

### Key takeaways
- LangChain v1 focuses on agents: `create_agent` + middleware.
- Core abstractions live in `langchain-core`; providers ship as separate packages.
- Old chains such as `RetrievalQA` now live in `langchain-classic`.

## Chat Models and Messages
### Initialising a model
```python
from langchain.chat_models import init_chat_model

model = init_chat_model("openai:gpt-5.5", temperature=0)      # "provider:model" string
# or provider classes directly:
from langchain_anthropic import ChatAnthropic
claude = ChatAnthropic(model="claude-sonnet-5", max_tokens=1024)
```
`init_chat_model` picks the right integration from the provider prefix (`openai:`, `anthropic:`, `google_genai:`, `ollama:`...). Common parameters: `temperature`, `max_tokens`, `timeout`, `max_retries`.

### The Runnable methods every model has
```python
model.invoke("Explain RAG in one sentence")          # -> AIMessage
for chunk in model.stream("Write a haiku"):         # -> AIMessageChunk stream
    print(chunk.content, end="", flush=True)
model.batch(["Q1", "Q2"])                            # parallel calls
await model.ainvoke("...")                           # async variants: ainvoke/astream/abatch
```

### Messages
```python
from langchain.messages import SystemMessage, HumanMessage, AIMessage, ToolMessage

messages = [
    SystemMessage("You are a concise assistant."),
    HumanMessage("What is LangGraph?"),
]
reply = model.invoke(messages)
reply.content            # text (or a list of blocks for multimodal output)
reply.content_blocks     # standardized blocks: text, reasoning, tool_call, image...
reply.usage_metadata     # {'input_tokens': ..., 'output_tokens': ..., 'total_tokens': ...}
reply.tool_calls         # list of tool calls the model requested
```
Message roles: **system** (instructions), **human/user** (input), **ai/assistant** (model output, may include `tool_calls`) and **tool** (a tool's result, linked by `tool_call_id`). You can also pass tuples like `("user", "hi")` or OpenAI-style dicts.

### Multimodal input
```python
msg = HumanMessage(content=[
    {"type": "text", "text": "Describe this chart"},
    {"type": "image", "url": "https://example.com/chart.png"},
])
```

### Rate limits, fallbacks, caching
```python
from langchain_core.rate_limiters import InMemoryRateLimiter
limiter = InMemoryRateLimiter(requests_per_second=2)
model = init_chat_model("openai:gpt-5.5", rate_limiter=limiter)
robust = model.with_fallbacks([init_chat_model("anthropic:claude-sonnet-5")])
robust = model.with_retry(stop_after_attempt=3)
```

### Key takeaways
- `init_chat_model("provider:model")` gives a uniform interface across providers.
- Every model is a Runnable: `invoke`, `stream`, `batch` and async variants.
- `AIMessage` carries `content`, `content_blocks`, `tool_calls` and `usage_metadata`.

## Prompt Templates
Prompt templates turn user input into well-structured model input.

### PromptTemplate (string)
```python
from langchain_core.prompts import PromptTemplate
pt = PromptTemplate.from_template("Summarize in {n} words: {text}")
pt.format(n=10, text="LangChain is ...")     # -> str
pt.input_variables                           # ['n', 'text']
```

### ChatPromptTemplate (messages)
```python
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

prompt = ChatPromptTemplate.from_messages([
    ("system", "You are an expert in {domain}. Answer briefly."),
    MessagesPlaceholder("history"),           # inject a list of prior messages
    ("human", "{question}"),
])
value = prompt.invoke({
    "domain": "RAG",
    "history": [("human", "hi"), ("ai", "hello!")],
    "question": "What is chunking?",
})
value.to_messages()          # [SystemMessage, HumanMessage, AIMessage, HumanMessage]
```

### Partial variables and few-shot
```python
prompt = ChatPromptTemplate.from_messages([("system", "Today is {date}."), ("human", "{q}")])
dated = prompt.partial(date="2026-11-21")            # pre-fill some variables

from langchain_core.prompts import FewShotChatMessagePromptTemplate
examples = [{"input": "2+2", "output": "4"}, {"input": "3*3", "output": "9"}]
example_prompt = ChatPromptTemplate.from_messages([("human", "{input}"), ("ai", "{output}")])
few_shot = FewShotChatMessagePromptTemplate(examples=examples, example_prompt=example_prompt)
```
Templates use Python f-string syntax by default; literal braces must be doubled: `{{ "json": true }}`.

### Prompt management
Prompts are code: version them in Git, or in a prompt registry such as the **LangSmith Hub** or **Langfuse Prompt Management**, so you can change prompts without redeploying.

### Key takeaways
- `PromptTemplate` produces strings; `ChatPromptTemplate` produces lists of messages.
- `MessagesPlaceholder` inserts conversation history.
- Use `.partial()` to pre-fill variables and double braces for literal `{}`.

## LCEL: The LangChain Expression Language and Runnables
LCEL composes components with the `|` (pipe) operator. Everything that implements the **Runnable** interface — prompts, models, parsers, retrievers, plain functions — can be chained.

### A basic chain
```python
from langchain_core.output_parsers import StrOutputParser

chain = prompt | model | StrOutputParser()
chain.invoke({"domain": "RAG", "history": [], "question": "Define recall@k"})   # -> str
```
Data flows left to right: the dict fills the prompt, the prompt value goes to the model, the AIMessage goes to the parser, which returns a plain string.

### Core runnables
```python
from langchain_core.runnables import RunnableLambda, RunnableParallel, RunnablePassthrough
from operator import itemgetter

add_one = RunnableLambda(lambda x: x + 1)
double = RunnableLambda(lambda x: x * 2)
(add_one | double).invoke(3)                    # 8  -> (3+1)*2

RunnableParallel(a=add_one, b=double).invoke(3) # {'a': 4, 'b': 6} — branches run in parallel

RunnablePassthrough().invoke("x")               # 'x' unchanged
RunnablePassthrough.assign(n=lambda d: len(d["text"])).invoke({"text": "abc"})
# {'text': 'abc', 'n': 3} — keeps input keys and adds new ones

itemgetter("question")                          # pick a key from the input dict
```
A **dict literal** inside a chain is automatically converted to a `RunnableParallel`, and plain functions are converted to `RunnableLambda`.

### The classic RAG chain in LCEL
```python
def format_docs(docs):
    return "\n\n".join(d.page_content for d in docs)

rag_chain = (
    {"context": retriever | format_docs, "question": RunnablePassthrough()}
    | ChatPromptTemplate.from_template("Use the context to answer.\n{context}\n\nQ: {question}")
    | model
    | StrOutputParser()
)
rag_chain.invoke("What is hybrid search?")
```

### Runnable superpowers
| Method | What it does |
|---|---|
| `.invoke / .batch / .stream` | single, many, streamed |
| `.ainvoke / .abatch / .astream` | async versions |
| `.astream_events(version=...)` | fine-grained event stream (tokens, tool starts...) |
| `.with_retry()` | automatic retries |
| `.with_fallbacks([...])` | try alternatives on error |
| `.with_config(tags=..., run_name=...)` | tracing metadata |
| `.bind(stop=[...])` | bind fixed kwargs |
| `.get_graph().draw_mermaid()` | visualise the chain |

`RunnableBranch` and plain Python routing functions let you choose a sub-chain at runtime.

### When to use LCEL vs LangGraph
LCEL is great for linear or parallel pipelines (prompt → model → parser, RAG). For loops, branching state machines, persistence or human approval use **LangGraph**.

### Key takeaways
- `a | b` feeds a's output into b; dicts become `RunnableParallel`, functions become `RunnableLambda`.
- `RunnablePassthrough.assign` adds keys while keeping the input.
- All runnables get batch, stream, async, retry and fallback for free.

## Structured Output and Output Parsers
LLMs return text; applications need data. LangChain offers two approaches.

### with_structured_output (preferred)
Uses the provider's native tool-calling / JSON-schema features.
```python
from pydantic import BaseModel, Field

class Ticket(BaseModel):
    """A support ticket extracted from an email."""
    category: str = Field(description="billing, bug or feature")
    priority: int = Field(ge=1, le=5)
    summary: str

extractor = model.with_structured_output(Ticket)
t = extractor.invoke("My invoice is wrong and I need it fixed today!")
t.category, t.priority        # ('billing', 5) -> a Ticket instance
```
- Accepts a Pydantic model, a `TypedDict` or a JSON schema dict.
- `include_raw=True` returns the raw message plus the parsed object (and parsing errors).
- `method="json_schema"`, `"function_calling"` or `"json_mode"` selects the provider mechanism.

### Output parsers
For models without native structured output, or for simple formats:
```python
from langchain_core.output_parsers import StrOutputParser, JsonOutputParser, PydanticOutputParser

parser = PydanticOutputParser(pydantic_object=Ticket)
prompt = PromptTemplate.from_template(
    "Extract a ticket.\n{format_instructions}\nEmail: {email}"
).partial(format_instructions=parser.get_format_instructions())
chain = prompt | model | parser
```
`JsonOutputParser` can stream partial JSON objects; `StrOutputParser` extracts `.content` as a string.

### Structured output in agents
```python
from langchain.agents import create_agent
agent = create_agent(model="openai:gpt-5.5", tools=[search], response_format=Ticket)
result = agent.invoke({"messages": [("user", "Create a ticket for: login button broken")]})
result["structured_response"]        # Ticket(...)
```

### Key takeaways
- Prefer `with_structured_output(Schema)` — it uses native provider features.
- Output parsers + format instructions work with any model.
- `create_agent(..., response_format=Schema)` returns `structured_response`.

## Tools and Tool Calling
Tools let a model act: search, query a DB, call an API. The model never runs code itself — it **requests** a tool call with arguments, your code executes it, and the result goes back as a `ToolMessage`.

### Defining tools
```python
from langchain.tools import tool

@tool
def get_weather(city: str, unit: str = "celsius") -> str:
    """Get the current weather for a city."""
    return f"22 degrees {unit} in {city}"

get_weather.name           # 'get_weather'
get_weather.description    # from the docstring
get_weather.args           # JSON schema built from type hints
get_weather.invoke({"city": "Pune"})
```
The **name, description and argument schema** are what the model sees, so write clear docstrings and precise types. Use a Pydantic `args_schema` for complex inputs, and `ToolRuntime` to access state, context or a store inside a tool.

### Binding tools to a model
```python
model_with_tools = model.bind_tools([get_weather])
ai = model_with_tools.invoke("Weather in Pune?")
ai.tool_calls    # [{'name': 'get_weather', 'args': {'city': 'Pune'}, 'id': 'call_1', 'type': 'tool_call'}]

from langchain.messages import ToolMessage
result = get_weather.invoke(ai.tool_calls[0]["args"])
followup = model_with_tools.invoke([
    ("user", "Weather in Pune?"), ai,
    ToolMessage(content=result, tool_call_id=ai.tool_calls[0]["id"]),
])
```
`tool_choice="any"` forces a tool call; `parallel_tool_calls` controls whether several tools may be requested at once.

### MCP tools
The **Model Context Protocol (MCP)** standardises how apps expose tools to models. The `langchain-mcp-adapters` package (and built-in MCP support added in LangChain 1.4) converts MCP server tools into LangChain tools:
```python
from langchain_mcp_adapters.client import MultiServerMCPClient
client = MultiServerMCPClient({
    "math": {"command": "python", "args": ["math_server.py"], "transport": "stdio"},
    "docs": {"url": "http://localhost:8000/mcp", "transport": "streamable_http"},
})
tools = await client.get_tools()
```

### Key takeaways
- `@tool` turns a typed, documented function into a tool; the docstring becomes its description.
- `bind_tools` lets the model emit `tool_calls`; you execute them and return `ToolMessage`s.
- MCP adapters bring tools from any MCP server into LangChain.

## Agents with create_agent and Middleware
An **agent** runs a loop: the model decides whether to call tools; tools run; results go back; repeat until the model answers without a tool call.

### create_agent
```python
from langchain.agents import create_agent

agent = create_agent(
    model="openai:gpt-5.5",
    tools=[get_weather, search_docs],
    system_prompt="You are a helpful travel assistant. Cite sources.",
)
result = agent.invoke({"messages": [{"role": "user", "content": "Weather in Goa and best beach?"}]})
print(result["messages"][-1].content)
```
`create_agent` returns a compiled **LangGraph** graph, so it supports streaming, checkpointers (memory), interrupts and LangSmith tracing out of the box.

### Middleware: hooks into the loop
Middleware intercepts the agent at well-defined points:
| Hook | When |
|---|---|
| `before_agent` | once, before the agent starts |
| `before_model` | before each model call |
| `wrap_model_call` | around each model call (modify request, retry, swap model) |
| `after_model` | after each model response |
| `wrap_tool_call` | around each tool execution |
| `after_agent` | once, after the agent finishes |

### Built-in middleware
```python
from langchain.agents.middleware import (
    SummarizationMiddleware, HumanInTheLoopMiddleware, PIIMiddleware,
    ModelCallLimitMiddleware, ToolCallLimitMiddleware, ModelFallbackMiddleware,
)
agent = create_agent(
    model="openai:gpt-5.5",
    tools=[read_email, send_email],
    middleware=[
        PIIMiddleware("email", strategy="redact", apply_to_input=True),
        SummarizationMiddleware(model="openai:gpt-5.4-mini", trigger=("tokens", 4000), keep=("messages", 20)),
        HumanInTheLoopMiddleware(interrupt_on={"send_email": True}),
        ModelCallLimitMiddleware(run_limit=10),
    ],
    checkpointer=InMemorySaver(),       # HITL needs a checkpointer to pause/resume
)
```
Others include todo-list planning, LLM tool selection, tool retry, context editing and model fallback.

### Custom middleware
```python
from langchain.agents.middleware import before_model, wrap_model_call, dynamic_prompt

@dynamic_prompt
def by_role(request):
    role = request.runtime.context.get("role", "user")
    return f"You are assisting a {role}. Be precise."

@wrap_model_call
def pick_model(request, handler):
    if len(request.messages) > 20:
        request = request.override(model=big_model)    # route long chats to a bigger model
    return handler(request)

agent = create_agent(model="openai:gpt-5.4-mini", tools=tools, middleware=[by_role, pick_model])
```
Class-based middleware subclasses `AgentMiddleware` and implements hook methods; it can also add state fields and tools.

### Key takeaways
- `create_agent(model, tools, system_prompt, middleware, response_format, checkpointer)` is the v1 agent API.
- Middleware hooks: before/after agent, before/after model, wrap model call, wrap tool call.
- Human-in-the-loop middleware requires a checkpointer so the run can pause and resume.

## Retrieval: Loaders, Splitters, Embeddings and Vector Stores
Retrieval-augmented generation (RAG) gives the model fresh or private knowledge at query time.

### 1. Load documents
```python
from langchain_community.document_loaders import PyPDFLoader, WebBaseLoader
docs = PyPDFLoader("handbook.pdf").load()     # list[Document]
docs[0].page_content, docs[0].metadata        # text + {'source': ..., 'page': 0}
```
A `Document` has `page_content` (text) and `metadata` (dict). Loaders exist for PDFs, web pages, Notion, Google Drive, databases, etc.

### 2. Split into chunks
```python
from langchain_text_splitters import RecursiveCharacterTextSplitter
splitter = RecursiveCharacterTextSplitter(chunk_size=1000, chunk_overlap=200, add_start_index=True)
chunks = splitter.split_documents(docs)
```
`RecursiveCharacterTextSplitter` tries separators in order (`"\n\n"`, `"\n"`, `" "`, `""`) to keep paragraphs and sentences together. Other splitters: `TokenTextSplitter`, `MarkdownHeaderTextSplitter`, `HTMLHeaderTextSplitter`, language-aware code splitters.

### 3. Embed and store
```python
from langchain_openai import OpenAIEmbeddings
from langchain_core.vectorstores import InMemoryVectorStore

embeddings = OpenAIEmbeddings(model="text-embedding-3-small")
store = InMemoryVectorStore(embeddings)       # or Chroma, FAISS, PGVector, Qdrant, Pinecone...
ids = store.add_documents(chunks)
store.similarity_search("vacation policy", k=4)
store.similarity_search_with_score("vacation policy", k=4)
```

### 4. Retrieve
```python
retriever = store.as_retriever(search_type="mmr", search_kwargs={"k": 4, "fetch_k": 20})
retriever.invoke("How many vacation days?")   # list[Document]
```
- `search_type="similarity"` (default), `"mmr"` (maximal marginal relevance — diversity) or `"similarity_score_threshold"`.
- Retrievers are Runnables, so they slot into LCEL chains.

### 5. Retrieval as an agent tool (agentic RAG)
```python
@tool
def search_handbook(query: str) -> str:
    """Search the employee handbook."""
    return "\n\n".join(d.page_content for d in retriever.invoke(query))

agent = create_agent("openai:gpt-5.5", tools=[search_handbook],
                     system_prompt="Answer HR questions using the handbook tool.")
```
The agent decides *when* and *how often* to search — better for multi-step questions than a fixed 2-step chain.

### Key takeaways
- RAG pipeline: load → split → embed → store → retrieve → generate.
- `RecursiveCharacterTextSplitter` with overlap is the robust default splitter.
- `as_retriever()` turns any vector store into a Runnable retriever; MMR adds diversity.

## Memory, Streaming and Production with LangSmith
### Short-term memory (conversation state)
Agents remember a conversation through a **checkpointer** keyed by `thread_id`.
```python
from langgraph.checkpoint.memory import InMemorySaver

agent = create_agent("openai:gpt-5.5", tools=[], checkpointer=InMemorySaver())
config = {"configurable": {"thread_id": "user-42"}}
agent.invoke({"messages": [("user", "My name is Asha")]}, config)
agent.invoke({"messages": [("user", "What's my name?")]}, config)   # remembers "Asha"
```
Use `PostgresSaver`/`SqliteSaver` in production. Long conversations need trimming or `SummarizationMiddleware` to stay inside the context window.

### Long-term memory
Facts that must survive across threads (user preferences, profiles) go into a **store** (`InMemoryStore`, `PostgresStore`) that tools and middleware can read/write via namespaces.

### Streaming
```python
for chunk in agent.stream({"messages": [("user", "Plan a trip")]}, stream_mode="updates"):
    print(chunk)          # one update per step (model / tools)

for token, meta in agent.stream({"messages": [("user", "Hi")]}, stream_mode="messages"):
    print(token.content, end="")   # LLM tokens as they arrive
```
Modes: `"values"` (full state after each step), `"updates"` (only changes), `"messages"` (token stream), `"custom"` (your own events).

### LangSmith: tracing and evaluation
```bash
export LANGSMITH_TRACING=true
export LANGSMITH_API_KEY=...
export LANGSMITH_PROJECT=rag-app
```
With these environment variables every chain/agent run is traced: inputs, outputs, latency, tokens, cost, tool calls. LangSmith also offers datasets, evaluators (LLM-as-judge, heuristics), prompt hub, online evaluation and deployment (Agent Server).

### Production checklist
- Pin package versions; read the migration guide when upgrading.
- Set timeouts, retries and fallbacks on model calls.
- Cap agent loops (`ModelCallLimitMiddleware`, `recursion_limit`).
- Trace everything (LangSmith or Langfuse) and build an eval set before changing prompts or models.
- Stream responses for perceived latency; cache embeddings.

### Key takeaways
- Checkpointer + `thread_id` = conversation memory; a store = cross-thread memory.
- `stream_mode="messages"` streams tokens; `"updates"` streams step-by-step progress.
- Setting `LANGSMITH_TRACING=true` traces every run automatically.
