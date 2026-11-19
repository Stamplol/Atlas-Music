"""Pass through xcodebuild output and emit GitHub `::error::` annotations.

Used in the workflow as:  xcodebuild ... 2>&1 | tee build.log | python3 ios/xcode_annotate.py
With `set -o pipefail` the job still fails when xcodebuild fails.

Emits both file/line annotations for Swift diagnostics and generic annotations for
linker, simulator-launch and test-runner failures (which have no source location).
"""
import os
import re
import sys

PATTERN = re.compile(r"^(.*?):(\d+):(\d+): (error|warning): (.*)$")
GENERIC = re.compile(r"(error:|\*\* TEST FAILED \*\*|fatal error|Test suite .* failed|"
                     r"Could not launch|test host app.*(crash|failed))", re.I)


def escape_property(value: str) -> str:
    return (value.replace("%", "%25").replace("\r", "%0D")
            .replace("\n", "%0A").replace(":", "%3A").replace(",", "%2C"))


def main() -> int:
    workspace = os.environ.get("GITHUB_WORKSPACE", os.getcwd())
    emitted = 0
    for raw in sys.stdin:
        line = raw.rstrip("\n")
        sys.stdout.write(raw)
        sys.stdout.flush()
        m = PATTERN.match(line)
        if m and m.group(4) == "error":
            path, lineno, col, _, message = m.groups()
            if path.startswith(workspace + os.sep):
                path = os.path.relpath(path, workspace).replace(os.sep, "/")
            print(
                f"::error file={escape_property(path)},line={lineno},col={col}::"
                + message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"),
                flush=True,
            )
            emitted += 1
        elif GENERIC.search(line) and emitted < 80:
            print("::error::" + line.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")[:600], flush=True)
            emitted += 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
