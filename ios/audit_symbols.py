"""Static cross-file audit: duplicate top-level types & unresolved @Environment types.

Run: python ios/audit_symbols.py
This is a smoke test, not a compiler — Swift can only truly be built in Xcode.
"""
import os
import re
from collections import defaultdict

ROOT = os.path.join(os.path.dirname(__file__), "AtlasMusic")
DECL = re.compile(r"^(?:@[A-Za-z_][A-Za-z0-9_]*(?:\([^\n]*\))?\s+)*"
                  r"(?:public\s+|private\s+|internal\s+|fileprivate\s+)*"
                  r"(?:final\s+)?(struct|class|enum|protocol|actor)\s+([A-Za-z_][A-Za-z0-9_]*)", re.M)
ENV = re.compile(r"@Environment\(\s*([A-Za-z_][A-Za-z0-9_]*)\.self")
EXT = re.compile(r"^\s*extension\s+([A-Za-z_][A-Za-z0-9_]*)", re.M)

BUILTIN = {
    "EnvironmentValues", "模型", "View", "String", "Int", "Date", "URL", "Data", "Color",
    "Font", "Shape", "UIApplication", "UITabBar", "UIScreen", "AVAudioSession", "AVAudioEngine",
    "AVAudioPlayerNode", "AVAudioUnitEQ", "AVAudioUnitVarispeed", "AVAudioMixerNode", "Timer",
    "DispatchQueue", "NotificationCenter", "FileManager", "Bundle", "UserDefaults", "WidgetKit",
}


def main():
    types = defaultdict(list)
    envs = defaultdict(list)
    files = []
    for dirpath, _, names in os.walk(ROOT):
        for n in names:
            if n.endswith(".swift"):
                files.append(os.path.join(dirpath, n))
    for path in files:
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        text = open(path, encoding="utf-8", errors="replace").read()
        for kind, name in DECL.findall(text):
            types[name].append((rel, kind))
        for name in ENV.findall(text):
            envs[name].append(rel)

    dups = {k: v for k, v in types.items() if len(v) > 1}
    print(f"Swift files: {len(files)} | distinct top-level types: {len(types)}")
    print(f"Duplicate type names ({len(dups)}):")
    for k, v in sorted(dups.items()):
        print("  ", k, "->", ", ".join(f"{f}({kind})" for f, kind in v))

    known = set(types) | BUILTIN
    missing = {k: v for k, v in envs.items() if k not in known}
    print(f"@Environment types used: {len(envs)} | unresolved ({len(missing)}):")
    for k, v in sorted(missing.items()):
        print("  ", k, "used in", ", ".join(v))

    print("RESULT:", "NEEDS ATTENTION" if (dups or missing) else "OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
