# macOS Developer ID and notarization (deferred)

Unsigned jAER DMGs still work: README already tells users to right-click Open and to install into a **user folder**. Homebrew casks do **not** require notarization.

In-app self-update that replaces an app in `/Applications` is flaky until the app and installer are Developer ID signed and notarized.

When this becomes a priority:

1. Enroll in the [Apple Developer Program](https://developer.apple.com/programs/) (~USD 99/year). An organization account may need a D-U-N-S number.
2. Create **Developer ID Application** and **Developer ID Installer** certificates.
3. Let install4j sign and notarize (Installer → Code Signing / macOS notarization; `notarytool` + staple). Do not commit certificates or the Apple ID app-specific password; store them like SignPath secrets (Dropbox `signpath/` or GitHub Actions secrets).
4. Staple the DMG and attach that file to GitHub Releases.

This is an organization hassle, not a jAER code change. Keep Intel media id 38; Apple Silicon is media id 39.
