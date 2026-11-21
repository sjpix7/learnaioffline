---
id: embeddings
title: Text Embedding Models
short: How embeddings work, training, similarity, the model landscape, MTEB, Matryoshka and quantization
emoji: 📐
color: #00838F
---
## What Are Embeddings?
An **embedding** is a dense vector of numbers that represents the meaning of a piece of text (or an image, audio, code...). Texts with similar meaning get vectors that are **close** in the vector space.
```python
from sentence_transformers import SentenceTransformer
model = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")   # 384-dim
vecs = model.encode(["How do I reset my password?", "Steps to recover account access", "Best pizza in Rome"])
vecs.shape            # (3, 384)
model.similarity(vecs, vecs)   # first two are close; the pizza sentence is far
```

### What embeddings are used for
| Task | How |
|---|---|
| Semantic search / RAG | nearest neighbours of a query vector |
| Clustering & topic discovery | k-means/HDBSCAN on vectors |
| Classification | vectors as features for a light classifier |
| Deduplication | near-duplicate detection by similarity threshold |
| Recommendations | similar items/users |
| Anomaly detection | points far from clusters |
| Reranking & evaluation | semantic similarity scores |

### Properties to know
- **Dimension** (d): length of the vector — e.g. 384, 768, 1024, 1536, 3072, 4096.
- **Max input length**: tokens the model can read (e.g. 512, 8192, 32k); longer text is truncated.
- **Similarity metric**: usually cosine; many models output **normalised** vectors.
- **Symmetric vs asymmetric**: symmetric tasks compare like with like (sentence ↔ sentence); asymmetric tasks compare short queries with long passages and often use **query/passage prefixes or instructions**.

### Key takeaways
- Embeddings map text to vectors where distance reflects meaning.
- They power search, RAG, clustering, classification and deduplication.
- Know each model's dimension, max tokens and intended similarity metric.

## From Word Vectors to Transformer Embeddings
### Static word embeddings
- **word2vec** (Mikolov et al., 2013) — skip-gram and CBOW: predict context words from a word (or vice versa). Famous analogy: `king − man + woman ≈ queen`.
- **GloVe** (Stanford, 2014) — factorises global word co-occurrence statistics.
- **fastText** (Facebook, 2016) — sums character n-gram vectors, so it handles rare and misspelled words.
Limitation: **one vector per word**, regardless of context ("bank" of a river vs a bank account).

### Contextual embeddings
Transformers like **BERT** (2018) produce token embeddings that depend on the surrounding sentence. But raw BERT `[CLS]` vectors or averaged tokens make poor sentence embeddings for similarity.

### Sentence-BERT (SBERT)
Reimers & Gurevych (2019) fine-tuned BERT in a **siamese** setup on sentence pairs (NLI, STS) so that **mean-pooled** outputs are directly comparable with cosine similarity. This created the **sentence-transformers** library and the modern bi-encoder approach.

### Pooling strategies
| Pooling | Description |
|---|---|
| Mean pooling | average all token vectors (masking padding) — most common for SBERT models |
| CLS pooling | use the `[CLS]` token vector (BGE, many BERT-style models) |
| Last-token pooling | use the final token — common for decoder-LLM-based embedders (e.g. E5-Mistral, Qwen3-Embedding) |

### LLM-based embedders
Since ~2023 many top models start from decoder LLMs (Mistral, Qwen, Gemma) and are fine-tuned contrastively — e.g. E5-Mistral, NV-Embed, gte-Qwen, Qwen3-Embedding, Gemini Embedding — giving long context and strong multilingual quality at the cost of size.

### Key takeaways
- word2vec/GloVe/fastText give one static vector per word.
- SBERT made transformer sentence embeddings comparable via siamese fine-tuning + pooling.
- Pooling choices: mean, CLS or last-token; LLM-based embedders dominate recent leaderboards.

