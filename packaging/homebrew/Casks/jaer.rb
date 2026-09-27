cask "jaer" do
  version "3.5.3"

  # SHA256 of the public 3.5.3 DMGs (not an rc). Confirm the installer .app name
  # on a Mac before creating SensorsINI/homebrew-jaer.
  # See packaging/homebrew/README.md.
  on_arm do
    sha256 "80b45af56a21aabd2ed1effb44fb3cd38d337a599644c9ac516bc3cfd2f5e917"

    url "https://github.com/SensorsINI/jaer/releases/download/#{version}/jAER_macos_aarch64_#{version.tr(".", "_")}.dmg"
  end
  on_intel do
    sha256 "4844d5fc2086485eb1255e4e3cf4dad90c255141cd6123431d47c4b4ba7ef25e"

    url "https://github.com/SensorsINI/jaer/releases/download/#{version}/jAER_macos_#{version.tr(".", "_")}.dmg"
  end

  name "jAER"
  desc "Desktop Java application for event cameras and silicon cochleas"
  homepage "https://jaerproject.org/"

  livecheck do
    url "https://github.com/SensorsINI/jaer/releases/latest"
    strategy :github_latest
  end

  depends_on formula: "libusb"

  # Homebrew 7 disables `depends_on macos: :catalina` (no replacement).
  # Its oldest supported release is Big Sur, so a Catalina floor cannot be expressed.

  # install4j media 38/39 installerName: "jAER ${compiler:sys.version} Installer".
  # Confirm with hdiutil attach (an rc DMG is OK for the name, not for sha256).
  installer_app = "jAER #{version} Installer.app"

  installer script: {
    executable: "#{installer_app}/Contents/MacOS/JavaApplicationStub",
    args:       ["-q", "-dir", "#{appdir}/jAER"],
    sudo:       false,
  }

  # Quarantine can SIGKILL the install4j stub before -q; strip xattr first.
  # Step blocks cannot interpolate Ruby locals; {{version}} is expanded at install time.
  preflight_steps do
    run "/usr/bin/xattr",
        args:           ["-cr", "{{staged_path}}/jAER {{version}} Installer.app"],
        writable_paths: ["jAER {{version}} Installer.app"]
  end

  postflight_steps do
    write_file "jAER/.jaer-packaged-install", "homebrew\n", base: :appdir
    symlink "jAER/jaer.app", "jAER.app",
            source_base:         :appdir,
            target_base:         :appdir,
            overwrite:           true,
            remove_on_uninstall: true
  end

  uninstall delete: [
    "#{appdir}/jAER",
    "#{appdir}/jAER.app",
  ]

  zap trash: "~/Library/Preferences/net.sf.jaer.plist"

  caveats <<~EOS
    Live USB cameras on Apple Silicon need Homebrew libusb (already a dependency).

    The install4j installer is launched with -q after clearing quarantine.
    Installed tree: #{appdir}/jAER (launcher symlink #{appdir}/jAER.app).

    Updates: brew upgrade --cask jaer
    (do not use jAER Help → Download and install for Homebrew installs).
  EOS
end
