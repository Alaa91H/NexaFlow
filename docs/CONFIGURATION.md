# Configuration reference

This guide describes the new data, sensor and external-input configuration implemented for v3.74. Existing device actions remain subject to the permission/capability checks shown in the builder. Catalog presence is not a guarantee of support on a particular ROM.

## Data actions

Select an operation in the action editor. `input` is literal text; a nonblank `inputPath` reads the workflow context instead. `outputPath` defaults to `$.data.result`. Inputs use at most 16,384 characters, JSON nesting is limited to 32 levels, arrays to 1,024 entries and produced output to a bounded budget. The shared run context separately enforces its 256 KiB budget. Invalid input fails the action without overwriting its previous output. Exit execution without a run context reports `DATA_CONTEXT_REQUIRED`.

| Action | Operations | Additional configuration |
| --- | --- | --- |
| Text | TRIM, UPPER, LOWER, REPLACE, SPLIT, LENGTH, SUBSTRING | `argument` is a literal search/delimiter; `replacement` is literal replacement. `start`/`end` are Unicode code-point indexes, inclusive/exclusive. Case conversion uses Locale.ROOT. |
| Encoding | BASE64_ENCODE/DECODE, URL_ENCODE/DECODE, HEX_ENCODE/DECODE | Text is UTF-8. URL encoding uses form semantics (`+` for a space); it is not whole-URL normalization. |
| Hash | SHA-256, SHA-512 | Hex digest of UTF-8 input. A digest is not encryption or password storage. |
| Random | UUID, TOKEN, INTEGER | TOKEN `argument`: byte count 16–256, default 32; URL-safe Base64 without padding. INTEGER `min`/`max`: inclusive, default 0/100; range width must fit a positive signed Int. Uses SecureRandom. |
| Math | ADD, SUBTRACT, MULTIPLY, DIVIDE, MIN, MAX, ABS, ROUND | `argument` is the second operand, or decimal places 0–32 for ROUND. DECIMAL128 arithmetic; decimal-string output preserves precision. Divide by zero fails. |
| Date/time | FORMAT, PARSE, ADD_SECONDS, NOW | Input is an ISO-8601 instant. PARSE returns epoch milliseconds; ADD_SECONDS uses integer `argument`; FORMAT uses a Java date pattern and `zone` (UTC by default). NOW uses wall-clock time. |
| JSON | POINTER, PRETTY, COMPACT, KEYS | POINTER `argument` follows RFC 6901 (`/items/0/name`, `~0`, `~1`); empty pointer selects the root. Missing/invalid pointers fail. KEYS requires an object. |
| Array | LENGTH, FIRST, LAST, REVERSE, UNIQUE, JOIN, SORT_NUMERIC | Input is a JSON array. JOIN uses `argument` as separator. Numeric sort preserves numeric values. FIRST/LAST of an empty array return null. |

Example chain: JSON POINTER reads `/values` from `{"values":[3,1,2]}` into `$.values`; Array SORT_NUMERIC reads `$.values` and writes `$.sorted`. A later action can use that result. Structured context values are serialized as JSON before transformation.

Replacement and join output are conservatively bounded before allocation, so some large otherwise-valid transformations are rejected. Base64/hex decoding is for text, not a binary-file pipeline. No arbitrary scripting or regular-expression execution is offered by these actions.

## Sensor trigger

Existing modes: proximity, light, shake and step counter. New numeric modes:

| Mode | Reading | Required sensor |
| --- | --- | --- |
| PRESSURE | hPa | Pressure |
| TEMPERATURE | °C | Ambient temperature, not battery temperature |
| HUMIDITY | % | Relative humidity |
| MAGNETIC | µT, vector magnitude | Magnetic field |
| ACCELERATION | m/s², vector magnitude | Linear acceleration |
| GYROSCOPE | rad/s, vector magnitude | Gyroscope |
| GRAVITY | m/s², vector magnitude | Gravity |
| HINGE | Degrees | Hinge angle |

Numeric modes support ABOVE, BELOW, AT_LEAST, AT_MOST and BETWEEN. `threshold` is required; BETWEEN also requires `upperThreshold` at least as large. Nonfinite/invalid values do not match. These are stateful conditions: the monitor can run exit behavior after a matching condition stops matching. Sampling uses Android's normal rate and a 200 ms event debounce; task cooldowns still apply. This is not a precision measurement or high-frequency data acquisition feature. Hardware/OEM delivery must be tested on the target device.

## HTTP action

Only HTTPS is accepted, including local endpoints. Methods: GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS. Bodies apply to POST/PUT/PATCH/DELETE. Headers use one `Name: value` line each, maximum 32 headers and 8 KiB. Authorization and Content-Type can be configured; framing/transport-owned headers and duplicate names are rejected. Custom headers are stripped on a redirect to another origin. Never put credentials in shared task files: only external execution capability tokens are automatically stripped.

`allowPrivateNetwork` defaults to false. Opting in permits validated local/private destinations and adds Android 17 local-network permission to the builder/runtime requirement. All DNS answers are checked and the transport connects only to that validated set. Multicast/unspecified destinations remain invalid. HTTPS certificate/hostname checks remain enabled.

Timeout is 1–60 seconds per request hop; up to five redirects and one to five attempts are bounded independently. Connection failures, 429 and 5xx can retry. A stable idempotency key is sent; duplicate prevention still depends on the server honoring it. Response bodies are capped at 128 KiB; an oversized response fails with empty body and `truncated=true`. The output object contains `status`, `body`, `truncated`, `contentType`, `finalUrl`, `bytesRead`.

## Links, webhooks and imports

Manage external task access in task details: enable, rotate, revoke, then share or recreate the shortcut. A tokenless/invalid link opens review. Valid custom-scheme links still require explicit confirmation, with authorization checked again afterward. Revocation invalidates old links.

Webhooks listen on loopback, require a token and configured path/method, and are not an internet-facing service. New triggers generate tokens; use task details to repair legacy empty-token configurations. Rate/concurrency/header/deadline limits can reject bursts.

Backups and `.nexaflow` single-task files use the same preflight limits. Imports are disabled for review and cannot replace existing IDs silently. Deep-link and webhook tokens are excluded/cleared. Other configuration values may contain secrets and must be reviewed before sharing.
