# PIIPIBUBUS LAB — iOS source project

**Status: source prepared, not compiled or tested with Xcode. No installable IPA has been produced or signed yet.** The current development environment is Linux. The app requires iOS 18.4 or newer because the supported WebKit custom file-picker delegate was introduced in iOS 18.4. Use Xcode 16.3 or newer.

This is a separate iPhone/iPad application. Training runs in the app's own WKWebView window. Google sign-in uses Apple's protected ASWebAuthenticationSession sheet. It does not send Google sign-in through an embedded WebView. Accounts, coaching access and workouts use the existing HTTPS backend. The app requires internet for loading workouts, syncing and uploading videos.

Gallery selections use Apple's PHPicker. Videos are copied into the private app cache, then exported to MP4 using AVFoundation's 1080p preset before the existing private uploader reads them. The Photos original is never edited. Already compatible smaller MP4 files are retained when an export would make them larger. Users get progress, cancellation and an explicit original-file fallback if processing is unsupported. The app needs no broad photo-library access to read the user-selected items.

## Windows-only route

1. Extract `PIIPIBUBUS-iOS-project.zip` on Windows. It contains only the native iOS code and a build workflow; no server secrets, account data or signing keys.
2. Put these files in a GitHub repository you own. A standard macOS runner is free for a public repository. A private repository uses your plan's included Actions allowance; do not enable paid usage if you want to stay free.
3. Run **Actions → Build iOS app → Run workflow**. The workflow uses a cloud Mac, produces an **unsigned** arm64 iPhone `.ipa`, and uploads it as `PIIPIBUBUS-LAB-unsigned-IPA`.
4. If the build fails, keep the build log and fix the reported compiler error before trying to sign anything. This source has not yet passed an Apple SDK build.
5. Download the artifact, extract the IPA, and use Sideloadly on your Windows computer to sign and install it on your USB-connected iPhone with your own Apple account. Enter Apple credentials locally in that software, not into the repository or CI.
6. Follow Apple's developer trust and Developer Mode prompts on the iPhone.
7. A free Apple account's signing profile expires after 7 days. Sideloadly can refresh signing while the computer/device connection is available. This is a personal testing route, not a permanent public distribution service.

There is no GitHub connection available to the current assistant session, so the cloud build has not been run. The ZIP is source, not an IPA and not an application you can install by tapping it on your iPhone.

## Mac route

Install Xcode and XcodeGen (`brew install xcodegen`). From the extracted folder, run `xcodegen generate --spec ios/project.yml --project ios`, open `ios/PIIPIBUBUSLAB.xcodeproj`, choose your Personal Team in Signing & Capabilities, connect your iPhone and Run. For an unsigned CI-style IPA, use `bash ios/scripts/build-unsigned.sh`.

## Security and verification

- App navigation is restricted to the selected HTTPS site. Other user-selected HTTPS links open externally.
- Gallery and JavaScript dialogs require the trusted main frame. No unrestricted JavaScript bridge is installed.
- The native login uses a 256-bit random verifier and a short-lived single-use server grant. Native HTTP responses never contain Supabase access/refresh tokens. The final WebView request receives the server's Secure HttpOnly session cookies.
- HTTP is not allowed through ATS exceptions. Native status calls use the WebView's actual user agent; no browser-only Google policy is bypassed.
- The callback scheme only brings the authentication session back; no credential is in the callback URL.
- Cache copies/exports are private, and files older than 24 hours are cleared at startup. Processing cancellation removes incomplete exports.

Required next verification: successful Xcode compile; signed installation; Google round trip; retained login after reopening; portrait and landscape video compression; audio/video timing; cancellation; original-file integrity; upload and coach visibility; LV/EN controls. Local structural checks do not replace these device checks.

Primary references:
- Apple free-account limits: https://developer.apple.com/help/account/basics/about-your-developer-account/
- Apple custom WebKit file picker: https://developer.apple.com/documentation/webkit/wkuidelegate/webview(_:runopenpanelwith:initiatedbyframe:completionhandler:)
- Apple authentication sessions: https://developer.apple.com/documentation/authenticationservices/aswebauthenticationsession
- GitHub runner costs: https://docs.github.com/en/actions/reference/runners/github-hosted-runners
- Sideloadly official site: https://sideloadly.io/
