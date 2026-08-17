# WIP: GitHub Actions / Java runtime upgrades

Status: **partial** — SignPath Windows workflow uses current action majors; compile target still Java 21.

## Done (this pass)

| Item | Detail |
|------|--------|
| `actions/checkout` | `@v5` in `.github/workflows/sign-windows-test.yml` |
| `actions/setup-java` | `@v5` (Node 24 runtime; clears setup-java@v4 / Node 20 deprecation) |
| `actions/upload-artifact` | `@v7` for signed installer upload |
| install4j localization | Track `packaging/install4j/install4j-custom-resources.utf8` so CI media builds succeed |

## Still open

1. **JDK 25 installer bundle** — deferred to **3.2.1** with SignPath-signed Windows media. 3.2.0 stays `jaer.install4j` `release="21/jdk-21.0.12+8"`. `ant run` already needs JDK 25+; `javac` still targets 21. When bumping: `jreBundles`, `setup-java`, smoke-test `ant compile` / install4j.
2. **Other Actions** — when adding more workflows, prefer Node-24-based majors (`checkout@v5`, `setup-java@v5`, `upload-artifact@v7+`). Third-party actions (install4j setup, SignPath) may lag; pin and revisit.
3. **install4j on CI** — currently pinned to **13.0.2** to match local; bump when upgrading the desktop install4j license install.

## Notes

- GitHub-hosted `windows-latest` already provides Ant; do not reintroduce removed `stCarolas/setup-ant`.
- Media under `currentInstallers/<version>/` remains gitignored; only the custom localization `.utf8` under `packaging/install4j/` is tracked. Historical media is `jaer-older-installers/` (also gitignored).
