---
id: ai_frameworks
title: AI Frameworks Landscape
short: PyTorch to vLLM, Hugging Face, Haystack, DSPy, Semantic Kernel, guardrails, gateways and protocols
emoji: 🧰
color: #455A64
---
## The Modern AI Stack: A Map
Building AI products means picking tools across several layers. Knowing the layers helps you choose and combine frameworks instead of comparing apples with oranges.

| Layer | Purpose | Examples |
|---|---|---|
| Deep learning frameworks | define and train neural networks | PyTorch, TensorFlow/Keras, JAX |
| Model hubs & libraries | pretrained models, datasets, fine-tuning | Hugging Face Transformers, Datasets, PEFT, TRL |
| Inference & serving | run models fast | vLLM, SGLang, TGI, llama.cpp, Ollama, TensorRT-LLM |
| Model APIs & gateways | access hosted models uniformly | OpenAI, Anthropic, Gemini APIs; LiteLLM, OpenRouter |
| LLM application frameworks | prompts, chains, RAG, tools | LangChain, LlamaIndex, Haystack, Semantic Kernel, DSPy |
| Agent frameworks | agent loops, multi-agent | LangGraph, CrewAI, OpenAI Agents SDK, Google ADK, Microsoft Agent Framework, Pydantic AI, smolagents |
| Retrieval infrastructure | vectors & search | FAISS, Chroma, pgvector, Qdrant, Weaviate, Milvus, Pinecone, Elasticsearch |
| Structured output & guardrails | reliable, safe outputs | Instructor, Outlines, Guardrails AI, NeMo Guardrails, Llama Guard |
| Evaluation & observability | measure & monitor | LangSmith, Langfuse, Arize Phoenix, RAGAS, DeepEval, promptfoo, TruLens |
| Protocols | interoperability | MCP (agent ↔ tools), A2A (agent ↔ agent) |
| App/UI layer | serve to users | FastAPI, Streamlit, Gradio, Chainlit, Next.js + Vercel AI SDK |

### Key takeaways
- Frameworks live at different layers; most products combine several.
- Compare tools within a layer (vLLM vs TGI), not across layers (vLLM vs LangChain).
- Protocols (MCP, A2A) connect components across vendors.

## Deep Learning Frameworks: PyTorch, TensorFlow and JAX
### PyTorch
Developed by Meta AI, now governed by the **PyTorch Foundation** (Linux Foundation). Dominant in research and LLMs.
- **Eager execution** (define-by-run) with dynamic graphs — feels like normal Python.
- Autograd, `torch.nn` modules, `torch.compile` for graph compilation and speedups (PyTorch 2.x).
- Distributed training: DDP, FSDP; ecosystem: torchvision, torchaudio, Lightning.
```python
import torch, torch.nn as nn
model = nn.Sequential(nn.Linear(768, 256), nn.ReLU(), nn.Linear(256, 2))
x = torch.randn(4, 768)
loss = nn.functional.cross_entropy(model(x), torch.tensor([0, 1, 1, 0]))
loss.backward()                      # autograd computes gradients
```

### TensorFlow and Keras
Google's framework; strong production tooling (TF Serving, TFLite / LiteRT for mobile, TF.js). **Keras 3** is multi-backend: the same Keras code runs on **TensorFlow, JAX or PyTorch**.

### JAX
Google's library for high-performance numerical computing: NumPy-like API plus composable transformations — `jit` (XLA compilation), `grad` (autodiff), `vmap` (vectorisation), `pmap`/sharding (parallelism). Used for large-scale training on TPUs (Gemini, Gemma research); neural-net libraries on top: Flax, Optax.
```python
import jax, jax.numpy as jnp
f = lambda x: jnp.sum(x ** 2)
jax.grad(f)(jnp.array([1.0, 2.0]))   # Array([2., 4.])
```

### ONNX
**ONNX** (Open Neural Network Exchange) is an open model format; **ONNX Runtime** runs exported models efficiently across hardware (CPU, GPU, mobile), common for deploying embedding models and classifiers.

### Key takeaways
- PyTorch dominates LLM research and training; `torch.compile` adds speed.
- Keras 3 runs on TensorFlow, JAX or PyTorch backends; TF excels at mobile/production tooling.
- JAX offers jit/grad/vmap on XLA; ONNX makes models portable.

## The Hugging Face Ecosystem
Hugging Face is the hub of open AI: **models, datasets and Spaces (demos)** plus libraries.

