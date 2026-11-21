---
id: mcp
title: Model Context Protocol (MCP)
short: The open standard connecting AI apps to tools and data: servers, tools, resources, transports and security
emoji: 🔌
color: #5E35B1
---
## What MCP Is and Why It Exists
Every AI application needs context and actions: files, databases, tickets, calendars, search, internal APIs. Before MCP, each app wrote its own connector for each system. With **M** apps and **N** systems that is **M × N** custom integrations, each with its own auth, schemas and bugs.

### MCP in one sentence
The **Model Context Protocol (MCP)** is an open protocol that standardises how AI applications connect to external tools, data and prompts. A system is wrapped once as an **MCP server**, and any MCP-compatible app (the **host**) can use it. The integration problem becomes **M + N**.

People often call it "**USB-C for AI applications**": one plug shape, many devices.

### A short history
| Date | Milestone |
|---|---|
| Nov 2024 | Anthropic open-sources MCP (spec version `2024-11-05`) with Python and TypeScript SDKs |
| Mar 2025 | Spec `2025-03-26`: Streamable HTTP transport, OAuth 2.1 authorization, tool annotations |
| 2025 | OpenAI, Google, Microsoft and most IDEs and agent frameworks adopt MCP |
| Jun 2025 | Spec `2025-06-18`: structured tool output, elicitation, resource links; JSON-RPC batching removed |
| Sep 2025 | The official **MCP Registry** launches in preview |
| Nov 2025 | Spec `2025-11-25`: experimental tasks, URL-mode elicitation, Client ID Metadata Documents, icons |
| Dec 2025 | MCP is donated to the **Agentic AI Foundation** under the Linux Foundation |

Spec versions are **dates** (`YYYY-MM-DD`). A version only changes when a backwards-incompatible change is made.

### What MCP standardises
| Building block | Offered by | What it is |
|---|---|---|
| Tools | server | functions the model can call (search, create ticket, run query) |
| Resources | server | readable data the app can attach as context (files, records, schemas) |
| Prompts | server | reusable templates the user can pick (slash commands) |
| Sampling | client | the server asks the host's LLM to generate text |
| Roots | client | the client tells the server which folders/URIs it may work in |
| Elicitation | client | the server asks the user for extra input via the host |

### MCP vs plain function calling
**Function calling** is a model feature: you pass tool schemas in an API request and the model returns a structured call. **MCP** sits one level up. It defines how tools are **discovered, described, invoked and secured** across processes and vendors. A host usually turns MCP tools into function-calling tool definitions for whatever model it uses.

### Key takeaways
- MCP turns M × N custom integrations into M + N reusable ones.
- Servers offer tools, resources and prompts; clients offer sampling, roots and elicitation.
- Spec versions are dates; MCP is now an open standard governed under the Linux Foundation.

## Architecture: Hosts, Clients, Servers and the Lifecycle
### The three roles
| Role | Description | Examples |
|---|---|---|
| Host | the AI application the user interacts with; owns the LLM, the UI and security decisions | Claude Desktop, Claude Code, VS Code, Cursor, ChatGPT, a custom agent |
| Client | a connector inside the host that keeps a **1:1** connection with one server | one client per configured server |
| Server | a program that exposes tools, resources and prompts | GitHub, Postgres, filesystem, Slack, your own API |

A host with three servers configured runs **three clients**. Servers are isolated from each other: a server cannot see the conversation or other servers. The host decides what each server receives.

### Local and remote servers
- **Local servers** run as a subprocess on the user's machine and talk over **stdio**. Example: a filesystem server.
- **Remote servers** run as web services and talk over **Streamable HTTP**, usually with OAuth. Example: a SaaS vendor's hosted MCP endpoint.

