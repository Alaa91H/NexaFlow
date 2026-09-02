"""Crude Kotlin delimiter-balance check used to validate the strict-execution fix."""
import io
import sys

FILES = [
    "core/execution/src/test/java/com/nexaflow/core/execution/workflow/WorkflowInterpreterFlowControlTest.kt",
    "core/execution/src/test/java/com/nexaflow/core/execution/workflow/WorkflowInterpreterExtensionTest.kt",
    "core/execution/src/main/java/com/nexaflow/core/execution/workflow/WorkflowInterpreter.kt",
    "core/execution/src/main/java/com/nexaflow/core/execution/workflow/Workflow.kt",
    "core/execution/src/main/java/com/nexaflow/core/execution/workflow/ExecutionJournal.kt",
]


def check(path):
    text = io.open(path, encoding="utf-8").read()
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == '"':
                    break
                j += 1
            i = j + 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            i = j if j >= 0 else n
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            i = (j + 2) if j >= 0 else n
            continue
        if c in "(){}[]":
            out.append(c)
        i += 1
    s = "".join(out)
    while True:
        m = len(s)
        s = s.replace("()", "").replace("{}", "").replace("[]", "")
        if len(s) == m:
            break
    return s


bad = False
for f in FILES:
    leftover = check(f)
    status = "OK" if not leftover else "UNBALANCED: " + leftover[:40]
    if leftover:
        bad = True
    print("%-95s %s" % (f, status))
print("ALL BALANCED" if not bad else "PROBLEMS FOUND")
sys.exit(1 if bad else 0)