---
id: rag_architectures
title: RAG Architectures
short: Naive to agentic — query transforms, routing, hierarchical, corrective, self-reflective, multimodal
emoji: 🏗️
color: #4E342E
---
## Naive, Advanced and Modular RAG
Research surveys (e.g. Gao et al., 2023) group RAG systems into three paradigms.

### Naive RAG
`index → retrieve top-k → stuff into prompt → generate`. Simple and often good enough, but suffers from:
- **Low precision** — irrelevant chunks in context.
- **Low recall** — relevant chunks missed.
- **Hallucination** when context is weak, and redundancy when chunks repeat.

### Advanced RAG
Adds optimisation **before** and **after** retrieval:
- *Pre-retrieval*: better chunking, metadata, query rewriting/expansion, HyDE, routing.
- *Retrieval*: hybrid search, fine-tuned embeddings, metadata filters.
- *Post-retrieval*: reranking, context compression, de-duplication, reordering.

### Modular RAG
Treats RAG as composable **modules** (search, memory, routing, fusion, predict, task adapters) arranged in flexible flows — including loops, branches and agent control. Most modern systems (LangGraph, LlamaIndex Workflows) are modular RAG.

### A mental checklist for any architecture
| Stage | Question to ask |
|---|---|
| Indexing | Are chunks the right unit? Is structure/metadata kept? |
| Query | Is the query well-formed for retrieval? |
| Retrieval | Which retrievers (dense, sparse, graph, SQL)? |
| Post-retrieval | Rerank? Compress? Filter? |
| Generation | Grounded prompt, citations, refusal path? |
| Control | One shot, iterative, or agent-driven? |

### Key takeaways
- Naive RAG = retrieve-then-read; advanced RAG adds pre/post-retrieval optimisations.
- Modular RAG composes interchangeable modules into flexible flows.
- Diagnose by stage: indexing, query, retrieval, post-retrieval, generation, control.

## Query Transformation Architectures
Transform the user's query before retrieval.

### Multi-query retrieval
Generate N rephrasings → retrieve for each → union/fuse (RRF). Example: "How do I speed up my Postgres?" → "Postgres query performance tuning", "PostgreSQL indexes slow queries", "configure shared_buffers work_mem".

### RAG-Fusion
Multi-query + **Reciprocal Rank Fusion** of the result lists, then generate. Improves robustness to phrasing.

### HyDE (Hypothetical Document Embeddings)
Generate a fake answer passage; embed it; search with it. Bridges the question-vs-document style gap.

### Step-back prompting
Ask a more **general** question first ("What factors affect Postgres performance?"), retrieve for both the step-back and the original question, then answer with both contexts. Helps questions needing principles.

### Query decomposition (sub-questions)
Break a complex question into sub-questions, answer each (possibly sequentially, feeding earlier answers forward), then synthesise:
```text
Q: "Compare the revenue growth of Acme and Globex in 2025."
 → q1: "Acme revenue growth 2025"
 → q2: "Globex revenue growth 2025"
 → final: compare answers to q1 and q2
```
LlamaIndex's `SubQuestionQueryEngine` and LangGraph map-reduce (`Send`) implement this.

### Example: multi-query in LangGraph-style pseudocode
```python
def generate_queries(state):
    qs = llm.with_structured_output(Queries).invoke(f"Give 3 search queries for: {state['question']}")
    return {"queries": qs.items}

def retrieve_all(state):
    lists = [retriever.invoke(q) for q in state["queries"] + [state["question"]]]
    return {"docs": rrf_merge(lists)[:6]}
```

### Key takeaways
- Multi-query/RAG-Fusion improve recall across phrasings.
- HyDE embeds a hypothetical answer; step-back retrieves general principles.
- Decomposition answers sub-questions and then synthesises.

## Routing and Multi-Source RAG
Real systems have several knowledge sources: product docs, a SQL database, a ticket system, a graph, the web.

### Routing strategies
| Router | How |
|---|---|
| Logical (rule-based) | keywords/regex/metadata ("invoice #" → billing DB) |
| Semantic | embed the query, compare to route descriptions/examples, pick the closest |
| LLM classifier | structured output: `{"route": "sql" | "docs" | "web"}` |
| Agent | the model chooses tools dynamically, possibly several |

```python
from typing import Literal
from pydantic import BaseModel

class Route(BaseModel):
    datasource: Literal["product_docs", "orders_sql", "web_search"]

router = llm.with_structured_output(Route)
route = router.invoke("What's the status of order 1182?").datasource   # 'orders_sql'
```

