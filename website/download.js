(function () {
  const FALLBACK = "https://github.com/SensorsINI/jaer/releases/latest";
  const PRE_FALLBACK = "https://github.com/SensorsINI/jaer/releases";
  const LABELS = {
    windows: "Windows",
    macos_aarch64: "macOS (Apple Silicon)",
    macos_intel: "macOS (Intel)",
    linux: "Linux",
  };

  function formatSize(bytes) {
    if (typeof bytes !== "number" || bytes <= 0) {
      return "";
    }
    const mb = bytes / (1024 * 1024);
    return mb >= 100 ? Math.round(mb) + " MB" : mb.toFixed(1) + " MB";
  }

  function detectKey() {
    const ua = navigator.userAgent || "";
    const platform = navigator.platform || "";
    const uaData = navigator.userAgentData;
    const hintPlatform = uaData && uaData.platform ? uaData.platform : "";

    if (/Win/i.test(ua) || /Windows/i.test(hintPlatform) || /Win32|Win64/i.test(platform)) {
      return Promise.resolve({ key: "windows", linuxArm: false, macAmbiguous: false });
    }

    const isMac =
      /Mac/i.test(ua) || /macOS/i.test(hintPlatform) || /Mac/i.test(platform);
    if (isMac) {
      if (uaData && typeof uaData.getHighEntropyValues === "function") {
        return uaData
          .getHighEntropyValues(["architecture"])
          .then(function (hints) {
            const arch = hints.architecture || "";
            if (/x86/i.test(arch)) {
              return { key: "macos_intel", linuxArm: false, macAmbiguous: false };
            }
            if (/arm|aarch64/i.test(arch)) {
              return { key: "macos_aarch64", linuxArm: false, macAmbiguous: false };
            }
            return { key: null, linuxArm: false, macAmbiguous: true };
          })
          .catch(function () {
            return { key: null, linuxArm: false, macAmbiguous: true };
          });
      }
      // Safari on macOS does not expose User-Agent Client Hints, and Apple
      // Silicon Safari can still report MacIntel. Do not guess a CPU-specific
      // DMG for ambiguous Macs; send the main button to the release page and
      // let the user choose one of the explicit macOS links below.
      return Promise.resolve({ key: null, linuxArm: false, macAmbiguous: true });
    }

    if ((/Linux/i.test(ua) || /Linux/i.test(hintPlatform)) && !/Android/i.test(ua)) {
      const linuxArm = /aarch64|arm64/i.test(ua) || /arm/i.test(hintPlatform);
      return Promise.resolve({ key: "linux", linuxArm: linuxArm, macAmbiguous: false });
    }

    return Promise.resolve({ key: null, linuxArm: false, macAmbiguous: false });
  }

  function fillButton(btn, meta, release, detected, label) {
    if (!btn) {
      return;
    }
    const asset = detected.key && release && release[detected.key] ? release[detected.key] : null;
    const tag = release && release.tag_name ? release.tag_name : "";

    if (asset && asset.url) {
      btn.href = asset.url;
    } else if (release && release.html_url) {
      btn.href = release.html_url;
    }

    btn.textContent = label;

    const bits = [];
    if (tag) {
      bits.push(tag);
    }
    if (detected.key && LABELS[detected.key]) {
      bits.push(LABELS[detected.key]);
    }
    if (asset) {
      const size = formatSize(asset.size);
      if (size) {
        bits.push(size);
      }
    }
    if (meta && bits.length) {
      meta.hidden = false;
      meta.textContent = bits.join(" · ");
    }
  }

  function apply(latest, detected) {
    const btn = document.getElementById("download-btn");
    const meta = document.getElementById("download-meta");
    const note = document.getElementById("download-note");
    const preSlot = document.getElementById("prerelease-slot");
    const preBtn = document.getElementById("prerelease-btn");
    const preMeta = document.getElementById("prerelease-meta");
    const keys = ["windows", "macos_aarch64", "macos_intel", "linux"];

    keys.forEach(function (key) {
      const a = document.querySelector('#other-platforms a[data-key="' + key + '"]');
      if (!a) {
        return;
      }
      const asset = latest && latest[key];
      if (asset && asset.url) {
        a.href = asset.url;
      }
      if (detected.key === key) {
        a.setAttribute("aria-current", "true");
      }
    });

    fillButton(btn, meta, latest, detected, "Download Stable");

    const pre = latest && latest.prerelease;
    if (pre && preSlot && preBtn) {
      preSlot.hidden = false;
      fillButton(preBtn, preMeta, pre, detected, "Download Prerelease");
    } else if (preSlot) {
      preSlot.hidden = true;
    }

    if (detected.linuxArm) {
      note.hidden = false;
      note.textContent = "Linux installers are x64 only.";
    } else if (detected.macAmbiguous) {
      note.hidden = false;
      note.textContent =
        "macOS browser architecture is ambiguous. Use the macOS Apple Silicon or macOS Intel link below based on About This Mac.";
    }
  }

  Promise.all([
    fetch("latest.json", { cache: "no-store" }).then(function (r) {
      if (!r.ok) {
        throw new Error("latest.json " + r.status);
      }
      return r.json();
    }),
    detectKey(),
  ])
    .then(function (pair) {
      apply(pair[0], pair[1]);
    })
    .catch(function () {
      detectKey().then(function (detected) {
        apply(null, detected);
        const btn = document.getElementById("download-btn");
        if (btn) {
          btn.href = FALLBACK;
          btn.textContent = "Download Stable";
        }
        const preBtn = document.getElementById("prerelease-btn");
        if (preBtn) {
          preBtn.href = PRE_FALLBACK;
        }
      });
    });
})();
