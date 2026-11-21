---
id: llm_models
title: LLM Models
short: Transformers, tokens, training, decoding, model families, reasoning, MoE, quantization, fine-tuning
emoji: 🧠
color: #283593
---
## The Transformer Architecture
Almost every modern LLM is a **decoder-only Transformer** (Vaswani et al., "Attention Is All You Need", 2017). It predicts the **next token** given all previous tokens.

### Building blocks
| Component | Role |
|---|---|
| Token embeddings | map token ids to vectors |
| Positional information | tells the model token order (sinusoidal, learned, or **RoPE** rotary embeddings in most modern LLMs) |
| Self-attention | each token mixes information from relevant earlier tokens |
| Multi-head attention | several attention "heads" learn different relationships in parallel |
| Feed-forward network (MLP) | per-token non-linear transformation (often SwiGLU) |
| Residual connections + normalisation | stable deep training (pre-norm, RMSNorm) |
| LM head + softmax | turns the final vector into a probability for every vocabulary token |

### Scaled dot-product attention
```text
Attention(Q, K, V) = softmax( Q·Kᵀ / sqrt(d_k) ) · V
```
- **Q** (queries), **K** (keys), **V** (values) are linear projections of the token vectors.
- Dividing by `sqrt(d_k)` keeps logits from growing too large.
- A **causal mask** stops tokens from attending to future positions.
- Cost grows **quadratically** with sequence length (n² attention scores) — the core reason long context is expensive.

### Encoder, decoder, encoder-decoder
| Type | Examples | Typical use |
|---|---|---|
| Encoder-only | BERT, RoBERTa | classification, embeddings |
| Decoder-only | GPT, Claude, Llama, Gemini, Qwen, Mistral | generation, chat, agents |
| Encoder-decoder | T5, BART | translation, summarisation |

### Efficiency variants
- **Multi-Query / Grouped-Query Attention (MQA/GQA)** share keys/values across heads to shrink the KV cache.
- **FlashAttention** computes exact attention with fewer memory reads.
- **Sliding-window / sparse attention** and **Multi-head Latent Attention (MLA, DeepSeek)** reduce long-context cost.

### Key takeaways
- LLMs are decoder-only Transformers predicting the next token.
- Attention = softmax(QKᵀ/√d_k)·V with a causal mask; cost is quadratic in length.
- RoPE, GQA and FlashAttention are standard modern ingredients.

## Tokens and Tokenization
Models read **tokens**, not characters or words. A tokenizer splits text into sub-word units from a fixed **vocabulary** (typically 32k–256k entries).

### Algorithms
| Algorithm | Used by |
|---|---|
| Byte-Pair Encoding (BPE), byte-level | GPT family (tiktoken: cl100k_base, o200k_base), Llama 3+ |
| WordPiece | BERT |
| Unigram / SentencePiece | T5, Gemma, many multilingual models |

BPE starts from characters/bytes and repeatedly **merges the most frequent adjacent pair** into a new token until the vocabulary size is reached.

```python
import tiktoken
enc = tiktoken.get_encoding("o200k_base")
ids = enc.encode("Tokenization matters!")
len(ids), [enc.decode([i]) for i in ids]
```

### Rules of thumb
- English: **~4 characters ≈ 1 token**, **~0.75 words ≈ 1 token** (100 tokens ≈ 75 words).
- Code, numbers, and many non-Latin scripts use **more tokens** per word.
- Leading spaces are usually part of the token (" hello" ≠ "hello").

### Why tokens matter
- **Pricing** is per token (input and output often priced differently; cached input cheaper).
- **Context window** limits are in tokens (prompt + output).
- **Latency** grows with output tokens (generated one at a time).
- Tokenization explains odd failures — counting letters, arithmetic on long numbers, rare words.

### Cost arithmetic
```text
cost = input_tokens/1e6 × input_price + output_tokens/1e6 × output_price
e.g. 12,000 in × $3/M + 800 out × $15/M = $0.036 + $0.012 = $0.048
```

### Key takeaways
- LLMs operate on sub-word tokens produced by BPE/WordPiece/Unigram tokenizers.
- ~4 characters or ~0.75 words per token in English.
- Pricing, context limits and latency are all measured in tokens.

## How LLMs Are Trained
### 1. Pre-training
Self-supervised **next-token prediction** on trillions of tokens (web, books, code, papers). Objective: minimise cross-entropy loss. Produces a **base model** that continues text but doesn't follow instructions reliably.

**Scaling laws** (Kaplan 2020; Chinchilla/Hoffmann 2022) relate loss to parameters, data and compute; Chinchilla suggested ~20 training tokens per parameter for compute-optimal training — modern models train far beyond that to make inference cheaper.

