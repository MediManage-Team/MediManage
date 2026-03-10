# MediManage Release Checklist

Use this checklist before cutting a release installer.

## 1. Version and Branch

- [ ] Confirm release branch and target version.
- [ ] Update version in `pom.xml`.
- [ ] Verify installer version fields in `setup.iss`.
- [ ] Verify docs mention the same release version.

## 2. Build Validation

- [ ] Run full build locally: `mvn clean test package dependency:copy-dependencies`
- [ ] Confirm tests pass.
- [ ] Confirm no critical warnings in build output.

## 3. Security and Data

- [ ] Verify no secrets/API keys are committed.
- [ ] Verify bundled DB strategy is intentional for this release.
- [ ] Verify default credentials policy is documented and safe.

## 4. Packaging

- [ ] Ensure `JAVA_HOME` points to JDK 21.
- [ ] Run `build_full_installer.bat`.
- [ ] Confirm `jpackage` output is generated.
- [ ] Confirm Inno Setup compilation succeeds.

## 5. Smoke Test (Installer)

- [ ] Install on a clean Windows machine/VM.
- [ ] Launch app and complete login flow.
- [ ] Validate DB path and write permissions.
- [ ] Validate billing flow and invoice generation.
- [ ] Validate AI engine starts and `/health` is reachable.

## 6. Release Artifacts

- [ ] Archive generated installer and checksums.
- [ ] Save release notes with fixed issues and known limitations.
- [ ] Tag release in git.