### The connection lifecycle
1. **Initialization:** the client sends an `initialize` request with its `protocolVersion`, `capabilities` and `clientInfo`.
2. The server replies with the agreed `protocolVersion`, its own `capabilities`, `serverInfo` and optional `instructions`.
3. The client sends the `notifications/initialized` notification. Normal operation can now begin.
4. **Operation:** the client and server exchange requests and notifications according to the negotiated capabilities.
5. **Shutdown:** for stdio the client closes the server's stdin (then signals the process if needed); for HTTP it closes the connections or sends `DELETE` to end the session.

```json
{"jsonrpc": "2.0", "id": 1, "method": "initialize",
 "params": {"protocolVersion": "2025-11-25",
            "capabilities": {"roots": {"listChanged": true}, "sampling": {}, "elicitation": {}},
            "clientInfo": {"name": "my-agent", "version": "1.0.0"}}}
```

### Version negotiation
The client sends the **latest** version it supports. If the server supports it, it replies with the same version. Otherwise it replies with another version it supports (normally its latest). If the client cannot use the server's version, it should **disconnect**.

### Capability negotiation
Each side declares what it supports during initialization. Only negotiated features may be used.
| Side | Capability | Meaning |
|---|---|---|
| Server | `tools` | offers tools (`listChanged` = will notify when the list changes) |
| Server | `resources` | offers resources (`subscribe`, `listChanged`) |
| Server | `prompts` | offers prompts (`listChanged`) |
| Server | `logging` | can send log messages |
| Server | `completions` | supports argument autocompletion |
| Client | `roots` | can provide filesystem roots (`listChanged`) |
| Client | `sampling` | lets servers request LLM completions |
| Client | `elicitation` | lets servers ask the user for input |

### Key takeaways
- Host = the app; client = one connector per server; server = the capability provider.
- Every connection starts with initialize → response → notifications/initialized.
- Version and capability negotiation decide which features may be used.

## JSON-RPC Messages and the Base Protocol
### JSON-RPC 2.0
All MCP messages are **JSON-RPC 2.0**, UTF-8 encoded. There are three kinds:
| Kind | Has `id`? | Has `method`? | Expects a reply? |
|---|---|---|---|
| Request | yes | yes | yes |
| Response | yes (same id as the request) | no | no |
| Notification | **no** | yes | **no** |

```json
{"jsonrpc": "2.0", "id": 7, "method": "tools/call", "params": {"name": "add", "arguments": {"a": 2, "b": 3}}}
{"jsonrpc": "2.0", "id": 7, "result": {"content": [{"type": "text", "text": "5"}], "isError": false}}
{"jsonrpc": "2.0", "method": "notifications/tools/list_changed"}
```

Rules MCP adds on top of JSON-RPC:
- Request IDs must be a **string or integer**, never `null`, and must not be reused within a session.
- A response contains **either** `result` **or** `error`, never both.
- Since `2025-06-18`, JSON-RPC **batching** (sending an array of messages) is no longer supported.

### Standard error codes
| Code | Name | Typical cause |
|---|---|---|
| -32700 | Parse error | invalid JSON |
| -32600 | Invalid Request | not a valid JSON-RPC object |
| -32601 | Method not found | unknown method or capability not negotiated |
| -32602 | Invalid params | bad arguments, unknown tool name |
| -32603 | Internal error | server-side failure |
| -32002 | Resource not found | MCP-specific, for `resources/read` |

```json
{"jsonrpc": "2.0", "id": 9, "error": {"code": -32602, "message": "Unknown tool: delete_everything"}}
```

### Both sides send requests
MCP is **bidirectional**. The client calls `tools/call`; the server can call `sampling/createMessage`, `roots/list` or `elicitation/create` on the client. Either side can send `ping` to check the connection is alive.

### Utilities
| Utility | How it works |
|---|---|
| Ping | `ping` request; the receiver replies with an empty result |
| Progress | the requester puts a `progressToken` in `params._meta`; the receiver sends `notifications/progress` with `progress`, optional `total` and `message` |
| Cancellation | `notifications/cancelled` with the `requestId` and an optional `reason` |
| Logging | server sends `notifications/message` with a level; client can set the minimum with `logging/setLevel` |
| Pagination | list results may include `nextCursor`; pass it back as `cursor` to get the next page |
| Completion | `completion/complete` suggests values for prompt arguments and resource-template variables |

