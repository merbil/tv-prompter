# Contributing

Thanks for helping. Issues and pull requests are welcome.

## Setup
- JDK 17 and the Android command-line tools, with `platforms;android-34` and `build-tools;34.0.0`. Actually, you don't need Android Studio or Gradle.
- `scripts/build-apk.sh` builds `Prompter.apk`, and `scripts/install.sh <tv-ip>` installs it on your TV.

## Working without a TV
Almost all of the app is two web pages, `assets/prompter.html` (the TV screen) and `assets/edit.html` (the editor).
So you can run them on your computer, like this.

```sh
python3 scripts/preview-server.py --notes scripts/demo-notes.md
```

Then open `http://localhost:8080/` and use the arrow keys, Enter and 0 like the remote, and `http://localhost:8080/edit` for the editor. However, a TV shows web pages at about 960×540, so check your change at that size too.

## Before opening a PR
- `scripts/build-apk.sh` must work (it also runs in CI).
- Also, if you change the TV page, try it on a real Android TV if you can, or at least in the preview server.
- Add a line to `RELEASE_NOTES.md` under the next version.

## Signing your own builds
Without any settings, `scripts/build-apk.sh` signs with your Android debug key. To use your own release key, set
`PROMPTER_KEYSTORE`, `PROMPTER_KEYSTORE_PASS` and `PROMPTER_KEY_ALIAS`. Never commit a keystore, though. Also, Android only
installs an update over an app signed with the same key, so a build signed with your key has to replace the official
one (uninstall first).
