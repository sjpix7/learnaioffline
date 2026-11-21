---
id: llamaindex
title: LlamaIndex
short: Data framework for RAG and agents — documents, indexes, query engines, Workflows
emoji: 🦙
color: #6A1B9A
---
## LlamaIndex Overview
LlamaIndex is a Python framework for connecting LLMs to **your data**: ingesting documents, building indexes, retrieving context and running agents and event-driven **Workflows**. The company behind it also offers **LlamaParse** / **LlamaCloud** for document parsing and managed indexes.

### Where LlamaIndex shines
- Data connectors (hundreds of readers on **LlamaHub**).
- High-quality document parsing (PDF tables, layouts) with LlamaParse.
- Many index types and advanced retrieval (hierarchical, auto-merging, routers, sub-questions).
- Agents (`FunctionAgent`, `ReActAgent`, multi-agent `AgentWorkflow`) built on **Workflows**.

As of late 2026 the framework is Python-first (the `llama-index` 0.14.x line) and Workflows ship as a standalone package (`llama-index-workflows`). The TypeScript version (LlamaIndex.TS) was deprecated in 2026.

### Packages
```bash
pip install llama-index            # starter bundle: core + OpenAI LLM/embeddings + common readers
pip install llama-index-core llama-index-llms-anthropic llama-index-embeddings-huggingface
pip install llama-index-vector-stores-qdrant
```
Integrations are separate packages named `llama-index-<type>-<provider>`; core abstractions live in `llama_index.core`.

### The five-line RAG
```python
from llama_index.core import VectorStoreIndex, SimpleDirectoryReader

documents = SimpleDirectoryReader("data").load_data()
index = VectorStoreIndex.from_documents(documents)
query_engine = index.as_query_engine()
print(query_engine.query("What did the author do growing up?"))
```
Behind those lines: load → split into nodes → embed → store in an in-memory vector store → retrieve top-k → synthesize an answer with the LLM.

### Key stages of a LlamaIndex app
| Stage | Components |
|---|---|
| Loading | Readers, LlamaParse, Documents |
| Indexing | Node parsers, embeddings, indexes |
| Storing | StorageContext, vector/doc/index stores |
| Querying | Retrievers, postprocessors, response synthesizers, query engines, chat engines |
| Agents | tools, FunctionAgent, AgentWorkflow, Workflows |
| Evaluating | evaluators, retriever metrics |

### Key takeaways
- LlamaIndex specialises in data ingestion, indexing and retrieval for LLMs.
- `VectorStoreIndex.from_documents(...).as_query_engine()` is the minimal RAG.
- Integrations are separate `llama-index-*` packages; core lives in `llama_index.core`.

## Documents, Nodes and Data Loading
### Document vs Node
- A **Document** is a container for any data source (a PDF, a web page, a DB row): `text` + `metadata`.
- A **Node** (`TextNode`) is a **chunk** of a Document — the atomic unit that gets embedded and retrieved. Nodes keep metadata and **relationships** (source document, previous/next node, parent/child).
```python
from llama_index.core import Document
from llama_index.core.schema import TextNode

doc = Document(text="LlamaIndex connects LLMs to data.", metadata={"source": "intro.md", "year": 2026})
node = TextNode(text="A chunk of text", metadata={"page": 3})
```

### Readers
```python
from llama_index.core import SimpleDirectoryReader
reader = SimpleDirectoryReader(input_dir="./docs", recursive=True, required_exts=[".pdf", ".md"])
docs = reader.load_data()
```
`SimpleDirectoryReader` handles common types (PDF, DOCX, Markdown, CSV, images...). **LlamaHub** provides readers for Notion, Slack, Google Drive, databases, websites and more.

### LlamaParse
For complex PDFs (tables, multi-column layouts, charts), LlamaParse converts documents to clean Markdown/JSON:
```python
from llama_cloud_services import LlamaParse
parser = LlamaParse(result_type="markdown")
docs = parser.load_data("annual_report.pdf")
```

