cask "jaer" do
  version "3.2.0"
  # Replace after the DMG is on GitHub Releases: shasum -a 256 jAER_macos_3_2_0.dmg
  sha256 :no_check

  url "https://github.com/SensorsINI/jaer/releases/download/#{version}/jAER_macos_#{version.tr(".", "_")}.dmg"
  name "jAER"
  desc "Desktop Java application for event cameras and silicon cochleas"
  homepage "https://jaerproject.org"

  livecheck do
    url "https://github.com/SensorsINI/jaer/releases.atom"
    strategy :github_latest
  end

  depends_on formula: "libusb"
  depends_on macos: ">= :catalina"

  # install4j macOS Folder media: a GUI installer inside the DMG.
  # After the first 3.2.0 (or later) DMG is published, confirm the .app name in the
  # mounted volume and adjust installer script / app stanza if needed.
  installer script: {
    executable:   "jAER.app/Contents/MacOS/JavaApplicationStub",
    args:         ["-q"],
    sudo:         false,
    must_succeed: false,
  }

  caveats <<~EOS
    Live USB cameras on Apple Silicon need Homebrew libusb (already a dependency).

    If the silent installer did not run, open the DMG and run the jAER installer,
    preferably into a user folder (unsigned builds; Gatekeeper: right-click Open).

    Intel media is install4j id 38 (jAER_macos_*.dmg). Apple Silicon is id 39
    (jAER_macos_aarch64_*.dmg); add a `on_arm` / `on_intel` url+sha256 split when both exist.

    Updates: brew upgrade --cask jaer
    (do not use jAER Help → Download and install for Homebrew installs).
  EOS

  zap trash: [
    "~/Library/Preferences/net.sf.jaer.plist",
  ]
end
