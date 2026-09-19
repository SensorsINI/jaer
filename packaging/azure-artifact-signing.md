# Azure Artifact Signing (Windows)

Public Trust identity **Tobias Delbruck** (Completed). Certificate profile **`jaer-public`** (Active). Artifact Signing account **`jAER`**, West US 2, Account URI `https://wus2.codesigning.azure.net/`. Basic SKU.

Leaf certificates rotate every few days; that is expected. Identity expires **2027-09-13** — renew in the portal before then. Publisher on signed exes is **Tobias Delbruck** (not Sensors Group, not SignPath Foundation).

GitHub workflow: `.github/workflows/build-win-sign.yml` (**Build Windows (sign)**). `workflow_dispatch` or `workflow_call` from `release.yml`. SignPath test-signing stays on `.github/workflows/sign-windows-test.yml`.

## One-time: Entra app + GitHub OIDC

Do this in [Azure portal](https://portal.azure.com) with the same account that owns subscription **Azure subscription 1**. Do not put client secrets in git.

1. **Microsoft Entra ID** → **App registrations** → **New registration**.
   - Name: `jaer-github-signing`
   - Supported account types: **This organizational directory only** (personal Azure AD / Default Directory is fine)
   - Redirect URI: skip
   - **Register**
2. Copy **Application (client) ID** and **Directory (tenant) ID**.
3. **Certificates & secrets** → **Federated credentials** → **Add credential**.
   - Federated credential scenario: **GitHub Actions deploying Azure resources**
   - Organization: `SensorsINI`
   - Repository: `jaer`
   - Entity type: **Environment**
   - GitHub environment name: `azure-signing`
   - Name: `github-azure-signing`
   - **Add**
4. Open Artifact Signing account **jAER** → **Access control (IAM)** → **Add role assignment**:
   - Role: **Artifact Signing Certificate Profile Signer**
   - Members: the app `jaer-github-signing` (search; it is not listed under Users by default)
   - **Review + assign**
5. GitHub repo **SensorsINI/jaer** → **Settings** → **Environments** → **New environment** → name `azure-signing`. No required reviewers for the first dry run (add later if you want).
6. Repo **Settings** → **Secrets and variables** → **Actions** → Secrets:
   - `AZURE_CLIENT_ID` — application (client) ID
   - `AZURE_TENANT_ID` — directory (tenant) ID
   - `AZURE_SUBSCRIPTION_ID` — from the jAER account Overview (**Subscription ID**)
   - `INSTALL4J_LICENSE` — already set for SignPath
7. Optional variables (workflow has these defaults):
   - `AZURE_CODESIGN_ACCOUNT` = `jAER`
   - `AZURE_CODESIGN_PROFILE` = `jaer-public`
   - `AZURE_CODESIGN_ENDPOINT` = `https://wus2.codesigning.azure.net/`

No Azure client secret. OIDC only.

## Run

Push the workflow file to `master`, then:

```text
gh workflow run build-win-sign.yml
gh run watch
```

Or Actions → **Build Windows (sign)** → Run workflow.

Job takes ~30–50 minutes (install4j media). Download artifact **jaer-windows-azure-signed**. On Windows: Properties → Digital Signatures → signer **Tobias Delbruck**, timestamp Microsoft.

## After a good dry run

`workflow_dispatch` on this workflow only uploads the Actions artifact. Production attaches via [`release.yml`](../.github/workflows/release.yml) assemble. Do not clobber a Release exe with local `ant upload-installers`.

Winget: YAML `Publisher` is **Tobias Delbruck**. Fill SHA256 from the **Latest** GitHub asset, not this dry-run artifact and not an `-rc` tag (the public tag rebuilds media). Then `wingetcreate submit`. See [winget/README.md](winget/README.md).

Microsoft Learn: [Artifact Signing quickstart](https://learn.microsoft.com/en-us/azure/artifact-signing/quickstart), [GitHub OIDC](https://github.com/Azure/artifact-signing-action/blob/main/docs/OIDC.md).
