# Releasing SMBsync+

The Android APK workflow builds on pushes to `main`. Pushing a version tag such as `v2.55-plus.10` builds the APK again and publishes a GitHub release from that Actions run.

The repository must have these Actions secrets:

- `SMBSYNC_PLUS_RELEASE_KEYSTORE_BASE64`: Base64 encoding of the existing release keystore.
- `SMBSYNC_PLUS_RELEASE_SIGNING_PROPERTIES`: Gradle signing properties for that keystore. The workflow replaces the `storeFile` path with the runner's temporary path.

Increase `versionCode` and `versionName` in `SMBSync2/build.gradle` before tagging. The workflow checks that the tag matches `versionName` and that the APK uses the established signing certificate. A matching file in `.github/release-notes/` supplies release notes; otherwise GitHub generates them. The asset name must stay `SMBsync-plus-<tag>.apk` for the in-app update checker.
