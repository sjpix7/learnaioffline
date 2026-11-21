---
id: langfuse
title: Langfuse
short: Open-source LLM observability — traces, prompts, evaluations, datasets and cost
emoji: 🪢
color: #E65100
---
## LLM Observability and What Langfuse Is
LLM apps are non-deterministic: the same input can produce different outputs, a retrieval step can silently return irrelevant chunks, and an agent can loop ten times and burn tokens. **Observability** means capturing enough structured data — inputs, outputs, intermediate steps, latency, tokens, cost, errors and quality scores — to debug, evaluate and improve the system.

### Langfuse in one sentence
**Langfuse** is an open-source (MIT-licensed core) **LLM engineering platform** for tracing, prompt management, evaluation, datasets/experiments and metrics. You can use **Langfuse Cloud** or **self-host** it.

### Core features
| Feature | What it does |
|---|---|
| Tracing | records every LLM call, retrieval, tool call and agent step as nested observations |
| Sessions & users | group traces into conversations; attribute usage per user |
| Cost & token tracking | computes cost from model usage and price definitions |
| Prompt management | versioned prompts with labels (e.g. `production`) fetched at runtime |
| Evaluation | scores from users, humans (annotation queues), LLM-as-a-judge or code |
| Datasets & experiments | run your app over a test set and compare versions |
| Metrics & dashboards | latency, cost, quality over time; Metrics API |
| Playground | test prompts and models interactively |

### Why not just logs?
Logs are flat text. LLM traces are **trees**: a user request → retrieval span → embedding call → LLM generation → tool calls → final answer. Langfuse stores this hierarchy with timings, token usage and model parameters so you can see exactly where quality or latency went wrong.

### Integrations at a glance
Native SDKs for **Python** and **JS/TS**, built on **OpenTelemetry**; integrations for the OpenAI SDK, LangChain/LangGraph, LlamaIndex, LiteLLM, Vercel AI SDK, CrewAI, OpenAI Agents SDK, Pydantic AI, and more via OTel instrumentations.

### Key takeaways
- Observability = structured, hierarchical records of what your LLM app did and why.
- Langfuse covers tracing, prompt management, evals, datasets and metrics in one open-source platform.
- It runs in the cloud or self-hosted and integrates via SDKs and OpenTelemetry.

## Setup, Architecture and Data Model
### Getting started
```bash
pip install langfuse
export LANGFUSE_PUBLIC_KEY="pk-lf-..."
export LANGFUSE_SECRET_KEY="sk-lf-..."
export LANGFUSE_BASE_URL="https://cloud.langfuse.com"   # EU region; https://us.cloud.langfuse.com for US
```
```python
from langfuse import get_client
langfuse = get_client()            # singleton configured from environment variables
langfuse.auth_check()              # verify credentials (avoid in hot paths)
```
API keys come in pairs: the **public key** identifies the project and the **secret key** authenticates; keep the secret server-side.

### The data model
| Object | Meaning |
|---|---|
| Trace | one end-to-end request or operation (e.g. one chat turn) |
| Observation | a step inside a trace; observations nest to form a tree |
| Generation | an observation for an LLM call (model, parameters, usage, cost) |
| Span | an observation for any unit of work with a duration |
| Event | a point-in-time observation without duration |
| Session | a group of traces (a multi-turn conversation) via `session_id` |
| Score | an evaluation attached to a trace, observation or session |

Newer SDKs add semantic observation types — `agent`, `tool`, `chain`, `retriever`, `embedding`, `evaluator`, `guardrail` — so agent traces render meaningfully.

### Architecture (self-hosting)
Langfuse v3+ is a scalable, event-driven architecture:
- **Web** container (UI + APIs) and **Worker** container (asynchronous ingestion processing).
- **PostgreSQL** for transactional data (users, projects, prompts, configuration).
- **ClickHouse** (OLAP database) for traces, observations and scores at high volume.
- **Redis/Valkey** for queues and caching.
- **S3-compatible blob storage** for raw events and large payloads.

You can run it with Docker Compose for testing or Kubernetes (Helm chart) for production. The SDKs send data asynchronously in batches, so tracing adds minimal latency to your app.