Log levels follow syslog (RFC 5424): `debug`, `info`, `notice`, `warning`, `error`, `critical`, `alert`, `emergency`.

### The _meta field
Many objects allow a `_meta` field for extra metadata (progress tokens, vendor data). Keys starting with reserved prefixes such as `io.modelcontextprotocol/` belong to the spec.

### Key takeaways
- Requests have an id and a method; responses echo the id; notifications have no id and get no reply.
- Use standard JSON-RPC error codes; MCP adds -32002 for missing resources.
- Progress, cancellation, logging, pagination, ping and completion are shared utilities.

## Tools: Letting the Model Take Actions
### What a tool is
A **tool** is a function a server exposes for the **model** to call. Tools are **model-controlled**: the LLM decides when to call them, based on their name, description and schema. Hosts should keep a **human in the loop** and let users approve or deny calls.

### Discovering and calling tools
```json
{"jsonrpc": "2.0", "id": 2, "method": "tools/list"}
```
```json
{"jsonrpc": "2.0", "id": 2, "result": {"tools": [{
  "name": "get_weather",
  "title": "Weather lookup",
  "description": "Get current weather for a city.",
  "inputSchema": {"type": "object",
                  "properties": {"city": {"type": "string"}},
                  "required": ["city"]},
  "annotations": {"readOnlyHint": true, "openWorldHint": true}
}]}}
```
The client calls a tool with `tools/call`, passing `name` and `arguments`. If the server declared `tools.listChanged`, it sends `notifications/tools/list_changed` when its tools change, and the client should call `tools/list` again.

### Tool fields
| Field | Purpose |
|---|---|
| `name` | unique identifier used in `tools/call` |
| `title` | human-friendly display name (optional) |
| `description` | tells the model what the tool does and when to use it |
| `inputSchema` | JSON Schema (type `object`) for the arguments |
| `outputSchema` | optional JSON Schema for `structuredContent` |
| `annotations` | hints about behaviour (read-only, destructive, idempotent, open-world) |
| `icons` | optional icons for display (added in `2025-11-25`) |

### Tool results
A result has a `content` array, plus optional `structuredContent` and an `isError` flag.
| Content type | Use |
|---|---|
| `text` | plain text |
| `image` | base64 `data` + `mimeType` |
| `audio` | base64 `data` + `mimeType` |
| `resource_link` | a URI pointing to a resource the client can read later |
| `resource` | an embedded resource with its contents |

```json
{"content": [{"type": "text", "text": "{\"temp_c\": 21, \"sky\": \"clear\"}"}],
 "structuredContent": {"temp_c": 21, "sky": "clear"},
 "isError": false}
```
With an `outputSchema`, the server **must** return `structuredContent` that matches it. For backwards compatibility it should also include the serialised JSON as a `text` block.

### Two kinds of errors
- **Protocol errors** (unknown tool, malformed request) are JSON-RPC `error` responses.
- **Tool execution errors** (API failed, invalid date) are normal results with `"isError": true` and an explanation in `content`. The **model sees them** and can fix its call and retry.

### Tool annotations
| Annotation | Meaning | Default |
|---|---|---|
| `readOnlyHint` | tool does not modify its environment | false |
| `destructiveHint` | tool may perform destructive updates (only meaningful if not read-only) | true |
| `idempotentHint` | calling repeatedly with the same arguments has no extra effect | false |
| `openWorldHint` | tool interacts with an open world (web, external systems) | true |

Annotations are **hints**. Clients must treat them as **untrusted** unless the server itself is trusted.

### Designing good tools
- Few, well-named tools with distinct descriptions; prefer task-level tools (`create_invoice`) over thin API wrappers.
- Tight schemas: enums, required fields, formats, sensible defaults.
- Return concise, model-friendly results; paginate or truncate big outputs.
- Write error messages that tell the model how to fix the call.