## Training Embeddings: Contrastive Learning
### Bi-encoders
A **bi-encoder** encodes query and document **independently**, so document vectors can be precomputed and indexed. Relevance = similarity of the two vectors. (A **cross-encoder** instead reads both together — more accurate, but cannot precompute; used for reranking.)

### Contrastive objective (InfoNCE)
Training pulls **positive** pairs (query, relevant passage) together and pushes **negatives** apart:
```text
L = −log( exp(sim(q, p⁺)/τ) / Σ_j exp(sim(q, p_j)/τ) )
```
- **τ** (temperature) sharpens or smooths the distribution.
- **In-batch negatives**: other passages in the same batch serve as negatives — larger batches give more negatives (MultipleNegativesRankingLoss in sentence-transformers).
- **Hard negatives**: passages that look relevant but aren't — they teach fine distinctions and are crucial for strong retrievers.

### Typical training stages
1. **Weakly-supervised pre-training** on massive pairs (title–body, question–answer, query–click).
2. **Supervised fine-tuning** on high-quality labelled data with hard negatives (MS MARCO, NQ, NLI).
3. Increasingly, **synthetic data** generated by LLMs, and distillation from cross-encoders.

### Fine-tuning your own
```python
from sentence_transformers import SentenceTransformer, losses
from sentence_transformers.trainer import SentenceTransformerTrainer
from datasets import Dataset

model = SentenceTransformer("BAAI/bge-base-en-v1.5")
train = Dataset.from_dict({"anchor": queries, "positive": passages})
loss = losses.MultipleNegativesRankingLoss(model)
trainer = SentenceTransformerTrainer(model=model, train_dataset=train, loss=loss)
trainer.train()
```
Domain fine-tuning (legal, medical, code, internal jargon) often gives larger retrieval gains than switching to a bigger general model. Wrap the loss in `MatryoshkaLoss` to also get truncatable vectors.

### Key takeaways
- Bi-encoders embed separately (indexable); cross-encoders score pairs jointly (reranking).
- InfoNCE contrastive loss with in-batch and hard negatives is the standard objective.
- Fine-tuning on domain pairs with MultipleNegativesRankingLoss is cheap and effective.

## Similarity, Normalisation and Vector Arithmetic
### Measures
```text
cosine(a, b) = a·b / (‖a‖‖b‖)        range [−1, 1], 1 = same direction
dot(a, b)    = Σ aᵢ bᵢ
L2(a, b)     = sqrt(Σ (aᵢ − bᵢ)²)
```
For **unit vectors** (‖a‖ = ‖b‖ = 1): `dot = cosine` and `L2² = 2 − 2·cosine`. That's why normalising once at index time lets you use the fastest metric (dot product) without changing rankings.

### Normalisation
```python
import numpy as np
v = np.array([3.0, 4.0])
np.linalg.norm(v)        # 5.0
v / np.linalg.norm(v)    # [0.6, 0.8] — unit length
```

### Interpreting scores
- Cosine values are **model-specific**: 0.8 may be "very similar" for one model and "unrelated" for another (some models compress all scores into a narrow band such as 0.7–1.0).
- Use scores for **ranking**, and calibrate any threshold on labelled data.

### Anisotropy
Raw transformer embeddings often occupy a narrow cone (all pairs look similar). Contrastive training fixes most of this; whitening or normalisation can help older models.

### Key takeaways
- Normalise vectors so dot product equals cosine and L2 ranks the same way.
- Similarity thresholds don't transfer between models — calibrate them.
- Use similarity for ranking first; thresholds second.

## The Embedding Model Landscape (2026)
The field moves fast — always check current leaderboards and docs. Notable families:

