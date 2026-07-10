# YouTubeAdBlock-LSPosed

Minimal LSPosed module for the official YouTube app. One hook, nothing else.

## What it does
Blocks known Google ad-network hosts (doubleclick.net, googlesyndication.com, etc.)
by hooking java.net.InetAddress.getAllByName/getByName and throwing
UnknownHostException for those hosts. YouTube's ad-request network calls fail
silently, same as if the host did not resolve - playback is unaffected.

## Why this approach (not hooking YouTube's own ad classes)
YouTube's internal ad-serving classes are R8-obfuscated and renamed on every
release, so hooking them by class/method name breaks every update and needs
re-reversing the APK each time. Hooking the public InetAddress API is stable
across every YouTube version because the app cannot rename a system class.

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
- Blocks ad network requests, not in-app UI ad slots - some placeholder/empty
  ad views may still flash briefly since the app still tries to render them.
- Host list is a starting set; add more domains to BLOCKED_HOSTS in
  AdBlockHook.java as needed.