### Metadata matters
Metadata travels with nodes and can be:
- injected into the text that is embedded and/or shown to the LLM (control with `excluded_embed_metadata_keys` / `excluded_llm_metadata_keys`),
- used for **metadata filtering** at query time (e.g. `year == 2026`).

### Key takeaways
- Documents are sources; Nodes are chunks with metadata and relationships.
- `SimpleDirectoryReader` loads local files; LlamaHub adds hundreds of connectors.
- Use LlamaParse for layout-heavy PDFs; use metadata for filtering and context.

## Node Parsers and the Ingestion Pipeline
### Node parsers (splitters)
```python
from llama_index.core.node_parser import SentenceSplitter
splitter = SentenceSplitter(chunk_size=512, chunk_overlap=50)
nodes = splitter.get_nodes_from_documents(docs)
```
| Parser | Behaviour |
|---|---|
| SentenceSplitter | token-sized chunks that respect sentence boundaries (default) |
| TokenTextSplitter | splits strictly by token count |
| SemanticSplitterNodeParser | splits where embedding similarity between sentences drops |
| SentenceWindowNodeParser | one node per sentence, with a surrounding "window" in metadata |
| HierarchicalNodeParser | parent/child chunks at several sizes (e.g. 2048/512/128) |
| MarkdownNodeParser / HTMLNodeParser / JSONNodeParser | structure-aware splitting |
| CodeSplitter | language-aware code chunks |

### Global defaults with Settings
```python
from llama_index.core import Settings
from llama_index.llms.openai import OpenAI
from llama_index.embeddings.openai import OpenAIEmbedding

Settings.llm = OpenAI(model="gpt-5.5", temperature=0.1)
Settings.embed_model = OpenAIEmbedding(model="text-embedding-3-small")
Settings.chunk_size = 512
Settings.chunk_overlap = 50
```
`Settings` is a global singleton of defaults; you can still pass components explicitly to any index or engine.

### IngestionPipeline
```python
from llama_index.core.ingestion import IngestionPipeline, IngestionCache
from llama_index.core.extractors import TitleExtractor, KeywordExtractor

pipeline = IngestionPipeline(
    transformations=[
        SentenceSplitter(chunk_size=512, chunk_overlap=50),
        TitleExtractor(),                 # LLM-generated metadata
        OpenAIEmbedding(),                # embeddings as the last transformation
    ],
    vector_store=qdrant_vector_store,     # write directly to a vector DB
    docstore=docstore,                    # enables de-duplication / upserts
)
nodes = pipeline.run(documents=docs)
```
- Each (node + transformation) result is **cached**, so re-running only processes changed data.
- With a docstore attached, documents with the same id are **de-duplicated** and changed documents are **upserted**.
- `pipeline.run(num_workers=4)` parallelises ingestion.

### Key takeaways
- `SentenceSplitter(chunk_size, chunk_overlap)` is the default chunker.
- `Settings` sets global LLM, embedding model and chunking defaults.
- `IngestionPipeline` chains transformations with caching, de-duplication and direct vector-store writes.

## Indexes and Storage
### Index types
| Index | How it retrieves | Good for |
|---|---|---|
| VectorStoreIndex | top-k embedding similarity | most RAG use cases |
| SummaryIndex | returns all nodes (optionally filtered) | summarising whole documents |
| TreeIndex | hierarchical summaries traversed top-down | hierarchical summarisation |
| KeywordTableIndex | keyword → node mapping | exact keyword lookups |
| PropertyGraphIndex | knowledge graph of entities/relations + vectors | GraphRAG, multi-hop questions |
| DocumentSummaryIndex | per-document summaries used to pick documents | routing across many documents |

### StorageContext and persistence
```python
from llama_index.core import StorageContext, load_index_from_storage

index = VectorStoreIndex.from_documents(docs)
index.storage_context.persist(persist_dir="./storage")          # save to disk

storage_context = StorageContext.from_defaults(persist_dir="./storage")
index = load_index_from_storage(storage_context)                # reload without re-embedding
```
A `StorageContext` bundles a **docstore** (nodes), **index store** (index metadata), **vector store** (embeddings) and graph store.

