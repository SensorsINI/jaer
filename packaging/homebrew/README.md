# Homebrew cask (macOS)

This folder is the content of a Homebrew tap (`Casks/jaer.rb`). SHA256 values for 3.2.0 Intel and Apple Silicon DMGs are filled from `updates.xml`. Publish it as **`SensorsINI/homebrew-jaer`** (GitHub repo name must be `homebrew-jaer`).

## Own tap (do this first)

On a Mac with `gh` logged into SensorsINI:

```bash
gh repo create SensorsINI/homebrew-jaer --public --description "Homebrew cask tap for jAER"
git clone https://github.com/SensorsINI/homebrew-jaer.git
mkdir -p homebrew-jaer/Casks
cp packaging/homebrew/Casks/jaer.rb homebrew-jaer/Casks/
cd homebrew-jaer
git add Casks/jaer.rb
git commit -m "Add jAER 3.2.0 cask (Intel + Apple Silicon)"
git push -u origin HEAD
```

Users:

```bash
brew tap sensorsini/jaer
brew install --cask jaer
brew upgrade --cask jaer
```

Local smoke test (Homebrew 6+ requires a tap; do **not** `gh repo create SensorsINI/homebrew-jaer` until the public cask is intended):

```bash
hdiutil attach ~/Downloads/jAER_macos_aarch64_3_2_0.dmg
ls /Volumes/jAER*
brew tap-new tobidelbruck/jaer
cp packaging/homebrew/Casks/jaer.rb "$(brew --repo tobidelbruck/jaer)/Casks/"
HOMEBREW_NO_AUTO_UPDATE=1 brew install --cask --yes tobidelbruck/jaer/jaer
```

The installer `.app` on the 3.2.0 DMG is:

`jaer - Java Tools for Address Event Representation Sensors and Processing Installer.app/Contents/MacOS/JavaApplicationStub`

(not `jAER.app`). Silent install uses `-q -dir #{appdir}/jAER`. Unsigned builds: the cask `preflight` runs `xattr -cr` (Homebrew 6 has no `--no-quarantine` install flag). `postflight` writes `.jaer-packaged-install` and a `jAER.app` symlink next to the install folder.

`depends_on formula: "libusb"` matches live USB on Apple Silicon.

## Graduate to homebrew/cask

After URLs and checksums are stable for a couple of releases, open a PR to [Homebrew/homebrew-cask](https://github.com/Homebrew/homebrew-cask). Official casks reject `:no_check` SHA256 (already filled).

## Marker file

If the cask can write into the install tree, add `.jaer-packaged-install` so jAER hides **Download and install**. Homebrew users should use `brew upgrade --cask jaer`.
