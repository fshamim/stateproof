# Central Publish Status

Date: 2026-02-16  
Target version: `0.8.0-alpha02`  
Namespace: `io.github.fshamim`

## Result

- `publish` to Central staging API: **succeeded**
- Manual upload/finalize: **succeeded** (`HTTP 200`)
- Repository state: **released**
- Portal deployment ID: `57e3541e-0d0f-4a7a-85a5-772776ad9b43`

## Issues Encountered and Resolved

1. `402 Payment Required` on legacy OSSRH endpoint  
   - Fixed by switching to:  
     `https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/`

2. Invalid signatures (`public key not found`)  
   - Fixed by publishing the signing key to key servers.

3. Missing `sources` / `javadocs` for JVM publications  
   - Fixed by adding source/javadoc artifacts to:
     - `stateproof-gradle-plugin`
     - `stateproof-annotations`
     - `stateproof-ksp`
     - `stateproof-core` JVM publication
     - `stateproof-viewer` JVM publication
     - `stateproof-compose` JVM publication

## Notes

- Maven Central indexing can lag after release; `repo1.maven.org` and `search.maven.org` may return `404` until sync completes.