### External vector stores
```python
import qdrant_client
from llama_index.vector_stores.qdrant import QdrantVectorStore

client = qdrant_client.QdrantClient(url="http://localhost:6333")
vector_store = QdrantVectorStore(client=client, collection_name="docs")
storage_context = StorageContext.from_defaults(vector_store=vector_store)
index = VectorStoreIndex.from_documents(docs, storage_context=storage_context)
# later: VectorStoreIndex.from_vector_store(vector_store)
```

### Property graphs
```python
from llama_index.core import PropertyGraphIndex
from llama_index.core.indices.property_graph import SimpleLLMPathExtractor

pg_index = PropertyGraphIndex.from_documents(
    docs, kg_extractors=[SimpleLLMPathExtractor(max_paths_per_chunk=10)],
)
pg_index.as_query_engine(include_text=True).query("How is Alice connected to Acme?")
```
Extractors include `SimpleLLMPathExtractor` (free-form triples), `SchemaLLMPathExtractor` (allowed entity/relation types) and `ImplicitPathExtractor` (from node relationships). Graph stores include Neo4j and an in-memory store.

### Key takeaways
- `VectorStoreIndex` is the workhorse; `SummaryIndex` and `PropertyGraphIndex` serve summary and graph use cases.
- `persist()` and `load_index_from_storage()` avoid re-embedding on restart.
- Plug any vector DB in through `StorageContext.from_defaults(vector_store=...)`.

## Retrievers, Postprocessors and Query Engines
A **query engine** = retriever + node postprocessors + response synthesizer.

### Retrievers
```python
retriever = index.as_retriever(similarity_top_k=5)
nodes = retriever.retrieve("What is the refund policy?")     # list[NodeWithScore]
for n in nodes: print(n.score, n.node.metadata)
```
Metadata filters:
```python
from llama_index.core.vector_stores import MetadataFilters, MetadataFilter, FilterOperator
filters = MetadataFilters(filters=[MetadataFilter(key="year", value=2026, operator=FilterOperator.GTE)])
retriever = index.as_retriever(similarity_top_k=5, filters=filters)
```

### Node postprocessors
| Postprocessor | Purpose |
|---|---|
| SimilarityPostprocessor(similarity_cutoff=0.75) | drop low-score nodes |
| SentenceTransformerRerank / LLMRerank / CohereRerank | re-rank with a cross-encoder or LLM |
| KeywordNodePostprocessor | require/exclude keywords |
| MetadataReplacementPostProcessor | swap a sentence for its window (sentence-window retrieval) |
| LongContextReorder | put best nodes at the start and end (lost-in-the-middle) |

### Response synthesizers
`response_mode` controls how retrieved chunks become an answer:
| Mode | Behaviour |
|---|---|
| compact (default) | stuff as many chunks as fit per LLM call, refine across calls |
| refine | one LLM call per chunk, iteratively refining the answer |
| tree_summarize | summarise chunks recursively bottom-up — great for summaries |
| simple_summarize | truncate all chunks into a single call |
| accumulate | answer per chunk and concatenate |
| no_text | return nodes only, no LLM call |

```python
query_engine = index.as_query_engine(
    similarity_top_k=8,
    node_postprocessors=[SentenceTransformerRerank(model="cross-encoder/ms-marco-MiniLM-L-6-v2", top_n=3)],
    response_mode="compact",
    streaming=True,
)
response = query_engine.query("Summarise the warranty terms")
response.print_response_stream()
response.source_nodes             # citations
```

### Advanced query engines
- **RouterQueryEngine** — an LLM selector picks the best engine (e.g. summary vs vector) per query.
- **SubQuestionQueryEngine** — decomposes a complex query into sub-questions over different tools/indexes, then combines answers.
- **RetrieverQueryEngine** — assemble your own retriever + postprocessors + synthesizer.
- **AutoMergingRetriever** — with hierarchical nodes, merges retrieved children into their parent when enough siblings match.

### Key takeaways
- Query engine = retriever → postprocessors → response synthesizer.
- `response_mode` (compact, refine, tree_summarize...) controls synthesis cost and quality.
- Routers and sub-question engines handle multi-index and multi-part queries.