### SDK generations
The Python SDK v3 (GA June 2025) was rebuilt on **OpenTelemetry**; later majors (v4, v5) refined the API (for example `propagate_attributes()` for trace-level attributes). Always match the SDK major to your server version when self-hosting.

### Key takeaways
- Configure with `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY` and `LANGFUSE_BASE_URL`; get the client with `get_client()`.
- Traces contain nested observations (spans, generations, events, agent/tool types); sessions group traces.
- Self-hosted Langfuse uses Postgres, ClickHouse, Redis and blob storage with web + worker containers.

## Tracing with @observe and Context Managers
### The @observe decorator
```python
from langfuse import observe, get_client

@observe()
def retrieve(query: str) -> list[str]:
    return vector_store.search(query, k=4)

@observe(as_type="generation", name="answer-llm")
def generate(query: str, docs: list[str]) -> str:
    return llm(prompt(query, docs))

@observe()
def rag_pipeline(query: str) -> str:          # outermost call -> root of the trace
    docs = retrieve(query)                     # child observation
    return generate(query, docs)               # child observation

rag_pipeline("What is hybrid search?")
get_client().flush()     # send buffered events before a short-lived script exits
```
`@observe` automatically captures the function name, arguments (input), return value (output), timing and exceptions, and **nests** calls using OpenTelemetry context propagation — even across modules. It works on sync and async functions. Disable I/O capture with `@observe(capture_input=False, capture_output=False)` for sensitive data.

### Context managers
```python
langfuse = get_client()

with langfuse.start_as_current_observation(as_type="span", name="handle-request") as root:
    root.update(input={"q": "hi"})
    with langfuse.start_as_current_observation(
        as_type="generation", name="llm-call", model="gpt-5.5",
        model_parameters={"temperature": 0.2},
    ) as gen:
        text = call_model("hi")
        gen.update(output=text, usage_details={"input": 12, "output": 30})
    root.update(output=text)
```
- `start_as_current_observation` sets the new observation as the **active context**, so anything created inside becomes its child, and ends it automatically.
- `start_observation(...)` creates an observation **without** changing the active context; you must call `.end()` yourself.
- `update_current_span()` / `update_current_generation()` modify the active observation from deep inside your code.

### Trace-level attributes
```python
from langfuse import propagate_attributes

with langfuse.start_as_current_observation(as_type="span", name="chat-turn"):
    with propagate_attributes(user_id="user-123", session_id="sess-9", tags=["beta"], metadata={"tier": "pro"}):
        rag_pipeline("hello")       # every observation inside inherits these attributes
```

### Trace IDs and linking
- Trace IDs are W3C-compatible 32-character hex strings; `langfuse.create_trace_id(seed="order-77")` creates deterministic ids from your own identifiers.
- `langfuse.get_current_trace_id()` and `get_current_observation_id()` let you return the id to the frontend (e.g. to attach user feedback later).

### Key takeaways
- `@observe()` nests function calls into a trace automatically; use `as_type="generation"` for LLM calls.
- `start_as_current_observation` = context manager that becomes the parent of nested work.
- Call `flush()` in scripts/serverless before exit; use `propagate_attributes` for user/session/tags.

## Framework Integrations
### OpenAI SDK drop-in
```python
from langfuse.openai import openai        # instead of: import openai

client = openai.OpenAI()
resp = client.chat.completions.create(
    model="gpt-5.5",
    messages=[{"role": "user", "content": "Hello"}],
    name="greeting",                       # optional Langfuse-specific args
    metadata={"feature": "onboarding"},
)
```
Every call becomes a generation with model, parameters, token usage, cost and latency.

### LangChain and LangGraph
```python
from langfuse.langchain import CallbackHandler
handler = CallbackHandler()

chain.invoke({"question": "..."}, config={"callbacks": [handler]})
agent.invoke({"messages": [("user", "hi")]}, config={"callbacks": [handler]})
```
The callback handler maps chains, LLM calls, retrievers and tools to nested observations — LangGraph nodes appear as a tree.

