"""Migrate NexaFlow's 26 build.gradle.kts files to a Gradle version catalog.

Scans every build file for `"group:artifact[:version]"` dependency literals and
plugin declarations, generates gradle/libs.versions.toml, then rewrites the
files to use catalog accessors (libs.* / libs.plugins.*).

Alias convention: `group-artifact` with dots replaced by dashes. When the same
artifact appears with two versions (androidx.test:core), the older one gets a
`-v<version>` suffix so both stay addressable.
"""
import glob
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)

build_files = [
    p for p in glob.glob("**/build.gradle.kts", recursive=True)
    if "/build/" not in p
]

coord_re = re.compile(r'"([a-zA-Z0-9._-]+):([a-zA-Z0-9._-]+)(?::([0-9][a-zA-Z0-9._+\-]*))?"')
plugin_re = re.compile(r'id\("([a-zA-Z0-9._-]+)"\)(?:\s+version\s+"([0-9][a-zA-Z0-9._+\-]*)")?')

coords = {}  # (group, artifact, version|None) -> [files]
plugins = {}  # plugin_id -> [files]

for p in build_files:
    text = open(p, encoding="utf-8").read()
    for m in coord_re.finditer(text):
        key = (m.group(1), m.group(2), m.group(3))
        coords.setdefault(key, []).append(p)
    for m in plugin_re.finditer(text):
        plugins.setdefault((m.group(1), m.group(2)), []).append(p)

# ---- assign aliases -------------------------------------------------------
def alias_for(group, artifact, version):
    base = (group + "-" + artifact).replace(".", "-")
    # Distinct versions of the same artifact get version-suffixed aliases.
    versions = sorted({v for (g, a, v) in coords if (g, a) == (group, artifact)})
    if len(versions) > 1 and version is not None and version != max(versions):
        return base + "-v" + version.replace(".", "_").replace("-", "_")
    return base

library_aliases = {}  # (g, a, v) -> alias
for (g, a, v) in coords:
    library_aliases[(g, a, v)] = alias_for(g, a, v)

plugin_alias_map = {
    "com.android.application": "android-application",
    "com.android.library": "android-library",
    "org.jetbrains.kotlin.android": "kotlin-android",
    "org.jetbrains.kotlin.plugin.compose": "kotlin-compose",
    "org.jetbrains.kotlin.plugin.serialization": "kotlin-serialization",
    "com.google.dagger.hilt.android": "hilt-android",
    "com.google.devtools.ksp": "ksp",
}

# ---- write gradle/libs.versions.toml --------------------------------------
versions = sorted({v for (_, _, v) in coords if v is not None})
version_refs = {v: "v" + v.replace(".", "_").replace("-", "_") for v in versions}
plugin_versions = sorted({v for (_, v) in plugins if v is not None})
plugin_version_refs = {v: "pv" + v.replace(".", "_").replace("-", "_") for v in plugin_versions}

lines = []
lines.append("[versions]")
for v in versions:
    lines.append('%s = "%s"' % (version_refs[v], v))
for v in plugin_versions:
    lines.append('%s = "%s"' % (plugin_version_refs[v], v))
lines.append("")
lines.append("[libraries]")
for (g, a, v) in sorted(coords):
    alias = library_aliases[(g, a, v)]
    if v is not None:
        lines.append('%s = { module = "%s:%s", version.ref = "%s" }' % (alias, g, a, version_refs[v]))
    else:
        lines.append('%s = { module = "%s:%s" }' % (alias, g, a))
lines.append("")
lines.append("[plugins]")
for (pid, v) in sorted(plugins, key=lambda kv: kv[0]):
    alias = plugin_alias_map[pid]
    if v is not None:
        lines.append('%s = { id = "%s", version.ref = "%s" }' % (alias, pid, plugin_version_refs[v]))
    else:
        lines.append('%s = { id = "%s" }' % (alias, pid))
lines.append("")

toml_dir = os.path.join(ROOT, "gradle")
os.makedirs(toml_dir, exist_ok=True)
with open(os.path.join(toml_dir, "libs.versions.toml"), "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))
print("Wrote gradle/libs.versions.toml with %d libraries, %d plugins, %d versions"
      % (len(coords), len(plugins), len(versions)))

# ---- rewrite build files ---------------------------------------------------
def replace_plugin(text, pid, version):
    alias = plugin_alias_map[pid]
    if version is not None:
        return text.replace(
            'id("%s") version "%s"' % (pid, version),
            "alias(libs.plugins.%s)" % alias,
        )
    return text.replace('id("%s")' % pid, "alias(libs.plugins.%s)" % alias)

total_changed = 0
for p in build_files:
    text = open(p, encoding="utf-8").read()
    original = text
    for (pid, version) in plugins:
        text = replace_plugin(text, pid, version)
    for (g, a, v) in coords:
        alias = library_aliases[(g, a, v)]
        literal = '"%s:%s"' % (g, a) if v is None else '"%s:%s:%s"' % (g, a, v)
        text = text.replace(literal, "libs.%s" % alias)
    if text != original:
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        total_changed += 1
        print("REWROTE", p)
print("Rewrote %d build files" % total_changed)