### Key takeaways
- Tools are model-controlled; list with tools/list, invoke with tools/call.
- Results carry content blocks, optional structuredContent and isError.
- Execution failures go in the result (isError) so the model can recover; annotations are untrusted hints.

## Resources and Prompts
### Resources: data the app can read
A **resource** is a piece of data identified by a **URI**: a file, a database schema, a log, a ticket. Resources are **application-controlled**. The host decides what to read and attach as context, for example by letting the user pick files or by attaching relevant records automatically.

```json
{"jsonrpc": "2.0", "id": 3, "method": "resources/read", "params": {"uri": "file:///project/README.md"}}
```
```json
{"jsonrpc": "2.0", "id": 3, "result": {"contents": [
  {"uri": "file:///project/README.md", "mimeType": "text/markdown", "text": "# My project..."}]}}
```
Contents are either `text` or base64-encoded `blob` (binary data such as images or PDFs).

### Resource methods
| Method | Purpose |
|---|---|
| `resources/list` | list concrete resources (paginated) |
| `resources/templates/list` | list parameterised URI templates |
| `resources/read` | read the contents of a URI |
| `resources/subscribe` / `resources/unsubscribe` | watch a resource for changes |
| `notifications/resources/updated` | server says a subscribed resource changed |
| `notifications/resources/list_changed` | server says the list of resources changed |

### URI schemes and templates
Common schemes: `file://`, `https://`, `git://`, and custom schemes such as `postgres://` or `notes://`. **Resource templates** use **RFC 6570** URI templates, for example `weather://forecast/{city}`, so one definition covers many resources. The client fills in the variables, and `completion/complete` can suggest values.

### Resource annotations
| Annotation | Meaning |
|---|---|
| `audience` | who it is for: `["user"]`, `["assistant"]` or both |
| `priority` | importance from 0.0 (optional) to 1.0 (required) |
| `lastModified` | ISO 8601 timestamp |

### Prompts: user-picked templates
A **prompt** is a reusable, parameterised message template. Prompts are **user-controlled**. Hosts typically show them as **slash commands** or menu items (for example `/review-pr`).
```json
{"jsonrpc": "2.0", "id": 4, "method": "prompts/get",
 "params": {"name": "code_review", "arguments": {"language": "python"}}}
```
The result is a list of `messages`, each with a `role` (`user` or `assistant`) and `content` (text, image, audio or embedded resource). The host inserts these messages into the conversation.

### Who controls what
| Primitive | Controlled by | Typical UI |
|---|---|---|
| Tools | model | the model calls them; the user approves |
| Resources | application | file pickers, @-mentions, automatic context |
| Prompts | user | slash commands, menus |

### Key takeaways
- Resources are URI-addressed data (text or blob), read with resources/read; templates use RFC 6570.
- Subscriptions and list_changed notifications keep clients up to date.
- Prompts are user-chosen templates that expand into conversation messages.

## Client Features: Sampling, Roots and Elicitation
### Sampling: borrowing the host's LLM
With **sampling**, a server asks the client to run an LLM completion via `sampling/createMessage`. The server needs no API key of its own. The host keeps control of model choice, cost and privacy.
```json
{"jsonrpc": "2.0", "id": 11, "method": "sampling/createMessage", "params": {
  "messages": [{"role": "user", "content": {"type": "text", "text": "Summarise this diff in one line"}}],
  "modelPreferences": {"hints": [{"name": "claude-sonnet"}], "costPriority": 0.3,
                       "speedPriority": 0.5, "intelligencePriority": 0.8},
  "systemPrompt": "You are a concise release-notes writer.",
  "maxTokens": 100}}
```
- **Model preferences** are advisory. `hints` are substring matches on model names; the three priorities (0 to 1) express trade-offs. The client picks the actual model.
- The host should let the user **review and edit** the request and the result: a human stays in the loop.
- Since `2025-11-25`, sampling requests can include **tools**, so a server can run a small agentic loop through the host.

