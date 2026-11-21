---
id: agentic_ai
title: Agentic AI & Frameworks
short: Agent loops, tools, memory, multi-agent patterns — CrewAI, OpenAI Agents SDK, ADK, MAF and more
emoji: 🤖
color: #C62828
---
## What Is an AI Agent?
An **AI agent** is a system where an LLM **decides the control flow**: it chooses actions (tool calls), observes results, and continues until a goal is met. A common definition: *agent = LLM + tools + a loop (+ memory and instructions)*.

### Workflows vs agents
Anthropic's "Building effective agents" (2024) draws a useful line:
- **Workflows** — LLMs and tools orchestrated through **predefined code paths** (prompt chaining, routing, parallelisation, orchestrator-workers, evaluator-optimizer).
- **Agents** — LLMs **dynamically direct** their own processes and tool usage.

Use the simplest thing that works: a single LLM call → a workflow → an agent, adding autonomy only when needed.

### The agent loop
```text
while not done and steps < max_steps:
    response = llm(messages, tools)
    if response.tool_calls:
        for call in response.tool_calls:
            result = run_tool(call.name, call.args)
            messages.append(tool_result(call.id, result))
    else:
        done = True           # final answer
```

### Levels of autonomy
| Level | Example |
|---|---|
| 0 — Single call | summarise this text |
| 1 — Router | choose which prompt/tool to use |
| 2 — Tool caller | call tools in a loop until done (ReAct) |
| 3 — Multi-step planner | plan, execute subtasks, replan |
| 4 — Multi-agent system | specialised agents collaborating |

### Core components
- **Model** — the reasoning engine.
- **Instructions** — system prompt / role / goal.
- **Tools** — functions, APIs, retrieval, code execution, MCP servers.
- **Memory** — short-term (conversation/state) and long-term (facts, preferences).
- **Orchestration** — loop, graph, crew or workflow.
- **Guardrails** — input/output validation, permissions, human approval.

### Key takeaways
- Agents let the LLM choose the next action; workflows follow predefined paths.
- Agent = model + instructions + tools + loop (+ memory, guardrails).
- Start simple; add autonomy only where fixed workflows fail.

## Reasoning Patterns: ReAct, Plan-and-Execute, Reflection
### ReAct (Reason + Act)
Yao et al. (2022) interleave **Thought → Action → Observation** steps:
```text
Thought: I need the population of the capital of Japan.
Action: search("capital of Japan")
Observation: Tokyo
Thought: Now find Tokyo's population.
Action: search("Tokyo population 2025")
Observation: ~14 million (city proper)
Thought: I can answer.
Final Answer: About 14 million people live in Tokyo.
```
Modern models do this natively through **tool calling** — the "thought" may be hidden reasoning, and actions are structured `tool_calls`.

### Plan-and-Execute
1. A **planner** LLM writes a multi-step plan.
2. An **executor** (often a smaller model or a ReAct agent) runs each step.
3. A **replanner** updates the plan with results.
Pros: fewer expensive calls, explicit structure. Variants: ReWOO (plan with variable placeholders, execute tools, then solve), LLMCompiler (parallel task DAG).

### Reflection and self-critique
- **Reflection**: generate → critique → revise (evaluator-optimizer).
- **Reflexion** (Shinn et al., 2023): store verbal feedback from failures in memory to improve future attempts.
- **Self-consistency**: sample several reasoning paths and take a majority vote.

### Tree/graph search over thoughts
**Tree of Thoughts** and **LATS** (Language Agent Tree Search) explore multiple reasoning branches with evaluation and backtracking — powerful but costly.

### Choosing
| Task | Pattern |
|---|---|
| Interactive tool use, unknown number of steps | ReAct / tool-calling loop |
| Long tasks with clear structure | plan-and-execute |
| Quality-critical writing/code | reflection / evaluator-optimizer |
| Hard reasoning puzzles | self-consistency, tree search |

### Key takeaways
- ReAct interleaves reasoning, actions and observations.
- Plan-and-execute separates planning from execution; replanning adapts.
- Reflection loops critique and revise; tree search explores branches.

## Tools, Function Calling and MCP
### Function calling
You describe tools with a **name**, **description** and **JSON Schema** of parameters. The model returns a structured call; your code executes it and returns the result.
```json
{
  "name": "get_order_status",
  "description": "Look up the shipping status of an order by its id.",
  "parameters": {
    "type": "object",
    "properties": {"order_id": {"type": "string"}},
    "required": ["order_id"]
  }
}
```
Good tool design:
- Few, well-named tools with **clear, distinct descriptions**.
- Precise parameter types and enums; return concise, informative results (and helpful errors).
- Make actions **idempotent** where possible; require approval for destructive ones.