| Library | Purpose |
|---|---|
| transformers | thousands of pretrained models with a common API (`AutoModel`, `pipeline`) |
| datasets | load/process datasets efficiently (Arrow-backed, streaming) |
| tokenizers | fast Rust tokenizers |
| PEFT | parameter-efficient fine-tuning (LoRA, QLoRA, prefix tuning) |
| TRL | post-training: SFTTrainer, DPOTrainer, GRPOTrainer, reward modelling |
| accelerate | run the same training code on CPU/GPU/multi-GPU/TPU |
| sentence-transformers | embeddings and rerankers (now maintained by HF) |
| diffusers | image/video diffusion models |
| smolagents | minimal code-acting agents |
| Text Generation Inference (TGI) / Text Embeddings Inference (TEI) | production servers for LLMs / embeddings |

```python
from transformers import pipeline
clf = pipeline("sentiment-analysis")
clf("I love building RAG systems!")      # [{'label': 'POSITIVE', 'score': 0.99...}]

from transformers import AutoTokenizer, AutoModelForCausalLM
tok = AutoTokenizer.from_pretrained("Qwen/Qwen3-0.6B")
model = AutoModelForCausalLM.from_pretrained("Qwen/Qwen3-0.6B", torch_dtype="auto")
```
Model cards document intended use, licence, training data and limitations; `safetensors` is the safe, fast weight format (no arbitrary code execution like pickle).

### Key takeaways
- transformers + datasets + tokenizers form the core; PEFT and TRL handle fine-tuning and alignment.
- `pipeline()` gives one-line inference for common tasks.
- Prefer safetensors weights and read model cards and licences.

## LLM Application Frameworks
### LangChain
Broadest integration ecosystem; v1 centres on `create_agent` + middleware, built on LangGraph. Covered in depth in its own topic.

### LlamaIndex
Data-centric: ingestion, indexing, advanced retrieval, LlamaParse, Workflows and agents. Covered in its own topic.

### Haystack (deepset)
Production-oriented framework built around **Pipelines** of **Components** (retrievers, embedders, generators, routers) connected explicitly as a directed graph (loops allowed). Haystack 2.x components are simple Python classes with `@component` and typed inputs/outputs; it also supports agents and serialises pipelines to YAML.
```python
from haystack import Pipeline
from haystack.components.builders import PromptBuilder
pipe = Pipeline()
pipe.add_component("retriever", retriever)
pipe.add_component("prompt", PromptBuilder(template="Context: {% for d in documents %}{{d.content}}{% endfor %} Q: {{question}}"))
pipe.add_component("llm", generator)
pipe.connect("retriever.documents", "prompt.documents")
pipe.connect("prompt", "llm")
```

### Semantic Kernel (Microsoft)
SDK for .NET, Python and Java that integrates LLMs into enterprise apps through **plugins** (functions the model can call), planners and memory connectors. Its agent capabilities have converged into the **Microsoft Agent Framework**.

### DSPy (Stanford)
"**Programming — not prompting — language models.**" You declare **signatures** (`"question -> answer"`) and **modules** (`dspy.Predict`, `dspy.ChainOfThought`, `dspy.ReAct`); **optimisers** (e.g. MIPROv2, BootstrapFewShot, GEPA) then automatically tune prompts and few-shot examples against a metric.
```python
import dspy
dspy.configure(lm=dspy.LM("openai/gpt-5.4-mini"))
qa = dspy.ChainOfThought("question -> answer")
qa(question="What is hybrid search?").answer
```

### Comparison
| Framework | Strength |
|---|---|
| LangChain | integrations, agents with middleware |
| LlamaIndex | data ingestion, indexing, retrieval |
| Haystack | explicit, production pipelines |
| Semantic Kernel | enterprise .NET/Java integration |
| DSPy | automatic prompt/program optimisation |

### Key takeaways
- Haystack = explicit pipelines of typed components.
- Semantic Kernel = enterprise plugins/planners, converging into Microsoft Agent Framework.
- DSPy compiles declarative signatures/modules into optimised prompts using a metric.

## Agent Frameworks Compared
| Framework | Maintainer | Core abstraction | Notable features |
|---|---|---|---|
| LangGraph | LangChain | state graph (nodes/edges) | durable execution, interrupts, persistence, streaming |
| LangChain create_agent | LangChain | agent loop + middleware | prebuilt, customisable via hooks |
| CrewAI | CrewAI Inc. | crews of role-based agents, tasks; Flows | sequential/hierarchical process, YAML config |
| OpenAI Agents SDK | OpenAI | Agent + Runner | handoffs, guardrails, sessions, tracing |
| Google ADK | Google | LlmAgent + workflow agents | Sequential/Parallel/Loop agents, `adk web`, A2A |
| Microsoft Agent Framework | Microsoft | agents + graph workflows | successor to AutoGen & Semantic Kernel; .NET + Python |
| LlamaIndex AgentWorkflow | LlamaIndex | event-driven workflows | handoffs, data tools |
| Pydantic AI | Pydantic | typed Agent | dependency injection, validated outputs |
| smolagents | Hugging Face | CodeAgent | actions as Python code |
| Claude Agent SDK | Anthropic | agent harness | tools, subagents, skills, hooks, MCP |
| Strands Agents | AWS | model-driven loop | AWS/Bedrock integration |
| Mastra | Mastra | TypeScript agents/workflows | TS ecosystem |

