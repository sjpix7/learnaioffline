---
id: hybrid_rag
title: Hybrid RAG
short: Combine keyword (BM25/sparse) and semantic (dense) retrieval with fusion and reranking
emoji: 🧬
color: #AD1457
---
## Lexical vs Semantic Retrieval
Two families of retrieval have complementary strengths.

### Lexical (keyword) retrieval
Matches **exact terms**. Classic algorithms: TF-IDF and **BM25**, implemented with an **inverted index** (term → list of documents).
- ✅ Exact identifiers: product codes ("SKU-4471"), error codes ("ORA-00942"), names, acronyms, rare jargon.
- ✅ Fast, cheap, explainable, no model needed.
- ❌ Vocabulary mismatch: "car" doesn't match "automobile"; misses paraphrases.

### Semantic (dense) retrieval
Embeds queries and documents into dense vectors and matches by **meaning**.
- ✅ Synonyms, paraphrases, cross-lingual matches, natural-language questions.
- ❌ Can miss exact rare tokens, numbers and codes; out-of-domain jargon; harder to explain.

### Hybrid retrieval
**Run both and fuse the results.** Hybrid search consistently beats either alone on most benchmarks and in production, because failures of one are often covered by the other.

```text
query ──► BM25 / sparse ──► ranked list A ─┐
      └─► dense vectors ──► ranked list B ─┴─► fusion (RRF / weighted) ─► rerank ─► LLM
```

### Key takeaways
- Lexical search nails exact terms; dense search captures meaning.
- Hybrid search combines both to cover each other's blind spots.
- Typical pipeline: parallel retrieval → fusion → rerank → generate.

## BM25 and Sparse Retrieval
### TF-IDF intuition
A term is important to a document if it appears **often in that document** (term frequency, TF) but **rarely across the corpus** (inverse document frequency, IDF).

### BM25
BM25 (Okapi BM25) improves TF-IDF with **term-frequency saturation** and **length normalisation**:
```text
score(D, Q) = Σ_{q in Q} IDF(q) · f(q,D)·(k1 + 1) / ( f(q,D) + k1·(1 − b + b·|D|/avgdl) )

IDF(q) = ln( (N − n(q) + 0.5) / (n(q) + 0.5) + 1 )
```
- `f(q,D)` — frequency of term q in document D; `|D|` — document length; `avgdl` — average document length.
- `N` — number of documents; `n(q)` — documents containing q.
- **k1** (typically 1.2–2.0) controls TF saturation: repeated terms add less and less.
- **b** (typically 0.75) controls length normalisation: 0 = none, 1 = full.

```python
from rank_bm25 import BM25Okapi
corpus = [doc.lower().split() for doc in docs]
bm25 = BM25Okapi(corpus, k1=1.5, b=0.75)
scores = bm25.get_scores("reset password".split())
```

### Learned sparse retrieval
Models like **SPLADE** and BGE-M3's sparse output produce **sparse vectors** over the vocabulary with learned weights, including **expansion terms** that don't literally appear in the text ("car" → also weights "vehicle"). They keep inverted-index efficiency while reducing vocabulary mismatch.

### Sparse vs dense vectors
| | Sparse | Dense |
|---|---|---|
| Dimensions | vocabulary size (~30k–250k) | 256–4096 |
| Non-zeros | few (tens–hundreds) | all |
| Index | inverted index | ANN (HNSW, IVF) |
| Strength | exact terms, interpretability | semantics |

### Key takeaways
- BM25 = IDF-weighted, saturated term frequency with length normalisation (k1, b).
- Rare terms get high IDF; very common terms contribute little.
- Learned sparse models (SPLADE) add semantic expansion to inverted indexes.

## Fusion Methods: RRF and Weighted Scores
BM25 scores (unbounded, e.g. 0–25) and cosine similarities (−1..1) are on **different scales**, so you can't just add them. Two standard solutions:

### Reciprocal Rank Fusion (RRF)
Uses **ranks only**, ignoring raw scores:
```text
RRF(d) = Σ_{each result list r}  1 / (k + rank_r(d))        with k = 60 by convention
```
- A document missing from a list contributes nothing for that list.
- Robust, parameter-light, no score calibration needed — the default in Elasticsearch, OpenSearch, Azure AI Search, Qdrant and LangChain's `EnsembleRetriever`.

Worked example (k = 60): document X is rank 1 in BM25 and rank 3 in dense.
```text
RRF(X) = 1/(60+1) + 1/(60+3) = 0.016393 + 0.015873 = 0.032266
```
Document Y is rank 2 in BM25 only: `RRF(Y) = 1/62 = 0.016129` → X ranks above Y.

