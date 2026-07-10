# YouTubeAdBlock-LSPosed

Minimal LSPosed module for the official YouTube app.

## What it does
1. **Ad blocking** - blocks known Google ad-network hosts (doubleclick.net,
   googlesyndication.com, etc.) by hooking java.net.InetAddress.getAllByName/
   getByName and throwing UnknownHostException for those hosts. Ad requests
   fail silently, same as if the host did not resolve.
2. **Background playback** - patches YouTube's actual internal check for
   whether background/minimized playback is allowed, forcing it to always
   return true. Video/audio keeps playing when you lock the screen, switch
   apps, or go to the home screen.

## How background playback was found (read this before reporting it broken)
The first version of this hook guessed at the fix by no-oping Activity
lifecycle callbacks (onUserLeaveHint/moveTaskToBack). That was a blind
guess and didn't reliably work, because YouTube doesn't decide whether to
keep playing based on those callbacks directly - it reads a boolean from
its own client config check first.

To find the real check, the shipped APK was decompiled with apktool and
searched for a method matching the exact opcode fingerprint used by the
open-source ReVanced project's own "Remove background playback
restrictions" patch (`BackgroundPlaybackManagerFingerprint`: a public
static method, one object parameter, boolean return, with a specific
25-instruction sequence - bit-flag check, then a oneof-case check, then a
nested boolean field read). A full scan of every method in the APK
(~54,000 smali files) found exactly one match:

```
Laozg;->p(Lbedy;)Z
```

This hook patches that method (found by name first, falling back to a
shape match if the name changes) to always return `true`. This is the
actual gate YouTube itself checks, not a guess about lifecycle timing -
which is why it's expected to actually work where the first version
didn't.

## If YouTube updates and it stops working
R8 reassigns short obfuscated names (`aozg`, `p`, etc.) on every YouTube
build, so the exact class/method name in `BackgroundPlaybackHook.java`
will eventually go stale. To fix:
1. Download the new YouTube APK and decompile with `apktool d`.
2. Re-run the fingerprint search for a public static method with exactly
   one non-primitive parameter, boolean return type, and the same opcode
   shape described above (const/4, if-eqz, iget, and-int/lit16, if-eqz,
   iget-object, if-nez, sget-object, iget, const, if-ne, iget-object,
   if-nez, sget-object, iget, if-ne, iget-object, check-cast, goto,
   sget-object, goto, const/4, if-eqz, iget-boolean, if-eqz).
3. Update `GATE_CLASS`/`GATE_METHOD` in `BackgroundPlaybackHook.java`.

## Why this approach for ad blocking
java.net.InetAddress is a stable, public Android framework class YouTube
cannot rename, unlike its own obfuscated ad-serving classes which change
every release.

## Build
GitHub Actions builds a debug APK automatically on every push - check the
Actions tab for the latest artifact. To build locally:
```
git clone https://github.com/e-creator1309/YouTubeAdBlock-LSPosed.git
cd YouTubeAdBlock-LSPosed
gradle assembleDebug
```
(No gradle wrapper is committed - install Gradle locally, or open the
project in Android Studio, which generates the wrapper automatically.)

## Install
1. Build/download the debug APK and install it on a rooted/LSPosed-enabled
   device.
2. Enable the module in LSPosed Manager, scope it to
   com.google.android.youtube.
3. Force-stop and reopen YouTube.

## Limitations
- Ad blocking: blocks network requests, not in-app UI ad slots - some
  placeholder/empty ad views may still flash briefly. Host list is a
  starting set; add more domains to BLOCKED_HOSTS in AdBlockHook.java.
- Background playback: this survives leaving the app, locking the screen,
  or switching apps. It does NOT survive force-stopping YouTube or
  swiping it away in recents - that kills the whole process, and no
  Xposed module can keep a killed process running.
- Tied to the specific YouTube build this was analyzed against; see
  "If YouTube updates" above.
