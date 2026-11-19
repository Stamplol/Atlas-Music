"""Check that every @Environment(SomeType.self) refers to an @Observable class."""
import os
import re

ROOT = os.path.join(os.path.dirname(__file__), "AtlasMusic")
env = set()
attrs = {}
for dirpath, _, names in os.walk(ROOT):
    for name in names:
        if not name.endswith(".swift"):
            continue
        text = open(os.path.join(dirpath, name), encoding="utf-8", errors="replace").read()
        env.update(re.findall(r"@Environment\(\s*([A-Za-z_]\w*)\.self", text))
        for m in re.finditer(
            r"(?m)^((?:@[A-Za-z_]\w*(?:\([^\n]*\))?\s+)*)(?:final\s+)?(?:struct|class)\s+([A-Za-z_]\w*)",
            text,
        ):
            attrs[m.group(2)] = m.group(1).strip()

print("Environment types:", sorted(env))
bad = []
for name in sorted(env):
    if name not in attrs:
        print(f"  {name}: not a local type (system/provided)")
        continue
    a = attrs[name]
    ok = "@Observable" in a
    print(f"  {name}: observable={ok}  attrs={a}")
    if not ok:
        bad.append(name)
print("RESULT:", "OK" if not bad else "MISSING @Observable: " + ", ".join(bad))
