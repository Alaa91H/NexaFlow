# Startup benchmark evidence

`.github/workflows/benchmark.yml` runs the cold-start macrobenchmark nightly on
an API 36 emulator and enforces `config/benchmark-budget.json`.

## Status

`BLOCKED` for absolute claims — emulator numbers are a trend proxy only. No
physical-device series exists yet, so the provisional ceiling in the budget
file is **not** a measured p95 and must be replaced by the first ten physical
runs.

## Recording a run

1. Download the `benchmark-evidence` artifact from the workflow run.
2. Copy `*-benchmarkData.json` into `docs/evidence/benchmarks/<YYYY-MM-DD>/`
   together with the runner image, emulator image, commit and build variant.
3. Update the running table below.

## Series

| Date | Commit | Environment | startup median (ms) | p95 (ms) | Notes |
|---|---|---|---|---|---|
| _none yet_ | | | | | |

## Budget policy

- The gate compares each benchmark's `startupMs.median` with the ceiling.
- Lower the ceiling when a run improves it; raising it requires an ADR entry
  explaining the regression and the remediation plan.
- A run that produces no `benchmarkData.json` fails instead of passing
  silently.