### OpenTelemetry instrumentations
Because the SDK is OTel-native, any OpenTelemetry instrumentation shows up in Langfuse:
```python
from openinference.instrumentation.llama_index import LlamaIndexInstrumentor
from langfuse import get_client
LlamaIndexInstrumentor().instrument()
langfuse = get_client()
```
The same approach works for Anthropic, Bedrock, Google GenAI, CrewAI, smolagents and others. You can also send OTLP data directly to Langfuse's OpenTelemetry endpoint from any language.

### Other integrations
- **LiteLLM** proxy/SDK callbacks.
- **Vercel AI SDK**, **Mastra** (JS/TS).
- **OpenAI Agents SDK**, **Pydantic AI**, **Google ADK**, **Microsoft Agent Framework** via OTel.

### Key takeaways
- `from langfuse.openai import openai` traces OpenAI calls with one import change.
- LangChain/LangGraph use `CallbackHandler` passed in `config={"callbacks": [...]}`.
- OpenTelemetry instrumentations make almost any framework observable in Langfuse.

## Sessions, Users, Metadata, Tokens and Cost
### Sessions
A **session** groups traces that belong together — typically one chat conversation. Set the same `session_id` on each turn's trace and Langfuse shows a session replay with all turns.

### Users
Setting `user_id` enables per-user analytics: number of traces, token usage, cost and scores for each user — useful for quotas, abuse detection and billing.

### Tags, metadata, release, version, environment
- **Tags** — short labels for filtering (`["rag", "beta"]`).
- **Metadata** — arbitrary JSON (`{"retriever": "hybrid", "k": 8}`).
- **Release / version** — compare behaviour between deployments.
- **Environment** — `LANGFUSE_TRACING_ENVIRONMENT=production` separates prod/staging/dev data.

### Token usage and cost
For generations Langfuse records `usage_details` (input, output, cached, reasoning tokens...) and `cost_details`. If you don't send cost, Langfuse **infers** it from the model name using built-in and custom **model price definitions** (you can add your own models and prices in project settings).
```python
gen.update(
    model="my-finetuned-model",
    usage_details={"input": 1200, "output": 300},
    cost_details={"input": 0.0012, "output": 0.0009},     # optional explicit cost in USD
)
```

### Masking and sampling
```python
from langfuse import Langfuse

def mask(data, **kwargs):
    return redact_emails(data)        # runs on inputs/outputs before they leave your process

langfuse = Langfuse(mask=mask, sample_rate=0.2)   # keep 20% of traces
```
`LANGFUSE_SAMPLE_RATE` also controls sampling. Sampling is decided per trace, so a trace is kept or dropped as a whole.

### Key takeaways
- `session_id` groups multi-turn conversations; `user_id` enables per-user usage and cost.
- Cost is computed from usage + model price definitions when not sent explicitly.
- Use `mask` to redact PII and `sample_rate` to control volume.

## Prompt Management
Hard-coded prompts force a redeploy for every wording tweak. Langfuse Prompt Management stores prompts centrally with **versions** and **labels**.

### Creating and fetching prompts
```python
langfuse.create_prompt(
    name="rag-answer",
    type="text",
    prompt="Answer using only the context.\nContext: {{context}}\nQuestion: {{question}}",
    labels=["production"],
    config={"model": "gpt-5.5", "temperature": 0.1},
)

prompt = langfuse.get_prompt("rag-answer")                 # label "production" by default
prompt = langfuse.get_prompt("rag-answer", label="staging")
prompt = langfuse.get_prompt("rag-answer", version=3)
text = prompt.compile(context=ctx, question=q)             # fills {{variables}}
model_cfg = prompt.config                                  # model settings stored with the prompt
```
Chat prompts (`type="chat"`) store a list of messages and `compile()` returns the filled message list.

### Labels and versions
- Every save creates a new immutable **version** (1, 2, 3...).
- **Labels** are movable pointers: `production`, `staging`, `latest` (automatically the newest version) or your own like `experiment-b`.
- Promoting a prompt = moving the `production` label to another version; apps pick it up without a deploy. Rolling back is moving it back.

### Performance and reliability
The SDK **caches** prompts client-side (default TTL 60 seconds) and refreshes them in the background, so fetching adds no latency on the hot path. Provide a `fallback` prompt for the first fetch in case Langfuse is unreachable.

