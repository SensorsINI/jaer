(function () {
  const FALLBACK = "https://github.com/SensorsINI/jaer/releases/latest";
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
      return Promise.resolve({ key: "windows", linuxArm: false });
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
              return { key: "macos_intel", linuxArm: false };
            }
            return { key: "macos_aarch64", linuxArm: false };
          })
          .catch(function () {
            return { key: "macos_aarch64", linuxArm: false };
          });
      }
      return Promise.resolve({ key: "macos_aarch64", linuxArm: false });
    }

    if ((/Linux/i.test(ua) || /Linux/i.test(hintPlatform)) && !/Android/i.test(ua)) {
      const linuxArm = /aarch64|arm64/i.test(ua) || /arm/i.test(hintPlatform);
      return Promise.resolve({ key: "linux", linuxArm: linuxArm });
    }

    return Promise.resolve({ key: null, linuxArm: false });
  }

  function apply(latest, detected) {
    const btn = document.getElementById("download-btn");
    const meta = document.getElementById("download-meta");
    const note = document.getElementById("download-note");
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

    const asset = detected.key && latest && latest[detected.key] ? latest[detected.key] : null;
    const tag = latest && latest.tag_name ? latest.tag_name : "";

    if (asset && asset.url) {
      btn.href = asset.url;
      btn.textContent = "Download jAER for " + LABELS[detected.key];
      const bits = [];
      if (tag) {
        bits.push("Version " + tag);
      }
      const size = formatSize(asset.size);
      if (size) {
        bits.push(size);
      }
      if (bits.length) {
        meta.hidden = false;
        meta.textContent = bits.join(" · ");
      }
    } else if (tag) {
      btn.href = latest.html_url || FALLBACK;
      meta.hidden = false;
      meta.textContent = "Version " + tag;
    }

    if (detected.linuxArm) {
      note.hidden = false;
      note.textContent = "Linux installers are x64 only.";
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
        if (detected.key && LABELS[detected.key]) {
          btn.textContent = "Download jAER for " + LABELS[detected.key];
        }
      });
    });
})();
