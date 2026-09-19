# Homebrew cask (macOS)

[Homebrew](https://brew.sh/) named itself after home brewing. The commands still use that vocabulary.

- A **formula** is a *recipe*: Homebrew compiles a CLI tool or library from source (or pours a pre-built **bottle**). jAER is not a formula. We already ship a DMG; we are not asking Homebrew to `ant compile`.
- A **keg** is *one barrel of a specific brew sitting in storage*: one installed version of a formula, e.g. `/opt/homebrew/Cellar/wget/1.21.4`. Upgrade = new keg; uninstall = drop that keg. `brew list` is the inventory of kegs. jAER does **not** become a keg, because it is not a formula.
- The **cellar** is the room where those kegs live (`/opt/homebrew/Cellar`). For GUI casks Homebrew uses a parallel room called **Caskroom** (`/opt/homebrew/Caskroom/jaer/3.5.2`): that is the staged DMG / installer for that version, not the app users launch. Our cask then runs install4j into `#{appdir}/jAER` (usually `/Applications/jAER`) and adds a `jAER.app` symlink. Formula → keg in the Cellar; cask → version folder in Caskroom, then files in Applications.
- A **cask** is a *barrel of finished drink*: you do not brew it, you tap it and pour. In Homebrew that means a short Ruby file that downloads a vendor’s `.dmg` / `.pkg` / `.app` and installs a GUI program. jAER’s file is [`Casks/jaer.rb`](Casks/jaer.rb). Always use `--cask` (`brew install --cask jaer`). Without `--cask`, brew looks for a formula named `jaer` (a keg in the Cellar) and will not find us.
- A **tap** is an *extra spigot* you connect so brew can pour from another barrel. Out of the box, Homebrew only drinks from two barrels: `homebrew/core` (formulae → kegs) and `homebrew/cask` (official GUI apps, e.g. `brew install --cask firefox`). Anyone can publish another barrel on GitHub. `brew tap user/repo` tells brew to also search that barrel.

This folder is the content of that third-party tap. GitHub naming: the repo **must** be `homebrew-<name>`. Org `SensorsINI` + repo `homebrew-jaer` becomes tap `sensorsini/jaer` (org lowercased, `homebrew-` prefix stripped). Users never type `homebrew-jaer` in brew commands.

## Do not create the public tap until Latest 3.5.2

`jaer.rb` is a **template**. SHA256 values are 64 zeros. URLs use `#{version}` → public tag `3.5.2`, not `3.5.2-rc.0`.

The public tag **rebuilds** the DMGs ([`docs/README-releasing-tagging.md`](../../docs/README-releasing-tagging.md)). Filenames stay `jAER_macos_*_3_5_2.dmg`, but SHA256 changes. A public tap that hashes the rc will break the day Latest is published.

Gate: [GitHub Latest](https://github.com/SensorsINI/jaer/releases/latest) is `3.5.2`, and both DMGs are notarized (`spctl` / stapler; [macos-notarization.md](../macos-notarization.md)).

## Fill SHA256 (after Latest)

```bash
gh release download 3.5.2 -p "jAER_macos_3_5_2.dmg" -p "jAER_macos_aarch64_3_5_2.dmg"
shasum -a 256 jAER_macos_3_5_2.dmg jAER_macos_aarch64_3_5_2.dmg
```

Paste into `Casks/jaer.rb` (`on_intel` and `on_arm`). Commit in this repo first. Pin URLs at the version tag, not `/releases/latest/download/` and not Dropbox.

## Confirm the installer `.app` name (Mac)

install4j media 38/39 use `installerName="jAER ${compiler:sys.version} Installer"`. Expect:

`jAER 3.5.2 Installer.app/Contents/MacOS/JavaApplicationStub`

(not `jAER.app`, and not the old long 3.2.0 name). Confirm:

```bash
hdiutil attach ~/Downloads/jAER_macos_aarch64_3_5_2.dmg
ls /Volumes/jAER*
```

An **rc** DMG is OK for the **name** (`VERSION.txt` is already `3.5.2`). It is not OK for SHA256.

`preflight` still runs `xattr -cr` so Gatekeeper quarantine does not SIGKILL the install4j stub before `-q`. Silent install: `-q -dir #{appdir}/jAER`. `postflight` writes `.jaer-packaged-install` (hides in-app **Download and install**) and a `jAER.app` symlink. `depends_on formula: "libusb"` is for live USB on Apple Silicon.

## Personal tap (optional rc or local test)

Homebrew 6+ will not install a cask from a raw file; it needs a tap. A personal tap is a kegerator in your garage: only your Mac sees it. Do **not** `gh repo create SensorsINI/homebrew-jaer` until Latest hashes are in `jaer.rb`.

```bash
brew tap-new tobidelbruck/jaer
cp packaging/homebrew/Casks/jaer.rb "$(brew --repo tobidelbruck/jaer)/Casks/"
# For an rc test only: edit that copy's url to .../download/3.5.2-rc.0/... and sha256 of that DMG.
HOMEBREW_NO_AUTO_UPDATE=1 brew install --cask --yes tobidelbruck/jaer/jaer
```

## Public tap (after Latest hashes)

On a Mac with `gh` logged into SensorsINI:

```bash
gh repo create SensorsINI/homebrew-jaer --public --description "Homebrew cask tap for jAER"
git clone https://github.com/SensorsINI/homebrew-jaer.git
mkdir -p homebrew-jaer/Casks
cp packaging/homebrew/Casks/jaer.rb homebrew-jaer/Casks/
cd homebrew-jaer
git add Casks/jaer.rb
git commit -m "Add jAER 3.5.2 cask (Intel + Apple Silicon)"
git push -u origin HEAD
```

Users:

```bash
brew tap sensorsini/jaer
brew install --cask jaer
brew upgrade --cask jaer
```

Fully qualified: `brew install --cask sensorsini/jaer/jaer`.

## Graduate to homebrew/cask

After URLs and checksums are stable for a couple of releases, open a PR to [Homebrew/homebrew-cask](https://github.com/Homebrew/homebrew-cask). That is like putting the same barrel on the main bar so nobody needs `brew tap` first. Official casks reject `:no_check` SHA256.