### Proprietary APIs
| Model | Notes |
|---|---|
| OpenAI text-embedding-3-small / -large | 1536 / 3072 dims; `dimensions` parameter shortens vectors (Matryoshka); small is very cheap ($0.02 per 1M tokens) |
| Google Gemini Embedding (gemini-embedding-001) | 3072 dims with Matryoshka (768/1536/3072); topped MTEB multilingual in 2025; Gemini Embedding 2 (2026) is natively multimodal |
| Cohere Embed v4 | multimodal (text + images), long context, Matryoshka sizes, `input_type` for query vs document |
| Voyage AI (MongoDB) | voyage-3.5 / Voyage 4 family (2026, mixture-of-experts, shared embedding space across sizes); domain models (code, law, finance) |
| Jina embeddings v3/v4 | task-specific LoRA adapters, 8k context; v4 multimodal; late chunking support |

### Open-weight models
| Model | Notes |
|---|---|
| Qwen3-Embedding (0.6B / 4B / 8B) | instruction-aware, 32k context, flexible dims up to 4096; 8B led MTEB multilingual in 2025–26 |
| BGE-M3 (BAAI) | dense + sparse + multi-vector (ColBERT) outputs in one model, 100+ languages, 8192 tokens, 1024 dims |
| E5 family (Microsoft) | requires `"query: "` / `"passage: "` prefixes; multilingual-e5-large; E5-Mistral |
| nomic-embed-text v1.5/v2 | open data and code; prefixes `search_query:` / `search_document:`; Matryoshka |
| all-MiniLM-L6-v2 | tiny (22M params), 384 dims — classic fast baseline |
| NV-Embed, gte-Qwen, Stella, mxbai-embed, EmbeddingGemma | strong open alternatives |

### Choosing a model
1. **Language(s)** and **domain** of your data.
2. **Quality on your data** — build a small retrieval eval (queries + relevant docs) and compare 3–4 candidates.
3. **Cost** (API price or GPU), **latency** and **throughput**.
4. **Dimensions/storage** at your scale.
5. **Context length** for long chunks.
6. **Deployment constraints**: data residency, on-prem, licence.

### Key takeaways
- Leading APIs: OpenAI text-embedding-3, Gemini Embedding, Cohere Embed v4, Voyage, Jina.
- Leading open models: Qwen3-Embedding, BGE-M3, E5, Nomic, MiniLM for speed.
- Choose by evaluating on your own data, not just leaderboards.

## Benchmarks: MTEB and Beyond
### MTEB
The **Massive Text Embedding Benchmark** (Muennighoff et al., 2022; hosted on Hugging Face) evaluates embedding models across task types:
| Task type | Measures |
|---|---|
| Retrieval | nDCG@10 on query→document search (BEIR datasets) |
| Reranking | ranking candidate lists |
| Classification | logistic regression on embeddings |
| Clustering | V-measure of clusters |
| Pair classification | duplicate/paraphrase detection |
| STS | correlation with human similarity scores |
| Summarization | similarity of summaries to references |
| Bitext mining | finding translations |

**MMTEB** (2025) extended it to hundreds of tasks across 250+ languages, with separate leaderboards (English, multilingual, code, domain-specific). **BEIR** is the classic zero-shot retrieval benchmark; **MIRACL** covers multilingual retrieval; **LoCo/LongEmbed** test long documents.

### Reading leaderboards wisely
- Look at the **task type you need** (usually Retrieval), not only the average.
- Check model **size, dimensions, max tokens and licence**.
- Watch for **contamination** — models trained on benchmark-like data can overfit.
- Rankings change monthly; validate on **your own data**.

### A minimal custom evaluation
```python
import numpy as np

def recall_at_k(model, queries, corpus, relevant, k=5):
    q = model.encode(queries, normalize_embeddings=True)
    d = model.encode(corpus, normalize_embeddings=True)
    hits = 0
    for i, row in enumerate(q @ d.T):
        top = np.argsort(-row)[:k]
        hits += int(relevant[i] in top)
    return hits / len(queries)
```

