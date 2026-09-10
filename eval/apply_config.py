"""Apply the eval-tuned retrieval config to the backend's application.yml.

Reads best-vdb-config.yml (best-k, similarity-threshold) and writes them into the
`rag:` block of application.yml as `top-k` and `similarity-threshold`, preserving
every other key and all comments. Restart the backend afterwards to load it.

Run:
    docker compose --profile eval run --rm eval python apply_config.py
"""
import os

from ruamel.yaml import YAML

BEST = os.environ.get("BEST_CONFIG", "best-vdb-config.yml")
APP_YML = os.environ.get("APP_YML", "/appconfig/application.yml")

yaml = YAML()  # round-trip loader: keeps comments + formatting
yaml.preserve_quotes = True


def main() -> None:
    with open(BEST) as f:
        best = yaml.load(f)
    k = int(best["best-k"])
    threshold = float(best["similarity-threshold"])

    with open(APP_YML) as f:
        app = yaml.load(f)

    rag = app.get("rag")
    if rag is None:
        raise SystemExit(f"no `rag:` block found in {APP_YML}")
    rag["top-k"] = k
    rag["similarity-threshold"] = threshold

    with open(APP_YML, "w") as f:
        yaml.dump(app, f)

    print(f"updated {APP_YML}: rag.top-k={k}, rag.similarity-threshold={threshold}")
    print("restart backend to apply: docker compose up -d --build backend")


if __name__ == "__main__":
    main()
