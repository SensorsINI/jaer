# Dropbox ignore setup for jAER (and sibling Python repos)

`rules.dropboxignore` is **local to each computer**. It does **not** sync. Keep this file in git so every machine (and agent) can apply the same rules.

Official docs:

- Ignore rules: <https://help.dropbox.com/sync/how-to-prevent-files-from-syncing>
- Ignore an existing file/folder: <https://help.dropbox.com/sync/ignored-files>

Applies to **Dropbox Personal** (Basic/Plus/Professional/Family). Not available on Dropbox **team** accounts (e.g. `Dropbox (iniLabs)`).

---

## What must stay local (do not sync)

| Pattern | Why it exists |
|---------|----------------|
| `.venv/` | Python virtualenv (OS/arch-specific; locks Dropbox if synced) |
| `__pycache__/` | Python bytecode |
| `GitHub/SensorsINI/jaer/build/` | Ant compiled classes (`build/classes`, generated sources) |
| `GitHub/SensorsINI/jaer/bin/` | Extra compiled-class tree (NetBeans / leftover) |
| `GitHub/SensorsINI/jaer/dist/` | Packaged `jAER.jar` output (`dist.jar=${dist.dir}/jAER.jar`) |
| `jAER.jar` / `jaer.jar` | Fat/app jar anywhere under this Dropbox |

Do **not** ignore `jars/`, `lib/`, or `ivy/` unless you decide that later. Those are dependencies, not compile output.

Paths in `rules.dropboxignore` are **relative to the Dropbox root**, with `/` separators. Adjust the `GitHub/SensorsINI/jaer/…` prefix if this checkout is not at `<Dropbox>/GitHub/SensorsINI/jaer`.

---

## Two mechanisms (you need both)

1. **`rules.dropboxignore`** (Dropbox **root** only) — future files/folders matching a rule stay local. **Forward-only:** already-synced items stay on dropbox.com. This is Dropbox’s official ignore-rules file; it is **not** the same as per-folder `.dropboxignore` files.
2. **Ignore attribute on existing folders** — gray minus icon; item stays on disk, is removed from dropbox.com and other devices.

New `.venv` / `__pycache__` / `build` after the rules exist are ignored by (1). Existing `jaer/build` and `jaer/bin` need (2).

On **Linux**, a local helper (`dropbox-ignore` / `apply-dropboxignore`) applies (2) from gitignore-style `.dropboxignore` files placed anywhere under Dropbox. That helper does **not** replace (1).

---

## Agent checklist (another computer)

