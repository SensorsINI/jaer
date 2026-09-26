# Linux .deb (GitHub Release asset, not an apt repo)

First release that includes the file is **3.6.0**. Do not upload it onto the 3.5.2 release. Snapshot may carry it after the media is on `master`. Keep the Unix `.sh` as the homepage download.

install4j media id **45** (`linuxDeb`, `setupType="none"`) builds `jAER_linux-amd64_<version_underscores>.deb` next to `jAER_unix_*.sh`. It is an archive into `/opt/jAER` (launcher `jaer`). `apt` must not open a GUI. `Depends: libusb-1.0-0`. `dpkg -i` does not install Depends; `sudo apt install ./file.deb` does.

```text
wget -O jAER_linux-amd64_3_6_0.deb \
  https://github.com/SensorsINI/jaer/releases/download/3.6.0/jAER_linux-amd64_3_6_0.deb
sudo apt install ./jAER_linux-amd64_3_6_0.deb
```

There is no `apt install jaer` by name. That needs a signed apt repo (GPG key and `Packages` index), which this ship does not add.

Do not start with Debian ftp-master or Ubuntu archive. Bundled Temurin and USB cameras fight Debian Java policy and snap/flatpak sandboxes.

`postinst` writes `/opt/jAER/.jaer-packaged-install` so the in-app updater hides **Download and install**. Upgrades are a newer `.deb` from the release, not Help → Download and install. In-app updates for `.sh` installs stay on media id 37 (`scripts/generate-updates-xml.py`).
