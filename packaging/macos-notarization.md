# macOS Developer ID and notarization

Unsigned jAER DMGs still work with right-click Open and a **user folder**. Homebrew casks do **not** require notarization.

In-app self-update that replaces an app in `/Applications` is flaky until the app and installer are Developer ID signed and notarized.

**Account:** Individual, paid, **Active** (Tobias Delbruck). Gatekeeper will show the personal legal name. Not ETH/UZH/SensorsINI.

**Developer ID Application** (G2) is in the Mini **login** keychain and shows as a valid codesigning identity. If Keychain marks the leaf red, install **Developer ID - G2** from [Apple PKI](https://www.apple.com/certificateauthority/) (`DeveloperIDG2CA.cer`). Do not set Always Trust the leaf.

Keep Intel media id 38; Apple Silicon is media id 39.

jAER media is **macosFolder** DMGs, not a `.pkg`. Skip **Developer ID Installer**. install4j **13.0.2** signs with Developer ID Application and notarizes with the **App Store Connect API** (issuer + key ID + `.p8`). It does **not** use `notarytool` Apple ID passwords.

## Secrets on this Mini (repo-root `signpath/`, not Dropbox)

Gitignored (`/signpath/` in `.gitignore`) and listed in `.cursorignore`. Do not paste contents into chat.

| File | What |
|------|------|
| `install4j/license.txt` | install4j license (one line) |
| `signpath/macos-developer-id-application.p12` | Developer ID Application + private key |
| `signpath/AuthKey.p8` | App Store Connect API private key (stable name; Apple’s download name may differ) |
| `signpath/apple-issuer-id.txt` | Issuer UUID |
| `signpath/apple-key-id.txt` | Key ID |
| `signpath/macos-p12-password.txt` | `.p12` export password (one line). Needed for `ant` (no TTY). Or `export JAER_MAC_KEYSTORE_PASSWORD`. |

## Build signed DMGs (this Mini only)

`ant install4j` / `ant release` run `scripts/run-install4jc.sh`. Issuer/key ID stay out of `jaer.install4j`. Windows/CI must not upload unsigned Mac DMGs over these files. GitHub Mac assets: build here, then `ant upload-installers` from this Mini.

Finder: double-click the `.dmg`, then **`jAER <version> Installer`**. That bundle name is `installerName` on media 38/39 (`jaer.install4j`). Get Info does not show notarization. After a successful build:

```bash
xcrun stapler validate currentInstallers/<ver>/jAER_macos_aarch64_*.dmg
spctl -a -t open --context context:primary-signature -vv currentInstallers/<ver>/jAER_macos_aarch64_*.dmg
```

`source=Notarized Developer ID` and a stapled ticket are the proof. `Unnotarized Developer ID` means codesign worked and Apple still rejected or has not stapled. install4j writes `currentInstallers/<ver>/*.dmg.notarization.log` (`status: Invalid` lists unsigned nested natives).

Optional fallback for notarization logs: `xcrun notarytool store-credentials` with the **API key**. Agree to the Xcode license first (`sudo xcodebuild -license` in Terminal).

## Enrollment (done)

Individual membership via the Apple Developer app. Identity verification required a legal-name match (Tobias, not Tobi). CSR via Keychain Access **menu bar** → Certificate Assistant. Website: **Developer ID** → **G2 SUB-CA** → Application. Install `.cer` into **login**.
