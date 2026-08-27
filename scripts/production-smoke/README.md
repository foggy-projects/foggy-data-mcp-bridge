# Production OAuth smoke

This harness starts isolated Keycloak 26.3.2 and PostgreSQL 15 containers, launches the
current `foggy-mcp-launcher` JAR, and checks the real OAuth resource-server boundary.
It uses development-only fixed credentials and loopback ports `18090`, `18091`, and
`15433`; never reuse them outside this smoke environment.

```bash
python scripts/production-smoke/run_smoke.py
```

Use `--skip-build` when the current Launcher JAR already exists. Containers and their
anonymous data are removed on completion; `--keep` leaves them running for diagnosis.
The script validates PostgreSQL connectivity, protected-resource metadata, missing-token,
role, scope and audience rejection, successful analyst/admin calls, Keycloak signing-key
rotation/JWKS refresh, and absence of issued raw tokens from Launcher logs.