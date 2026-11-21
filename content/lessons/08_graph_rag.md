---
id: graph_rag
title: Graph RAG
short: Knowledge graphs for multi-hop and global questions — GraphRAG, LightRAG, Neo4j, Cypher
emoji: 🔗
color: #2E7D32
---
## Knowledge Graph Fundamentals
A **knowledge graph (KG)** represents information as **entities** (nodes) connected by **relationships** (edges). The basic fact unit is a **triple**: `(subject, predicate, object)`, e.g. `(Marie Curie, WON, Nobel Prize)`.

### Two main graph models
| Model | Description | Query language |
|---|---|---|
| Property graph | nodes and edges have labels and key-value properties | Cypher (Neo4j, Memgraph), GQL (ISO standard), Gremlin |
| RDF | everything is a triple; uses URIs and ontologies (RDFS/OWL) | SPARQL |

```text
(:Person {name: "Ada"})-[:WORKS_AT {since: 2021}]->(:Company {name: "Acme"})
```

### Graph vocabulary
- **Degree** — number of edges attached to a node (in-degree / out-degree for directed graphs).
- **Path** — a sequence of edges; a **hop** is one edge traversal.
- **Neighbourhood** — nodes within k hops.
- **Community** — a densely connected group of nodes.
- **Ontology / schema** — allowed entity and relation types.

### Why graphs help RAG
Vector RAG retrieves chunks that *look similar* to the query. Some questions need **connections** that no single chunk states:
- **Multi-hop**: "Which university did the founder of the company that acquired X attend?"
- **Aggregation / global**: "What are the main themes across 5,000 support tickets?"
- **Relationship reasoning**: "How is supplier A connected to the recall?"

A graph makes those connections explicit and traversable.

### Key takeaways
- KGs store facts as entity–relationship–entity triples.
- Property graphs (Cypher/GQL) and RDF (SPARQL) are the two main models.
- Graphs help with multi-hop, relational and corpus-wide questions where vector similarity struggles.

## Building Knowledge Graphs with LLMs
Traditional KG construction needed hand-written NLP pipelines. LLMs can now extract entities and relations directly from text.

### The extraction pipeline
1. **Chunk** the documents.
2. **Extract** entities (with types and descriptions) and relationships (with descriptions/weights) from each chunk using an LLM prompt or structured output.
3. **Resolve entities** — merge duplicates ("IBM", "I.B.M.", "International Business Machines") using string similarity, embeddings and/or an LLM.
4. **Store** nodes and edges in a graph database, keeping links back to the **source chunks** (provenance).
5. **Enrich** — compute embeddings for nodes, detect communities, summarise.

### Schema-guided vs schema-free
- **Schema-free** extraction captures anything but yields noisy, inconsistent relation names.
- **Schema-guided** extraction restricts entity types (`Person`, `Company`, `Drug`) and relations (`WORKS_AT`, `TREATS`) for cleaner, queryable graphs.

```python
from pydantic import BaseModel

class Relation(BaseModel):
    source: str
    source_type: str
    relation: str
    target: str
    target_type: str

class Extraction(BaseModel):
    relations: list[Relation]

extractor = llm.with_structured_output(Extraction)
result = extractor.invoke(
    "Extract (source, relation, target) triples using types Person, Company, Product.\n\n"
    "Text: Ada Lovelace founded Acme in 2019. Acme makes the Nova drone."
)
# relations: (Ada Lovelace)-[FOUNDED]->(Acme), (Acme)-[MAKES]->(Nova drone)
```
Tools that do this for you: LangChain's `LLMGraphTransformer`, LlamaIndex `PropertyGraphIndex` extractors, Neo4j's LLM Graph Builder, Microsoft GraphRAG, LightRAG.

### Quality and cost concerns
- Extraction requires an LLM call per chunk (often several) — the dominant indexing cost.
- Entity resolution errors split or wrongly merge nodes, breaking multi-hop paths.
- Always keep provenance so answers can cite original text.

### Key takeaways
- LLM-based extraction turns chunks into entities and relations; entity resolution merges duplicates.
- Schemas make graphs cleaner and easier to query.
- Indexing cost is dominated by LLM extraction calls.

## Microsoft GraphRAG
**GraphRAG** (Microsoft Research, 2024) builds a knowledge graph from a corpus and pre-computes **community summaries** to answer both specific and **global** questions.

### Indexing pipeline
1. Split documents into **text units** (chunks).
2. LLM-extract **entities**, **relationships** and optional **claims/covariates**; summarise descriptions of entities that appear many times.
3. Run hierarchical community detection with the **Leiden** algorithm to partition the graph into nested communities (levels 0, 1, 2...).
4. Generate a **community report** (LLM summary) for every community at every level.
5. Embed entity descriptions (and text units) for vector lookup. Outputs are stored as Parquet tables.

