# Optional Linux .deb (not Ubuntu/Debian official)

Not for **3.5**. Not in the **3.6** winget/Homebrew first ship. Revisit after those catalogs work. Keep the Unix `.sh`.

Do **not** start with Debian ftp-master or Ubuntu archive. Bundled Temurin and USB cameras fight Debian Java policy and snap/flatpak sandboxes (`raw-usb` / `--device=all`).

Linux channel remains the install4j `.sh` installer plus in-app update. install4j Deb is an **archive**: no wizard, default path (typically `/opt`), and `sudo apt install` must not launch a GUI. `dpkg -i` does not pull Depends; `sudo apt install ./file.deb` does. A real `apt install jaer` needs a signed repo.

A later `.deb` can wrap that payload into `/opt/jaer` with **no sandbox**:

```
Package: jaer
Section: science
Priority: optional
Maintainer: SensorsINI <https://github.com/SensorsINI/jaer>
Depends: libusb-1.0-0
Architecture: amd64
Description: Desktop Java application for event cameras
```

Host the `.deb` on GitHub Releases next to the `.sh`. An apt repo (GPG key, `Packages.gz`) is only worth it if you want `apt install jaer`; that is extra signing and hosting.

Write `.jaer-packaged-install` into `/opt/jaer` so the in-app updater tells people to use the distro package instead of Download and install.