### Model Context Protocol (MCP)
**MCP** (introduced by Anthropic in Nov 2024, now an open standard) is a protocol connecting AI applications to tools and data — "USB-C for AI apps".
| Role | Description |
|---|---|
| Host | the AI application (IDE, chat app, agent) |
| Client | connector inside the host, one per server |
| Server | exposes capabilities |

Server primitives: **Tools** (model-invoked functions), **Resources** (readable data/context), **Prompts** (templates). Client features include **sampling** (server asks the host's LLM to generate), **roots** and **elicitation** (ask the user for input). Messages use **JSON-RPC 2.0** over **stdio** (local) or **Streamable HTTP** (remote; replaced the older HTTP+SSE transport).
```python
# A tiny MCP server with the official Python SDK (FastMCP)
from mcp.server.fastmcp import FastMCP
mcp = FastMCP("weather")

@mcp.tool()
def forecast(city: str) -> str:
    """Get a 3-day forecast for a city."""
    return f"Sunny in {city}"

if __name__ == "__main__":
    mcp.run()            # stdio by default
```

### Code execution as a tool
Agents that write and run code (e.g. smolagents `CodeAgent`, Code Interpreter) can do math, data analysis and chained operations in one step — but must run in a **sandbox** (containers, E2B, Modal, WebAssembly).

### Key takeaways
- Tools = name + description + JSON schema; the model requests, your code executes.
- MCP standardises tools/resources/prompts between hosts and servers via JSON-RPC (stdio or Streamable HTTP).
- Sandbox any code-executing tool.

## Memory for Agents
### Types of memory
| Type | Scope | Example |
|---|---|---|
| Short-term / working | current conversation or task | message history, scratchpad, LangGraph thread state |
| Long-term semantic | facts | "user prefers Python", company facts |
| Episodic | past experiences | previous runs, successful trajectories (few-shot) |
| Procedural | how to act | system prompt rules, learned instructions |

### Managing the context window
- **Trimming** — keep the last N messages / tokens.
- **Summarisation** — replace old turns with a running summary (LangChain `SummarizationMiddleware`).
- **Retrieval-based memory** — store memories in a vector store and retrieve relevant ones per turn.
- **Structured state** — keep key facts in typed fields instead of free text.

### Writing memories
- **Hot path**: the agent decides during the conversation to save a memory (a `save_memory` tool).
- **Background**: a separate process extracts memories after the conversation.

### Tools and frameworks
LangGraph stores (`InMemoryStore`, `PostgresStore`) + LangMem; CrewAI memory (short-term, long-term, entity); Letta (formerly MemGPT) stateful agents with self-editing memory blocks; Mem0 and Zep as memory layers; LlamaIndex `Memory` blocks.

### Key takeaways
- Distinguish short-term (thread) from long-term (cross-session) memory.
- Control context growth with trimming, summarisation and retrieval.
- Memories can be written in the hot path (tools) or in the background.

## Multi-Agent Patterns
Splitting work across specialised agents can improve modularity and quality — at the cost of more tokens and coordination complexity.

### Common topologies
| Pattern | How it works | Example |
|---|---|---|
| Supervisor / orchestrator-workers | a manager agent delegates to specialists and integrates results | research manager + searcher + writer |
| Hierarchical | supervisors of teams | department-level supervisors |
| Handoff / swarm | agents transfer control directly to each other | triage → billing agent |
| Sequential pipeline | fixed order of agents | draft → edit → fact-check |
| Parallel / map-reduce | many agents work on parts concurrently | review 10 files in parallel |
| Debate / critic | agents argue or critique to improve answers | proposer vs critic |
| Group chat | agents share a conversation; a selector picks the next speaker | AutoGen GroupChat |

### Design principles
- Give each agent a **clear role, tools and success criteria**.
- Pass **only necessary context** between agents (context isolation reduces confusion and cost).
- Prefer deterministic orchestration where possible; let LLMs decide only what needs judgement.
- Add **termination conditions** and step budgets to avoid endless conversations.

### When multi-agent is worth it
✅ Parallelisable research, distinct expertise areas, too many tools for one agent, context isolation needs.
❌ Tightly coupled tasks requiring shared context (often a single agent with good tools is better and cheaper).

### Key takeaways
- Supervisor, hierarchical, handoff/swarm, sequential, parallel and debate are the core patterns.
- Isolate context, define roles and termination conditions.
- Multi-agent adds cost; use it for parallelism, specialisation or context isolation.

## CrewAI: Agents, Tasks, Crews and Flows
**CrewAI** is a Python framework (independent of LangChain) for role-based multi-agent "crews" and event-driven **Flows**.

### Core concepts
| Concept | Meaning |
|---|---|
| Agent | an autonomous unit with `role`, `goal`, `backstory`, tools and an LLM |
| Task | a unit of work with `description`, `expected_output` and an assigned agent |
| Crew | a team of agents + tasks + a process |
| Process | how tasks run: `Process.sequential` or `Process.hierarchical` (manager LLM delegates) |
| Flow | event-driven orchestration of steps and crews with state |

### A two-agent crew
```python
from crewai import Agent, Task, Crew, Process, LLM
from crewai_tools import SerperDevTool

llm = LLM(model="openai/gpt-5.5", temperature=0.2)

researcher = Agent(
    role="Senior Research Analyst",
    goal="Find the latest developments in {topic}",
    backstory="You are an expert analyst who cites sources.",
    tools=[SerperDevTool()],
    llm=llm,
)
writer = Agent(
    role="Tech Writer",
    goal="Write clear articles about {topic}",
    backstory="You turn research into engaging prose.",
    llm=llm,
)

research = Task(description="Research {topic} news from the last month.",
                expected_output="10 bullet points with sources", agent=researcher)
article = Task(description="Write a 400-word article from the research.",
               expected_output="A markdown article", agent=writer,
               context=[research])          # receives the research task's output

crew = Crew(agents=[researcher, writer], tasks=[research, article], process=Process.sequential)
result = crew.kickoff(inputs={"topic": "Graph RAG"})   # {topic} placeholders are filled
print(result.raw)
```
Useful options: `allow_delegation=True` on agents, `output_pydantic=Model` / `output_json` on tasks, task `guardrail` functions, `memory=True` and `planning=True` on crews, `human_input=True` on tasks.

### YAML projects
`crewai create crew my_crew` scaffolds a project with `config/agents.yaml`, `config/tasks.yaml` and a `@CrewBase` class using `@agent`, `@task` and `@crew` decorators; run it with `crewai run`.

### Flows
```python
from crewai.flow.flow import Flow, start, listen, router
from pydantic import BaseModel

class State(BaseModel):
    topic: str = ""
    draft: str = ""

class BlogFlow(Flow[State]):
    @start()
    def pick_topic(self):
        self.state.topic = "Hybrid RAG"

    @listen(pick_topic)
    def write(self):
        self.state.draft = crew.kickoff(inputs={"topic": self.state.topic}).raw

    @router(write)
    def check(self):
        return "publish" if len(self.state.draft) > 500 else "rewrite"

    @listen("publish")
    def publish(self): ...

BlogFlow().kickoff()
```
`@start` marks entry points, `@listen` reacts to other methods' completion, `@router` returns a label to branch, and `or_`/`and_` combine triggers. Flows hold typed state and can embed crews — giving precise control around autonomous crews.

### Key takeaways
- CrewAI agents have role/goal/backstory; tasks have description/expected_output; crews run them via a process.
- `context=[task]` passes outputs between tasks; hierarchical process adds a manager.
- Flows (`@start`, `@listen`, `@router`) add event-driven, stateful control around crews.

## OpenAI Agents SDK
The **OpenAI Agents SDK** (Python `openai-agents`, JS `@openai/agents`; successor of the experimental Swarm) is a lightweight, provider-agnostic framework with a few primitives.

### Primitives
| Primitive | Purpose |
|---|---|
| Agent | instructions + model + tools + handoffs + guardrails + output_type |
| Runner | runs the agent loop (`run`, `run_sync`, `run_streamed`) |
| Tools | `@function_tool` Python functions, hosted tools (web search, file search, code interpreter), MCP servers |
| Handoffs | delegate the conversation to another agent |
| Guardrails | input/output validation running alongside the agent |
| Sessions | automatic conversation memory (e.g. `SQLiteSession`) |
| Tracing | built-in traces of every run |

```python
from agents import Agent, Runner, function_tool, input_guardrail, GuardrailFunctionOutput

@function_tool
def get_balance(account_id: str) -> str:
    """Return the account balance."""
    return "$1,240.00"

billing = Agent(name="Billing", instructions="Handle billing questions.", tools=[get_balance])
tech = Agent(name="Tech Support", instructions="Solve technical problems.")

@input_guardrail
async def no_homework(ctx, agent, user_input):
    flagged = "homework" in str(user_input).lower()
    return GuardrailFunctionOutput(output_info=None, tripwire_triggered=flagged)

triage = Agent(
    name="Triage",
    instructions="Route the user to the right specialist.",
    handoffs=[billing, tech],
    input_guardrails=[no_homework],
)

result = Runner.run_sync(triage, "What's the balance on account 991?")
print(result.final_output)
```
- A **handoff** is exposed to the model as a tool (e.g. `transfer_to_billing`); the receiving agent takes over the conversation.
- When a guardrail's **tripwire** triggers, the run raises an exception (e.g. `InputGuardrailTripwireTriggered`).
- `output_type=MyPydanticModel` makes the final output structured.
- "Agents as tools" (`agent.as_tool(...)`) lets a manager call sub-agents without handing off control.

### Key takeaways
- Agents SDK primitives: Agent, Runner, tools, handoffs, guardrails, sessions, tracing.
- Handoffs transfer control; agents-as-tools keep the manager in control.
- Guardrail tripwires stop runs early.

## Google ADK, Microsoft Agent Framework and AutoGen
### Google Agent Development Kit (ADK)
A code-first, model-agnostic (optimised for Gemini) framework with Python, Java, Go and TypeScript SDKs.
```python
from google.adk.agents import Agent, SequentialAgent, ParallelAgent, LoopAgent

def get_weather(city: str) -> dict:
    """Return the weather for a city."""
    return {"city": city, "forecast": "sunny"}

weather_agent = Agent(
    name="weather_agent",
    model="gemini-flash-latest",
    instruction="Answer weather questions using the tool.",
    tools=[get_weather],
    output_key="weather",          # save the final response into session state
)
pipeline = SequentialAgent(name="trip_planner", sub_agents=[weather_agent, packing_agent])
```
- **LlmAgent** (`Agent`) for reasoning; **workflow agents** — `SequentialAgent`, `ParallelAgent`, `LoopAgent` — for deterministic orchestration.
- Sessions, state, memory and artifacts are managed by services (`InMemorySessionService`, ...) with a `Runner`.
- Dev tools: `adk web` (UI), `adk run` (CLI), `adk api_server`; deploy to Vertex AI Agent Engine or Cloud Run. Supports MCP tools and the **A2A** protocol.

### Microsoft Agent Framework (MAF)
Microsoft's unified successor to **AutoGen** and **Semantic Kernel** (public preview Oct 2025, **1.0 GA in April 2026**) for Python and .NET.
```python
import asyncio
from agent_framework import Agent, tool
from agent_framework.openai import OpenAIChatClient

@tool
def get_weather(location: str) -> str:
    """Get the weather for a location."""
    return f"Sunny in {location}"

async def main():
    agent = Agent(client=OpenAIChatClient(), name="WeatherBot",
                  instructions="You are a helpful weather assistant.", tools=[get_weather])
    print(await agent.run("What's the weather in Seattle?"))

asyncio.run(main())
```
- **Agents** with tools, sessions (multi-turn), middleware and streaming.
- **Workflows**: graph-based, type-safe orchestration with checkpointing and human-in-the-loop; built-in orchestrations such as **Sequential, Concurrent, Group Chat, Handoff and Magentic**.
- Native MCP and A2A support, OpenTelemetry observability, Azure AI Foundry integration.

### AutoGen
Microsoft Research's pioneering multi-agent framework (conversational agents, `AssistantAgent`, `UserProxyAgent`, `RoundRobinGroupChat`, `SelectorGroupChat`). v0.4 rewrote it as an async, event-driven architecture. AutoGen is now in maintenance mode, with **Microsoft Agent Framework** as the recommended path forward (AG2 is a community fork of the original AutoGen).

### Key takeaways
- ADK: LlmAgent + Sequential/Parallel/Loop workflow agents, `output_key` state, `adk web` dev UI.
- Microsoft Agent Framework unifies AutoGen + Semantic Kernel: agents + graph workflows, GA in 2026.
- AutoGen pioneered conversational multi-agent systems; MAF is its successor.

## smolagents, Pydantic AI and Other Frameworks
### Hugging Face smolagents
A minimal library whose flagship **CodeAgent** writes its actions as **Python code** instead of JSON tool calls — code naturally expresses loops, variables and composition, often needing fewer steps.
```python
from smolagents import CodeAgent, WebSearchTool, InferenceClientModel, tool

@tool
def celsius_to_f(c: float) -> float:
    """Convert Celsius to Fahrenheit.
    Args:
        c: temperature in Celsius
    """
    return c * 9 / 5 + 32

agent = CodeAgent(tools=[WebSearchTool(), celsius_to_f], model=InferenceClientModel())
agent.run("What's today's temperature in Paris in Fahrenheit?")
```
`ToolCallingAgent` uses classic JSON tool calls. Code execution should be sandboxed (E2B, Docker, Modal, WebAssembly executors).

### Pydantic AI
From the Pydantic team: type-safe agents with dependency injection and validated structured output.
```python
from pydantic import BaseModel
from pydantic_ai import Agent, RunContext

class CityInfo(BaseModel):
    city: str
    country: str

agent = Agent("openai:gpt-5.5", output_type=CityInfo, system_prompt="Extract the city and country.")

@agent.tool
def lookup(ctx: RunContext[str], name: str) -> str:
    """Look up a place in the user's database."""
    return f"{name} found for user {ctx.deps}"

result = agent.run_sync("I live in the capital of France", deps="user-1")
result.output        # CityInfo(city='Paris', country='France')
```

### More frameworks
| Framework | Highlights |
|---|---|
| LangGraph / LangChain | graph runtime, durable execution, `create_agent` + middleware |
| LlamaIndex agents | data-centric agents, AgentWorkflow |
| Claude Agent SDK | the agent harness behind Claude Code: tools, subagents, skills, MCP |
| Strands Agents (AWS) | model-driven agents, Bedrock integration |
| Agno | lightweight, fast multi-agent framework |
| Mastra | TypeScript agent framework |
| DSPy | programming (not prompting) LMs, with optimisers |
| Letta | stateful agents with persistent memory |

### Key takeaways
- smolagents' CodeAgent acts by writing Python code; sandbox execution.
- Pydantic AI emphasises type safety, dependency injection and validated outputs.
- Pick frameworks by control needs, ecosystem and language.

## A2A, Evaluation and Safety of Agents
### Agent2Agent (A2A) protocol
**A2A** (introduced by Google in April 2025, now a Linux Foundation project) lets agents built on different frameworks **discover and collaborate** with each other.
- **Agent Card**: a JSON document (served at a well-known URL such as `/.well-known/agent-card.json`) describing an agent's name, skills, endpoint and auth.
- Communication over HTTP with **JSON-RPC 2.0**; streaming via SSE; push notifications for long tasks.
- Core objects: **Task** (with lifecycle states such as submitted, working, input-required, completed), **Message**, **Part** (text/file/data) and **Artifact** (outputs).

**MCP vs A2A**: MCP connects an agent to **tools and data** (vertical); A2A connects **agents to agents** (horizontal). They are complementary.

### Evaluating agents
| What | How |
|---|---|
| Final answer quality | reference answers, LLM-as-judge, task success rate |
| Trajectory | did it call the right tools in a sensible order? (trajectory match, tool-call accuracy) |
| Tool use | argument correctness, error rate |
| Efficiency | steps, tokens, latency, cost per task |
| Robustness | adversarial inputs, prompt injection, tool failures |

Benchmarks: SWE-bench (coding), GAIA (general assistants), τ-bench/τ²-bench (tool-agent-user interaction), WebArena, OSWorld, Terminal-Bench, BrowseComp. Tools: LangSmith, Langfuse, RAGAS agent metrics, DeepEval, Arize Phoenix.

### Safety and guardrails
- **Least privilege**: give agents only the tools and permissions they need.
- **Human-in-the-loop** approval for irreversible or costly actions.
- **Prompt injection defence**: treat tool outputs, web pages and documents as untrusted; never let them change instructions or permissions.
- **Sandboxing** for code and browser actions.
- **Budgets**: max steps, max tokens, timeouts, rate limits.
- **Audit**: trace every action; log who approved what.
- **Output validation**: schemas, policy checks, PII filters.

### Key takeaways
- A2A connects agents across frameworks via Agent Cards and JSON-RPC tasks; MCP connects agents to tools.
- Evaluate final answers, trajectories, tool use, efficiency and robustness.
- Least privilege, HITL, injection defences, sandboxing and budgets are essential agent safety controls.