### Multi-index patterns
- **Router query engine**: choose one index per query (summary index vs vector index).
- **Fan-out**: query several sources in parallel and fuse.
- **Text-to-SQL** for structured questions, vector RAG for unstructured, graph for relationships.

### Example: support assistant
1. Classify intent: `how_to` → docs vector index; `order_status` → SQL tool; `outage` → status-page API.
2. Retrieve from the chosen source(s).
3. Generate with source-specific prompts and citations.

### Key takeaways
- Route queries to the right source with rules, semantic similarity, an LLM classifier or an agent.
- Combine vector RAG with Text-to-SQL and graph queries for multi-source systems.
- Fan-out + fusion when several sources may be relevant.

## Hierarchical Retrieval: Parent-Document, Sentence Window, Auto-Merging and RAPTOR
Small chunks embed precisely; large chunks give the LLM enough context. Hierarchical architectures get both.

### Parent-document (small-to-big) retrieval
Index **small child chunks** for search; when a child matches, return its **larger parent** chunk/document to the LLM.
```python
# LangChain classic: ParentDocumentRetriever
from langchain_classic.retrievers import ParentDocumentRetriever
retriever = ParentDocumentRetriever(
    vectorstore=vs, docstore=InMemoryStore(),
    child_splitter=RecursiveCharacterTextSplitter(chunk_size=200),
    parent_splitter=RecursiveCharacterTextSplitter(chunk_size=2000),
)
```

### Sentence-window retrieval
Embed **single sentences**; store a window of ±k neighbouring sentences in metadata; at query time replace the matched sentence with its window (LlamaIndex `SentenceWindowNodeParser` + `MetadataReplacementPostProcessor`).

### Auto-merging retrieval
With hierarchical nodes (e.g. 2048 → 512 → 128 tokens), if enough child nodes of the same parent are retrieved, **merge** them into the parent (LlamaIndex `AutoMergingRetriever`).

### Multi-vector / summary indexing
Embed a **summary** or **hypothetical questions** for each chunk, but return the original chunk. Questions-per-chunk ("doc2query") match user questions well.

### RAPTOR
**Recursive Abstractive Processing for Tree-Organized Retrieval** (2024):
1. Embed and **cluster** leaf chunks (Gaussian mixture on reduced embeddings).
2. **Summarise** each cluster with an LLM → new nodes.
3. Repeat recursively to build a tree up to a root.
4. Retrieve from all levels ("collapsed tree"), so queries can match details (leaves) or themes (summaries).

### Key takeaways
- Search small, read big: parent-document, sentence-window and auto-merging retrieval.
- Index summaries or generated questions while returning originals.
- RAPTOR builds a tree of cluster summaries to answer both detailed and thematic questions.

## Contextual Retrieval and Late Chunking
A chunk like "Revenue grew 3% over the previous quarter" is ambiguous on its own — which company? which quarter?

### Contextual retrieval (Anthropic, 2024)
Before embedding, use an LLM to write a short **chunk-specific context** situating the chunk within the whole document, and **prepend** it:
```text
<document>{{WHOLE_DOCUMENT}}</document>
Here is the chunk we want to situate within the whole document:
<chunk>{{CHUNK}}</chunk>
Give a short succinct context to situate this chunk within the overall document
for the purposes of improving search retrieval. Answer only with the context.
```
→ "This chunk is from ACME Corp's Q2 2023 SEC filing; previous quarter revenue was $314M. Revenue grew 3%..."

- Apply to both **contextual embeddings** and **contextual BM25**.
- Anthropic reported that contextual embeddings + contextual BM25 cut the top-20 retrieval failure rate by **49%**, and by **67%** when reranking was added.
- **Prompt caching** makes it affordable: the whole document is cached once while each chunk is processed.

### Late chunking
Embed the **whole document** (or a long window) with a long-context embedding model first, producing token embeddings that "see" the full document; **then** pool token embeddings per chunk span. Each chunk vector carries document context without an extra LLM call (proposed by Jina AI, 2024).

### Comparison
| Technique | Extra cost | Mechanism |
|---|---|---|
| Plain chunking | none | chunk embedded alone |
| Contextual retrieval | 1 LLM call per chunk (cached) | LLM-written context prepended |
| Late chunking | long-context embedding pass | token embeddings pooled after full-doc encoding |
| Header prepending | none | add title/section path to chunk text |

### Key takeaways
- Isolated chunks lose context; contextual retrieval prepends LLM-written situating context.
- Combine contextual embeddings + contextual BM25 + reranking for the largest gains.
- Late chunking pools token embeddings from a full-document encoding.

