# TODO

Follow-ups for the retrieval eval + tuning work on `feat/retrieval-eval`.

## Unit test strategy (agreed)

**Principle: eval is not a CI gate.** Evaluation is non-deterministic, slow, and needs
Ollama + models + pgvector — everything CI must *not* depend on. So eval runs offline /
on demand and emits **artifacts**; CI runs fast, deterministic tests *against those
artifacts*.

### Eval emits (offline, committed artifacts)
- [x] `eval/best-vdb-config.yml` — tuned `best-k` + `similarity-threshold` (from `sweep.py`).
- [ ] metrics baseline — `recall@k`, `reject_rate`, `MRR` snapshot for regression tracking.

### CI runs (deterministic, no models)
- [ ] **Config-sync test** — assert `application.yml` `rag.top-k` / `rag.similarity-threshold`
      equal `best-vdb-config.yml`. Fails if someone swept but forgot to run `apply_config`
      (config drift). This is the "artifact = expected result" check.
- [ ] **Plumbing / unit tests** (no embeddings needed):
  - [ ] `RagProperties` binds `similarity-threshold`.
  - [ ] `retrieve()` passes the threshold to `SearchRequest`; a missing arg falls back to config.
  - [ ] `/api/retrieve` contract — returns `page` / `source` / `score` / `text`.
  - [ ] `apply_config.py` idempotency — running it twice yields no diff.
  - [ ] `best-vdb-config.yml` schema — keys present, types valid, threshold in [0, 1].

### Quality regression (scheduled, NOT a PR gate)
- [ ] Nightly / on-demand eval compared to the metrics baseline.
- [ ] Assert **tolerances, not equality** (e.g. `recall@k >= baseline - epsilon`). Embedding
      sampling + HNSW approximation make exact snapshots flaky even offline.

## Retrieval quality follow-ups
- [x] Threshold enforced in `answer()` (drop chunks below `rag.similarity-threshold`).
- [ ] Add **real-phrasing** covered cases — current set is handbook-aligned (overfit); `0.66`
      over-rejects real questions that score ~0.57–0.62.
- [ ] Expand negatives 3 -> ~15 (esp. semantically-adjacent) — `reject_rate` is fitted to 3 points.
- [ ] Re-sweep with a realistic `REJECT_TARGET` (e.g. 0.8) once the golden set is richer.
- [ ] Consider a complementary grounding/relevance signal — a scalar threshold is a blunt gate.

## Known limitations (accepted for the hackathon)
- `similarity-threshold: 0.66` is aggressive by design — proves the eval -> apply loop, needs tuning.
- Ingestion appends with no dedup — re-uploading a PDF duplicates chunks.
- Chat is stateless — no multi-turn context.
