---
id: vector_rag
title: Vector RAG
short: Retrieval-augmented generation with embeddings, chunking, vector databases and reranking
emoji: 🧭
color: #1565C0
---
## Why Retrieval-Augmented Generation?
LLMs know only what was in their training data, up to a **knowledge cutoff**, and they cannot see your private documents. Asking them about either leads to outdated answers or **hallucinations** (fluent but false statements).

**Retrieval-Augmented Generation (RAG)** fixes this by retrieving relevant information at query time and putting it into the prompt, so the model answers *grounded* in that context. The term comes from the 2020 paper by Lewis et al. (Facebook AI Research).

### RAG vs fine-tuning vs long context
| Approach | Best for | Weakness |
|---|---|---|
| RAG | fresh, private, large or frequently-changing knowledge; citations | retrieval quality limits answers |
| Fine-tuning | style, format, domain behaviour, latency | poor at injecting facts; costly to update |
| Long context | a few large documents per request | cost and latency grow with tokens; "lost in the middle" |

They combine well: a fine-tuned model can still use RAG, and long-context models still benefit from retrieving the *right* pages.

### Benefits of RAG
- **Freshness** — update the index, not the model.
- **Access control** — retrieve only what the user is allowed to see.
- **Traceability** — cite the sources used.
- **Cost** — send a few relevant chunks instead of whole corpora.

### Vector RAG specifically
"Vector RAG" means retrieval by **semantic similarity of embeddings**: text is turned into vectors, and the chunks whose vectors are closest to the query vector are retrieved. Other retrieval styles — keyword (BM25), graph traversal, SQL — are covered in Hybrid RAG and Graph RAG.

### Key takeaways
- RAG = retrieve relevant context at query time + generate a grounded answer.
- It addresses knowledge cutoffs, private data and hallucinations, and enables citations.
- Vector RAG retrieves by embedding similarity.

## The RAG Pipeline End to End
RAG has two phases.

### Offline: indexing
1. **Load** documents (PDFs, HTML, wikis, tickets, DB rows).
2. **Clean & parse** — strip boilerplate, extract tables, keep structure.
3. **Chunk** — split into passages of a few hundred tokens.
4. **Embed** each chunk with an embedding model.
5. **Store** vectors + text + metadata in a vector database (index).

### Online: query time
6. **Embed the query** with the *same* embedding model.
7. **Retrieve** the top-k most similar chunks (optionally filter by metadata).
8. **(Optional) rerank** the candidates with a cross-encoder.
9. **Augment** — build a prompt with instructions + context + question.
10. **Generate** — the LLM answers, ideally with citations.

```python
# A dependency-light illustration of the whole loop
import numpy as np

def embed(texts):                       # stand-in for a real embedding model
    return np.array([model.encode(t) for t in texts])

chunks = ["Refunds are accepted within 30 days.", "Shipping takes 5-7 business days."]
chunk_vecs = embed(chunks)
chunk_vecs /= np.linalg.norm(chunk_vecs, axis=1, keepdims=True)   # normalise once

def retrieve(query, k=1):
    q = embed([query])[0]
    q /= np.linalg.norm(q)
    scores = chunk_vecs @ q                         # cosine similarity via dot product
    top = np.argsort(-scores)[:k]
    return [chunks[i] for i in top]

context = "\n".join(retrieve("How long do I have to return an item?"))
prompt = f"Answer using only the context.\nContext:\n{context}\n\nQuestion: How long do I have to return an item?"
```

### The golden rule
Query and documents **must be embedded by the same model** (and the same version and dimension). Vectors from different models live in different spaces and are not comparable.

### Key takeaways
- Indexing: load → parse → chunk → embed → store. Query: embed → retrieve → rerank → prompt → generate.
- Always embed queries and documents with the same model.
- Normalised vectors let you compute cosine similarity as a dot product.

## Chunking Strategies
Chunking decides **what a retrievable unit is**. Too large: chunks contain irrelevant text, dilute the embedding and waste context. Too small: chunks lose meaning and context.

