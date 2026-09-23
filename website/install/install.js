(function () {
  const ua = navigator.userAgent || "";
  const platform = navigator.platform || "";
  const hint = navigator.userAgentData && navigator.userAgentData.platform
    ? navigator.userAgentData.platform
    : "";
  let id = null;
  if (/Win/i.test(ua) || /Windows/i.test(hint) || /Win32|Win64/i.test(platform)) {
    id = "windows";
  } else if (/Mac/i.test(ua) || /macOS/i.test(hint) || /Mac/i.test(platform)) {
    id = "macos";
  } else if ((/Linux/i.test(ua) || /Linux/i.test(hint)) && !/Android/i.test(ua)) {
    id = "linux";
  }
  if (!id) {
    return;
  }
  const section = document.getElementById(id);
  if (!section) {
    return;
  }
  section.classList.add("section-current");
})();
