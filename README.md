# PIIPIBUBUS LAB — iPhone app

Standalone iOS app window with native gallery selection, local video compression and system Google sign-in.

**Premium feedback update: iOS 1.1/build 2 compiled successfully for arm64 iPhone, minimum iOS 18.4. The IPA is unsigned; sign it locally before installation. Android 2.2/versionCode 4 also compiled successfully. Physical-device testing remains required.**

[Download IPA](https://edijs-calisthenics.groovycocoa1.chatgpt.site/downloads/piipibubus-lab-unsigned.ipa) · [Successful build](https://github.com/pipibubu1/piipibubus-lab-ios/actions/runs/38582090666) · [Installation instructions](https://edijs-calisthenics.groovycocoa1.chatgpt.site/install.html)

On Windows, install [Sideloadly](https://sideloadly.io/), follow its Apple driver prerequisites, connect iPhone by USB, load the IPA and use your own Apple account to sign it locally. Trust your developer profile and enable Developer Mode if requested. Apple credentials are not needed by this repository or the training website.

With a free Apple account signing lasts seven days. Sideloadly offers automatic renewal when your computer and phone are available. Opening the IPA directly on iPhone will not install it. This is a test build, not an App Store release.

Compilation and archive contents are verified. Google sign-in, gallery compression and training sync still need physical iPhone testing.

IPA SHA256: `2967fb9224704faa68b6d7f89fb721a5486667405918eb4271122d499d634af1`

To rebuild: Actions → Build iOS app → Run workflow. The workflow uses no Apple signing credentials. Source: `ios/App/`. Build script for macOS: `bash ios/scripts/build-unsigned.sh`.

Native haptics are optional, off by default, trusted-origin scoped and rate limited. New native feedback requires the updated APK/IPA; web UI updates alone cannot add native APIs. Signing keys and Apple credentials remain private and are never uploaded to this repository or CI.

Android source: `android/`. CI build: https://github.com/pipibubu1/piipibubus-lab-ios/actions/runs/38582613587. The website APK is signed locally with the original certificate for update compatibility.
