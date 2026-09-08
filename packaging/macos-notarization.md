# macOS Developer ID and notarization

Unsigned jAER DMGs still work: README already tells users to right-click Open and to install into a **user folder**. Homebrew casks do **not** require notarization.

In-app self-update that replaces an app in `/Applications` is flaky until the app and installer are Developer ID signed and notarized.

**Account:** Individual, paid, **Active** (Tobias Delbruck). Gatekeeper will show the personal legal name. Not ETH/UZH/SensorsINI.

**Developer ID Application** (G2) is in the Mini **login** keychain and shows as a valid codesigning identity. If Keychain marks the leaf red, install **Developer ID - G2** from [Apple PKI](https://www.apple.com/certificateauthority/) (`DeveloperIDG2CA.cer`). Do not set Always Trust on the leaf.

Keep Intel media id 38; Apple Silicon is media id 39. `install4j/jaer.install4j` has no signing/notarization config yet.

## Next: Installer cert and notarytool (Mac Mini)

Do this on the Mac that will run `install4jc`. Account Holder only. [Apple: Developer ID certificates](https://developer.apple.com/help/account/certificates/create-developer-id-certificates).

1. Copy **Team ID** from [Membership](https://developer.apple.com/account) (10 characters). Put it in a local note under gitignored `signpath/` or similar. Do not commit it if you also store passwords there.
2. Create a Certificate Signing Request: Keychain Access → Certificate Assistant → **Request a Certificate From a Certificate Authority** → your email, Common Name = your name, **Saved to disk** (do not email).
3. [Certificates](https://developer.apple.com/account/resources/certificates/list) → **+** → **Developer ID Application** → upload the CSR → Download the `.cer` → double-click to install into **login** Keychain. Confirm it appears under **My Certificates** with a private key.
4. Repeat for **Developer ID Installer** (same or a second CSR). install4j may use it for the installer/pkg path; the `.app` and typically the DMG use Application.
5. Check: `security find-identity -v -p codesigning` should list `Developer ID Application: Tobias Delbruck (TEAMID)`.
6. [App-specific password](https://support.apple.com/en-us/102654) at [account.apple.com](https://account.apple.com) → Sign-In & Security. Name it e.g. `jaer-notarytool`. Do **not** use the Apple Account password.
7. Store notary credentials in Keychain (replace email, team id, password):

       xcrun notarytool store-credentials "jaer-notarytool" --apple-id "YOUR_APPLE_ID_EMAIL" --team-id "TEAMID" --password "xxxx-xxxx-xxxx-xxxx"

8. After that, wire install4j (Installer → Code Signing / macOS notarization; `notarytool` + staple). Then `ant release` on the Mac, staple the DMGs, attach those files to GitHub Releases.

Never commit `.cer`, `.p12`, CSR, Team ID + password, or the app-specific password. Same rule as SignPath secrets.

## Enrollment (done)

Individual membership via the Apple Developer app / [enroll](https://developer.apple.com/programs/enroll/). Identity verification required a legal-name match (Tobias, not Tobi). Failed ID scans: [developer.apple.com/contact](https://developer.apple.com/contact) → Membership and Account.
