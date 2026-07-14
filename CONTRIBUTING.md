# Contributing

TestSleuth is early-stage. Contributions should keep the roadmap principles intact:

- Collect facts before deriving recommendations.
- Keep the core model framework-neutral.
- Preserve local-first behavior.
- Prefer conservative findings with explicit evidence, risk, and verification guidance.

## Development

Run the full local check before opening changes:

```bash
mvn verify
```

## Changelog

Update `CHANGELOG.md` for each user-visible fix, feature, behavior change, security change, or noteworthy documentation change. Internal-only cleanup does not need a changelog entry unless it affects contributors.

## Design Changes

Material architecture decisions should be captured as ADRs in `docs/adr`.