## Chat Engines and Memory
A **chat engine** is a stateful, multi-turn version of a query engine.
```python
chat_engine = index.as_chat_engine(chat_mode="condense_plus_context", verbose=True)
chat_engine.chat("What does the policy say about refunds?")
chat_engine.chat("And for digital goods?")      # follow-up uses chat history
chat_engine.reset()                              # clear memory
```
| chat_mode | How it works |
|---|---|
| condense_question | rewrite the follow-up into a standalone question, then query |
| context | retrieve with the user message and put context in the system prompt |
| condense_plus_context | condense the question, retrieve, then answer with history + context |
| simple | plain chat with the LLM, no retrieval |
| best / react / openai | agent-based modes that call the query engine as a tool |

### Memory
```python
from llama_index.core.memory import Memory
memory = Memory.from_defaults(session_id="user-1", token_limit=40000)
```
Memory keeps recent messages within a token budget and can flush older content into long-term **memory blocks** (static facts, extracted facts, vector memory). Agents accept a `memory=` argument as well.

### Streaming chat
```python
resp = chat_engine.stream_chat("Explain step by step")
for token in resp.response_gen:
    print(token, end="")
```

### Key takeaways
- Chat engines add conversation history to retrieval.
- `condense_plus_context` is a strong default for RAG chatbots.
- `Memory` bounds history by tokens and can maintain long-term memory blocks.

## Agents in LlamaIndex
LlamaIndex agents are built on Workflows and live in `llama_index.core.agent.workflow`.

### Tools
```python
from llama_index.core.tools import FunctionTool, QueryEngineTool

def multiply(a: float, b: float) -> float:
    """Multiply two numbers."""
    return a * b

docs_tool = QueryEngineTool.from_defaults(
    query_engine=index.as_query_engine(),
    name="company_docs",
    description="Answers questions about company policies.",
)
```
Plain Python functions can be passed directly; LlamaIndex converts them into `FunctionTool`s using the signature and docstring.

### FunctionAgent and ReActAgent
```python
from llama_index.core.agent.workflow import FunctionAgent, ReActAgent
from llama_index.llms.openai import OpenAI

agent = FunctionAgent(
    tools=[multiply, docs_tool],
    llm=OpenAI(model="gpt-5.5"),
    system_prompt="You are a helpful assistant that uses tools.",
)
response = await agent.run(user_msg="What is 12.5 * 4, and what's our refund window?")
print(str(response))
```
- **FunctionAgent** uses the LLM's native function/tool-calling API.
- **ReActAgent** uses prompting (Thought / Action / Observation) and works with any LLM.
- **CodeActAgent** writes and executes code as its action.

### State with Context
```python
from llama_index.core.workflow import Context
ctx = Context(agent)
await agent.run(user_msg="My name is Meera", ctx=ctx)
await agent.run(user_msg="What's my name?", ctx=ctx)     # remembers via the shared context
```

### Multi-agent with AgentWorkflow
```python
from llama_index.core.agent.workflow import AgentWorkflow

research = FunctionAgent(name="Researcher", description="Finds facts", tools=[search],
                         llm=llm, can_handoff_to=["Writer"])
writer = FunctionAgent(name="Writer", description="Writes reports", tools=[save_report], llm=llm)

workflow = AgentWorkflow(agents=[research, writer], root_agent="Researcher")
result = await workflow.run(user_msg="Write a short report on GraphRAG")
```
Agents **hand off** control to each other; streaming events (`AgentStream`, `ToolCall`, `ToolCallResult`, `AgentOutput`) let you show progress.

### Key takeaways
- `FunctionAgent` (native tool calling) and `ReActAgent` (prompted reasoning) are the main single agents.
- `QueryEngineTool` turns any RAG engine into an agent tool.
- `AgentWorkflow` coordinates multiple agents with handoffs; `Context` carries state across runs.

