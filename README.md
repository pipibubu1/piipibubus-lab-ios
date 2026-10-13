# PIIPIBUBUS LAB — iPhone app

Standalone iOS app window with native gallery selection, local video compression and system Google sign-in.

**Release build succeeded on Xcode 16.4 for arm64 iPhone, minimum iOS 18.4. The IPA is unsigned. Sign it locally before installation. Physical iPhone testing is still required.**

[Download IPA](https://edijs-calisthenics.groovycocoa1.chatgpt.site/downloads/piipibubus-lab-unsigned.ipa) · [Successful build](https://github.com/pipibubu1/piipibubus-lab-ios/actions/runs/38481485178) · [Installation instructions](https://edijs-calisthenics.groovycocoa1.chatgpt.site/install.html)

On Windows, install [Sideloadly](https://sideloadly.io/), follow its Apple driver prerequisites, connect iPhone by USB, load the IPA and use your own Apple account to sign it locally. Trust your developer profile and enable Developer Mode if requested. Apple credentials are not needed by this repository or the training website.

With a free Apple account signing lasts seven days. Sideloadly offers automatic renewal when your computer and phone are available. Opening the IPA directly on iPhone will not install it. This is a test build, not an App Store release.

Compilation and archive contents are verified. Google sign-in, gallery compression and training sync still need physical iPhone testing.

IPA SHA256: `b4b8d360ca567e06b8bbf793aef15c133fb1a27492ddd32848da2afdbdd741e7`

To rebuild: Actions → Build iOS app → Run workflow. The workflow uses no Apple signing credentials. Source: `ios/App/`. Build script for macOS: `bash ios/scripts/build-unsigned.sh`.
