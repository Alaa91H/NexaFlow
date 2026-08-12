# -*- coding: utf-8 -*-
"""Add a Java 21 toolchain launcher to every module's unit-test JVM.

Robolectric 4.17's SDK 36/37 sandboxes require Java 21. Instead of pinning
@Config(sdk=[35]), run the test JVM on a Java 21 Gradle toolchain so tests
exercise the real targetSdk (37) — the same the app ships with.

Injects into each module's testOptions.unitTests block:
    all {
        it.javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        })
        it.jvmArgs(...)   # kept if already present
    }
"""
import io, os, re

ROOT = os.path.join(os.path.dirname(__file__), "..")

MODULES = [
    "app",
    "core/automation-engine",
    "core/database",
    "core/execution",
    "core/plugin-sdk",
    "feature/automation-builder",
    "feature/icons",
    "sample-plugins/nfc-toggle",
]

ADD_OPENS = [
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--add-opens=java.base/java.security=ALL-UNNAMED",
    "--add-opens=java.base/java.text=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
]

TOOLCHAIN_SNIPPET = (
    "all {\n"
    "    // Robolectric 4.17's SDK 36/37 sandboxes require Java 21; run the\n"
    "    // unit-test JVM on a Java 21 Gradle toolchain (auto-provisioned by\n"
    "    // Gradle) so tests exercise the real targetSdk (37).\n"
    "    it.javaLauncher.set(javaToolchains.launcherFor {\n"
    "        languageVersion.set(JavaLanguageVersion.of(21))\n"
    "    })\n"
    "    it.jvmArgs(\n"
    + ",\n".join('        "%s"' % a for a in ADD_OPENS) + ",\n"
    "    )\n"
    "}\n"
)

def has_toolchain(path):
    with io.open(path, "r", encoding="utf-8", newline="") as f:
        return "javaLauncher.set" in f.read()

def add_import(path):
    with io.open(path, "r", encoding="utf-8", newline="") as f:
        content = f.read()
    if "import org.gradle.jvm.toolchain.JavaLanguageVersion" in content:
        return False
    # Insert after the first import line (keeps Kotlin DSL valid).
    m = re.search(r'^(import .*)$', content, re.MULTILINE)
    if not m:
        return False
    nl = "\n" if "\n" in content else "\r\n"
    content = content[:m.start()] + m.group(1) + nl + "import org.gradle.jvm.toolchain.JavaLanguageVersion" + content[m.end():]
    with io.open(path, "w", encoding="utf-8", newline="") as f:
        f.write(content)
    return True

def inject(path):
    with io.open(path, "r", encoding="utf-8", newline="") as f:
        content = f.read()
    if "javaLauncher.set" in content:
        print("  already has toolchain, skip")
        return False
    # Replace `unitTests {\n            isIncludeAndroidResources = true\n        }`
    # with the same plus an `all { ... }` block. Match flexible whitespace.
    pat = re.compile(
        r'(unitTests\s*\{)(.*?)(\})',
        re.DOTALL,
    )
    def repl(m):
        head = m.group(1)
        body = m.group(2)
        tail = m.group(3)
        indent = "\n" + (" " * 12)
        return head + body + indent + TOOLCHAIN_SNIPPET.replace("\n", indent) + "\n        " + tail
    new_content, n = pat.subn(repl, content, count=1)
    if n == 0:
        print("  WARN: no unitTests block matched")
        return False
    with io.open(path, "w", encoding="utf-8", newline="") as f:
        f.write(new_content)
    return True

for mod in MODULES:
    path = os.path.join(ROOT, mod, "build.gradle.kts")
    print("== %s ==" % mod)
    add_import(path)
    inject(path)
print("done")