### Linking prompts to traces
Pass the prompt object to the generation (`langfuse_prompt=prompt` in the OpenAI integration, or `prompt=prompt` when creating a generation) so Langfuse can show metrics — latency, cost, scores — **per prompt version**.

### Key takeaways
- Prompts are versioned; labels like `production` point at a version and can be moved for release/rollback.
- `get_prompt(name, label=...)` + `compile(**vars)` fills `{{variables}}`.
- Client-side caching and fallbacks keep prompt fetching fast and safe.

## Evaluation: Scores, LLM-as-a-Judge, Datasets and Experiments
### Scores
A score is a named value attached to a trace, observation or session. Data types: **numeric**, **categorical** and **boolean**.
```python
langfuse.create_score(trace_id=trace_id, name="user-feedback", value=1, data_type="BOOLEAN",
                      comment="thumbs up")

with langfuse.start_as_current_observation(as_type="span", name="answer") as span:
    span.score(name="relevance", value=0.9)          # score this observation
    span.score_trace(name="overall", value=0.8)      # score the whole trace
```

### Sources of evaluation
| Source | Example |
|---|---|
| User feedback | thumbs up/down sent from the UI with the trace id |
| Human annotation | reviewers label traces in **annotation queues** |
| LLM-as-a-judge | managed evaluators score live or experiment traces (hallucination, relevance, toxicity...) |
| Custom code | your own checks via SDK/API (regex, JSON validity, RAGAS metrics) |

### Datasets
A **dataset** is a collection of items with `input` and optional `expected_output` and metadata — created from code, CSV upload or by adding production traces.
```python
langfuse.create_dataset(name="faq-eval")
langfuse.create_dataset_item(dataset_name="faq-eval",
                             input={"question": "Refund window?"},
                             expected_output="30 days")
```

### Experiments
```python
from langfuse import Evaluation

def task(*, item, **kwargs):
    return rag_pipeline(item["input"]["question"])

def exact(*, output, expected_output, **kwargs):
    return Evaluation(name="exact_match", value=float(expected_output.lower() in output.lower()))

result = langfuse.run_experiment(
    name="hybrid-retriever-v2",
    data=[{"input": {"question": "Refund window?"}, "expected_output": "30 days"}],
    task=task,
    evaluators=[exact],
)
print(result.format())
```
Each experiment run creates traces linked to dataset items, so you can compare runs (prompt v3 vs v4, model A vs B) side by side in the UI.

### Key takeaways
- Scores can be numeric, categorical or boolean and attach to traces, observations or sessions.
- Evaluation sources: user feedback, annotation queues, LLM-as-a-judge and custom code.
- Datasets + experiments let you compare app versions on the same test items.

## Production Patterns and Analytics
### Short-lived processes
Serverless functions, CLI scripts and notebooks can exit before the background exporter sends data. Call `langfuse.flush()` (or `shutdown()`) at the end.

### Performance
The SDK batches and sends events in a background thread; network issues never break your app (events are dropped after retries rather than raising). Tune `flush_at` and `flush_interval` for high throughput.

### Closing the loop
1. **Trace** production traffic.
2. **Monitor** dashboards for latency, cost and score trends; filter by user, tag, release or prompt version.
3. **Evaluate** with LLM-as-a-judge and user feedback; send bad traces to annotation queues.
4. **Curate** failing traces into datasets.
5. **Experiment** with prompt/model/retriever changes against the dataset.
6. **Release** by moving the `production` prompt label; compare metrics by version.

### Metrics API and exports
Query aggregated metrics programmatically (cost per user per day, p95 latency by model) via the Metrics API, or export data for BI tools.

### Langfuse vs alternatives
| Tool | Notes |
|---|---|
| Langfuse | open source, self-hostable, framework-agnostic, OTel-based |
| LangSmith | LangChain's platform; deep LangChain/LangGraph integration, deployment |
| Arize Phoenix | open source, OpenInference/OTel-based tracing and evals |
| Weights & Biases Weave | experiment-centric tracing and evals |
| Helicone | proxy/gateway-based logging |

### Key takeaways
- Always `flush()` in short-lived processes; tracing failures never crash your app.
- The improvement loop: trace → monitor → evaluate → curate datasets → experiment → release.
- Langfuse stands out for being open source, self-hostable and framework-agnostic.
