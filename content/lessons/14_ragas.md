---
id: ragas
title: RAGAS
short: Evaluate RAG and agents — faithfulness, relevancy, context precision/recall, testsets, experiments
emoji: 📊
color: #F9A825
---
## Why Evaluate RAG Systems?
You cannot improve what you don't measure. A RAG system can fail in **retrieval** (wrong or missing context) or in **generation** (hallucination, irrelevant answer), and changing a prompt, chunk size, embedding model or LLM can silently break either.

### Why classic metrics fall short
- **BLEU / ROUGE** count n-gram overlap with a reference answer — a correct paraphrase scores low and a fluent wrong answer can score high.
- Human evaluation is accurate but slow and expensive.
- **LLM-as-a-judge** metrics scale evaluation by having a model check specific, well-defined properties.

### What is RAGAS?
**Ragas** (Retrieval Augmented Generation Assessment, Es et al., 2023) is an open-source Python library for evaluating LLM applications — originally RAG, now also agents and tool use. It provides:
- **Metrics** for retrieval and generation quality (many LLM-based, some not).
- **Test data generation** from your documents.
- **Experiments** to compare app versions systematically.
- Integrations with LangChain, LlamaIndex, Langfuse, LangSmith and more.

### The evaluation mindset
1. Build a **test set** representative of real questions.
2. Choose **metrics** that map to failure modes.
3. Run **experiments** on every change; track scores over time.
4. Look at **failing samples**, not just averages.
5. Add a **CI quality gate** (e.g. faithfulness ≥ 0.8).

### Key takeaways
- RAG fails in retrieval or generation — measure both.
- BLEU/ROUGE miss meaning; LLM-judged metrics evaluate specific properties at scale.
- Ragas provides metrics, test generation and experiments for LLM apps.

## Core Metric: Faithfulness
**Faithfulness** measures whether the response is **factually consistent with the retrieved context** — the main hallucination metric for RAG.

### How it is computed
1. An LLM breaks the response into individual **claims** (statements).
2. For each claim, an LLM checks whether it can be **inferred from the retrieved context**.
3. Score:
```text
Faithfulness = (number of claims supported by the retrieved context) / (total number of claims in the response)
```
Range 0–1; higher is better.

### Worked example
Context: "Einstein was born on 14 March 1879 in Ulm, Germany."
Response: "Einstein was born in Germany on 20 March 1879."
- Claim 1: born in Germany → supported ✅
- Claim 2: born on 20 March 1879 → not supported ❌
- Faithfulness = 1/2 = **0.5**