### Selection criteria
1. **Control vs convenience** — graph/workflow frameworks (LangGraph, MAF workflows, ADK workflow agents) give precise control; high-level ones (CrewAI crews, Agents SDK) are faster to start.
2. **Durability & HITL** — need pause/resume over hours? Prefer frameworks with checkpointing.
3. **Ecosystem** — cloud (Azure → MAF, GCP → ADK, AWS → Strands), model vendor, language (.NET, TS, Java).
4. **Observability** — OpenTelemetry support, tracing integrations.
5. **Team skills & maturity** — release stability, docs, community.

### Key takeaways
- Each agent framework centres on a different abstraction: graphs, crews, runners, workflow agents, code actions.
- Choose by control needs, durability, ecosystem, observability and team skills.
- Many teams mix frameworks via MCP and A2A.

## Inference Engines and Model Gateways
### Self-hosted inference engines
| Engine | Highlights |
|---|---|
| vLLM | PagedAttention, continuous batching, OpenAI-compatible server, broad model support |
| SGLang | RadixAttention prefix caching, fast structured generation, strong for agents |
| TGI (Hugging Face) | production server, HF integration |
| TensorRT-LLM / NVIDIA NIM / Triton | maximum NVIDIA GPU performance |
| llama.cpp | C/C++ inference, GGUF quantization, CPU/Apple Silicon |
| Ollama | developer-friendly local model runner built on llama.cpp |
| LM Studio | desktop GUI for local models |
| MLX (Apple) | array framework for Apple Silicon |

### Managed inference
Together AI, Fireworks, Groq (LPU hardware), Cerebras, Replicate, Baseten, Modal, AWS Bedrock, Azure AI Foundry, Google Vertex AI — host open and proprietary models behind APIs.

### Gateways and routers
- **LiteLLM** — Python SDK + proxy that exposes 100+ providers through an **OpenAI-compatible** interface, with budgets, fallbacks, load balancing and logging.
- **OpenRouter** — hosted router/marketplace for many models behind one API key.
- Cloud API gateways (Portkey, Kong AI Gateway, Cloudflare AI Gateway) add caching, rate limits and observability.
```python
from litellm import completion
completion(model="anthropic/claude-sonnet-5", messages=[{"role": "user", "content": "hi"}])
completion(model="ollama/llama3.2", messages=[{"role": "user", "content": "hi"}])
```

### The OpenAI-compatible API as a lingua franca
vLLM, SGLang, Ollama, LiteLLM and many providers expose `/v1/chat/completions`, so the same client code can target different backends by changing `base_url`.

### Key takeaways
- vLLM/SGLang/TGI/TensorRT-LLM for GPU serving; llama.cpp/Ollama/LM Studio for local.
- LiteLLM and OpenRouter unify many providers behind one OpenAI-style API.
- OpenAI-compatible endpoints let you swap backends by changing `base_url`.

## Structured Output and Guardrails
### Structured output libraries
| Tool | Approach |
|---|---|
| Native provider features | JSON-schema constrained decoding (OpenAI Structured Outputs, Gemini schemas, Anthropic tool use) |
| Instructor | Pydantic models + validation + automatic retries over many providers |
| Outlines | constrained generation (regex, JSON schema, grammars) for open models |
| XGrammar / llguidance | fast grammar-constrained decoding engines used inside servers |
| BAML | a DSL for typed LLM functions |
```python
import instructor
from pydantic import BaseModel
from openai import OpenAI

class Invoice(BaseModel):
    vendor: str
    total: float

client = instructor.from_openai(OpenAI())
inv = client.chat.completions.create(model="gpt-5.4-mini", response_model=Invoice,
                                     messages=[{"role": "user", "content": "ACME billed $1,250.50"}])
```

### Guardrails
| Tool | Focus |
|---|---|
| Guardrails AI | validators (PII, toxicity, regex, competitor mentions) on inputs/outputs |
| NVIDIA NeMo Guardrails | programmable rails (Colang) for dialogue flows, topics, jailbreak and fact checks |
| Llama Guard / ShieldGemma | safety-classifier models for prompts and responses |
| Prompt-injection detectors | e.g. Prompt Guard, Lakera |
| Provider moderation APIs | content classification |

