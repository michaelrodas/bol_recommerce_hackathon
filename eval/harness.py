"""Retrieval eval harness (detached, Python).

Hits the backend's /api/retrieve endpoint for each golden case and scores:
  - covered cases: recall@k + MRR (page match, optional keyword guard)
  - negative cases: top-1 similarity score (to size a future threshold)

The endpoint is a thin passthrough to the same RagService.retrieve() the app uses,
so this measures the real retrieval pipeline — no reimplementation.

Run (after seed.py):
    docker compose --profile eval run --rm eval
"""
import os

import pandas as pd
import requests
import yaml
from tabulate import tabulate

BACKEND = os.environ.get("BACKEND_URL", "http://localhost:8080")
TOPK = int(os.environ.get("TOPK", "3"))
# Calibrate once: if metadata page_number != printed "Page N", set this constant.
PAGE_OFFSET = int(os.environ.get("PAGE_OFFSET", "0"))


def load_cases(path: str = "retrieval.yaml") -> list:
    with open(path) as f:
        return yaml.safe_load(f)["cases"]


def retrieve(query: str, topk: int = TOPK, threshold: float = 0.0) -> list:
    # Always send an explicit threshold so eval sees RAW scores. The backend's
    # retrieve() defaults a missing threshold to the prod config value, which would
    # hide every chunk below it and blind the sweep to lower thresholds.
    body: dict = {"query": query, "topK": topk, "threshold": threshold}
    resp = requests.post(f"{BACKEND}/api/retrieve", json=body, timeout=120)
    resp.raise_for_status()
    return resp.json()["results"]


def adjusted_page(res: dict) -> int | None:
    page = res.get("page")
    return page + PAGE_OFFSET if page is not None else None


def score_covered(case: dict, results: list) -> tuple[bool, int | None]:
    """Return (hit, 1-based rank of first hitting chunk)."""
    expected = set(case["expected_pages"])
    kw = case.get("expected_keyword")
    for i, res in enumerate(results):
        page_ok = adjusted_page(res) in expected
        kw_ok = kw is None or kw.lower() in (res.get("text") or "").lower()
        if page_ok and kw_ok:
            return True, i + 1
    return False, None


def main() -> None:
    cases = load_cases()
    rows = []
    covered_total = covered_hits = 0
    rr_sum = 0.0
    covered_hit_scores: list[float] = []
    neg_top_scores: list[float] = []

    for c in cases:
        results = retrieve(c["question"])
        pages = [adjusted_page(r) for r in results]
        top_score = results[0]["score"] if results else None

        if c["category"] == "covered":
            covered_total += 1
            hit, rank = score_covered(c, results)
            if hit:
                covered_hits += 1
                rr_sum += 1.0 / rank
                covered_hit_scores.append(results[rank - 1]["score"])
            rows.append([
                c["id"], "covered", pages, c["expected_pages"],
                round(top_score, 3) if top_score is not None else None,
                "Y" if hit else "N", rank or "",
            ])
        else:
            if top_score is not None:
                neg_top_scores.append(top_score)
            rows.append([
                c["id"], "not_covered", pages, "-",
                round(top_score, 3) if top_score is not None else None, "-", "",
            ])

    headers = ["id", "category", "retrieved_pages", "expected", "top_score", "hit", "rank"]
    print(tabulate(rows, headers=headers))

    recall = covered_hits / covered_total if covered_total else 0.0
    mrr = rr_sum / covered_total if covered_total else 0.0
    print()
    print(f"recall@{TOPK}: {recall:.2f}  ({covered_hits}/{covered_total})")
    print(f"MRR:        {mrr:.3f}")
    if covered_hit_scores:
        print(f"covered hit score:  {min(covered_hit_scores):.3f} .. {max(covered_hit_scores):.3f}")
    if neg_top_scores:
        print(f"negative top score: {min(neg_top_scores):.3f} .. {max(neg_top_scores):.3f}")
        if covered_hit_scores:
            gap_lo, gap_hi = max(neg_top_scores), min(covered_hit_scores)
            verdict = "separable" if gap_hi > gap_lo else "OVERLAP — no clean cut"
            print(f"threshold window:   ({gap_lo:.3f} .. {gap_hi:.3f})  -> {verdict}")

    pd.DataFrame(rows, columns=headers).to_csv("results.csv", index=False)
    print("\nwrote results.csv")


if __name__ == "__main__":
    main()
