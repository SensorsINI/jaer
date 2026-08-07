Local SignPath / install4j credentials (Dropbox)
================================================

This folder stays on your machine (gitignored except this README).
Do not commit API tokens or license keys.

Fill in these files (one value per file; see stubs created beside this README):

  signpath-organization-id.txt
      SignPath Organization ID (UUID). You may paste the whole project
      overview block; scripts extract the UUID automatically.

  signpath-api-token.txt
      API token from SignPath user "CI builds" → API Token tab.
      Single line, no quotes.

  install4j-license.txt
      Your install4j license key (single line). Used by local Ant when
      present; also synced to GitHub Actions secret INSTALL4J_LICENSE.

  signpath-project-slug.txt
      Default: jaer

  signpath-signing-policy-slug.txt
      Default: test-signing  (use release-signing later when VALID)

Sync to GitHub Actions (secrets are set via gh CLI; nothing is committed):

    powershell -File scripts/sync-signpath-secrets-to-github.ps1

Recreate empty stubs if missing:

    powershell -File scripts/init-signpath-local.ps1

GitHub Actions still needs those secrets/vars on the repo (runners cannot
read your Dropbox). The sync script is the bridge; values never live in git.

Tracked artifact config (not secret): .signpath/artifact-configurations/
See also README-releasing-tagging.txt → SignPath Windows CI.
