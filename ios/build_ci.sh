#!/usr/bin/env bash
# Atlas iOS — reproduce the GitHub Actions build locally on a Mac.
# Usage: bash ios/build_ci.sh
set -euo pipefail

cd "$(dirname "$0")/.."

python3 ios/generate_xcodeproj.py
mkdir -p ios/.build

LINE="$(xcrun simctl list devices available \
  | grep -E '^    iPhone (1[5-9]|[2-9][0-9])' | head -n 1)"
SIM_ID="$(echo "$LINE" | grep -oE '[0-9A-Fa-f]{8}-[0-9A-Fa-f-]{27}' | head -n 1)"
if [ -z "$SIM_ID" ]; then
  echo "No iPhone simulator available. Open Xcode > Settings > Platforms and install an iOS 17+ runtime."
  exit 1
fi
SIM_NAME="$(echo "$LINE" | sed -E "s/^ +(.+) \(${SIM_ID}\).*/\1/")"
xcrun simctl boot "$SIM_ID" || true
echo "Using simulator: $SIM_NAME ($SIM_ID)"

xcodebuild \
  -project ios/AtlasMusic.xcodeproj \
  -scheme AtlasMusic \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath ios/.build/DerivedData \
  CODE_SIGNING_ALLOWED=NO \
  build

rm -rf ios/.build/AtlasMusic.xcresult
xcodebuild test \
  -project ios/AtlasMusic.xcodeproj \
  -scheme AtlasMusic \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "platform=iOS Simulator,id=$SIM_ID" \
  -derivedDataPath ios/.build/DerivedData \
  -resultBundlePath ios/.build/AtlasMusic.xcresult \
  CODE_SIGNING_ALLOWED=NO

echo "OK — build and tests finished. Results: ios/.build/AtlasMusic.xcresult"
