#!/bin/bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$project_root"
command -v xcodebuild >/dev/null || { echo 'This build needs macOS and Xcode 16.3 or newer.' >&2; exit 1; }
command -v xcodegen >/dev/null || { echo 'Install XcodeGen first: brew install xcodegen' >&2; exit 1; }
xcodegen generate --spec ios/project.yml --project ios
xcodebuild -project ios/PIIPIBUBUSLAB.xcodeproj -scheme PIIPIBUBUSLAB \
  -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath .ios-build CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY='' build
mkdir -p ios-output/Payload
cp -R .ios-build/Build/Products/Release-iphoneos/PIIPIBUBUSLAB.app ios-output/Payload/
cd ios-output
ditto -c -k --keepParent Payload PIIPIBUBUS-LAB-unsigned.ipa
printf '%s\n' 'Built an unsigned IPA. Sign it locally before installing. It is not an App Store or TestFlight release.'
