cask "jaer" do
  version "3.5.2"

  # PLACEHOLDER SHA256 (64 zeros). Do not publish SensorsINI/homebrew-jaer until
  # GitHub Latest is 3.5.2 and these hashes are shasum -a 256 of that tag's DMGs.
  # See packaging/homebrew/README.md. Public tag rebuilds media; do not hash an -rc.
  on_arm do
    sha256 "0000000000000000000000000000000000000000000000000000000000000000"
    url "https://github.com/SensorsINI/jaer/releases/download/#{version}/jAER_macos_aarch64_#{version.tr(".", "_")}.dmg"
  end
  on_intel do
    sha256 "0000000000000000000000000000000000000000000000000000000000000000"
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

  # install4j media 38/39 installerName: "jAER ${compiler:sys.version} Installer".
  # Confirm with hdiutil attach (an rc DMG is OK for the name, not for sha256).
  installer_app = "jAER #{version} Installer.app"

  # Quarantine can SIGKILL the install4j stub before -q; strip xattr first.
  preflight do
    system_command "/usr/bin/xattr", args: ["-cr", "#{staged_path}/#{installer_app}"]
  end

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

    The install4j installer is launched with -q after clearing quarantine.
    Installed tree: #{appdir}/jAER (launcher symlink #{appdir}/jAER.app).

    Updates: brew upgrade --cask jaer
    (do not use jAER Help → Download and install for Homebrew installs).
  EOS

  zap trash: [
    "~/Library/Preferences/net.sf.jaer.plist",
  ]
end