### Roots: where the server may work
**Roots** are URIs (usually `file://` folders) the client exposes to tell a server which parts of the filesystem it should operate in. The server calls `roots/list`, and the client sends `notifications/roots/list_changed` when the user opens a different project. Roots are guidance and set scope. They are not a sandbox, so servers should respect them and hosts should still enforce access.
```json
{"roots": [{"uri": "file:///home/sam/projects/shop", "name": "shop"}]}
```

### Elicitation: asking the user mid-task
With **elicitation** (added in `2025-06-18`), a server asks the user for more information through the host with `elicitation/create`. **Form mode** sends a `message` and a `requestedSchema`: a flat object whose properties are primitive types (string, number, integer, boolean, enum).
```json
{"method": "elicitation/create", "params": {
  "message": "Which environment should I deploy to?",
  "requestedSchema": {"type": "object",
    "properties": {"env": {"type": "string", "enum": ["staging", "production"]}},
    "required": ["env"]}}}
```
The user's response has an `action`:
| Action | Meaning |
|---|---|
| `accept` | user submitted data (in `content`) |
| `decline` | user explicitly said no |
| `cancel` | user dismissed the dialog without choosing |

Servers must **not** use form mode to ask for passwords, API keys or other sensitive data. For that, **URL mode** (added in `2025-11-25`) sends the user to a URL, such as an OAuth or payment page, so secrets never pass through the client.

### Tasks (experimental)
The `2025-11-25` spec adds experimental **tasks**: a requester can turn a long-running request into a task, get a task id back straight away, and poll for status and results later ("call now, fetch later").

### Key takeaways
- Sampling lets servers use the host's model without their own API key; the user can review it.
- Roots tell servers which folders or URIs are in scope.
- Elicitation asks the user for structured input; never request secrets in form mode.

## Transports: stdio and Streamable HTTP
### stdio (local)
The client **launches the server as a subprocess** and talks to it over standard input and output.
- Messages are JSON-RPC objects separated by **newlines**, and must not contain embedded newlines.
- The server must write **only valid MCP messages to stdout**. Logs go to **stderr**.
- Best for local tools: very low latency, no network exposure, credentials come from environment variables.
```python
import sys, json
print(json.dumps({"jsonrpc": "2.0", "id": 1, "result": {}}), flush=True)   # stdout: protocol only
print("server started", file=sys.stderr)                                     # stderr: logs
```

### Streamable HTTP (remote)
Introduced in `2025-03-26`, it replaced the older **HTTP+SSE** transport (two endpoints). The server exposes **one endpoint** (for example `https://api.example.com/mcp`) that supports `POST` and `GET`.
| Step | What happens |
|---|---|
| Client → server | every JSON-RPC message is a new HTTP `POST`; `Accept` lists `application/json` and `text/event-stream` |
| Request reply | the server answers with a single JSON response **or** opens an **SSE stream** (for progress, server requests, then the response) |
| Notification or response | the server replies `202 Accepted` with no body |
| Server-initiated messages | the client may `GET` the endpoint to open an SSE stream for server → client messages |
| Session | the server may assign `Mcp-Session-Id` in the initialize response; the client sends it on every later request |
| Session ends | the server returns `404` for an expired session (client re-initializes); the client may send `DELETE` to end it |
| Version | after initialization the client sends the `MCP-Protocol-Version` header on every request |

### Resumability
Servers can attach an `id` to each SSE event. If a connection drops, the client reconnects with the **`Last-Event-ID`** header and the server can replay missed messages for that stream.

### Transport security
- Servers **must validate the `Origin` header** to prevent DNS rebinding attacks.
- Local HTTP servers should bind to **localhost (127.0.0.1)**, not 0.0.0.0.
- Use authentication for remote servers, and HTTPS everywhere.
- Session IDs must be secure and random, and must **never** be used as authentication.

