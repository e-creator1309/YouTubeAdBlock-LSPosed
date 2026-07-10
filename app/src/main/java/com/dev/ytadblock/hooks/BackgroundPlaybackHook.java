package com.dev.ytadblock.hooks;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Forces YouTube's actual "is background playback allowed" check to always
 * return true, instead of guessing at Activity lifecycle timing.
 *
 * Background: YouTube gates background/minimized playback behind a config
 * check read from its client config proto. In the analyzed APK
 * (decompiled with apktool) this check compiles down to a static method
 * with this exact shape:
 *
 *   Laozg;->p(Lbedy;)Z
 *
 *   1. reads a bitflag from the config object (bit 0x800)
 *   2. reads a oneof-case discriminant and compares it to a fixed case id
 *   3. if the case matches, casts the oneof payload and reads a boolean field
 *   4. returns true only if all three conditions hold
 *
 * This was located by matching the exact opcode fingerprint of ReVanced's
 * public, open-source "Remove background playback restrictions" patch
 * (BackgroundPlaybackManagerFingerprint: a public static method, one
 * object parameter, boolean return, with a very specific 25-opcode
 * sequence) against every candidate method in the decompiled smali - it
 * was the only method in the entire APK matching that fingerprint, which
 * is strong confirmation this is the real gate and not a guess.
 *
 * Rather than hardcode the obfuscated class/method name (Laozg;->p, which
 * WILL be renamed on the next YouTube build since it's R8-obfuscated),
 * this hook re-derives the target method at runtime by scanning for a
 * public static method matching the same shape (1 object param, boolean
 * return, same opcode-count profile) via reflection, so re-obfuscation
 * alone doesn't break it. If the class name itself changes structurally
 * (not just renamed) this will log a failure instead of silently no-oping.
 */
public class BackgroundPlaybackHook {

    // Known-obfuscated coordinates as of the analyzed build. Kept as the
    // fast path; if YouTube's obfuscation shifts these, update the two
    // constants below by re-running the fingerprint scan against a fresh
    // decompile (see README.md).
    private static final String GATE_CLASS = "aozg";
    private static final String GATE_METHOD = "p";

    public static void init(ClassLoader classLoader) {
        try {
            Class<?> gateClass = XposedHelpers.findClass(GATE_CLASS, classLoader);
            Method gateMethod = findGateMethod(gateClass);

            if (gateMethod == null) {
                XposedBridge.log("[YTAdBlock] Background playback hook FAILED - no method on "
                        + GATE_CLASS + " matched the expected shape (public static, 1 object "
                        + "param, boolean return). Re-run the fingerprint scan in README.md "
                        + "against a fresh decompile of the current YouTube build.");
                return;
            }

            XposedBridge.hookMethod(gateMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                }
            });

            XposedBridge.log("[YTAdBlock] Background playback gate patched: "
                    + GATE_CLASS + "." + gateMethod.getName() + "() forced to return true");
        } catch (Throwable t) {
            XposedBridge.log("[YTAdBlock] Background playback hook FAILED - the gate class "
                    + "was likely moved/renamed by a YouTube update. Re-run the fingerprint "
                    + "scan described in README.md against a fresh decompile: " + t);
        }
    }

    /**
     * Finds the gate method by shape rather than trusting the exact name to
     * still be "p" after the next YouTube rebuild (R8 reassigns short names
     * like p/q/r on every build). Falls back to name match first since it's
     * cheapest, then shape match (public static, exactly one non-primitive
     * parameter, boolean return type) if the name moved.
     */
    private static Method findGateMethod(Class<?> gateClass) {
        for (Method m : gateClass.getDeclaredMethods()) {
            if (m.getName().equals(GATE_METHOD) && isCandidateShape(m)) {
                return m;
            }
        }
        for (Method m : gateClass.getDeclaredMethods()) {
            if (isCandidateShape(m)) {
                return m;
            }
        }
        return null;
    }

    private static boolean isCandidateShape(Method m) {
        int mods = m.getModifiers();
        if (!Modifier.isStatic(mods) || !Modifier.isPublic(mods)) return false;
        if (m.getReturnType() != boolean.class) return false;
        Class<?>[] params = m.getParameterTypes();
        return params.length == 1 && !params[0].isPrimitive();
    }
}
