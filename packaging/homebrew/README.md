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

Local smoke test without a tap (Mac):

```bash
# Confirm the installer .app name inside the DMG, then:
hdiutil attach ~/Downloads/jAER_macos_aarch64_3_2_0.dmg
ls /Volumes/jAER*
brew install --cask ./packaging/homebrew/Casks/jaer.rb
```

If `jAER.app/Contents/MacOS/JavaApplicationStub` is wrong, fix the `installer script` executable path in `jaer.rb` and copy it to the tap.

`depends_on formula: "libusb"` matches live USB on Apple Silicon.

## Graduate to homebrew/cask

After URLs and checksums are stable for a couple of releases, open a PR to [Homebrew/homebrew-cask](https://github.com/Homebrew/homebrew-cask). Official casks reject `:no_check` SHA256 (already filled).

## Marker file

If the cask can write into the install tree, add `.jaer-packaged-install` so jAER hides **Download and install**. Homebrew users should use `brew upgrade --cask jaer`.