## Workflows: Event-Driven Orchestration
**Workflows** are LlamaIndex's event-driven way to compose steps — the foundation of its agents. A workflow is a class whose methods are `@step`s; steps **receive events** and **emit events**.
```python
from llama_index.core.workflow import Workflow, step, StartEvent, StopEvent, Event, Context

class RetrievedEvent(Event):
    nodes: list

class RAGWorkflow(Workflow):
    @step
    async def retrieve(self, ctx: Context, ev: StartEvent) -> RetrievedEvent:
        await ctx.store.set("query", ev.query)                  # shared state
        nodes = await retriever.aretrieve(ev.query)
        return RetrievedEvent(nodes=nodes)

    @step
    async def synthesize(self, ctx: Context, ev: RetrievedEvent) -> StopEvent:
        query = await ctx.store.get("query")
        answer = await synth.asynthesize(query, nodes=ev.nodes)
        return StopEvent(result=str(answer))

wf = RAGWorkflow(timeout=60, verbose=True)
result = await wf.run(query="What is LlamaIndex?")
```
### How routing works
- The **type annotations** on each step define which events it accepts and which it can return — LlamaIndex validates the graph from these types.
- `StartEvent` carries the kwargs passed to `run()`; returning a `StopEvent` ends the workflow and its `result` is returned.
- A step returning a union (`-> EventA | EventB`) can branch; steps can loop by emitting an event handled earlier.

### Context superpowers
| API | Purpose |
|---|---|
| `ctx.store.set/get` | shared state across steps (typed state models are supported) |
| `ctx.send_event(ev)` | emit events (e.g. fan out many in parallel) |
| `ctx.collect_events(ev, [EvA]*3)` | wait until N events arrive (fan-in) |
| `ctx.write_event_to_stream(ev)` | stream progress to the caller |
| `ctx.wait_for_event(...)` | human-in-the-loop pauses |

`@step(num_workers=4)` lets a step process several events concurrently. Workflows can be serialised to resume later, drawn as diagrams, and deployed as services (LlamaAgents / `llamactl`).

### Key takeaways
- Workflows are event-driven: steps consume and emit typed events from `StartEvent` to `StopEvent`.
- `Context` provides shared state, fan-out/fan-in and streaming.
- Agents and AgentWorkflow are themselves Workflows.

## Evaluation and Observability
### Response evaluators
```python
from llama_index.core.evaluation import FaithfulnessEvaluator, RelevancyEvaluator, CorrectnessEvaluator

faith = FaithfulnessEvaluator(llm=judge_llm)
response = query_engine.query("What is the refund window?")
result = faith.evaluate_response(response=response)
result.passing, result.score, result.feedback
```
| Evaluator | Checks |
|---|---|
| FaithfulnessEvaluator | is the answer supported by retrieved context (hallucination)? |
| RelevancyEvaluator | do the answer and context match the query? |
| CorrectnessEvaluator | answer vs reference answer (score 1–5) |
| SemanticSimilarityEvaluator | embedding similarity to a reference |
| GuidelineEvaluator | follows custom guidelines? |

### Retrieval evaluation
```python
from llama_index.core.evaluation import RetrieverEvaluator, generate_question_context_pairs

qa_dataset = generate_question_context_pairs(nodes, llm=llm, num_questions_per_chunk=2)
evaluator = RetrieverEvaluator.from_metric_names(["hit_rate", "mrr"], retriever=retriever)
results = await evaluator.aevaluate_dataset(qa_dataset)
```
**Hit rate** = fraction of queries whose relevant chunk appears in the top-k; **MRR** = mean of 1/rank of the first relevant chunk.

### Observability
LlamaIndex has an **instrumentation** module that emits events and spans. Common setups:
```python
# OpenTelemetry/OpenInference → Arize Phoenix, Langfuse, etc.
from openinference.instrumentation.llama_index import LlamaIndexInstrumentor
LlamaIndexInstrumentor().instrument()
```
RAGAS and DeepEval also integrate with LlamaIndex query engines for broader metric suites.

### Key takeaways
- Faithfulness, relevancy and correctness evaluators judge responses with an LLM.
- `RetrieverEvaluator` measures hit rate and MRR on generated question-context pairs.
- Instrument with OpenInference/OTel to trace LlamaIndex apps in Phoenix or Langfuse.
