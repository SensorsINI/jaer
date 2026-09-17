# F5 to run jAER (Cursor / VS Code)

Clones and Dropbox copies of this repo **do** pick up the `ant run` **task**. They **do not** pick up the F5 key. Cursor and VS Code never load workspace keybindings; that file is per editor on each computer.

On a new box, after the first `git pull` / Dropbox sync, run this once:

```bash
ant install-jaer-run-shortcut
```

Then **Developer: Reload Window**. **F5** (or **Ctrl+F5**) starts `ant run` as a task: no debugger, no debug toolbar. Output is the Terminal panel named `jAER: ant run`.

Also install into VS Code:

```bash
ant -Djaer.shortcut.editor=both install-jaer-run-shortcut
```

(`code` = VS Code only; default is Cursor.)

---

## What git / Dropbox already has

| File | Role |
|------|------|
| [`.vscode/tasks.json`](../.vscode/tasks.json) | Task **jAER: ant run** (`scripts/run-jaer.bat` / `.sh`) |
| [`.vscode/settings.json`](../.vscode/settings.json) | `"jaer.runOnF5": true` so the user keybinding applies only in this folder |

Ctrl+Shift+B is still **jAER: ant compile**. You can run the task from **Terminal → Run Task…** even before the shortcut is installed.

`.vscode/launch.json` is gone on purpose. A launch configuration always shows the debug toolbar (red stop).

---

## What each computer still needs

| Location | Why |
|----------|-----|
| Cursor **User** `keybindings.json` | F5 / Ctrl+F5 → `workbench.action.tasks.runTask` / `jAER: ant run` |

Paths (the Ant target writes these):

- Windows: `%APPDATA%\Cursor\User\keybindings.json`
- macOS: `~/Library/Application Support/Cursor/User/keybindings.json`
- Linux: `~/.config/Cursor/User/keybindings.json`

VS Code uses `Code` in place of `Cursor`.

---

## Checklist on a new machine

1. Open the **jaer** folder as the workspace root (the setting `jaer.runOnF5` is in this folder’s `.vscode/settings.json`).
2. `ant install-jaer-run-shortcut`
3. Reload the window.
4. Press F5. jAER should start; the debug toolbar should not appear.
5. If F5 still starts a debug session: Keyboard Shortcuts, search `F5`, confirm the task binding, and that this folder has `jaer.runOnF5`.

To stop jAER, use the trash icon on that terminal (not a debug stop button). A second F5 does not start another copy; Cursor asks whether to terminate the running task.

---

## Related

- [README.md](../README.md#developing-in-an-llm-ai-client-cursor-vs-code-) — JDK, Ant, Java extension
- [README-cursor-jaer-rules-setup.md](README-cursor-jaer-rules-setup.md) — Agent rules on another computer
