cask "jaer" do
  version "3.2.0"

  on_arm do
    sha256 "dab9ef4892b095ee7e2f74581609fcc3a2629a8db8beb95336d4b93de79d3b07"
    url "https://github.com/SensorsINI/jaer/releases/download/#{version}/jAER_macos_aarch64_#{version.tr(".", "_")}.dmg"
  end
  on_intel do
    sha256 "c3493d50a2c2b25147c376e830a7293fb463f285559e6cb45e9cd89ead863277"
    url "https://github.com/SensorsINI/jaer/releases/download/#{version}/jAER_macos_#{version.tr(".", "_")}.dmg"
  end

  name "jAER"
  desc "Desktop Java application for event cameras and silicon cochleas"
  homepage "https://jaerproject.org"

  livecheck do
    url "https://github.com/SensorsINI/jaer/releases/latest"
    strategy :github_latest
  end

  depends_on formula: "libusb"
  depends_on macos: ">= :catalina"

  # install4j macOS Folder media: GUI installer inside the DMG. Confirm the
  # .app name after `hdiutil attach` if silent install fails (unsigned; Gatekeeper).
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

    Updates: brew upgrade --cask jaer
    (do not use jAER Help → Download and install for Homebrew installs).
  EOS

  zap trash: [
    "~/Library/Preferences/net.sf.jaer.plist",
  ]
end