1. Find the **Personal** Dropbox root (see [Find Dropbox root](#find-dropbox-root)).
2. Open or create `<DropboxRoot>/rules.dropboxignore`. If missing: Dropbox app → Preferences → Sync → Ignore Rules → Modify Rules.
3. Append the [canonical rules](#canonical-rules) if they are not already present. Do not duplicate the block.
4. Confirm this jAER clone is under that Dropbox root at `GitHub/SensorsINI/jaer` (or edit the three `jaer/…` paths).
5. Apply the ignore attribute to **existing** `build`, `bin`, and `dist` (if present). Optionally to any existing `.venv` next to sibling repos (`rpg_e2vid`, etc.). On Linux, prefer `dropbox-ignore` with a repo `.dropboxignore` (see [Linux: `.dropboxignore` helper](#linux-dropboxignore-helper)).
6. Verify: `rules.dropboxignore` contains the block once; ignore stream/xattr is set; Explorer/Finder shows a gray minus on `build` / `bin`.

On native Windows use **PowerShell** (no `&&`, no bash). On Linux/WSL/macOS use bash.

---

## Canonical rules

Paste **once** at the bottom of `rules.dropboxignore`:

```
# Python: keep local, do not sync (any repo under this Dropbox)
.venv/
__pycache__/

# jAER compiled classes (Ant/NetBeans) and packaged jar
GitHub/SensorsINI/jaer/build/
GitHub/SensorsINI/jaer/bin/
GitHub/SensorsINI/jaer/dist/
jAER.jar
jaer.jar
```

---

## Find Dropbox root

Typical locations:

| OS | Personal Dropbox root |
|----|------------------------|
| Windows (this PC) | `F:\tobi\Dropbox (Personal)` — **not** the empty `C:\Users\tobid\Dropbox (Personal)` |
| Windows (default) | `%USERPROFILE%\Dropbox` or `%USERPROFILE%\Dropbox (Personal)` |
| macOS (File Provider) | `~/Library/CloudStorage/Dropbox` or `~/Library/CloudStorage/Dropbox-Personal` |
| macOS (older) | `~/Dropbox` or `~/Dropbox (Personal)` |
| Linux | `~/Dropbox` or `~/Dropbox (Personal)` |

`rules.dropboxignore` must live in that root, not inside `jaer/`.

Windows, locate it:

```powershell
@(
  "$env:USERPROFILE\Dropbox (Personal)\rules.dropboxignore",
  "$env:USERPROFILE\Dropbox\rules.dropboxignore",
  "F:\tobi\Dropbox (Personal)\rules.dropboxignore"
) | Where-Object { Test-Path $_ }
```

---

## Ignore existing folders

Replace `$dropbox` / the path with this machine’s Dropbox root.

### Windows PowerShell

```powershell
$jaer = "F:\tobi\Dropbox (Personal)\GitHub\SensorsINI\jaer"
foreach ($p in @("$jaer\build", "$jaer\bin", "$jaer\dist")) {
  if (Test-Path -LiteralPath $p) {
    Set-Content -LiteralPath $p -Stream com.dropbox.ignored -Value 1
    Get-Content -LiteralPath $p -Stream com.dropbox.ignored
  }
}
```

Expect `1` printed for each existing folder. Gray minus in Explorer.

Un-ignore:

```powershell
Clear-Content -LiteralPath $p -Stream com.dropbox.ignored
```

### macOS (File Provider)

```bash
JAER="$HOME/Library/CloudStorage/Dropbox-Personal/GitHub/SensorsINI/jaer"
for p in "$JAER/build" "$JAER/bin" "$JAER/dist"; do
  [ -e "$p" ] && xattr -w 'com.apple.fileprovider.ignore#P' 1 "$p"
done
```

Or Finder: right-click folder → **Do Not Sync**.

Older macOS (no File Provider):

```bash
xattr -w com.dropbox.ignored 1 "$p"
```

### Linux: `.dropboxignore` helper

Linux Dropbox uses the `com.dropbox.ignored` extended attribute (`attr` package). A local script in `~/bin` applies that attribute from per-directory `.dropboxignore` files (gitignore-style). It is **not** Dropbox’s official `rules.dropboxignore`.

Installed as:

- `~/bin/apply-dropboxignore` — the script
- `~/bin/dropbox-ignore` — symlink to the same script

Needs `~/bin` on `PATH` and the `attr` package (`sudo apt install attr`).

**How it works.** It searches the target directory for any `.dropboxignore` files and, for each pattern in those files, sets `com.dropbox.ignored=1` on matching files and folders. Patterns are relative to the directory that contains that `.dropboxignore`. Empty `.dropboxignore` files do nothing; the containing folder is not ignored unless a pattern matches it.

**Usage:**

```bash
# Current directory if already under ~/Dropbox, else ~/Dropbox
dropbox-ignore

# Target a specific folder or project
dropbox-ignore ~/Dropbox/GitHub/SensorsINI/jaer

# List matching items and whether they are ignored (no changes)
dropbox-ignore -l ~/Dropbox/GitHub/SensorsINI/jaer

# Remove ignore attribute (allow syncing again)
dropbox-ignore -u ~/Dropbox/GitHub/SensorsINI/jaer
```

`apply-dropboxignore` is the same command. `-h` / `--help` prints options.

**Example `.dropboxignore`.** Place one anywhere under Dropbox (typically the repo root), same idea as `.gitignore`:

```
# Dependencies & environments
node_modules/
.venv/
__pycache__/

# Build artifacts & logs
*.log
dist/
build/
```

For this jAER checkout, a file at `GitHub/SensorsINI/jaer/.dropboxignore`:

```
# jAER compiled classes (Ant/NetBeans) and packaged jar
build/
bin/
dist/
jAER.jar
jaer.jar

# Python (tools / sibling checkouts)
.venv/
__pycache__/
```

Then:

```bash
dropbox-ignore ~/Dropbox/GitHub/SensorsINI/jaer
```

**Manual `attr` (no helper).** Same effect as the script for the three folders:

```bash
JAER="$HOME/Dropbox/GitHub/SensorsINI/jaer"
# or: JAER="$HOME/Dropbox (Personal)/GitHub/SensorsINI/jaer"
for p in "$JAER/build" "$JAER/bin" "$JAER/dist"; do
  [ -e "$p" ] && attr -s com.dropbox.ignored -V 1 "$p"
done
```

Un-ignore one path:

```bash
attr -r com.dropbox.ignored "$p"
```

Check one path:

```bash
attr -g com.dropbox.ignored "$p"
```

---

## Verify

- `rules.dropboxignore` ends with the canonical block, **once**.
- Windows: `Get-Content -LiteralPath '<jaer>\build' -Stream com.dropbox.ignored` → `1`
- Linux: `dropbox-ignore -l ~/Dropbox/GitHub/SensorsINI/jaer` shows `[IGNORED]` for `build` / `bin` / `dist`, or `attr -g com.dropbox.ignored '<jaer>/build'` succeeds.
- Folder overlay is a gray minus, not a green check / blue sync.
- `jaer/dist` may not exist until `ant jar` / NetBeans dist; the rule still covers it when created.

Ignore rules do **not** delete copies already on dropbox.com. The ignore attribute on an existing folder does remove that folder from the server (it stays on this disk). If `build/` is still online after setting rules only, set the attribute or delete the online copy from dropbox.com.

---

## Do not

- Put `rules.dropboxignore` inside `jaer/` — Dropbox only reads the **Dropbox root** copy.
- Confuse `.dropboxignore` (per-folder; Linux `dropbox-ignore` helper only) with `rules.dropboxignore` (Dropbox root; official ignore rules on every OS).
- Rely on git `.gitignore` — that does not control Dropbox.
- Expect this setup to appear automatically on a new PC; copy the canonical block and re-apply the ignore attribute. On Linux, also install `dropbox-ignore` if you use `.dropboxignore` files.
- Use ignore rules on `Dropbox (iniLabs)` (team).
