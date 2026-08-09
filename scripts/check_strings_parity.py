"""Verify every values-*/strings.xml has exactly the same keys as values/strings.xml per module."""
import glob
import re

def norm(p: str) -> str:
    return p.replace("\\", "/")

def keys(p: str) -> set:
    try:
        return set(re.findall(r'name="([^"]+)"', open(p, encoding="utf-8").read()))
    except Exception:
        return set()

base: dict = {}
for p in glob.glob("**/src/main/res/values/strings.xml", recursive=True):
    p = norm(p)
    if "/build/" in p:
        continue
    base[p.split("/src/main/res")[0]] = keys(p)

problems = 0
for p in glob.glob("**/src/main/res/values-*/strings.xml", recursive=True):
    p = norm(p)
    if "/build/" in p:
        continue
    mod = p.split("/src/main/res")[0]
    loc = p.split("/values-")[1].split("/")[0]
    b, k = base.get(mod, set()), keys(p)
    missing = b - k
    extra = k - b
    if missing or extra:
        problems += 1
        print(f"{mod} [{loc}]: missing={sorted(missing)[:4]} extra={sorted(extra)[:4]}")
print("PARITY_PROBLEMS:", problems)
raise SystemExit(1 if problems else 0)
