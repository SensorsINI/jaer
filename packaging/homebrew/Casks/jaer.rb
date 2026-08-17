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
  depends_on macos: :catalina

  installer_app = "jaer - Java Tools for Address Event Representation Sensors and Processing Installer.app"

  # Unsigned install4j stub is SIGKILL'd under quarantine; strip before -q.
  preflight do
    system_command "/usr/bin/xattr", args: ["-cr", "#{staged_path}/#{installer_app}"]
  end

  # Confirmed 2026-08-17 on the 3.2.0 Apple Silicon DMG (`hdiutil attach`).
  installer script: {
    executable: "#{installer_app}/Contents/MacOS/JavaApplicationStub",
    args:       ["-q", "-dir", "#{appdir}/jAER"],
    sudo:       false,
  }

  postflight do
    File.write("#{appdir}/jAER/.jaer-packaged-install", "homebrew\n")
    FileUtils.ln_sf("#{appdir}/jAER/jaer.app", "#{appdir}/jAER.app")
  end

  uninstall delete: [
    "#{appdir}/jAER",
    "#{appdir}/jAER.app",
  ]

  caveats <<~EOS
    Live USB cameras on Apple Silicon need Homebrew libusb (already a dependency).

    The unsigned install4j installer is launched with -q after clearing quarantine.
    Installed tree: #{appdir}/jAER (launcher symlink #{appdir}/jAER.app).

    Updates: brew upgrade --cask jaer
    (do not use jAER Help → Download and install for Homebrew installs).
  EOS

  zap trash: [
    "~/Library/Preferences/net.sf.jaer.plist",
  ]
end
