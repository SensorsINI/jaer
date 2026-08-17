# Homebrew cask (macOS)

This folder is the content of a Homebrew tap (`Casks/jaer.rb`). Publish it as **`SensorsINI/homebrew-jaer`** (GitHub repo name must be `homebrew-jaer`).

## Own tap (do this first)

```bash
# From a clone of this jAER repo, after the macOS DMG is a GitHub Release asset:
# 1. Create SensorsINI/homebrew-jaer (empty repo with Casks/jaer.rb).
# 2. Fill sha256 in jaer.rb (shasum -a 256 jAER_macos_<ver>.dmg and jAER_macos_aarch64_<ver>.dmg).
# 3. Users:
brew tap sensorsini/jaer
brew install --cask jaer
brew upgrade --cask jaer
```

Local smoke test without a tap:

```bash
brew install --cask ./packaging/homebrew/Casks/jaer.rb
```

`depends_on formula: "libusb"` matches live USB on Apple Silicon.

## Graduate to homebrew/cask

After URLs and checksums are stable for a couple of releases, open a PR to [Homebrew/homebrew-cask](https://github.com/Homebrew/homebrew-cask). Official casks reject `:no_check` SHA256.

## Apple Silicon

`jaer.install4j` media id **38** is Intel (`amd64`). Media id **39** is Apple Silicon (`aarch64`). When id 39 DMGs are published, split the cask:

```ruby
on_intel do
  sha256 "..."
  url ".../jAER_macos_3_2_0.dmg"
end
on_arm do
  sha256 "..."
  url ".../jAER_macos_aarch64_3_2_0.dmg"
end
```

Until then, Intel DMG on Apple Silicon needs Rosetta for the bundled JRE; libusb is still native via Homebrew.

## Marker file

If the cask can write into the install tree, add `.jaer-packaged-install` so jAER hides **Download and install**. Homebrew users should use `brew upgrade --cask jaer`.