### Key takeaways
- MTEB covers retrieval, reranking, classification, clustering, STS, pair classification, summarization and bitext mining.
- MMTEB extends MTEB to many languages and domains; BEIR focuses on zero-shot retrieval.
- Use leaderboards to shortlist; decide with your own evaluation.

## Dimensions, Matryoshka Embeddings and Quantization
### Storage arithmetic
`bytes = N × d × bytes_per_value`
| Precision | Bytes per value | 1M × 1024-dim |
|---|---|---|
| float32 | 4 | 4.1 GB |
| float16 / bfloat16 | 2 | 2.0 GB |
| int8 (scalar quantization) | 1 | 1.0 GB |
| binary (1 bit) | 1/8 | 128 MB |

### Matryoshka Representation Learning (MRL)
MRL (Kusupati et al., 2022) trains embeddings so the **first m dimensions** are themselves a good embedding. You can truncate a 3072-dim vector to 1024, 512 or 256 dims (then **re-normalise**) with modest quality loss.
```python
from openai import OpenAI
client = OpenAI()
r = client.embeddings.create(model="text-embedding-3-large", input="hello", dimensions=256)
len(r.data[0].embedding)    # 256
```
```python
model = SentenceTransformer("nomic-ai/nomic-embed-text-v1.5", trust_remote_code=True, truncate_dim=256)
```

### Quantization
- **Scalar (int8)**: map each float to an 8-bit integer using per-dimension ranges → 4× smaller, ~1% quality loss typical.
- **Binary**: keep only the sign bit → 32× smaller; compare with Hamming distance; then **rescore** the top candidates with float vectors to recover most quality.
- **Product quantization (PQ)**: compress sub-vectors to codebook ids (used inside ANN indexes).

### A combined recipe at scale
Matryoshka-truncate (e.g. 1024 → 512) + binary index for first-pass search + int8/float rescoring of the top 100 → large memory savings with small accuracy loss.

### Key takeaways
- Memory = N × d × bytes; float32 uses 4 bytes per dimension.
- Matryoshka models allow truncating dimensions (re-normalise after truncation).
- int8 saves 4×, binary 32× (with rescoring).

## Using Embeddings Well in Practice
### Query/document instructions and prefixes
Asymmetric models expect hints:
| Model | Query | Document |
|---|---|---|
| E5 | `query: ...` | `passage: ...` |
| nomic-embed | `search_query: ...` | `search_document: ...` |
| BGE (English v1.5) | optional instruction "Represent this sentence for searching relevant passages: " | none |
| Qwen3-Embedding | `Instruct: <task>\nQuery: ...` | none |
| Cohere / Voyage / Gemini APIs | `input_type="search_query"` / task type `RETRIEVAL_QUERY` | `input_type="search_document"` / `RETRIEVAL_DOCUMENT` |
Forgetting prefixes can noticeably reduce retrieval quality.

### Batching, caching and throughput
- Embed in **batches** (e.g. 32–256 texts) — far faster than one by one.
- **Cache** embeddings keyed by (model, text hash); never re-embed unchanged chunks.
- Respect API rate limits with concurrency limits and retries.
- For self-hosting: Text Embeddings Inference (TEI), vLLM, Infinity, ONNX/int8 for CPU.

### Multilingual and cross-lingual
Multilingual models (BGE-M3, multilingual-e5, Qwen3-Embedding, Cohere, Gemini) map different languages into one space, so a Hindi query can retrieve an English document.

### Pitfalls
- Mixing vectors from **different models or versions** in one index.
- Truncation: text beyond max tokens is silently dropped — chunk accordingly.
- Embedding **very long** chunks dilutes meaning; embedding **tiny** ones loses context.
- Numbers, negation and exact codes are weak spots → combine with BM25 (hybrid search).

### Key takeaways
- Use the model's required prefixes / input types for queries vs documents.
- Batch and cache embeddings; track model + version per vector.
- Pair embeddings with keyword search for exact terms, numbers and codes.
