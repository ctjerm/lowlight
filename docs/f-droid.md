# F-Droid release

F-Droid builds Lowlight from this repository and signs the APK with its own key. It reads the store listing, icon, screenshots, and changelogs from `fastlane/metadata/android/en-US/`, so the listing lives in this repository.

The build already meets F-Droid's rules: every dependency is open source from Google's Maven repository or Maven Central, release builds are unsigned unless the upload-key environment variables are set, and `dependenciesInfo` is off, so the APK carries no Google-encrypted dependency block.

## First release

1. Make the GitHub repository public.
2. Tag the release commit and push the tag. The tag must point at a commit whose `versionCode` and `versionName` match the build entry below.

   ```powershell
   git tag -a v1.2.0 -m "Lowlight 1.2.0"
   git push origin v1.2.0
   ```

3. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata) on GitLab and add `metadata/io.github.ctjerm.lowlight.yml` with the content below.
4. Open a merge request using the "App inclusion" template. Its CI pipeline runs `fdroid lint` and a full build; fix anything it reports before a reviewer looks at it.

```yaml
Categories:
  - System
License: GPL-3.0-only
AuthorName: ctjerm
SourceCode: https://github.com/ctjerm/lowlight
IssueTracker: https://github.com/ctjerm/lowlight/issues

AutoName: Lowlight

RepoType: git
Repo: https://github.com/ctjerm/lowlight.git

Builds:
  - versionName: 1.2.0
    versionCode: 3
    commit: v1.2.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.2.0
CurrentVersionCode: 3
```

## Later releases

F-Droid checks the repository's tags and adds a build for each new version on its own:

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. F-Droid shows at most 500 characters.
3. Tag the commit `v<versionName>` and push the tag.

New builds usually appear in the F-Droid client a few days after the tag.
