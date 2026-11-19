"""Pass through xcodebuild output and emit GitHub `::error::` annotations.

Used in the workflow as:  xcodebuild ... 2>&1 | tee build.log | python3 ios/xcode_annotate.py
With `set -o pipefail` the job still fails when xcodebuild fails.
"""
import os
import re
import sys

PATTERN = re.compile(r"^(.*?):(\d+):(\d+): (error|warning): (.*)$")


def escape_property(value: str) -> str:
    return (value.replace("%", "%25").replace("\r", "%0D")
            .replace("\n", "%0A").replace(":", "%3A").replace(",", "%2C"))


def main() -> int:
    workspace = os.environ.get("GITHUB_WORKSPACE", os.getcwd())
    errors = 0
    for raw in sys.stdin:
        line = raw.rstrip("\n")
        sys.stdout.write(raw)
        sys.stdout.flush()
        m = PATTERN.match(line)
        if not m or m.group(4) != "error":
            continue
        errors += 1
        path, lineno, col, _, message = m.groups()
        if path.startswith(workspace + os.sep):
            path = os.path.relpath(path, workspace).replace(os.sep, "/")
        print(
            f"::error file={escape_property(path)},line={lineno},col={col}::"
            + message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"),
            flush=True,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