### Common strategies
| Strategy | How | Notes |
|---|---|---|
| Fixed-size | every N characters/tokens | simple, may cut sentences |
| Fixed-size + overlap | windows of N with O overlapping | preserves context across boundaries |
| Recursive | split by paragraphs, then sentences, then words | robust default (LangChain RecursiveCharacterTextSplitter) |
| Sentence-based | group whole sentences up to a size | respects grammar |
| Structure-aware | by Markdown headers, HTML sections, code functions | keeps logical units |
| Semantic | split where embedding similarity between sentences drops | topic-coherent chunks |
| Hierarchical / parent-child | small chunks for search, larger parents for context | see RAG Architectures |
| Late / contextual chunking | add document context to each chunk before embedding | see RAG Architectures |

### Sliding-window arithmetic
With chunk size **C**, overlap **O** and text length **L** (C > O), the step is `S = C − O` and the number of windows is:
```text
chunks = 1                         if L <= C
chunks = ceil((L - C) / S) + 1     otherwise
```
Example: L = 1000, C = 300, O = 50 → S = 250 → ceil(700/250) + 1 = 3 + 1 = **4 chunks**.

### Practical guidance
- Start around **256–1024 tokens** with **10–20 % overlap**, then tune with retrieval metrics.
- Keep metadata: source, title, section heading, page, timestamp, permissions.
- Prepend titles/headers to chunk text so isolated chunks keep meaning.
- Tables and code deserve special handling (keep tables whole; split code by function).

### Key takeaways
- Chunk size trades precision against context; overlap prevents boundary loss.
- Recursive and structure-aware splitting are strong defaults.
- Number of chunks ≈ ceil((L − C)/(C − O)) + 1 for sliding windows.

## Similarity Metrics and Vector Math
### Cosine similarity
`cos(a, b) = (a · b) / (‖a‖ ‖b‖)` — ranges from −1 (opposite) to 1 (same direction). It ignores vector length, focusing on direction (meaning).
```python
import numpy as np
a = np.array([1, 2, 2]); b = np.array([2, 0, 1])
dot = a @ b                          # 1*2 + 2*0 + 2*1 = 4
cos = dot / (np.linalg.norm(a) * np.linalg.norm(b))   # 4 / (3 * 2.236) ≈ 0.596
```

### Dot product (inner product)
`a · b = Σ aᵢbᵢ`. For **unit-normalised** vectors, dot product **equals** cosine similarity, which is why many systems normalise embeddings and use the faster dot product.

### Euclidean (L2) distance
`‖a − b‖ = sqrt(Σ (aᵢ − bᵢ)²)`. Smaller = more similar. For unit vectors: `‖a − b‖² = 2 − 2·cos(a, b)`, so ranking by L2 distance and by cosine gives the **same order**.

### Other metrics
- **Manhattan (L1)**: `Σ |aᵢ − bᵢ|`.
- **Hamming**: number of differing bits — used with binary-quantised embeddings.
- **Jaccard**: set overlap — for sparse/keyword features.

### Choosing a metric
Use the metric the embedding model was trained for (most text embedding models: cosine). Configure the vector DB to match (e.g. `distance="Cosine"` in Qdrant, `vector_cosine_ops` in pgvector).

### Key takeaways
- Cosine measures angle; dot product equals cosine for normalised vectors.
- L2 distance on unit vectors ranks identically to cosine.
- Match the DB metric to the embedding model's training objective.

## Approximate Nearest Neighbour (ANN) Indexes
Exact (brute-force / "flat") search compares the query with **every** vector: O(N·d). Fine for thousands of vectors, too slow for hundreds of millions. **ANN** indexes trade a little recall for huge speedups.

### HNSW — Hierarchical Navigable Small World
- Builds a multi-layer **proximity graph**: sparse upper layers for long jumps, dense bottom layer for fine search.
- Search starts at the top layer and greedily descends.
- Parameters: **M** (links per node — more = better recall, more memory), **efConstruction** (build-time candidate list), **efSearch** (query-time candidate list — higher = better recall, slower).
- Excellent recall/latency; memory-hungry; the default in most vector DBs.

### IVF — Inverted File index
- Clusters vectors with k-means into `nlist` cells (centroids).
- At query time searches only the `nprobe` nearest cells.
- More `nprobe` → higher recall, slower.

### PQ — Product Quantization
- Splits each vector into m sub-vectors and replaces each with the id of its nearest centroid in a small codebook → massive compression (e.g. 768 floats → 96 bytes).
- Often combined: **IVF-PQ**. Distances are approximate.

