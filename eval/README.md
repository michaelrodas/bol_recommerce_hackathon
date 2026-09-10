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
