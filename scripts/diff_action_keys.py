#!/usr/bin/env python3
"""Per-action diff: engine-read config keys vs editor-exposed keys.

Parsing notes (learned the hard way):
- Arms can share a multi-line comma-separated header
  (`ActionType.A,\n ActionType.B ->`), and string literals like JSON
  placeholders contain braces, so the parser strips string literals and
  comments before brace-depth counting and joins multi-line headers.
- Engine reads performed before the dispatch when() in the same execute()
  (e.g. a shared `enabled`) are attributed to every arm of that file.
- The supportedTypes fallback applies only to files with no dispatch when()
  (single-purpose handlers like HttpRequestHandler); a declared action with
  no arm in a when()-based handler is reported as ENGINE_NO_ARM instead of
  being polluted with file-wide keys.
- Editor keys inside delegated composables (same file) are resolved.

Prints per action: MISSING (engine reads, editor hides) and DEAD (editor
writes, engine ignores).
"""
import glob
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EDITOR = os.path.join(
    ROOT, "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/ActionConfigEditor.kt"
)
ENGINE = os.path.join(ROOT, "core/execution/src/main/java/com/nexaflow/core/execution")

STR_RE = re.compile(r'"(?:\\.|[^"\\])*"')
CHAR_RE = re.compile(r"'(?:\\.|[^'\\])'")


def clean(line):
    """Strip string literals, char literals, then comments, so brace-depth
counting only sees structural braces. Strings MUST be stripped before
comments — URLs like "https://host" otherwise truncate the line and
unbalance the braces."""
    line = STR_RE.sub('""', line)
    line = CHAR_RE.sub("''", line)
    line = re.sub(r"//.*", "", line)
    return line


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def brace_block(lines, start_idx):
    """Lines of the brace block starting at lines[start_idx] (inclusive).

    Handles Kotlin's brace-on-next-line style: if the opening line has no
    unmatched `{`, scanning continues until depth first drops to 0 *after*
    having gone above it (or the file ends).
    """
    depth = 0
    opened = False
    body = []
    for l in lines[start_idx:]:
        c = clean(l)
        depth += c.count("{") - c.count("}")
        body.append(l)
        if depth > 0:
            opened = True
        if opened and depth <= 0:
            break
    return body


def dispatch_when_body(source):
    """Body of the dispatch when(...) over actions, brace-safe.

    The returned body INCLUDES the when(...) header line, so arm headers sit
    at relative depth 1 regardless of brace style (inline `when (x) {` or
    next-line `{`). Nested dispatch whens sit at depth >= 2 and are excluded
    by split_arms.
    """
    lines = source.splitlines()
    for i, line in enumerate(lines):
        bare = clean(line)
        if (
            re.search(r"\bwhen\s*\(", bare)
            and ("action.type" in bare or "option.actionType" in bare)
        ):
            block = brace_block(lines, i)
            return "\n".join(block).rstrip()
    return ""


def pre_when_config_keys(source):
    """config[...] reads between `override suspend fun execute` and the when."""
    lines = source.splitlines()
    start = None
    when_idx = None
    for i, line in enumerate(lines):
        if "override suspend fun execute" in line:
            start = i
        if start is not None:
            bare = clean(line)
            if re.search(r"\bwhen\s*\(", bare) and "action.type" in bare:
                when_idx = i
                break
    if start is None or when_idx is None:
        return set()
    seg = "\n".join(lines[start:when_idx])
    return set(re.findall(r'(?:action\.)?config\["([a-zA-Z_]+)"\]', seg))


def split_arms(when_body):
    """Split a when() body into ordered {group-key: body} pairs.

    Handles multi-line comma-separated headers at brace depth 1.
    """
    arms = {}
    cur = None
    depth = 0
    buf = []
    header = []

    def flush(cur, buf):
        if cur:
            arms[cur] = "\n".join(buf)

    # dispatch_when_body drops the when(...) header line, so arm headers sit
    # at relative brace depth 1 (nested dispatch whens sit deeper and are
    # correctly excluded).
    ARM_DEPTH = 1
    for line in when_body.splitlines():
        bare = clean(line)
        m = re.match(r"^\s*(ActionType\.[A-Z_]+(?:\s*,\s*ActionType\.[A-Z_]+)*)\s*->", bare) if depth == ARM_DEPTH else None
        cont = re.match(r"^\s*ActionType\.[A-Z_]+\s*,\s*$", bare) if depth == ARM_DEPTH else None
        if m and not header:
            flush(cur, buf)
            names = [n.strip().removeprefix("ActionType.") for n in m.group(1).split(",")]
            cur = ",".join(names)
            buf = [line]
        elif cont and depth == ARM_DEPTH:
            header.append(line)
            continue
        elif header and depth == ARM_DEPTH and "->" in bare:
            header.append(line)
            joined = " ".join(h.strip() for h in header)
            m2 = re.match(r"^\s*(ActionType\.[A-Z_]+(?:\s*,\s*ActionType\.[A-Z_]+)*)\s*->", joined)
            flush(cur, buf)
            names = [n.strip().removeprefix("ActionType.") for n in m2.group(1).split(",")]
            cur = ",".join(names)
            buf = [line]
            header = []
        elif cur is not None:
            buf.append(line)
        depth += clean(line).count("{") - clean(line).count("}")
    flush(cur, buf)
    return arms