### Other options
- **Flat** — exact, the gold standard for evaluating ANN recall.
- **DiskANN / Vamana** — graph index designed for SSDs (billions of vectors).
- **ScaNN** — Google's anisotropic quantization library.
- **Scalar / binary quantization** — store int8 or 1-bit vectors, then rescore top candidates with full precision.

### Measuring ANN quality
**Recall@k (ANN)** = fraction of the true k nearest neighbours (from exact search) that the ANN index returns. Tune efSearch/nprobe until recall is acceptable (e.g. ≥ 0.95) at your latency budget.

### Memory estimate
Raw float32 storage = `N × d × 4 bytes`. 1 million 768-dim vectors ≈ 1,000,000 × 768 × 4 = 3.07 GB (before index overhead).

### Key takeaways
- HNSW (graph) is the default; tune M, efConstruction, efSearch.
- IVF searches a few clusters (nprobe); PQ compresses vectors.
- Raw memory ≈ N × d × 4 bytes for float32.

## Vector Databases
A vector database stores vectors **plus** payloads (text, metadata), supports ANN search, filtering, updates/deletes, replication and access control.

### Landscape
| System | Type | Notes |
|---|---|---|
| FAISS | library (Meta) | in-process ANN library; no server, no metadata filtering by itself |
| Chroma | embedded / server | developer-friendly, great for prototypes |
| pgvector | Postgres extension | vectors next to relational data; HNSW and IVFFlat indexes |
| Qdrant | open-source DB (Rust) | rich payload filtering, quantization, hybrid sparse+dense |
| Weaviate | open-source DB (Go) | hybrid search with `alpha`, modules for vectorisation |
| Milvus / Zilliz | open-source DB | large scale, many index types incl. GPU |
| Pinecone | managed SaaS | serverless, namespaces, hybrid |
| Elasticsearch / OpenSearch | search engines | BM25 + kNN vectors in one system |
| MongoDB Atlas Vector Search, Redis, LanceDB, Vespa | various | add vectors to existing stacks |

### pgvector example
```sql
CREATE EXTENSION vector;
CREATE TABLE chunks (id bigserial PRIMARY KEY, content text, source text, embedding vector(1536));
CREATE INDEX ON chunks USING hnsw (embedding vector_cosine_ops);

-- <=> cosine distance, <-> L2 distance, <#> negative inner product
SELECT content, 1 - (embedding <=> $1) AS similarity
FROM chunks
WHERE source = 'handbook'
ORDER BY embedding <=> $1
LIMIT 5;
```

### Qdrant example
```python
from qdrant_client import QdrantClient, models
client = QdrantClient(url="http://localhost:6333")
client.create_collection("docs", vectors_config=models.VectorParams(size=768, distance=models.Distance.COSINE))
client.upsert("docs", points=[models.PointStruct(id=1, vector=vec, payload={"text": t, "lang": "en"})])
hits = client.query_points("docs", query=qvec, limit=5,
    query_filter=models.Filter(must=[models.FieldCondition(key="lang", match=models.MatchValue(value="en"))])).points
```

### Choosing
- Already on Postgres and < ~10–50M vectors? **pgvector** keeps things simple.
- Need advanced filtering, quantization, hybrid search at scale? **Qdrant / Weaviate / Milvus**.
- Want zero ops? **Pinecone** or a managed offering.
- Prototyping? **Chroma** or in-memory stores.

### Key takeaways
- Vector DBs combine ANN search with metadata, filtering, CRUD and scaling.
- FAISS is a library, not a database; pgvector adds vectors to Postgres.
- In pgvector, `<=>` is cosine distance and `<->` is L2 distance.

## Metadata Filtering, Multi-Tenancy and Freshness
### Metadata filtering
Filters restrict search to chunks whose metadata matches: `department = "HR" AND year >= 2025`.
- **Pre-filtering**: filter first, then ANN search the subset — exact w.r.t. the filter, can be slow or hurt graph connectivity with very selective filters.
- **Post-filtering**: ANN search first, then drop non-matching hits — fast but may return fewer than k results.
- Modern engines (Qdrant, Weaviate, Milvus, pgvector with iterative scans) use **filter-aware** search to get the best of both.

