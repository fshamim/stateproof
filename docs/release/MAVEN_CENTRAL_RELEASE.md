# Maven Central Release Runbook

This runbook documents the release process for StateProof prereleases.

Current release target: `0.8.0-alpha01`

## Required Secrets / Properties

Set these in CI secrets or `~/.gradle/gradle.properties`:

- `centralUsername` (or env `CENTRAL_USERNAME`)
- `centralPassword` (or env `CENTRAL_PASSWORD`)
- `signingInMemoryKey` (ASCII-armored private PGP key, or env `SIGNING_KEY`)
- `signingInMemoryKeyPassword` (or env `SIGNING_PASSWORD`)
- Public signing key uploaded to a supported key server (recommended: `hkps://keys.openpgp.org`)

Optional:

- `centralRepositoryUrl`  
  Default: `https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/`

## One-Command Publish Flow

From repo root:

```bash
./gradlew clean build publish
```

This command:

1. builds all OSS modules,
2. runs tests,
3. signs all Maven publications (if signing keys are present),
4. publishes all publications to the configured Central staging API.

Finalize the deployment:

```bash
curl --fail-with-body \
  -u "${CENTRAL_USERNAME}:${CENTRAL_PASSWORD}" \
  -X POST \
  "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/io.github.fshamim?publishing_type=automatic"
```

## Recommended Local Preflight

```bash
./gradlew clean build publishToMavenLocal
```

Then verify local coordinates:

```bash
ls ~/.m2/repository/io/github/fshamim
```

## Tag and Release Sequence

1. Ensure `stateproofVersion=0.8.0-alpha01`.
2. Commit and tag:

```bash
git tag v0.8.0-alpha01
git push origin v0.8.0-alpha01
```

3. Tag push triggers release workflow: `.github/workflows/release.yml`.
4. Validate consumer resolution in iCages after cache clear:

```bash
rm -rf ~/.gradle/caches/modules-2/files-2.1/io.github.fshamim
./gradlew :app:stateproofScan :app:stateproofSyncAll :app:stateproofDiagrams :app:stateproofViewer
```

## If Publish Is Blocked

If Central credentials, namespace, or network blocks publish, record details in:

- `docs/release/CENTRAL_PUBLISH_BLOCKER_REPORT.md`

Include:
- exact failed task,
- first actionable error snippet,
- attempted environment/credentials source,
- remediation next steps.

Common publish blockers:
- missing public signing key in key servers,
- missing sources/javadocs in JVM publications,
- stale closed default repository (drop and republish).