### 2. Supervised fine-tuning (SFT) / instruction tuning
Train on curated (instruction, response) pairs and multi-turn chats so the model follows instructions and adopts a chat format.

### 3. Preference alignment
- **RLHF** (Reinforcement Learning from Human Feedback): train a **reward model** on human preference comparisons, then optimise the policy with RL (PPO) while a KL penalty keeps it close to the SFT model.
- **DPO** (Direct Preference Optimization, 2023): optimise directly on preference pairs (chosen vs rejected) without a separate reward model or RL loop — simpler and popular.
- **RLAIF / Constitutional AI**: AI feedback guided by written principles (Anthropic).

### 4. Reinforcement learning for reasoning
Models like OpenAI o1 (2024) and DeepSeek-R1 (Jan 2025) are trained with large-scale RL on **verifiable rewards** (math answers, unit tests) to produce long chains of thought. DeepSeek-R1 popularised **GRPO** (Group Relative Policy Optimization), which compares groups of sampled answers instead of using a value model.

### Post-training extras
Tool-use training, safety training, long-context extension, distillation from larger "teacher" models into smaller ones.

### Key takeaways
- Pre-training (next token) → SFT (instructions) → preference alignment (RLHF/DPO) → RL for reasoning.
- DPO removes the reward model; GRPO (DeepSeek) trains reasoning with group-relative rewards.
- Distillation transfers capability from big models to small ones.

## Decoding and Generation Parameters
At each step the model outputs **logits** for every token; decoding turns them into a choice.

### Temperature
`p_i = softmax(logit_i / T)`
- **T → 0**: nearly greedy, deterministic, focused.
- **T = 1**: the model's raw distribution.
- **T > 1**: flatter distribution, more random/creative.

### Truncation strategies
| Parameter | Effect |
|---|---|
| top-k | sample only from the k most likely tokens |
| top-p (nucleus) | sample from the smallest set whose cumulative probability ≥ p |
| min-p | keep tokens with probability ≥ min_p × top token probability |

Worked top-p example — probabilities sorted: A 0.50, B 0.25, C 0.15, D 0.10. With **top-p = 0.8**: cumulative 0.50 → 0.75 → 0.90 ≥ 0.8, so the nucleus is {A, B, C}.

### Other controls
- **max_tokens / max_output_tokens** — cap output length.
- **stop sequences** — end generation when a string appears.
- **frequency / presence penalties** — discourage repetition.
- **seed** — improves reproducibility (not guaranteed).
- **logprobs** — return token probabilities (useful for confidence and classification).
- Reasoning models expose **reasoning effort / thinking budget** instead of (or in addition to) temperature.

### Greedy vs sampling vs beam search
Greedy picks the argmax each step; beam search keeps several candidate sequences (common in translation, rarely used for chat); sampling with temperature/top-p is standard for chat models.

### Recommended defaults
| Use case | Settings |
|---|---|
| Extraction, classification, RAG answers | temperature 0–0.3 |
| General chat | 0.5–0.8 |
| Brainstorming, fiction | 0.8–1.2 |

### Key takeaways
- Temperature divides logits before softmax: low = focused, high = diverse.
- top-k keeps k tokens; top-p keeps the smallest set with cumulative probability ≥ p.
- Use low temperature for factual and structured tasks.

## Context Windows, KV Cache and Inference Performance
### Context window
The maximum number of tokens (input + output) the model can attend to at once. Frontier models in 2026 commonly offer **~1M-token** windows; many open models offer 128k+.
- Long context costs more and is slower (prefill grows with input length).
- **Lost in the middle**: retrieval of facts placed in the middle of long contexts is less reliable — benchmarks like needle-in-a-haystack and RULER measure this.

### Prefill vs decode
1. **Prefill**: process all input tokens in parallel → determines **time to first token (TTFT)**.
2. **Decode**: generate output tokens one at a time → determines **tokens per second** / inter-token latency.

### KV cache
During decoding the model stores each past token's **keys and values** for every layer so it doesn't recompute them.
```text
KV bytes per token = 2 (K and V) × layers × kv_heads × head_dim × bytes_per_value
```
Example: 32 layers, 8 KV heads, head_dim 128, fp16 (2 bytes): 2×32×8×128×2 = **131,072 bytes ≈ 128 KiB per token** → 32k tokens ≈ 4 GiB. GQA (fewer KV heads) shrinks this.

### Prompt caching
Providers cache the KV state of a repeated **prefix** (system prompt, documents, tools). Cached input tokens are billed at a steep discount and reduce latency — put stable content first, variable content last.

### Serving optimisations
- **Continuous batching** and **PagedAttention** (vLLM) pack many requests efficiently.
- **Speculative decoding**: a small draft model proposes tokens that the big model verifies in parallel.
- **Quantization** (next lessons) and tensor/pipeline parallelism for big models.