### Defence in depth
Input validation → instruction hierarchy (system prompts) → retrieval/tool permission limits → output validation (schema + policy) → human approval for risky actions → monitoring.

### Key takeaways
- Use native constrained decoding or Instructor/Outlines for reliable structured output.
- Guardrails AI (validators), NeMo Guardrails (programmable rails) and Llama Guard (classifier) address safety.
- Layer defences; no single guardrail is sufficient.

## Evaluation and Observability Tools
| Tool | Type | Highlights |
|---|---|---|
| LangSmith | platform (LangChain) | tracing, datasets, evaluators, prompt hub, deployment |
| Langfuse | open-source platform | tracing, prompt management, evals, self-hostable |
| Arize Phoenix | open-source | OpenInference/OTel tracing, evals, embeddings analysis |
| Weights & Biases Weave | platform | traces + evaluations tied to experiments |
| RAGAS | library | RAG and agent metrics, test generation |
| DeepEval (Confident AI) | library | pytest-style LLM tests, G-Eval, RAG/agent metrics |
| promptfoo | CLI | prompt/model comparisons, red-teaming, CI |
| TruLens | library | feedback functions, RAG triad |
| MLflow | platform | experiment tracking, LLM tracing and evaluation |
| OpenLLMetry / OpenInference | OTel instrumentation | vendor-neutral traces |

### What to evaluate
- **Offline**: fixed datasets before release (accuracy, faithfulness, tool-call accuracy, cost, latency).
- **Online**: sampled production traces with LLM-as-judge, user feedback, guardrail triggers.
- **Red-teaming**: adversarial prompts, jailbreaks, prompt injection.

### OpenTelemetry for GenAI
The OpenTelemetry **GenAI semantic conventions** standardise span attributes for model calls (model name, token usage, etc.), so traces can flow to any backend — Langfuse, Phoenix, Datadog, Grafana.

### Key takeaways
- Platforms (LangSmith, Langfuse, Phoenix, Weave) trace and evaluate; libraries (RAGAS, DeepEval, TruLens) compute metrics.
- promptfoo targets CLI comparisons and red-teaming in CI.
- OpenTelemetry GenAI conventions make traces portable.

## Protocols and Choosing Your Stack
### Protocols
| Protocol | Connects | Key ideas |
|---|---|---|
| MCP (Model Context Protocol) | AI apps ↔ tools/data | hosts, clients, servers; tools/resources/prompts; JSON-RPC over stdio or Streamable HTTP |
| A2A (Agent2Agent) | agent ↔ agent | Agent Cards, tasks, messages, artifacts; JSON-RPC over HTTP |
| OpenAI-compatible API | apps ↔ model servers | de-facto standard chat/completions endpoints |
| OpenTelemetry GenAI | apps ↔ observability | standard trace attributes |
| AG-UI | agent ↔ frontend | event protocol for streaming agent state to UIs |

### Reference stacks
**Prototype RAG chatbot**
Python + LangChain or LlamaIndex + Chroma/pgvector + hosted LLM API + Streamlit/FastAPI + Langfuse.

**Production enterprise RAG**
Ingestion (LlamaParse/Docling/Unstructured) → hybrid search (Qdrant/Weaviate/Elasticsearch) + reranker → LangGraph or LlamaIndex Workflows → FastAPI service → LiteLLM gateway → Langfuse/LangSmith tracing → RAGAS/DeepEval in CI.

**Agentic automation**
LangGraph / Microsoft Agent Framework / Google ADK for orchestration, MCP servers for tools, human-in-the-loop approvals, durable checkpoints (Postgres), OpenTelemetry tracing.

**On-prem / private**
vLLM or SGLang serving open-weight models (Qwen, Llama, Mistral, gpt-oss) + open embedding models (BGE-M3, Qwen3-Embedding) via TEI + pgvector/Milvus + self-hosted Langfuse.

### Decision checklist
- What layer am I choosing for?
- What are the latency, cost, privacy and compliance constraints?
- How will I **evaluate** and **observe** it?
- Can I swap components later (standard APIs, protocols)?
- Is the project actively maintained with stable releases?

### Key takeaways
- MCP standardises tool access; A2A standardises agent collaboration; OpenAI-style APIs standardise model access.
- Assemble stacks per layer: ingestion, retrieval, orchestration, serving, gateway, observability, evaluation.
- Favour standards and swappable components to keep up with a fast-moving field.