### Choosing a transport
| Need | Use |
|---|---|
| Local tool on the user's machine (files, git, CLI wrappers) | stdio |
| Shared, hosted or multi-user service | Streamable HTTP + OAuth |
| Old clients that only speak HTTP+SSE | serve the legacy endpoints alongside Streamable HTTP during migration |

### Key takeaways
- stdio = subprocess + newline-delimited JSON on stdout; logs to stderr.
- Streamable HTTP = one endpoint, POST for messages, optional SSE streams, Mcp-Session-Id sessions.
- Validate Origin, bind locally to 127.0.0.1, and never treat session IDs as auth.

## Building Servers and Clients in Python
### Installing the SDK
```bash
pip install "mcp[cli]"          # official Python SDK, includes FastMCP and the mcp CLI
```
Official SDKs exist for Python, TypeScript, Java, Kotlin, C#, Go, Ruby, Rust, Swift and PHP. The separate open-source **FastMCP 2.x** project (`pip install fastmcp`, `from fastmcp import FastMCP`) adds extras such as server composition, proxying and a high-level client.

### A server with FastMCP
```python
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("notes")

@mcp.tool()
def add(a: int, b: int) -> int:
    """Add two numbers."""
    return a + b

@mcp.resource("notes://{name}")
def read_note(name: str) -> str:
    """Return the text of a note."""
    return open(f"notes/{name}.md").read()

@mcp.prompt()
def summarize(topic: str) -> str:
    """Ask for a short summary of a topic."""
    return f"Summarize what my notes say about {topic} in 5 bullets."

if __name__ == "__main__":
    mcp.run()                              # stdio by default
```
FastMCP builds the `inputSchema` from **type hints**, and takes the description from the **docstring**. A tool that returns a Pydantic model, TypedDict or dataclass also gets an `outputSchema`, and its results include `structuredContent`.

### The Context object
Add a parameter typed `Context` to get logging, progress, resource reads, elicitation and sampling inside a tool:
```python
from mcp.server.fastmcp import FastMCP, Context

mcp = FastMCP("jobs")

@mcp.tool()
async def process(files: list[str], ctx: Context) -> str:
    """Process files and report progress."""
    for i, f in enumerate(files):
        await ctx.info(f"Processing {f}")
        await ctx.report_progress(progress=i + 1, total=len(files))
    return f"Processed {len(files)} files"
```

### Running over HTTP
```python
mcp.run(transport="streamable-http")      # serves http://localhost:8000/mcp by default
```

### Writing a client
```python
import asyncio
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

params = StdioServerParameters(command="python", args=["server.py"])

async def main():
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            tools = await session.list_tools()
            print([t.name for t in tools.tools])
            result = await session.call_tool("add", {"a": 2, "b": 3})
            print(result.content[0].text)

asyncio.run(main())
```
For remote servers use `streamablehttp_client("http://localhost:8000/mcp")` from `mcp.client.streamable_http`.

### TypeScript in brief
```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const server = new McpServer({ name: "demo", version: "1.0.0" });
server.registerTool("add",
  { title: "Add", description: "Add two numbers", inputSchema: { a: z.number(), b: z.number() } },
  async ({ a, b }) => ({ content: [{ type: "text", text: String(a + b) }] }));
await server.connect(new StdioServerTransport());
```

### Testing and connecting to hosts
- **MCP Inspector:** `npx @modelcontextprotocol/inspector` (or `mcp dev server.py`) opens a web UI to list and call tools, read resources and watch messages.
- **Claude Desktop:** add the server to `claude_desktop_config.json` under `mcpServers`.
- **Claude Code:** `claude mcp add notes -- python server.py`.
- **VS Code:** `.vscode/mcp.json` (top-level key `servers`); **Cursor:** `.cursor/mcp.json`.
```json
{"mcpServers": {"notes": {"command": "python", "args": ["/abs/path/server.py"],
                          "env": {"NOTES_DIR": "/abs/path/notes"}}}}
```

### Using MCP tools in agent frameworks
LangChain/LangGraph (`langchain-mcp-adapters`), the OpenAI Agents SDK, Google ADK, Microsoft Agent Framework, CrewAI, LlamaIndex and Pydantic AI can all load MCP tools. Many model APIs also accept remote MCP server URLs directly as tools.