### Key takeaways
- Context windows are measured in tokens; long inputs raise cost, latency and lost-in-the-middle risk.
- Prefill sets TTFT; decode sets tokens/second; the KV cache stores past keys/values.
- Prompt caching rewards stable prefixes; vLLM-style batching boosts throughput.

## The Model Landscape
Model names change monthly — learn the **families and trade-offs**, then check current docs, pricing and leaderboards (LMArena, Artificial Analysis, SWE-bench, GPQA, Humanity's Last Exam).

### Closed / proprietary families (API)
| Provider | Family | Notes |
|---|---|---|
| OpenAI | GPT series (GPT-4o → GPT-5.x → GPT-6.x in 2026) and o-series reasoning models | broad tooling: Responses API, Agents SDK, structured outputs |
| Anthropic | Claude (Haiku / Sonnet / Opus tiers; newer Fable/Mythos tiers in 2026) | strong coding/agentic use, long context, prompt caching, MCP originator |
| Google | Gemini (Flash / Pro tiers; Gemini 3 → Gemini 4 in late 2026) | natively multimodal, very long context, Vertex AI |
| xAI | Grok | real-time X/web data |
| Mistral AI | Mistral Large / Medium; open models too | European provider |

### Open-weight families
| Provider | Family | Notes |
|---|---|---|
| Meta | Llama (3.x, 4 with MoE) | the open-weight ecosystem catalyst |
| Alibaba | Qwen (2.5, 3, 3.x, 4) | strong multilingual/coding, many sizes, Apache-2.0 for many models |
| DeepSeek | V3 (MoE), R1 (reasoning) | R1 (Jan 2025) showed open reasoning models with RL; MIT licence |
| Moonshot | Kimi K2 / K3 | large open MoE models, agentic focus |
| Google | Gemma | small open models from Gemini research |
| Microsoft | Phi | small language models (SLMs) |
| Mistral | Mistral / Mixtral / Ministral | early open MoE (Mixtral 8x7B) |
| Zhipu (Z.ai) | GLM | open agentic models |
| OpenAI | gpt-oss (2025) | open-weight reasoning models |

"**Open weights**" means downloadable parameters; it is not always "open source" (training data/code may be closed, licences may restrict use).

### Choosing a model
1. **Task fit** — coding, reasoning, extraction, chat, multimodal, multilingual.
2. **Quality on your eval set**.
3. **Cost** per million tokens and **latency** (TTFT, tokens/s).
4. **Context window** and features (tools, structured output, vision, caching).
5. **Deployment**: API vs self-hosted (privacy, residency, offline).
6. **Licence** for open models.

A common pattern: a **router** sends easy requests to a small, cheap model and hard ones to a frontier model.

### Key takeaways
- Know the families: GPT, Claude, Gemini (closed); Llama, Qwen, DeepSeek, Gemma, Mistral, Kimi, GLM (open weights).
- Open weights ≠ fully open source; check licences.
- Choose with your own evals, then balance cost, latency and features.

## Reasoning Models and Mixture of Experts
### Reasoning ("thinking") models
Reasoning models generate a long internal **chain of thought** before answering, trading **test-time compute** for accuracy on math, coding, science and planning.
- Examples: OpenAI o-series and GPT-5 "thinking" modes, Claude extended thinking, Gemini thinking models, DeepSeek-R1, Qwen3 thinking modes.
- Controls: reasoning effort (low/medium/high) or a thinking-token budget.
- Thinking tokens are usually **billed as output** even when hidden.
- Prompting tips: state the goal and constraints clearly; you rarely need "think step by step" with them.

### Test-time compute scaling
Quality can improve by spending more compute at inference: longer reasoning, multiple samples + voting (self-consistency), or search with verifiers.

### Mixture of Experts (MoE)
An MoE layer replaces one big feed-forward block with many **expert** FFNs and a **router** that sends each token to the top-k experts.
- **Total parameters** (all experts) ≫ **active parameters** per token.
- Example: Mixtral 8x7B has ~47B total parameters but only ~13B active per token (2 of 8 experts).
- DeepSeek-V3: 671B total, ~37B active. Llama 4 and Qwen3 also ship MoE variants.
- Benefit: more knowledge capacity at lower compute per token. Cost: **all** experts must fit in memory.

### Multimodality
Modern frontier models are natively multimodal: text + images (and often audio/video) in, text (sometimes images/audio) out. Vision-language models power document understanding (charts, screenshots, ColPali-style RAG).

### Key takeaways
- Reasoning models spend test-time compute on hidden chains of thought; thinking tokens cost money.
- MoE: many experts, a router picks top-k per token — total ≫ active parameters.
- Memory must hold all MoE parameters even though compute uses only active ones.

## Running Models Locally: Quantization and Serving
### Memory for weights
`memory ≈ parameters × bytes_per_parameter` (plus KV cache and overhead)
| Precision | Bytes/param | 7B model | 70B model |
|---|---|---|---|
| FP32 | 4 | 28 GB | 280 GB |
| FP16 / BF16 | 2 | 14 GB | 140 GB |
| INT8 / FP8 | 1 | 7 GB | 70 GB |
| 4-bit (INT4/NF4) | 0.5 | 3.5 GB | 35 GB |

### Quantization methods
| Method | Notes |
|---|---|
| GGUF (llama.cpp) | file format with k-quants like Q4_K_M, Q8_0 — CPU/Apple Silicon friendly |
| GPTQ | post-training, layer-wise, GPU inference |
| AWQ | activation-aware weight quantization, protects salient weights |
| bitsandbytes (NF4/INT8) | load-time quantization in Transformers; used by QLoRA |
| FP8 | supported natively on recent GPUs (H100+), small quality loss |

Lower bits → smaller and faster, but some quality loss; 4-bit is the popular sweet spot for local use.

### Serving tools
| Tool | Best for |
|---|---|
| Ollama | easiest local runs (`ollama run llama3.2`), OpenAI-compatible API |
| llama.cpp | CPU/Metal/CUDA inference of GGUF models |
| LM Studio | desktop GUI for local models |
| vLLM | high-throughput GPU serving (PagedAttention, continuous batching), OpenAI-compatible server |
| SGLang | fast serving with structured generation and prefix caching |
| Hugging Face TGI | production text generation server |
| TensorRT-LLM / NVIDIA NIM | optimised NVIDIA deployment |

```bash
ollama pull qwen3:8b
ollama run qwen3:8b "Explain RAG in one line"

pip install vllm
vllm serve Qwen/Qwen3-8B --max-model-len 32768     # OpenAI-compatible at :8000/v1
```

### Key takeaways
- Weight memory ≈ params × bytes per param (FP16 = 2, INT8 = 1, 4-bit = 0.5).
- GGUF/llama.cpp/Ollama for local; vLLM/SGLang/TGI for GPU servers.
- 4-bit quantization makes 7–8B models run on laptops with modest quality loss.

## Prompting, Structured Output and Fine-Tuning
### Prompt engineering essentials
- Clear **role and goal**, explicit **constraints** and an **output format**.
- **Few-shot examples** for tricky formats.
- **Delimiters** (XML tags, Markdown) between instructions and data.
- Ask for reasoning when useful (or use a reasoning model).
- Put long documents **before** the question; keep stable content first for prompt caching.

### Structured output and function calling
Most APIs can **constrain** outputs to a JSON Schema (OpenAI Structured Outputs, Anthropic tool use, Gemini response schemas). Open-source options: Outlines, XGrammar, llama.cpp grammars, Instructor (validation + retries). Function calling is structured output for choosing tools and arguments.

### When to fine-tune
| Goal | Prefer |
|---|---|
| Inject fresh/private facts | RAG |
| Consistent style, tone or format | fine-tuning (or good prompts) |
| Lower latency/cost with a smaller model | fine-tuning / distillation |
| Domain jargon, classification | fine-tuning |
| Better tool use in a niche | fine-tuning on trajectories |

### Parameter-efficient fine-tuning (PEFT)
- **LoRA** (Low-Rank Adaptation): freeze the base weights W and learn a low-rank update `ΔW = B·A` (rank r ≪ d). Trainable parameters per adapted matrix drop from d×k to r×(d+k).
- **QLoRA**: LoRA on top of a **4-bit quantized** base model (NF4) — fine-tune large models on a single GPU.
- Adapters can be merged into the base or swapped per task.
```python
from peft import LoraConfig, get_peft_model
config = LoraConfig(r=16, lora_alpha=32, lora_dropout=0.05, target_modules=["q_proj", "v_proj"])
model = get_peft_model(base_model, config)
model.print_trainable_parameters()     # typically well under 1% of all parameters
```
Tooling: Hugging Face TRL (SFT, DPO, GRPO trainers), PEFT, Unsloth, Axolotl, and provider fine-tuning APIs.

LoRA math example: adapting a 4096×4096 matrix with r = 8 trains 8 × (4096 + 4096) = **65,536** parameters instead of 16,777,216.

### Key takeaways
- Good prompts: role, constraints, format, examples, delimiters; stable prefixes first.
- Use schema-constrained structured outputs for reliable JSON.
- LoRA trains low-rank updates; QLoRA does it on a 4-bit base; RAG is better for facts.
