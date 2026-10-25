# Required checks

The workflow is `.github/workflows/nexaflow-ci.yml`. Its `lint` job gates its `build` job. A successful tag publication must correspond to the exact intended commit.

## Source and unit gates

```text
python scripts/auto_fix.py --check
python scripts/check_strings_parity.py
python scripts/check_design_tokens.py --self-test
python scripts/check_design_tokens.py
python scripts/audit_catalog_and_releases.py catalog
python -m unittest discover -s scripts/tests -p test_exported_components.py
./gradlew detekt lintDebug testDebugUnitTest
```

### Design-token gate

`scripts/check_design_tokens.py` fails on any raw `Color(0x…)`, `Color(r, g, b)`
or `RoundedCornerShape(n.dp)` in a shipped source outside the theme packages
(`core/ui-components/.../ui/theme/` and `app/.../ui/theme/`). Pre-existing
offenders are pinned in `scripts/design_token_baseline.txt`, one
`path|literal` anchor each; the baseline must only ever shrink, and a
drifted anchor fails the gate so the list cannot rot silently.

The gate belongs in CI next to the hardcoded-text and vendor-neutrality
gates, and its self-test in `scripts/tests/test_check_design_tokens.py`
belongs beside theirs. It is not yet wired into
`.github/workflows/nexaflow-ci.yml` — the branch that introduced it could
not write files under `.github/workflows/` — so the wiring is still owed.

CI also runs resource-gate self-tests and `check_resources.py`. Version tags must match the newest changelog heading. New data/sensor/header tests cover outcomes, malformed inputs and chained output behavior.

## Build and package gates

Build debug/release APKs and the release AAB. Audit the merged release manifest with `scripts/audit_exported_components.py --merged app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`. CI verifies permissions, dependency metadata, APK signatures, production certificate identity for tags, native alignment, zipalign and bundletool validity.

## Separate device evidence

Run instrumentation tests, including `FrameworkPermissionGatesTest`, on an emulator/device. Hardware sensors, OEM restrictions, background operation and restoration need real-device checks. A passing unit suite does not imply these were performed. The [validation record](VALIDATION.md) must state which checks actually ran.

Repository branch protection is a server setting; this document does not assert that it is configured. Maintain required GitHub checks separately from workflow code.

## Automated dependency currency

Dependabot checks Gradle libraries and GitHub Actions daily. Its pull requests are watched by the `Merge Verified Dependency Updates` workflow and are squash-merged only after all GitHub pull-request checks pass. Versions remain pinned in the catalog and workflow files so each accepted update is reproducible and covered by dependency verification; dynamic Gradle version selectors are intentionally not used.