### Key takeaways
- FastMCP turns decorated Python functions into tools, resources and prompts using type hints and docstrings.
- ClientSession + stdio_client (or streamablehttp_client) is the Python client pattern: initialize, list, call.
- Test with MCP Inspector, then register the server in the host's config file.

## Security, Authorization and Production
### Authorization for HTTP servers
Authorization is **optional** and applies to **HTTP transports**. stdio servers take credentials from the environment instead. When it is used, MCP follows **OAuth 2.1**:
| Piece | Role |
|---|---|
| MCP server | an OAuth **resource server**: it validates access tokens |
| Authorization server | issues tokens (can be a separate identity provider) |
| Protected Resource Metadata (RFC 9728) | served at `/.well-known/oauth-protected-resource`; tells clients which authorization server to use |
| `401` + `WWW-Authenticate` | the server's response to unauthenticated requests, pointing to the resource metadata |
| PKCE | required for all clients (S256 method) |
| Resource Indicators (RFC 8707) | the client sends a `resource` parameter so tokens are bound to that specific MCP server |
| Client registration | Client ID Metadata Documents (preferred since `2025-11-25`), pre-registration, or Dynamic Client Registration (RFC 7591) |

> Token passthrough is forbidden: an MCP server must only accept tokens issued for itself, and must not forward the client's token to upstream APIs. Use a separate token for upstream calls.

### Main threats
| Threat | What happens | Defence |
|---|---|---|
| Tool poisoning | malicious instructions hidden in a tool description | review servers, pin versions, show full descriptions |
| Prompt injection via results | fetched web pages or emails contain instructions the model follows | treat tool output as untrusted data; require approval for sensitive actions |
| Rug pull | a server silently changes tool definitions after approval | detect definition changes; pin and re-approve |
| Confused deputy | a proxy server uses its own privileges for a user who should not have them | per-client consent, proper token audience checks |
| Token theft / passthrough | stolen or forwarded tokens used elsewhere | audience-bound tokens, short lifetimes, no passthrough |
| Session hijacking | an attacker reuses a session ID | random session IDs bound to the user; never use them as auth |
| Tool shadowing / name collision | two servers expose tools with the same name | namespace tools (e.g. `github__create_issue`) |
| Data exfiltration | a "lethal trifecta" of private data + untrusted content + outbound channel | limit tool combinations, egress controls, human review |

### Defence-in-depth checklist
- **Least privilege:** scoped tokens, read-only tools by default, limit roots.
- **Human in the loop:** confirm destructive or open-world tool calls; show arguments before running.
- **Trust:** install servers from trusted sources; prefer the official registry and signed or pinned versions.
- **Isolation:** run local servers in containers or sandboxes; validate all inputs server-side.
- **Observability:** log every tool call, argument and result (with OpenTelemetry or an LLM tracing tool).

### Running MCP in production
- **MCP Registry** (`registry.modelcontextprotocol.io`): an open catalogue of public servers, each described by a `server.json`; sub-registries and private registries build on it.
- **MCP gateways** sit between hosts and many servers to centralise auth, policy, audit logging, rate limits and tool filtering.
- Make remote servers **stateless** where possible (or store session state externally) so they scale horizontally behind a load balancer.
- Keep tool lists small per agent: too many tools waste context and confuse tool selection. Load tools on demand.

### MCP and A2A
**MCP** connects an agent to **tools and data** (vertical). **A2A (Agent2Agent)** connects **agents to other agents** across vendors (horizontal), using Agent Cards and tasks. Many systems use both.

### Key takeaways
- HTTP authorization uses OAuth 2.1: protected resource metadata, PKCE, resource indicators, no token passthrough.
- The big risks are tool poisoning, prompt injection, rug pulls, confused deputies and exfiltration.
- In production use trusted registries, gateways, least privilege, human approval and full logging.