```python
def rrf(result_lists, k=60):
    scores = {}
    for results in result_lists:
        for rank, doc_id in enumerate(results, start=1):
            scores[doc_id] = scores.get(doc_id, 0.0) + 1.0 / (k + rank)
    return sorted(scores, key=scores.get, reverse=True)

rrf([["a", "b", "c"], ["c", "a", "d"]])   # ['a', 'c', 'b', 'd']
```

### Weighted (convex) combination
Normalise scores per list (e.g. **min-max** to [0, 1]), then combine:
```text
hybrid(d) = α · dense_norm(d) + (1 − α) · sparse_norm(d)
min-max:   x_norm = (x − min) / (max − min)
```
- **α = 1** → pure dense; **α = 0** → pure keyword. Weaviate exposes exactly this `alpha` parameter.
- Needs tuning on an eval set, but can outperform RRF when tuned.
- Other normalisations: z-score, distribution-based score fusion (DBSF).

### Which one?
Start with **RRF** (no tuning). Move to weighted fusion if you have labelled queries to tune α, or if one retriever is clearly stronger for your data.

### Key takeaways
- Raw BM25 and cosine scores are not comparable; fuse by rank (RRF) or normalised score.
- RRF(d) = Σ 1/(k + rank), k = 60 by default.
- Weighted fusion: α·dense + (1−α)·sparse after normalisation; α=1 is pure vector.

## Implementing Hybrid Search
### LangChain EnsembleRetriever
```python
from langchain_community.retrievers import BM25Retriever
from langchain_classic.retrievers import EnsembleRetriever   # was langchain.retrievers before v1

bm25 = BM25Retriever.from_documents(chunks); bm25.k = 10
dense = vector_store.as_retriever(search_kwargs={"k": 10})
hybrid = EnsembleRetriever(retrievers=[bm25, dense], weights=[0.4, 0.6])   # weighted RRF
docs = hybrid.invoke("error ORA-00942 when querying table")
```

### Weaviate
```python
collection.query.hybrid(query="reset password", alpha=0.5, limit=5)   # alpha: 0 = BM25, 1 = vector
```

### Qdrant (dense + sparse with server-side fusion)
```python
from qdrant_client import models
client.query_points(
    "docs",
    prefetch=[
        models.Prefetch(query=dense_vec, using="dense", limit=20),
        models.Prefetch(query=models.SparseVector(indices=idx, values=vals), using="sparse", limit=20),
    ],
    query=models.FusionQuery(fusion=models.Fusion.RRF),
    limit=5,
)
```

### Elasticsearch / OpenSearch
Combine a `match` (BM25) query with a `knn` query and fuse with the `rrf` retriever (Elasticsearch) or a normalisation search pipeline (OpenSearch).

### Postgres: pgvector + full-text search
```sql
WITH dense AS (
  SELECT id, row_number() OVER (ORDER BY embedding <=> $1) AS r
  FROM chunks ORDER BY embedding <=> $1 LIMIT 20
), sparse AS (
  SELECT id, row_number() OVER (ORDER BY ts_rank_cd(tsv, q) DESC) AS r
  FROM chunks, plainto_tsquery('english', $2) q
  WHERE tsv @@ q ORDER BY ts_rank_cd(tsv, q) DESC LIMIT 20
)
SELECT id, SUM(1.0 / (60 + r)) AS rrf
FROM (SELECT * FROM dense UNION ALL SELECT * FROM sparse) u
GROUP BY id ORDER BY rrf DESC LIMIT 5;
```

### Key takeaways
- LangChain's `EnsembleRetriever` fuses retrievers with weighted RRF.
- Weaviate uses `alpha`; Qdrant fuses prefetch queries server-side with RRF/DBSF.
- Postgres can do hybrid with pgvector + tsvector and an RRF SQL query.

## Reranking: Cross-Encoders and Late Interaction
Fusion produces a good candidate list; **reranking** puts the very best on top.

### Bi-encoder vs cross-encoder
| | Bi-encoder (embeddings) | Cross-encoder (reranker) |
|---|---|---|
| Input | query and doc encoded separately | query + doc encoded **together** |
| Precompute docs? | yes | no — must run per (query, doc) pair |
| Speed | very fast (ANN) | slow (N model calls) |
| Accuracy | good | best |
| Use | first-stage retrieval | second-stage on top 20–100 |

