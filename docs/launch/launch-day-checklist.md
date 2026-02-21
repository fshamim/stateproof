# Launch Day Checklist (`0.8.0-alpha02`)

## Release Engineering

- [ ] `stateproofVersion` is `0.8.0-alpha02` in `gradle.properties`
- [ ] CI build green on default branch
- [ ] `./gradlew clean build publishToMavenLocal` succeeds
- [ ] Tag created: `v0.8.0-alpha02`
- [ ] Tag pushed to origin
- [ ] Central publish workflow passed

## Consumer Validation (iCages)

- [ ] `rm -rf ~/.gradle/caches/modules-2/files-2.1/io.github.fshamim`
- [ ] `./gradlew :app:stateproofScan` passes
- [ ] `./gradlew :app:stateproofSyncAll` passes
- [ ] `./gradlew :app:stateproofDiagrams` passes
- [ ] `./gradlew :app:stateproofViewer` passes

## Communication Pack

- [ ] GitHub release notes published
- [ ] Dev.to draft prepared
- [ ] LinkedIn draft prepared
- [ ] X/Twitter thread draft prepared
- [ ] Reddit post drafts prepared
- [ ] HN submission draft prepared

## Contingency

- [ ] If publish fails, create/update `docs/release/CENTRAL_PUBLISH_BLOCKER_REPORT.md`
- [ ] Record exact failing task and remediation plan
