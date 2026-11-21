"""Verified graph questions: answers computed with networkx over random small knowledge graphs."""
import networkx as nx
from .common import Collector, make_q, pick_distractors

TYPES = {"Person": ["Ada", "Linus", "Mira", "Ravi"], "Company": ["Acme", "Globex", "Orion", "Initech"],
         "Product": ["Nova", "Kite", "Vega"], "City": ["Pune", "Berlin", "Austin"]}
RELS = [("FOUNDED", "Person", "Company"), ("WORKS_AT", "Person", "Company"), ("INVESTED_IN", "Person", "Company"),
        ("LIVES_IN", "Person", "City"), ("MAKES", "Company", "Product"), ("ACQUIRED", "Company", "Company"),
        ("SUPPLIES", "Company", "Company"), ("LOCATED_IN", "Company", "City"), ("USES", "Company", "Product")]


def rand_graph(rng, n_edges=None):
    n_edges = n_edges or rng.randint(4, 7)
    pool = {t: rng.sample(v, rng.randint(2, len(v))) for t, v in TYPES.items()}
    G = nx.DiGraph()
    triples = []
    tries = 0
    while len(triples) < n_edges and tries < 200:
        tries += 1
        r, st, ot = rng.choice(RELS)
        a, b = rng.choice(pool[st]), rng.choice(pool[ot])
        if a == b or G.has_edge(a, b) or G.has_edge(b, a):
            continue
        G.add_edge(a, b, rel=r); triples.append((a, r, b))
    return G, triples


def show(triples):
    return "\n".join(f"({a})-[:{r}]->({b})" for a, r, b in triples)


def t_entities(rng):
    G, tr = rand_graph(rng)
    val = str(G.number_of_nodes())
    wrong = pick_distractors(val, [str(len(tr)), str(G.number_of_nodes() + 1), str(G.number_of_nodes() - 1), str(2 * len(tr))])
    if not wrong:
        return None
    return make_q(rng, "How many distinct entities (nodes) does this knowledge graph contain?", val, wrong, 1, "calculation", code=show(tr),
                  hint="Count unique subjects and objects, not triples.", expl=f"Entities: {', '.join(sorted(G.nodes))} → {val}.", diff=1, src="kg-n")


def t_degree(rng):
    G, tr = rand_graph(rng)
    node = rng.choice(list(G.nodes))
    kind = rng.choice(["total", "out", "in"])
    d = {"total": G.degree(node), "out": G.out_degree(node), "in": G.in_degree(node)}[kind]
    val = str(d)
    wrong = pick_distractors(val, [str(G.degree(node)), str(G.out_degree(node)), str(G.in_degree(node)), str(d + 1), str(max(0, d - 1)), str(d + 2)])
    if not wrong:
        return None
    label = {"total": "degree (in + out)", "out": "out-degree", "in": "in-degree"}[kind]
    return make_q(rng, f"What is the {label} of ({node})?", val, wrong, 1, "calculation", code=show(tr),
                  hint="Out-degree counts edges leaving the node; in-degree counts edges arriving.", expl=f"({node}): in={G.in_degree(node)}, out={G.out_degree(node)}.",
                  diff=1, src="kg-deg")


def t_hops(rng):
    G, tr = rand_graph(rng, rng.randint(5, 8))
    U = G.to_undirected()
    pairs = [(a, b) for a in G.nodes for b in G.nodes if a < b and nx.has_path(U, a, b) and nx.shortest_path_length(U, a, b) >= 2]
    if not pairs:
        return None
    a, b = rng.choice(pairs)
    d = nx.shortest_path_length(U, a, b)
    path = nx.shortest_path(U, a, b)
    val = str(d)
    wrong = pick_distractors(val, [str(d + 1), str(d - 1), str(d + 2), str(len(tr))])
    if not wrong:
        return None
    return make_q(rng, f"Ignoring edge direction, what is the minimum number of hops between ({a}) and ({b})?", val, wrong, 1, "calculation", code=show(tr),
                  hint="Run a breadth-first search from one entity until you reach the other.", expl=f"Shortest path: {' – '.join(path)} = {d} hops.", diff=2, src="kg-hop")