```python
from sentence_transformers import CrossEncoder
reranker = CrossEncoder("cross-encoder/ms-marco-MiniLM-L-6-v2")
pairs = [(query, d.page_content) for d in candidates]
scores = reranker.predict(pairs)
top = [d for _, d in sorted(zip(scores, candidates), key=lambda x: -x[0])][:5]
```
Hosted rerankers: Cohere Rerank, Voyage rerank, Jina Reranker; open models: BGE-reranker-v2, mxbai-rerank.

### Late interaction (ColBERT)
ColBERT keeps **one vector per token**. Relevance = **MaxSim**: for each query token, take the max similarity to any document token, then sum.
- More expressive than single-vector bi-encoders, much cheaper than cross-encoders (document token vectors are precomputed).
- Costs more storage (many vectors per document); PLAID/compression reduce this.
- BGE-M3 and Jina-ColBERT produce multi-vector outputs; ColPali applies the idea to page images.

### LLM rerankers
An LLM scores or orders candidates ("listwise" reranking, e.g. RankGPT). Highest flexibility, highest cost/latency.

### Key takeaways
- Cross-encoders read query and document together → most accurate, too slow for first stage.
- ColBERT's MaxSim late interaction sits between bi-encoders and cross-encoders.
- Typical: retrieve 50 with hybrid search → rerank → keep 5.

## Query Understanding: Rewriting, HyDE and Multi-Query
Better queries mean better retrieval for both lexical and dense legs.

### Query rewriting
An LLM turns a messy or conversational query into a clean, standalone search query: "and for the pro plan?" → "What is the refund policy for the Pro plan?" (condensing with chat history).

### Multi-query
Generate several paraphrases, retrieve for each, and fuse (RRF). Improves recall when wording varies.
```python
variants = llm.invoke(f"Write 3 different search queries for: {q}").content.splitlines()
lists = [retriever.invoke(v) for v in [q, *variants]]
fused = rrf([[d.id for d in l] for l in lists])
```
(LangChain's `MultiQueryRetriever` and LlamaIndex's `QueryFusionRetriever` implement this.)

### HyDE — Hypothetical Document Embeddings
Ask the LLM to write a **hypothetical answer**, then embed *that* to search. Answers look more like documents than questions do, which helps dense retrieval. Risk: a hallucinated answer can steer retrieval wrong for niche facts.

### Query expansion for BM25
Add synonyms, acronym expansions or spelling variants ("k8s" → "kubernetes") to the keyword leg.

### Query routing
Classify the query to pick a strategy: exact-match heavy (codes, names) → weight BM25; conceptual → weight dense; structured ("orders over $500 last month") → SQL; relational → graph.

### Key takeaways
- Rewrite conversational queries into standalone ones.
- Multi-query + RRF increases recall; HyDE embeds a hypothetical answer.
- Route queries to the retrieval leg (or weights) that suits them.

## Tuning and Evaluating Hybrid Systems
### What to tune
| Knob | Effect |
|---|---|
| α (dense weight) or RRF list weights | balance semantic vs keyword |
| k per retriever (candidate depth) | recall vs latency |
| RRF constant k | how strongly top ranks dominate (smaller k → more top-heavy) |
| BM25 k1, b | TF saturation, length normalisation |
| tokenisation/analyzers | stemming, stop-words, language |
| rerank depth & top_n | precision vs cost |
| chunk size | granularity for both legs |

### How to evaluate
1. Build a labelled set: queries + relevant chunk ids (from logs, SMEs, or LLM-generated then reviewed).
2. Measure **recall@k** of each leg alone and of the fused list — fusion should beat both.
3. Measure **MRR / nDCG** after reranking.
4. Run end-to-end metrics (RAGAS faithfulness, answer correctness).
5. Slice by query type (codes vs questions vs multilingual) — hybrid's gains are often concentrated in some slices.

### Worked tuning example
For 3 test queries the relevant doc's fused rank was 1, 3 and 2:
```text
MRR = (1/1 + 1/3 + 1/2) / 3 = (1 + 0.333 + 0.5) / 3 ≈ 0.611
```

### Production tips
- Run both legs **in parallel** to keep latency close to the slower one.
- Cache embeddings of frequent queries.
- Log which leg contributed each final result to debug regressions.
- Keep the BM25 index and vector index in sync (same chunk ids).

### Key takeaways
- Tune α/weights, candidate depth, BM25 params and reranker depth on a labelled set.
- Fusion should beat each leg alone on recall@k — verify it per query slice.
- Query both legs in parallel and keep their chunk ids consistent.
