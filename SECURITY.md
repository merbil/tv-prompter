# Security

TV Prompter runs a small web server on your TV, so security reports are very welcome.

**Please report vulnerabilities privately** using GitHub's
[private vulnerability reporting](https://github.com/merbil/tv-prompter/security/advisories/new)
rather than a public issue. I'll reply as soon as I can.

## What the app does on your network
- While TV Prompter is open, it serves the teleprompter page and the edit page on port 8080. So when you close the app, the server stops.
- The edit page is **plain HTTP with no password**, which means anyone on the same network can read and replace your notes while the app is open. Use it on your home network, not on a shared, office or public Wi-Fi.
- The notes are saved in the app's own storage on the TV, in a single text file, and saving replaces them. Also, notes over 2 MB are refused.
- The app has one permission, INTERNET, which it needs to run that server. Other than that, it has no account, no cloud and no analytics, and it never connects to anything outside your home network.

## Known limitations
- The APK is signed with my own key, not through the Play Store, so Google doesn't review it. That's why you should only download it from this repository's Releases page, or build it yourself from the source.
- To install it with adb you turn on USB debugging, which lets other devices on your network ask to control the TV. So turn it off again when you're done.
