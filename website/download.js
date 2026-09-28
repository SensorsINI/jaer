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

  function formatDay(iso) {
    if (!iso) {
      return "";
    }
    const d = new Date(iso);
    if (isNaN(d.getTime())) {
      return "";
    }
    return d.toISOString().slice(0, 10);
  }

  function formatBuiltTip(iso) {
    if (!iso) {
      return "";
    }
    const d = new Date(iso);
    if (isNaN(d.getTime())) {
      return "";
    }
    return d.toISOString().slice(0, 10) + " " + d.toISOString().slice(11, 16) + " UTC";
  }

  function appendSep(parent) {
    parent.appendChild(document.createTextNode(" · "));
  }

  function notesHref(tag) {
    if (!tag || tag === "snapshot") {
      return "";
    }
    const publicVer = String(tag).replace(/-rc\.[0-9]+$/, "");
    return (
      "https://github.com/SensorsINI/jaer/blob/master/release-notes/jaer-" +
      publicVer +
      "-release-notes.md"
    );
  }

  function fillMetaLine(meta, parts) {
    if (!meta) {
      return;
    }
    meta.hidden = false;
    meta.textContent = "";
    if (!parts.length) {
      return;
    }
    parts.forEach(function (node, i) {
      if (i) {
        appendSep(meta);
      }
      meta.appendChild(node);
    });
  }

  function versionNode(tag, htmlUrl) {
    const href = htmlUrl || notesHref(tag);
    if (!tag) {
      return null;
    }
    if (!href) {
      return document.createTextNode(tag);
    }
    const a = document.createElement("a");
    a.href = href;
    a.title = "Release notes for " + tag;
    a.textContent = tag;
    return a;
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

    if (!meta) {
      return;
    }
    const parts = [];
    const ver = versionNode(tag, notesHref(tag));
    if (ver) {
      parts.push(ver);
    }
    if (detected.key && LABELS[detected.key]) {
      parts.push(document.createTextNode(LABELS[detected.key]));
    }
    if (asset) {
      const size = formatSize(asset.size);
      if (size) {
        parts.push(document.createTextNode(size));
      }
    }
    fillMetaLine(meta, parts);
  }

  function apply(latest, detected) {
    const btn = document.getElementById("download-btn");
    const meta = document.getElementById("download-meta");
    const note = document.getElementById("download-note");
    const preSlot = document.getElementById("prerelease-slot");
    const preBtn = document.getElementById("prerelease-btn");
    const preMeta = document.getElementById("prerelease-meta");
    const snapSlot = document.getElementById("snapshot-slot");
    const snapBtn = document.getElementById("snapshot-btn");
    const snapMeta = document.getElementById("snapshot-meta");
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
    const preReady = !!(
      pre &&
      pre.tag_name &&
      (pre.windows || pre.macos_aarch64 || pre.macos_intel || pre.linux)
    );
    if (preReady && preSlot && preBtn) {
      preSlot.hidden = false;
      fillButton(preBtn, preMeta, pre, detected, "Download Prerelease");
    } else if (preSlot) {
      preSlot.hidden = true;
    }

    const snap = latest && latest.snapshot;
    const snapReady = !!(
      snap &&
      (snap.windows || snap.macos_aarch64 || snap.macos_intel || snap.linux)
    );
    if (snapReady && snapSlot && snapBtn) {
      snapSlot.hidden = false;
      fillButton(snapBtn, null, snap, detected, "Download Snapshot");
      if (snapMeta) {
        snapMeta.hidden = false;
        snapMeta.textContent = "";
        const parts = [];
        const stableTag = latest && latest.tag_name;
        const snapSha = snap.sha || snap.short_sha;
        const short = snap.short_sha || (snapSha ? String(snapSha).slice(0, 7) : "");
        if (short) {
          if (stableTag && snapSha) {
            const a = document.createElement("a");
            a.href =
              "https://github.com/SensorsINI/jaer/compare/" + stableTag + "..." + snapSha;
            a.title = "Commits on master since Stable.";
            a.textContent = short;
            parts.push(a);
          } else {
            parts.push(document.createTextNode(short));
          }
        }
        const builtIso = snap.built_at || snap.published_at;
        const day = formatDay(builtIso);
        if (day) {
          const tip = formatBuiltTip(builtIso);
          if (tip) {
            const t = document.createElement("span");
            t.textContent = day;
            t.title = tip;
            parts.push(t);
          } else {
            parts.push(document.createTextNode(day));
          }
        }
        if (detected.key && LABELS[detected.key]) {
          parts.push(document.createTextNode(LABELS[detected.key]));
        }
        const snapAsset = detected.key && snap[detected.key] ? snap[detected.key] : null;
        if (snapAsset) {
          const size = formatSize(snapAsset.size);
          if (size) {
            parts.push(document.createTextNode(size));
          }
        }
        if (!parts.length) {
          snapMeta.textContent = "Latest master · testers";
        } else {
          parts.forEach(function (node, i) {
            if (i) {
              appendSep(snapMeta);
            }
            snapMeta.appendChild(node);
          });
        }
      }
    } else if (snapSlot) {
      snapSlot.hidden = true;
    }

    if (detected.linuxArm) {
      note.hidden = false;
      note.textContent = "Linux installers are x64 only.";
    } else if (detected.macAmbiguous) {
      note.hidden = false;
      note.textContent =
        "macOS browser architecture is ambiguous. Use the macOS Apple Silicon or macOS Intel link below, or see the Install Guide.";
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
        const snapBtn = document.getElementById("snapshot-btn");
        if (snapBtn) {
          snapBtn.href = "https://github.com/SensorsINI/jaer/releases/tag/snapshot";
        }
      });
    });
})();
