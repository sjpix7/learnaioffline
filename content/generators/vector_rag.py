"""Computed Vector RAG questions (exact arithmetic)."""
from functools import partial
from .common import Collector
from . import vecmath as vm


def generate(rng, lesson_titles, limit=2000):
    T = [partial(vm.t_cosine, lesson=4), partial(vm.t_dot, lesson=4), partial(vm.t_l2, lesson=4), partial(vm.t_norm, lesson=4),
         partial(vm.t_nearest, lesson=2), partial(vm.t_unit_l2, lesson=4), partial(vm.t_chunks, lesson=3), partial(vm.t_chunks, lesson=3),
         partial(vm.t_precision_recall, lesson=9), partial(vm.t_mrr, lesson=9), partial(vm.t_ndcg, lesson=9), partial(vm.t_memory, lesson=5)]
    return Collector(rng, limit, per_src_cap=300).run_templates(T)