## Corrective RAG (CRAG)
**Corrective Retrieval-Augmented Generation** (Yan et al., 2024) adds a **retrieval evaluator** that grades retrieved documents and triggers corrective actions.

### The flow
1. Retrieve documents for the question.
2. A lightweight evaluator grades relevance → **Correct**, **Incorrect** or **Ambiguous**.
3. Actions:
   - *Correct*: refine the documents (decompose into "knowledge strips", filter irrelevant strips).
   - *Incorrect*: discard and fall back to **web search** (often after rewriting the query).
   - *Ambiguous*: combine refined docs and web results.
4. Generate from the corrected knowledge.

### CRAG in LangGraph
```python
class S(TypedDict):
    question: str
    docs: list[str]
    web_needed: bool

def retrieve(s):   return {"docs": retriever_texts(s["question"])}

def grade(s):
    keep = [d for d in s["docs"] if grader.invoke({"q": s["question"], "doc": d}).relevant]
    return {"docs": keep, "web_needed": len(keep) == 0}

def web(s):        return {"docs": s["docs"] + web_search(rewrite(s["question"]))}
def generate(s):   return {"answer": rag_answer(s["question"], s["docs"])}

g = StateGraph(S)
for n in [retrieve, grade, web, generate]: g.add_node(n.__name__, n)
g.add_edge(START, "retrieve"); g.add_edge("retrieve", "grade")
g.add_conditional_edges("grade", lambda s: "web" if s["web_needed"] else "generate")
g.add_edge("web", "generate"); g.add_edge("generate", END)
```

### Why it helps
Naive RAG blindly trusts retrieval. CRAG detects bad retrieval and **corrects** it instead of generating from irrelevant context.

### Key takeaways
- CRAG grades retrieved docs as correct / incorrect / ambiguous.
- Incorrect retrieval triggers query rewriting + web search; correct docs are refined into strips.
- Easy to implement as a LangGraph with a grading node and a conditional edge.

## Self-RAG and Adaptive RAG
### Self-RAG
**Self-Reflective RAG** (Asai et al., 2023) trains an LM to emit special **reflection tokens** that control retrieval and critique its own output:
| Token | Decides |
|---|---|
| Retrieve | should I retrieve now? (yes / no / continue) |
| ISREL | is this passage relevant? |
| ISSUP | is my generation supported by the passage? (fully / partially / no) |
| ISUSE | how useful is the response? (1–5) |

The model retrieves **on demand**, generates candidates per passage, and selects the best using its critiques.

### Self-RAG-style loops without fine-tuning
Approximate it with LLM graders in a graph:
1. Retrieve → grade documents.
2. Generate → **hallucination check** (is the answer grounded in docs?).
3. **Answer check** (does it address the question?).
4. If not grounded → regenerate; if not useful → rewrite the query and retrieve again (with a max iteration count).

### Adaptive RAG
Adaptive-RAG (Jeong et al., 2024) uses a **query-complexity classifier** to choose a strategy:
- Simple question → answer directly (no retrieval).
- Moderate → single-step retrieval.
- Complex → multi-step / iterative retrieval.
This saves cost on easy questions and spends effort on hard ones.

### Iterative / multi-hop retrieval
Methods like IRCoT interleave retrieval with chain-of-thought: each reasoning step can trigger a new retrieval using what was learned so far.

### Key takeaways
- Self-RAG uses reflection tokens to decide when to retrieve and to critique relevance/support/usefulness.
- Without fine-tuning, use grader nodes for relevance, hallucination and answer quality with loop limits.
- Adaptive RAG routes by query complexity: none / single-step / multi-step retrieval.

## Agentic RAG
In **agentic RAG**, an LLM agent decides **whether, where, how and how many times** to retrieve, using retrieval as tools inside a reasoning loop.

### Capabilities over fixed pipelines
- Choose among multiple tools (vector search, SQL, web, graph, calculator).
- Iterate: search, read, refine query, search again.
- Plan multi-step research and verify answers.
- Ask clarifying questions.

### Example with LangChain create_agent
```python
@tool
def search_docs(query: str) -> str:
    """Search internal engineering docs. Use specific technical keywords."""
    return format_docs(hybrid_retriever.invoke(query))

@tool
def query_incidents(sql: str) -> str:
    """Run a read-only SQL query on the incidents table (columns: id, service, severity, opened_at)."""
    return run_readonly_sql(sql)

agent = create_agent(
    model="openai:gpt-5.5",
    tools=[search_docs, query_incidents],
    system_prompt="Answer engineering questions. Search before answering. Cite doc titles.",
    middleware=[ModelCallLimitMiddleware(run_limit=8)],
)
```