def engine_arm_keys(source):
    """{action: (keys, no_arm)} for one handler file."""
    keys = {}
    when_body = dispatch_when_body(source)
    pre = pre_when_config_keys(source)
    has_when = bool(when_body)
    arm_seen = set()
    if has_when:
        for group, body in split_arms(when_body).items():
            arm_keys = set(re.findall(r'(?:action\.)?config\["([a-zA-Z_]+)"\]', body)) | pre
            for name in group.split(","):
                name = name.strip()
                arm_seen.add(name)
                keys.setdefault(name, set()).update(arm_keys)
    fallback_applied = False
    for m in re.finditer(r"supportedTypes[^=]*=\s*setOf\((.*?)\)", source, re.S):
        for name in re.findall(r"ActionType\.([A-Z_]+)", m.group(1)):
            if has_when:
                if name not in arm_seen:
                    keys.setdefault(name, set())  # ENGINE_NO_ARM marker
            else:
                keys.setdefault(name, set()).update(
                    re.findall(r'(?:action\.)?config\["([a-zA-Z_]+)"\]', source)
                )
                fallback_applied = True
    return keys


def editor_arm_keys(editor_source):
    """{action: keys} for the builder editor, resolving delegated composables."""
    when_body = dispatch_when_body(editor_source)
    lines = editor_source.splitlines()
    def_starts = {}
    for i, line in enumerate(lines):
        m = re.match(r"^(?:@\w+\s+|internal\s+|private\s+|public\s+)*fun\s+([A-Z][A-Za-z0-9]+)\(", line)
        if m:
            def_starts[m.group(1)] = i
    def_bodies = {
        name: "\n".join(brace_block(lines, i)) for name, i in def_starts.items()
    }

    skip_callees = {"if", "for", "while", "when", "return", "remember", "LaunchedEffect", "run"}

    def keys_of(body, seen=None):
        seen = seen or set()
        keys = set(re.findall(r'config\["([a-zA-Z_]+)"\]', body))
        keys |= set(re.findall(r'\("([a-zA-Z_]+)"\s+to\b', body))
        keys |= set(re.findall(r'mapOf\("([a-zA-Z_]+)"\s+to', body))
        for callee in set(re.findall(r"\b([A-Z][A-Za-z0-9]+)\(", body)):
            if callee in skip_callees or callee in seen:
                continue
            if callee in def_bodies:
                seen.add(callee)
                keys |= keys_of(def_bodies[callee], seen)
        return keys

    out = {}
    for group, body in split_arms(when_body).items():
        arm_keys = keys_of(body)
        for name in group.split(","):
            out.setdefault(name.strip(), set()).update(arm_keys)
    return out


def main():
    editor_source = read(EDITOR)
    editor_keys = editor_arm_keys(editor_source)

    engine_keys = {}
    for path in glob.glob(os.path.join(ENGINE, "handler/*.kt")):
        for name, keys in engine_arm_keys(read(path)).items():
            engine_keys.setdefault(name, set()).update(keys)

    total_missing = 0
    total_dead = 0
    print(f"{'Action':44} {'MISSING (engine reads, editor hides)':42} DEAD (editor writes, engine ignores)")
    for name in sorted(set(editor_keys) | set(engine_keys)):
        e_keys = engine_keys.get(name, set())
        d_keys = editor_keys.get(name, set())
        missing = sorted(e_keys - d_keys)
        dead = sorted(d_keys - e_keys)
        if missing or dead:
            total_missing += len(missing)
            total_dead += len(dead)
            print(f"{name:44} {','.join(missing) or '-':42} {','.join(dead) or '-'}")
    print(f"\ntotal hidden: {total_missing}, total dead: {total_dead}")


if __name__ == "__main__":
    main()
