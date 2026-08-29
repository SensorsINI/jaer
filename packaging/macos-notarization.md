# macOS Developer ID and notarization

Unsigned jAER DMGs still work: README already tells users to right-click Open and to install into a **user folder**. Homebrew casks do **not** require notarization.

In-app self-update that replaces an app in `/Applications` is flaky until the app and installer are Developer ID signed and notarized.

**Account type:** individual (Tobi Delbruck), not ETH/UZH/SensorsINI. Gatekeeper will show the personal legal name. No D‑U‑N‑S. ~USD 99/year.

Keep Intel media id 38; Apple Silicon is media id 39.

## Enroll now

Use the [Apple Developer app](https://developer.apple.com/support/app-account) on iPhone, iPad, or Mac (preferred). Website: [developer.apple.com/programs/enroll](https://developer.apple.com/programs/enroll/).

1. Apple Account with **two-factor authentication**. First and last name must be your **legal name** (no nickname/alias).
2. In the Developer app: Account → **Enroll** → **Individual** (or sole proprietor).
3. Confirm legal name, personal email, phone, and a street address (**no P.O. box**).
4. After Apple verifies identity, accept the Apple Developer Program License Agreement and pay the membership (card; price shown in local currency).
5. Wait until membership status is **Active** (often same day; identity review can take longer).

Do **not** create certificates until membership is Active.

Identity delays: Apple Account name does not match government ID, 2FA incomplete, or enrollment started from a shared/org-named Apple ID.

## After membership is Active (not yet)

1. In [Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/certificates/list), create:
   - **Developer ID Application** (sign the `.app`)
   - **Developer ID Installer** (sign the installer/pkg if install4j uses one)
2. Install the certs in Keychain on the Mac that runs `install4jc`.
3. Create an [app-specific password](https://support.apple.com/en-us/102654) for the Apple Account (for `notarytool`). Do not use the main Apple ID password.
4. Configure install4j: Installer → Code Signing / macOS notarization (`notarytool` + staple).
5. Store certs, team ID, and app-specific password like SignPath secrets (`signpath/` on Dropbox, gitignored). Never commit them.
6. Staple the DMG and attach that file to GitHub Releases.

Team ID is under Membership details after enrollment. You will need it for install4j and `notarytool`.