def t_two_hop(rng):
    G, tr = rand_graph(rng, rng.randint(5, 8))
    starts = [n for n in G.nodes if any(G.out_degree(m) for m in G.successors(n))]
    if not starts:
        return None
    s = rng.choice(starts)
    two = sorted({c for m in G.successors(s) for c in G.successors(m)})
    one = sorted(G.successors(s))
    if not two or set(two) == set(one):
        return None
    val = ", ".join(two)
    wrong = pick_distractors(val, [", ".join(one), ", ".join(sorted(set(one) | set(two))), ", ".join(sorted(set(G.nodes) - set(two) - {s})[:3]), s])
    if not wrong:
        return None
    return make_q(rng, f"Which entities does this Cypher return?\nMATCH ({{name: \"{s}\"}})-->()-->(x) RETURN DISTINCT x.name ORDER BY x.name",
                  val, wrong, 5, "calculation", code=show(tr), hint="Follow exactly two outgoing edges from the start node.",
                  expl=f"First hop from {s}: {', '.join(one)}; second hop: {val}.", diff=3, src="kg-2hop")


def t_rel_count(rng):
    G, tr = rand_graph(rng, rng.randint(5, 8))
    r = rng.choice([t[1] for t in tr])
    c = sum(1 for t in tr if t[1] == r)
    val = str(c)
    wrong = pick_distractors(val, [str(c + 1), str(max(0, c - 1)), str(len(tr)), str(c + 2)])
    if not wrong:
        return None
    return make_q(rng, f"What does this query return?\nMATCH ()-[r:{r}]->() RETURN count(r)", val, wrong, 5, "calculation", code=show(tr),
                  hint="Count relationships with that type.", expl=f"{c} relationship(s) of type {r}.", diff=1, src="kg-rel")


def t_neighbors(rng):
    G, tr = rand_graph(rng)
    n = rng.choice(list(G.nodes))
    nb = sorted(set(G.successors(n)) | set(G.predecessors(n)))
    if not nb:
        return None
    val = ", ".join(nb)
    others = sorted(set(G.nodes) - set(nb) - {n})
    wrong = pick_distractors(val, [", ".join(sorted(G.successors(n))) or "none", ", ".join(sorted(G.predecessors(n))) or "none",
                                   ", ".join(others[:2]) or "none", ", ".join(sorted(set(nb) | {n}))])
    if not wrong:
        return None
    return make_q(rng, f"Which entities are 1-hop neighbours of ({n}) in either direction?\nMATCH ({{name: \"{n}\"}})--(x) RETURN DISTINCT x.name",
                  val, wrong, 5, "calculation", code=show(tr), hint="-- (no arrow) matches relationships in both directions.",
                  expl=f"Neighbours of {n}: {val}.", diff=2, src="kg-nb")


def t_hub(rng):
    G, tr = rand_graph(rng, rng.randint(6, 8))
    deg = dict(G.degree())
    top = max(deg.values())
    hubs = [k for k, v in deg.items() if v == top]
    if len(hubs) != 1:
        return None
    val = hubs[0]
    wrong = pick_distractors(val, [k for k in sorted(deg, key=deg.get, reverse=True) if k != val])
    if not wrong:
        return None
    return make_q(rng, "Which entity is the hub with the highest degree centrality?", val, wrong, 7, "calculation", code=show(tr),
                  hint="Count all edges touching each entity.", expl="Degrees: " + ", ".join(f"{k}={v}" for k, v in sorted(deg.items())), diff=2, src="kg-hub")


def t_components(rng):
    G, tr = rand_graph(rng, rng.randint(3, 6))
    c = nx.number_weakly_connected_components(G)
    val = str(c)
    wrong = pick_distractors(val, [str(c + 1), str(max(1, c - 1)) if c > 1 else "2", str(G.number_of_nodes()), str(c + 2)])
    if not wrong:
        return None
    return make_q(rng, "Ignoring direction, into how many disconnected groups (connected components) does this graph fall?", val, wrong, 7, "calculation",
                  code=show(tr), hint="Entities connected by any path belong to the same component.",
                  expl="Components: " + " | ".join(", ".join(sorted(cc)) for cc in nx.weakly_connected_components(G)), diff=2, src="kg-cc")


def generate(rng, lesson_titles, limit=1700):
    return Collector(rng, limit, per_src_cap=300).run_templates([t_entities, t_degree, t_hops, t_two_hop, t_rel_count, t_neighbors, t_hub, t_components])