### Query modes
| Mode | How it works | Best for |
|---|---|---|
| Local search | find entities relevant to the query (via embeddings), then gather their neighbours, relationships, claims, community reports and source text units into the context | specific entity questions ("What does Acme make?") |
| Global search | map-reduce over community reports: each report produces partial answers with importance scores (map), then the best are combined (reduce) | holistic questions ("What are the main themes?") |
| DRIFT search | starts like a light global search over top community reports to produce an initial answer and follow-up questions, then runs local searches for each follow-up | questions needing both breadth and detail |
| Basic search | plain vector search over text units | baseline comparison |

### Why community summaries matter
Plain vector RAG answers "What are the top 5 themes in this dataset?" poorly because no single chunk contains the answer. GraphRAG's community reports are pre-computed summaries of every region of the graph, so global search can synthesise across the **whole corpus**.

### Trade-offs
- Indexing is **expensive** (many LLM calls for extraction and reports) and must be re-run or updated incrementally as data changes.
- Global search over many reports is costly at query time; **dynamic community selection** prunes irrelevant communities first.
- **LazyGraphRAG** (Microsoft, late 2024) defers LLM summarisation to query time, cutting indexing cost dramatically.

```bash
pip install graphrag
graphrag init --root ./ragtest          # creates settings.yaml + prompts
graphrag index --root ./ragtest         # build the graph & community reports
graphrag query --root ./ragtest --method global --query "What are the main themes?"
graphrag query --root ./ragtest --method local  --query "Who founded Acme?"
```

### Key takeaways
- GraphRAG = LLM-extracted graph + Leiden communities + community reports.
- Local search for entity questions; global (map-reduce over reports) for corpus-wide themes; DRIFT combines them.
- Indexing cost is high; LazyGraphRAG and dynamic community selection reduce cost.

## LightRAG and Other Graph-Based RAG Variants
### LightRAG
**LightRAG** (2024, HKU) is a lighter-weight graph RAG:
- Extracts entities and relations per chunk and stores them in a graph plus vector indexes of entities and relations.
- **Dual-level retrieval**: *low-level* keys target specific entities and their attributes; *high-level* keys target broader themes and relations. The LLM extracts both kinds of keywords from the query.
- Query modes: `naive` (plain vector), `local` (entity-focused), `global` (relation/theme-focused), `hybrid` (local + global) and `mix` (graph + vector).
- Supports **incremental updates** by merging new entities/edges into the existing graph — no full rebuild like classic GraphRAG.

### HippoRAG
Inspired by the hippocampus: builds a KG from passages and uses **Personalized PageRank** seeded by query entities to find related passages across hops — strong on multi-hop QA with a single retrieval step.

### Other patterns
- **KG-augmented vector RAG**: retrieve chunks by vectors, then expand with graph neighbours of the entities they mention.
- **Text2Cypher / Text2SPARQL**: the LLM writes a graph query that is executed for precise answers (counts, paths, filters).
- **GraphRetriever / graph traversal retrievers**: vector-search seed nodes, then traverse k hops.
- **RAPTOR** (tree, not graph): recursive clustering + summarisation of chunks into a tree (see RAG Architectures).

### Choosing a variant
| Need | Pick |
|---|---|
| Corpus-wide themes, report-style answers | Microsoft GraphRAG (global/DRIFT) |
| Cheaper indexing, frequent updates | LightRAG, LazyGraphRAG |
| Multi-hop factoid QA | HippoRAG, graph traversal retrievers |
| Precise structured questions over curated data | Text2Cypher over a schema-designed KG |

### Key takeaways
- LightRAG uses dual-level (entity + theme) retrieval and incremental graph updates.
- HippoRAG uses Personalized PageRank for multi-hop retrieval.
- Text2Cypher answers precise structural questions by generating graph queries.

## Graph Databases and Cypher
### Neo4j and Cypher basics
Cypher uses ASCII-art patterns: `()` for nodes, `-[]->` for relationships.
```cypher
// create
CREATE (a:Person {name: "Ada"})-[:FOUNDED {year: 2019}]->(c:Company {name: "Acme"});

// MERGE = match or create (idempotent upserts during ingestion)
MERGE (p:Product {name: "Nova"})
MERGE (c:Company {name: "Acme"})
MERGE (c)-[:MAKES]->(p);

// read: who founded the company that makes Nova?
MATCH (person:Person)-[:FOUNDED]->(c:Company)-[:MAKES]->(:Product {name: "Nova"})
RETURN person.name;

// variable-length paths: anything within 1..3 hops of Ada
MATCH (a:Person {name: "Ada"})-[*1..3]-(x) RETURN DISTINCT x LIMIT 25;

// shortest path
MATCH p = shortestPath((a:Person {name:"Ada"})-[*]-(b:Person {name:"Linus"})) RETURN length(p);

// aggregation
MATCH (c:Company)-[:MAKES]->(p:Product) RETURN c.name, count(p) AS products ORDER BY products DESC;
```
| Clause | Purpose |
|---|---|
| MATCH | find patterns |
| WHERE | filter |
| RETURN | choose output |
| CREATE | always create |
| MERGE | create only if the pattern doesn't exist |
| SET / REMOVE / DELETE | modify / remove |
| WITH | chain query parts |
| OPTIONAL MATCH | like a left outer join |

