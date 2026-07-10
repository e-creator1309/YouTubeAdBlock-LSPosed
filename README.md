# YouTubeAdBlock-LSPosed

Minimal LSPosed module for the official YouTube app. One hook, nothing else.

## What it does
1. **Ad blocking** - blocks known Google ad-network hosts (doubleclick.net,
   googlesyndication.com, etc.) by hooking java.net.InetAddress.getAllByName/
   getByName and throwing UnknownHostException for those hosts. Ad requests
   fail silently, same as if the host did not resolve.
2. **Background playback** - keeps video/audio playing after you lock the
   screen or leave the app (home button / switch apps), by no-opping
   Activity.onUserLeaveHint()/moveTaskToBack() and holding a partial wake
   lock so the CPU doesn't sleep mid-playback.

## Why this approach
Both features hook stable, public Android framework classes
(java.net.InetAddress, android.app.Activity) instead of YouTube's own
obfuscated/renamed internals. YouTube's internal classes change every
release and would need re-reverse-engineering each update; the Android
framework classes they're built on never change.

## Build
```
git clone https://github.com/e-creator1309/YouTubeAdBlock-LSPosed.git
cd YouTubeAdBlock-LSPosed
./gradlew assembleDebug
```
(No gradle wrapper is committed yet - run `gradle wrapper` once locally, or open
the project in Android Studio, which generates it automatically.)

## Install
1. Build/obtain the debug APK and install it on a rooted/LSPosed-enabled device.
2. Enable the module in LSPosed Manager, scope it to com.google.android.youtube.
3. Force-stop and reopen YouTube.

## Limitations
- Ad blocking: blocks network requests, not in-app UI ad slots - some
  placeholder/empty ad views may still flash briefly. Host list is a
  starting set; add more domains to BLOCKED_HOSTS in AdBlockHook.java.
- Background playback: no-ops onUserLeaveHint/moveTaskToBack app-wide for
  YouTube, which is a blunt instrument - it's what lets video keep playing,
  but it also means the app never gets the "user left" signal for anything
  else. If you notice odd behavior elsewhere in the app, that hook is why.
- Background playback over long periods will use more battery due to the
  held wake lock; this is expected since it's what keeps the CPU alive.