### Multi-tenancy and security
- Separate collections/namespaces per tenant, or a `tenant_id` payload filter that is **always** applied server-side.
- Enforce document-level permissions (ACLs) at retrieval time — never rely on the LLM to hide data it was given.

### Keeping the index fresh
- Store a stable document id and content hash; re-embed only changed documents (upsert) and delete removed ones.
- Track embedding model version in metadata; re-index everything when you change the model.
- Use timestamps for recency boosting ("latest policy wins").

### Self-querying
An LLM converts a natural-language question into a semantic query **plus** structured filters: "laptops under $1000 from 2025" → query "laptop", filter `price < 1000 AND year = 2025`.

### Key takeaways
- Pre-filtering is precise; post-filtering is fast but can under-fill results.
- Enforce tenant and permission filters on the server side.
- Re-embed changed docs by hash; re-index fully when the embedding model changes.

## Prompting, Grounding and Citations
### A solid RAG prompt
```text
You are a support assistant. Answer the question using ONLY the context below.
If the answer is not in the context, say "I don't know based on the provided documents."
Cite sources as [n] after each claim.

Context:
[1] (refund-policy.md) Refunds are accepted within 30 days of delivery...
[2] (shipping.md) Orders ship within 2 business days...

Question: Can I return an item after 5 weeks?
```
Key elements: role, **grounding instruction**, an explicit **"I don't know" escape hatch**, numbered sources and a citation format.

### Context assembly tips
- Order matters: models attend best to the **beginning and end** of long contexts ("lost in the middle"), so put the strongest chunks first (or first and last).
- De-duplicate near-identical chunks; respect a token budget.
- Include metadata (title, date) that helps the model judge relevance.
- Separate instructions from retrieved text with clear delimiters to reduce **prompt injection** from documents.

### Generation settings
Low temperature (0–0.3) for factual Q&A. Ask for structured output (answer + citations list) to make post-processing and evaluation easier.

### Key takeaways
- Instruct the model to use only the context and to admit when it doesn't know.
- Number sources for citations; put the best context first.
- Treat retrieved text as untrusted input — delimit it to limit prompt injection.

## Reranking and Retrieval Evaluation
### Two-stage retrieval
1. **Retrieve** many candidates fast (k = 20–100) with a bi-encoder (vector search) and/or BM25.
2. **Rerank** them with a slower, more accurate **cross-encoder** that reads query and passage *together* and outputs a relevance score; keep the top 3–8.

Rerankers: Cohere Rerank, Voyage rerank, Jina reranker, BGE-reranker, `cross-encoder/ms-marco-MiniLM-L-6-v2`, or an LLM as a listwise reranker.

### Retrieval metrics
Given a ranked list and the set of relevant documents:
- **Precision@k** = relevant results in top k / k.
- **Recall@k** = relevant results in top k / total relevant.
- **Hit rate@k** = 1 if any relevant result is in the top k (averaged over queries).
- **MRR** (Mean Reciprocal Rank) = average of 1/rank of the first relevant result.
- **nDCG@k** = DCG / ideal DCG, where `DCG = Σ relᵢ / log₂(i + 1)` rewards relevant items near the top.

Worked example: ranks of relevant docs for one query = {2, 5}, k = 5, 3 relevant docs exist in total:
```text
Precision@5 = 2/5 = 0.4
Recall@5    = 2/3 ≈ 0.667
RR          = 1/2 = 0.5   (first relevant at rank 2)
```

### End-to-end evaluation
Retrieval metrics need labelled relevance. Generation quality — faithfulness, answer relevancy, context precision/recall — is measured with frameworks like **RAGAS** (see that topic), often with LLM-as-a-judge.

### Common failure modes and fixes
| Symptom | Likely cause | Fix |
|---|---|---|
| Right doc never retrieved | chunking/embedding mismatch, vocabulary gap | hybrid search, better chunking, query rewriting |
| Retrieved but answer wrong | too much noise, weak prompt | reranking, fewer/better chunks, prompt |
| Hallucinated facts | model ignores context | stricter grounding prompt, faithfulness checks |
| Slow queries | huge k, no ANN tuning | tune efSearch/nprobe, cache, smaller k after rerank |

### Key takeaways
- Retrieve broadly with a bi-encoder, then rerank precisely with a cross-encoder.
- Know the formulas: precision@k, recall@k, MRR, nDCG.
- Diagnose retrieval and generation separately.