### Vector + graph in one database
Neo4j (and others like Memgraph, FalkorDB, ArangoDB, Amazon Neptune, Kùzu-style embedded graphs) support **vector indexes** on node properties, enabling hybrid retrieval: vector search finds entry nodes, Cypher expands their neighbourhood.
```cypher
CALL db.index.vector.queryNodes('chunk_embeddings', 5, $queryEmbedding)
YIELD node, score
MATCH (node)-[:MENTIONS]->(e:Entity)-[r]-(related)
RETURN node.text, e.name, type(r), related.name, score;
```

### Key takeaways
- Cypher patterns: `(n:Label {prop})-[:REL]->(m)`; MERGE for idempotent ingestion.
- Variable-length `[*1..3]` and `shortestPath` power multi-hop queries.
- Graph DBs with vector indexes enable vector-seeded graph expansion.

## Text2Cypher and GraphRAG with LangChain/LlamaIndex
### Text2Cypher
An LLM translates natural language into Cypher using the **graph schema**:
```python
from langchain_neo4j import Neo4jGraph, GraphCypherQAChain

graph = Neo4jGraph(url="bolt://localhost:7687", username="neo4j", password="...")
graph.refresh_schema()                          # node labels, relationship types, properties
chain = GraphCypherQAChain.from_llm(
    llm=model, graph=graph,
    validate_cypher=True,                        # fix relationship directions
    allow_dangerous_requests=True,               # you acknowledge the LLM writes DB queries
)
chain.invoke({"query": "How many products does Acme make?"})
```
Best practices:
- Use a **read-only** database user — never let generated queries write.
- Provide the schema and **few-shot examples** of question → Cypher.
- Validate/repair queries; retry on syntax errors; limit result sizes.

### LlamaIndex PropertyGraphIndex retrievers
- `LLMSynonymRetriever` — expands query keywords/synonyms and matches nodes.
- `VectorContextRetriever` — vector search over nodes, then expands paths.
- `TextToCypherRetriever` / `CypherTemplateRetriever` — generated or templated Cypher.

### Agentic GraphRAG
Expose several tools to an agent — `vector_search`, `graph_neighbours(entity)`, `run_cypher(query)` — and let it choose per question. This handles both fuzzy ("tell me about…") and precise ("how many…") questions.

### Key takeaways
- Text2Cypher needs schema context, examples, validation and a read-only DB user.
- LlamaIndex offers synonym, vector-context and text-to-Cypher graph retrievers.
- Agents combining vector and graph tools cover both fuzzy and precise questions.

## Evaluating Graph RAG and When to Use It
### What to measure
- **Answer quality**: correctness, faithfulness, comprehensiveness, diversity (GraphRAG's paper used LLM-judged comprehensiveness, diversity and empowerment vs vector RAG).
- **Multi-hop accuracy** on datasets like HotpotQA, MuSiQue, 2WikiMultiHopQA.
- **Graph quality**: entity extraction precision/recall, duplicate rate, relation accuracy.
- **Cost & latency**: indexing tokens, query tokens, query time.

### When Graph RAG is worth it
✅ Highly connected domains (supply chains, biomedical, legal, finance, IT dependencies).
✅ Questions about relationships, paths, multi-hop reasoning, or entire-corpus themes.
✅ Need for explainability (show the path used).

❌ Simple FAQ or lookup over independent documents — vector or hybrid RAG is cheaper.
❌ Rapidly changing data without budget for re-indexing.
❌ Very small corpora where long context suffices.

### Graph algorithms you may use
| Algorithm | Use in RAG |
|---|---|
| Leiden / Louvain | community detection for summaries |
| PageRank / Personalized PageRank | rank important or query-related nodes |
| Shortest path / BFS | multi-hop connection discovery |
| Node embeddings (node2vec) | similarity over structure |
| Degree centrality | find hub entities |

### A pragmatic architecture
1. Start with hybrid vector RAG and an eval set.
2. Identify failing question types (multi-hop, global).
3. Add a graph for those: entity extraction + graph expansion, or GraphRAG global search for summaries.
4. Route questions to vector, graph or hybrid retrieval with a classifier or agent.

### Key takeaways
- Evaluate answer quality, multi-hop accuracy, graph quality and cost.
- Graph RAG pays off for connected data and relationship/global questions.
- Route questions: not every query needs the graph.
