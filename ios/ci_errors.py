"""Fetch and summarize errors from the latest iOS GitHub Actions run.

No auth needed for public repos. Run: python ios/ci_errors.py
"""
import json
import os
import subprocess
import sys
import time
import urllib.request

REPO = "Stamplol/Atlas-Music"
BRANCH = "ios-swiftui-port"
API = f"https://api.github.com/repos/{REPO}"
HEADERS = {"User-Agent": "atlas-ios-ci-helper", "Accept": "application/vnd.github+json"}


def get(url):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def main():
    runs = json.loads(get(f"{API}/actions/runs?branch={BRANCH}&per_page=5"))
    if not runs.get("workflow_runs"):
        print("No workflow runs found")
        return 1
    run = runs["workflow_runs"][0]
    if "--wait" in sys.argv:
        local_sha = subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=os.path.dirname(__file__) + "/..", text=True
        ).strip()
        deadline = time.time() + 480
        while time.time() < deadline:
            runs = json.loads(get(f"{API}/actions/runs?branch={BRANCH}&per_page=5"))
            match = next((r for r in runs["workflow_runs"] if r["head_sha"] == local_sha), None)
            if match and match["status"] == "completed":
                run = match
                break
            if match:
                run = match
            time.sleep(10)
    print(f"Run {run['id']}  status={run['status']}  conclusion={run.get('conclusion')}")
    print(f"URL: {run['html_url']}")
    sha = run["head_sha"]
    checks = json.loads(get(f"{API}/commits/{sha}/check-runs?per_page=100"))
    annotations = []
    for check in checks.get("check_runs", []):
        if check.get("conclusion") not in ("failure", None):
            continue
        try:
            data = json.loads(get(f"{API}/check-runs/{check['id']}/annotations"))
        except Exception as e:
            print(f"  (annotations unavailable for {check['name']}: {e})")
            continue
        annotations.extend(data)
    print(f"--- {len(annotations)} failure annotations ---")
    seen = set()
    for a in annotations:
        msg = (a.get("message") or "").strip()
        key = (a.get("path"), a.get("start_line"), msg)
        if key in seen:
            continue
        seen.add(key)
        loc = f"{a.get('path')}:{a.get('start_line')}" if a.get("path") else "?"
        print(f"{loc}: {msg[:400]}")
        for extra in (a.get("details") or "").splitlines()[:4]:
            print(f"    {extra.strip()[:300]}")
    print(f"Run page: {run['html_url']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
