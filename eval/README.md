# Retrieval eval harness

Detached Python harness that scores the backend's retrieval quality against a
golden set of questions, over the real pipeline (via `POST /api/retrieve`).

## Files
- `retrieval.yaml` — golden set (covered cases with `expected_pages`/`expected_keyword`, plus negatives).
- `seed.py` — truncates the pgvector table and ingests the fixed handbook PDF.
- `harness.py` — runs each case, prints recall@k / MRR and the covered-vs-negative score gap, writes `results.csv`.

## Prerequisites
1. `docker compose up` (from `backend/`) healthy.
2. Copy the handbook PDF to `eval/handbook.pdf` (gitignored).
3. Run the following Docker commands from the `backend` folder

## Run
```bash
cd backend
docker compose --profile eval run --rm eval python seed.py   # once, seeds corpus
docker compose --profile eval run --rm eval                  # run eval
```

## Calibrate page offset (one-time)
`expected_pages` use the printed "Page N of 180". If Spring AI's chunk
`page_number` metadata differs, the `seal-broken-opened` case (expected 24) will
miss. Set `PAGE_OFFSET` (env in the compose `eval` service) to the constant delta.

## Metrics
- **recall@k** — covered case has its expected page in the top-k chunks.
- **MRR** — mean reciprocal rank of the first correct chunk.
- **score gap** — max(negative top score) vs min(covered hit score); a positive
  window means a similarity threshold can separate covered from not-covered
  (the input to adding `.similarityThreshold(...)` in prod).

## Tune + apply the retrieval config

Two commands turn eval results into the backend's live config.

### 1. Sweep -> `best-vdb-config.yml`
```bash
cd backend
docker compose --profile eval run --rm eval python sweep.py
```
Retrieves each case once at the max k, scores every `(k, threshold)` combo, and
writes the winner to `eval/best-vdb-config.yml`:
```yaml
best-k: 3
similarity-threshold: 0.6
```
**Objective:** maximise recall over covered cases subject to rejecting at least
`REJECT_TARGET` (default `1.0`) of the negatives; if the scores overlap and no
combo meets that, it falls back to the balanced best and prints a WARNING. Ties
break toward smaller k, then higher threshold. Tune via env: `K_GRID`,
`T_START`/`T_STOP`/`T_STEP`, `REJECT_TARGET`.

### 2. Apply -> `application.yml`
```bash
cd backend
docker compose --profile eval run --rm eval python apply_config.py
docker compose up -d --build backend   # restart to load the new config
```
Writes `rag.top-k` and `rag.similarity-threshold` into
`backend/src/main/resources/application.yml`, preserving all other keys and
comments (the file is bind-mounted into the eval container at `/appconfig`).

> **Note:** the written `rag.similarity-threshold` only changes behaviour once the
> backend binds and uses it — i.e. `RagProperties` has a `similarityThreshold`
> field and `RagService.answer()` passes it to `retrieve()`. That app-side wiring
> is the required counterpart to this pipeline.
