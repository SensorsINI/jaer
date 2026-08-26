# Cursor Agent rules for jAER (replicate on another computer)

How Agent chats pick up jAER 3 architecture and repo conventions. Cursor does
**not** invent an architecture brief from the index; it only injects what is in
`AGENTS.md`, `.cursor/rules/`, `@` attachments, or files the agent Reads.

Official: [Rules](https://cursor.com/docs/rules), [AGENTS.md](https://cursor.com/docs/rules.md#agentsmd).

Java / Ant / JDK setup for the IDE is in the main [README](../README.md)
(section *Developing in an LLM AI client*). This file is only the Agent rule
layout.

---

## What syncs with the repo

These files live in git (and Dropbox if you sync the working copy). Opening the
**jaer repo root** in Cursor is enough; there is no extra install step.

| File | When it enters the prompt |
|------|---------------------------|
| [`AGENTS.md`](../AGENTS.md) | Every Agent chat on this repo (short pipeline map) |
| [`.cursor/rules/jaer3-architecture.mdc`](../.cursor/rules/jaer3-architecture.mdc) | When `src/**/*.java` is in context, or the agent fetches the rule from its description. Injects `@docs/README-jaer3.md` |
| [`.cursor/rules/plans-location.mdc`](../.cursor/rules/plans-location.mdc) | Always. Plans go under `.cursor/plans/` |
| [`.cursor/rules/shell-by-platform.mdc`](../.cursor/rules/shell-by-platform.mdc) | Always. bash on Linux/WSL; PowerShell 5.1 on native Windows |

Canonical architecture prose stays in [`README-jaer3.md`](README-jaer3.md). Do not
paste that document into a rule body.

`.cursor/plans/*.plan.md` is **gitignored**; plans still Dropbox-sync with the
tree. Rules under `.cursor/rules/` **are** tracked.

---

## What does not sync

| Location | What belongs there |
|----------|-------------------|
| Cursor **User Rules** (Settings → Rules, or `~/.cursor/` user rules) | Machine/account preferences only (editor, shell habits). They apply to **every** project, including siblings such as `rpg_e2vid` |
| User-global plans folder | Not canonical for jAER. Use `jaer/.cursor/plans/` |
| `*.code-workspace` | Gitignored (machine paths, JDK home) |

Do **not** put the jAER pipeline summary in User Rules.

---

## Checklist on a new machine

1. Clone or Dropbox-sync this repository.
2. Open the **jaer** folder as the workspace root (or a multi-root workspace that includes it). Nested `AGENTS.md` applies when working in that tree.
3. Start an Agent chat. Confirm the context ring **Rules** slice lists `AGENTS.md` plus the two `alwaysApply` `.mdc` files.
4. Open a file under `src/` (for example `AEViewer.java`) and start another Agent turn. The **Rules** slice should also list `jaer3-architecture` (and the attached `README-jaer3.md`).
5. If a rule is missing: Project Rules in Cursor Settings → confirm `.cursor/rules/*.mdc` are enabled for this workspace. Restart Agent if you just pulled the files.

JDK, Ant, and the Java extension: [README.md](../README.md#developing-in-an-llm-ai-client-cursor-vs-code-).

---

## How the pieces fit

```text
every jaer Agent chat
  AGENTS.md                          short map + pointers
  plans-location.mdc                 alwaysApply
  shell-by-platform.mdc              alwaysApply

when src/**/*.java is in context
  jaer3-architecture.mdc             alwaysApply: false
    + @docs/README-jaer3.md          full pipeline (ViewLoop, PacketBundle, …)

User Rules                           other projects too; keep jAER-specific text out
```

A sentence in a rule that says “load this file” does nothing by itself. The
harness attaches a markdown file when an **applied** rule contains `@path`.
Without `@`, the agent must Read the file.

`alwaysApply: true` on `README-jaer3.md` would spend the Rules budget on every
turn (docs, git, packaging included). Keep the long doc on the glob/intelligent
rule.

---

## Adding or changing a rule

`.cursor/rules/*.mdc` with YAML frontmatter:

```yaml
---
description: Shown in the rule picker; Agent uses this to fetch the rule
globs: src/**/*.java          # omit if alwaysApply, or if description-only (intelligent)
alwaysApply: false            # true = every chat; ignores globs
---
```

- Keep always-on text short. Point at canonical docs with `@docs/…` instead of copying them.
- One concern per `.mdc` file.
- After editing rules, new Agent chats pick them up; an already-open chat may need a new conversation.

---

## Related

- [README-jaer3.md](README-jaer3.md) — event processing pipeline
- [README-usb.md](README-usb.md) — USB enumeration, Interface menu, EDT
- [dropbox-notes.md](dropbox-notes.md) — ignore `build/` / `dist/` so compile output stays local