### Notes
- Faithfulness does **not** check whether the answer is correct in the real world — only whether it is grounded in the provided context. A faithful answer based on wrong context is still "faithful".
- Inputs needed: `user_input`, `response`, `retrieved_contexts`.
- Variants: HHEM-based faithfulness (Vectara's hallucination classifier) for cheaper scoring; **ResponseGroundedness** in the NVIDIA metric family.

```python
from openai import AsyncOpenAI
from ragas.llms import llm_factory
from ragas.metrics.collections import Faithfulness

llm = llm_factory("gpt-5.4-mini", client=AsyncOpenAI())
metric = Faithfulness(llm=llm)
result = await metric.ascore(
    user_input="Where was Einstein born?",
    response="Einstein was born in Germany on 20 March 1879.",
    retrieved_contexts=["Einstein was born on 14 March 1879 in Ulm, Germany."],
)
result.value      # ≈ 0.5
```

### Key takeaways
- Faithfulness = supported claims / total claims in the response.
- It measures grounding in the retrieved context, not real-world truth.
- It needs user_input, response and retrieved_contexts.

## Core Metrics: Answer Relevancy, Context Precision and Context Recall
### Answer (Response) Relevancy
Does the response actually **address the question**? Ragas generates **N questions** (default 3) that the response would answer, embeds them, and averages their cosine similarity with the original question:
```text
AnswerRelevancy = (1/N) Σ cos( E(generated_question_i), E(user_input) )
```
Incomplete, evasive or off-topic answers get low scores. It does **not** judge factual correctness. Needs an LLM and an embedding model.

### Context Precision
Are the **relevant** chunks ranked **near the top** of the retrieved list?
```text
Context Precision@K = Σ_{k=1..K} ( Precision@k × v_k ) / (number of relevant chunks in the top K)
Precision@k = (relevant chunks in the top k) / k,     v_k = 1 if chunk k is relevant else 0
```
Worked example — relevance of retrieved chunks in order: [1, 0, 1]:
```text
P@1 = 1/1 = 1.0   (v1 = 1)
P@2 = 1/2 = 0.5   (v2 = 0, not counted)
P@3 = 2/3 ≈ 0.667 (v3 = 1)
CP = (1.0 + 0.667) / 2 ≈ 0.833
```
Relevance can be judged against a **reference answer** (`ContextPrecisionWithReference`) or against the **response** (`ContextPrecisionWithoutReference` / context utilisation).

### Context Recall
Did retrieval find **everything needed**? Ragas splits the **reference** (ground-truth) answer into claims and checks how many are attributable to the retrieved context:
```text
Context Recall = (reference claims supported by retrieved context) / (total claims in the reference)
```
It requires a `reference`. Low recall → retrieval is missing information (fix chunking, k, hybrid search, query rewriting).

### Which metric diagnoses what
| Low score | Indicates |
|---|---|
| Faithfulness | the generator hallucinates beyond the context |
| Answer relevancy | the answer drifts from the question or is incomplete |
| Context precision | noisy ranking — irrelevant chunks on top (add reranking) |
| Context recall | retrieval misses needed information |

### Key takeaways
- Answer relevancy = mean cosine between the question and questions generated from the answer.
- Context precision rewards relevant chunks ranked early; context recall checks coverage of the reference.
- Precision/recall diagnose retrieval; faithfulness/relevancy diagnose generation.

## More Ragas Metrics
### RAG and retrieval
| Metric | Measures | Formula / idea |
|---|---|---|
| Context Entities Recall | entity coverage | entities in (context ∩ reference) / entities in reference |
| Noise Sensitivity | how often the system makes errors due to irrelevant context | incorrect claims / total claims (**lower is better**) |
| Context Relevance (NVIDIA) | relevance of contexts to the question | LLM-judged rating |
| Response Groundedness (NVIDIA) | grounding of the response in context | LLM-judged rating |
| Answer Accuracy (NVIDIA) | agreement with the reference | LLM-judged rating |

### Answer correctness against a reference
| Metric | Idea |
|---|---|
| Factual Correctness | claim-level precision / recall / **F1** between response and reference |
| Semantic Similarity | cosine similarity of response and reference embeddings |
| Answer Correctness (legacy) | weighted mix of factual and semantic similarity |

Factual correctness uses: TP = response claims supported by the reference, FP = response claims not in the reference, FN = reference claims missing from the response:
```text
precision = TP / (TP + FP)      recall = TP / (TP + FN)      F1 = 2PR / (P + R)
```

### Traditional (non-LLM) metrics
BLEU, ROUGE, chrF, exact match, string presence, non-LLM string similarity (Levenshtein, Jaro-Winkler) — cheap and deterministic, useful for short or structured answers.

### Agent and tool-use metrics
| Metric | Measures |
|---|---|
| Tool Call Accuracy | did the agent call the expected tools with the expected arguments (in order)? |
| Tool Call F1 | precision/recall of tool calls |
| Agent Goal Accuracy | did the agent achieve the user's goal (with or without a reference)? |
| Topic Adherence | did the agent stay within allowed topics? |

### SQL, summaries and rubrics
SQL query equivalence, DataCompy score (compare result tables), Summarization score, and rubric-based scoring.

### Key takeaways
- Noise sensitivity is lower-is-better; most other metrics are higher-is-better.
- Factual correctness computes claim-level precision, recall and F1 against a reference.
- Agent metrics evaluate tool calls, goal completion and topic adherence.

## Data Structures: Samples and Datasets
### Field names (current Ragas)
| Field | Meaning |
|---|---|
| user_input | the question / user query |
| retrieved_contexts | list of retrieved chunk strings |
| response | the system's answer |
| reference | the ground-truth answer |
| reference_contexts | ground-truth relevant contexts |
| multi_responses | several responses (for some metrics) |

(Older v0.1 names were `question`, `contexts`, `answer`, `ground_truth`.)

### SingleTurnSample and EvaluationDataset
```python
from ragas import SingleTurnSample, EvaluationDataset

sample = SingleTurnSample(
    user_input="What is the refund window?",
    retrieved_contexts=["Refunds are accepted within 30 days of delivery."],
    response="You can request a refund within 30 days of delivery.",
    reference="30 days from delivery.",
)
dataset = EvaluationDataset(samples=[sample])
df = dataset.to_pandas()
```

### MultiTurnSample
For chatbots and agents, a `MultiTurnSample` holds a list of messages (`HumanMessage`, `AIMessage` with `tool_calls`, `ToolMessage` from `ragas.messages`) plus optional `reference` and `reference_tool_calls` — used by agent metrics like Tool Call Accuracy and Agent Goal Accuracy.

### Which metrics need what
| Metric | user_input | retrieved_contexts | response | reference |
|---|---|---|---|---|
| Faithfulness | ✅ | ✅ | ✅ | — |
| Answer Relevancy | ✅ | — | ✅ | — |
| Context Precision (with reference) | ✅ | ✅ | — | ✅ |
| Context Recall | ✅ | ✅ | — | ✅ |
| Factual Correctness | — | — | ✅ | ✅ |

Metrics that need **no reference** (faithfulness, answer relevancy) can run on production traffic; reference-based metrics need a labelled test set.

### Key takeaways
- Current field names: user_input, retrieved_contexts, response, reference.
- `SingleTurnSample` for Q&A; `MultiTurnSample` for conversations and tool calls.
- Reference-free metrics can monitor live traffic; reference-based ones need labelled data.

## The Ragas v0.4 API: Collections, llm_factory and Experiments
Ragas' API evolved quickly: v0.2 introduced `SingleTurnSample`/`EvaluationDataset`; v0.3.9 introduced the **collections** metrics; **v0.4** made collections the default and moved from `evaluate()` to an **experiment-based** workflow (legacy `ragas.metrics` imports still work with deprecation warnings).

### Setting up LLMs and embeddings
```python
from openai import AsyncOpenAI
from ragas.llms import llm_factory
from ragas.embeddings import OpenAIEmbeddings

client = AsyncOpenAI()
llm = llm_factory("gpt-5.4-mini", client=client)               # judge LLM
emb = OpenAIEmbeddings(client=client, model="text-embedding-3-small")
```

### Scoring with collection metrics
```python
from ragas.metrics.collections import Faithfulness, AnswerRelevancy, ContextPrecision, ContextRecall

faith = Faithfulness(llm=llm)
relevancy = AnswerRelevancy(llm=llm, embeddings=emb)
score = await faith.ascore(user_input=q, response=answer, retrieved_contexts=contexts)
print(score.value, score.reason)
```

### Experiments
```python
from ragas import Dataset, experiment

dataset = Dataset(name="faq_eval", backend="local/csv", root_dir="./evals")
dataset.append({"question": "Refund window?", "reference": "30 days"})
dataset.save()

@experiment()
async def rag_experiment(row):
    answer, contexts = await my_rag_app(row["question"])
    f = await faith.ascore(user_input=row["question"], response=answer, retrieved_contexts=contexts)
    return {**row, "response": answer, "faithfulness": f.value}

results = await rag_experiment.arun(dataset)     # results are saved per run for comparison
```
An experiment = (your app version) × (dataset) × (metrics), stored so you can compare runs — e.g. "hybrid retriever + reranker" vs "dense only".

### Legacy evaluate()
```python
from ragas import evaluate
result = evaluate(dataset=eval_dataset, metrics=[...], llm=..., embeddings=...)
result.to_pandas()
```
Still common in older tutorials; prefer experiments for new code.

### Key takeaways
- v0.4: metrics from `ragas.metrics.collections`, LLMs from `llm_factory`, `ascore()` returns a result with `.value`.
- `@experiment()` + `Dataset` replaces `evaluate()` for tracked, comparable runs.
- Older `ragas.metrics` imports still work but are deprecated.

## Synthetic Test Set Generation
Labelling hundreds of question/answer/context triples by hand is slow. Ragas can **generate** test data from your documents.

### How it works
1. Build a **knowledge graph** from documents: split into nodes, then apply **transforms** (extract headlines, summaries, entities, keyphrases, embeddings) and connect related nodes (e.g. by similarity or shared entities).
2. Create **personas** (who asks) and **scenarios**.
3. Use **query synthesizers** to create diverse questions with reference answers and reference contexts.

### Query types
| Synthesizer | Produces |
|---|---|
| SingleHopSpecificQuerySynthesizer | questions answerable from one chunk, about specific facts |
| MultiHopAbstractQuerySynthesizer | questions needing several chunks and abstract reasoning |
| MultiHopSpecificQuerySynthesizer | multi-chunk questions about specific facts |

```python
from ragas.testset import TestsetGenerator

generator = TestsetGenerator(llm=generator_llm, embedding_model=generator_embeddings)
testset = generator.generate_with_langchain_docs(docs, testset_size=50)
testset.to_pandas()[["user_input", "reference", "reference_contexts"]]
```

### Best practices
- **Review** generated questions — remove ambiguous or unrealistic ones.
- Mix synthetic data with **real user questions** from logs.
- Keep the test set **fixed** (versioned) so runs are comparable.
- Balance query types to reflect production traffic.

### Key takeaways
- Ragas builds a knowledge graph of your docs and synthesises single-hop and multi-hop questions.
- Generated samples include user_input, reference and reference_contexts.
- Review synthetic data and combine it with real queries.

## Custom Metrics, Judges and Integrations
### Aspect critics and rubrics
```python
from ragas.metrics import AspectCritic, RubricsScore     # legacy-style classes

conciseness = AspectCritic(
    name="conciseness",
    definition="Is the response concise and free of unnecessary detail?",
    llm=evaluator_llm,
)                         # binary verdict: 1 or 0

rubric = {
    "score1_description": "Incorrect or irrelevant",
    "score3_description": "Partially correct",
    "score5_description": "Fully correct and complete",
}
quality = RubricsScore(rubrics=rubric, llm=evaluator_llm)    # 1–5 scale
```
Newer versions also offer decorator-style **DiscreteMetric** (labels like pass/fail), **NumericMetric** and **RankingMetric** for quick custom metrics.

### Making LLM judges reliable
- Use a **strong judge model** (often stronger than the app model) and low temperature.
- Write **specific criteria**; prefer binary or small discrete scales over 1–10.
- **Align** the judge with human labels on a sample; measure agreement.
- Beware biases: position, verbosity and self-preference.
- Cache judge calls and track judge cost (each metric may make several LLM calls per sample).

### Integrations
- **LangChain / LlamaIndex**: wrap their LLMs/embeddings; evaluate chains and query engines.
- **Langfuse / LangSmith**: push Ragas scores onto traces; run evals on production samples.
- **CI**: run a small eval set on every pull request; fail the build below thresholds.

### Interpreting results
- Compare **distributions** and **per-sample** failures, not just means.
- Use metric pairs to locate problems: low context recall + high faithfulness → the generator is honest but retrieval misses info.
- Confirm big decisions with **human review** on a subset.

### Key takeaways
- AspectCritic gives binary verdicts; RubricsScore gives rubric-based 1–5 scores; DiscreteMetric/NumericMetric for custom metrics.
- Calibrate LLM judges against humans and watch for judge biases.
- Push scores to tracing tools and gate releases in CI.
