# Release signing

`app/build.gradle.kts` signs release builds with the **project keystore**
(`keystore/nexaflow-release.jks`) whenever it is configured, and falls back to
the debug keystore otherwise (CI / ad-hoc builds stay installable).

## Why the key is what it is

The keystore carries the **same key** that signed the app already installed on
the developer's device. Android only lets a new APK update an existing install
when both share the same signing certificate — so keeping the key means future
releases install **over** the current version without uninstalling or losing
data. Do **not** regenerate a fresh key just to "make it proper": that would
force every existing install to be wiped.

## Files (both gitignored — never commit them)

- `keystore/nexaflow-release.jks` — the keystore itself.
- `keystore/keystore.properties` — local credentials, read by the Gradle build:
  `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

To (re)create the properties file from a fresh checkout, copy the example:

```bash
cp keystore/keystore.properties.example keystore/keystore.properties
# then fill in the real values
```

## CI (GitHub Actions)

The workflow signs with the debug fallback unless the repository has these
secrets configured:

| Secret                     | Value                                                        |
| -------------------------- | ------------------------------------------------------------ |
| `NEXAFLOW_KEYSTORE_BASE64` | `base64 -w0 keystore/nexaflow-release.jks`                  |
| `NEXAFLOW_KEYSTORE_PASSWORD` | store password                                              |
| `NEXAFLOW_KEY_ALIAS`       | `nexaflow`                                                   |
| `NEXAFLOW_KEY_PASSWORD`    | key password                                                 |

The CI step decodes `NEXAFLOW_KEYSTORE_BASE64` into the workspace and the build
reads the `NEXAFLOW_*` env vars (which take precedence over the local file).
