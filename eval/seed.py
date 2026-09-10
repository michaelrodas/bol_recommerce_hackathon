"""Deterministic corpus seed for the retrieval eval.

Truncates the pgvector table, then ingests the single fixed PDF via the backend's
/api/documents endpoint so every eval run starts from a known state.

Run once (after `docker compose up` is healthy):
    docker compose --profile eval run --rm eval python seed.py
"""
import os

import psycopg2
import requests

BACKEND = os.environ.get("BACKEND_URL", "http://localhost:8080")
PDF = os.environ.get("PDF_PATH", "handbook.pdf")
TABLE = os.environ.get("VECTOR_TABLE", "vector_store")


def truncate() -> None:
    conn = psycopg2.connect(
        host=os.environ.get("PGHOST", "localhost"),
        port=os.environ.get("PGPORT", "5432"),
        dbname=os.environ.get("PGDATABASE", "ragdb"),
        user=os.environ.get("PGUSER", "postgres"),
        password=os.environ.get("PGPASSWORD", "postgres"),
    )
    conn.autocommit = True
    try:
        with conn.cursor() as cur:
            cur.execute(f"TRUNCATE TABLE {TABLE};")
        print(f"truncated {TABLE}")
    except psycopg2.errors.UndefinedTable:
        print(f"{TABLE} not present yet (backend will create it) — skipping truncate")
    finally:
        conn.close()


def upload() -> None:
    if not os.path.exists(PDF):
        raise SystemExit(
            f"PDF not found at '{PDF}'. Copy the handbook to eval/handbook.pdf "
            f"or set PDF_PATH."
        )
    with open(PDF, "rb") as f:
        resp = requests.post(
            f"{BACKEND}/api/documents",
            files={"files": (os.path.basename(PDF), f, "application/pdf")},
            timeout=600,
        )
    resp.raise_for_status()
    print("upload:", resp.json())


if __name__ == "__main__":
    truncate()
    upload()
