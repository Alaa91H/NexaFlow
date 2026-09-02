# Required Status Checks

Branch protection for `main` should require **all** of the following check names
from the `Android CI` workflow (`.github/workflows/android-ci.yml`) before a
merge is allowed, and before a tag push is considered valid for release.

## Jobs

| Check name | What it proves |
|---|---|
| `lint` | Resource hygiene (orphan/banned resources), string parity across all 11 locales, **catalog parity** (every trigger/action enum value appears exactly once in the builder picker), **tag hygiene on version tags** (tag ↔ `CHANGELOG.md` entry), resource-gate unit tests + pytest suite, Detekt static analysis, zero-tolerance Android Lint, unified resource gate. |
| `build` | Full unit-test suite, production-signing gate on version tags, debug + release APK and AAB builds, APK signature verification (v2/v3 + certificate-fingerprint match on tags), phone-permission manifest check, Gradle dependency verification freshness, 16 KB page-size alignment, zipalign verification, bundletool bundle validation + size report, native-library audit, artifact uploads, and — on version tags — the GitHub Release publication with generated release notes. |

## Setup (repository owner)

1. **Settings → Branches → Add branch protection rule** for `main`:
   - Require a pull request before merging *(optional, owner's choice)*.
   - **Require status checks to pass before merging** → select `Android CI / lint`
     and `Android CI / build` (exact names as reported by the checks API).
   - Require branches to be up to date before merging *(recommended)*.
2. **Settings → Environments (optional)**: gate tag-triggered runs behind a
   `release` environment for manual approval of production releases.
3. Keep `NEXAFLOW_KEYSTORE_BASE64`, `NEXAFLOW_KEYSTORE_PASSWORD`,
   `NEXAFLOW_KEY_ALIAS`, and `NEXAFLOW_KEY_PASSWORD` configured as Actions
   secrets — version tags **fail by design** without them (release builds must
   be production-signed).

## Nightly schedule

The workflow also runs on a nightly cron (`0 6 * * *`) against `main` to catch
dependency rot and flaky tests between releases. Nightly failures do not block
development but must be triaged within the next release cycle.