### Patterns
| Pattern | Description |
|---|---|
| Single agent + retrieval tools | the default agentic RAG |
| Router agent | picks the right specialist retriever/agent |
| Multi-agent research | planner, searchers, writer, critic |
| Deep research | long-horizon planning, many searches, report synthesis |

### Trade-offs
- ✅ Handles complex, multi-step, multi-source questions.
- ❌ More latency and tokens; less predictable; needs loop limits, tracing and evals.
- Rule of thumb: use the **simplest** architecture that passes your evals; add agency where fixed pipelines fail.

### Key takeaways
- Agentic RAG treats retrieval as tools inside an agent loop.
- Agents can route, iterate, verify and combine sources.
- Cap iterations and trace everything; prefer simpler pipelines when they suffice.

## Multimodal RAG
Documents contain images, charts, tables, slides and scanned pages. Text-only pipelines lose that information.

### Three approaches
| Approach | How | Pros/cons |
|---|---|---|
| Text conversion | OCR / parsers (LlamaParse, Docling, Unstructured) turn pages into Markdown; describe images with a VLM caption | simple, works with text RAG; captions can lose detail |
| Multimodal embeddings | embed images and text into one space (CLIP, Cohere Embed v4, Gemini Embedding 2, Jina v4) | search images by text directly |
| Page-image retrieval | embed page screenshots with a vision retriever like **ColPali** (multi-vector, late interaction) and send the page images to a vision LLM | no brittle parsing; preserves layout; more storage |

### ColPali
ColPali (2024) uses a vision-language model to produce **multi-vector** embeddings of document page images and scores them against query tokens with ColBERT-style **MaxSim**. It skips OCR/layout pipelines entirely and performs strongly on visually rich documents.

### Tables
- Keep tables intact as Markdown/HTML chunks; add a natural-language summary for embedding.
- For numeric questions, load tables into a DataFrame/SQL and use a code or SQL tool.

### Example flow
```text
PDF ─► page images ─► ColPali multi-vectors ─► vector DB (multi-vector)
query ─► ColPali query vectors ─► MaxSim top pages ─► VLM (e.g. GPT/Claude/Gemini vision) ─► answer + page citations
```

### Key takeaways
- Choose between converting to text, multimodal embeddings, or page-image retrieval.
- ColPali retrieves page images with late-interaction multi-vectors — no OCR needed.
- Treat tables specially: keep them whole and use SQL/code for numeric questions.

## Long Context, Cache-Augmented Generation and Production Choices
### RAG vs long context
Frontier models now accept **~1M tokens**. Why still retrieve?
- **Cost & latency** grow with input tokens on every request.
- **Lost in the middle**: models use information at the beginning/end of long inputs more reliably than in the middle.
- Corpora are often **far larger** than any context window, and change constantly.
- Access control and citations are easier with retrieval.

Long context *complements* RAG: retrieve more generously (whole sections) and let the model read.

### Cache-Augmented Generation (CAG)
For a **small, stable** knowledge base, preload all of it into the context once and reuse the model's **KV cache** (provider "prompt caching") across queries — no retrieval step, no retrieval errors. Breaks down when the knowledge is large or changes often.

### Choosing an architecture
| Situation | Suggested architecture |
|---|---|
| FAQ over a few hundred docs | naive/advanced vector RAG or CAG |
| Codes, IDs, jargon | hybrid (BM25 + dense) + reranker |
| Ambiguous chunk context (reports, filings) | contextual retrieval |
| Long documents, need surrounding context | parent-document / sentence window |
| Unreliable retrieval, open-domain fallback | CRAG |
| Mixed simple/complex questions | adaptive RAG |
| Multi-hop, relationship, corpus-wide questions | Graph RAG |
| Multi-source, multi-step research | agentic RAG |
| Scanned/visual documents | multimodal RAG (ColPali) |

### Production concerns
- **Evaluation** first: retrieval metrics + RAGAS-style generation metrics on a fixed test set.
- **Observability**: trace retrieval and generation (Langfuse/LangSmith).
- **Latency budget**: parallelise retrievers, limit rerank depth, stream tokens.
- **Freshness & permissions** in the index.
- **Guardrails**: refusals when context is missing, PII handling, prompt-injection defences for retrieved text.

### Key takeaways
- Long context doesn't replace RAG for large, changing or permissioned corpora.
- CAG preloads a small stable corpus and reuses the KV cache.
- Pick the simplest architecture that passes evals, then add components for observed failures.
