package com.dev.ytadblock.hooks;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Blocks known Google/YouTube ad-network hosts at DNS resolution level.
 *
 * Why this approach instead of hooking YouTube's obfuscated ad-serving classes:
 * YouTube's internal class/method names are R8-obfuscated and change on every
 * app update, so hooking them by name breaks constantly and requires re-analyzing
 * the APK each release. Hooking java.net.InetAddress instead is stable across
 * every YouTube version because it is a public Android API the app cannot rename.
 *
 * When YouTube tries to resolve one of these ad hosts, we throw
 * UnknownHostException - the same result as if the host genuinely did not
 * exist - so the ad request silently fails and playback continues.
 */
public class AdBlockHook {

    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
            "googleads.g.doubleclick.net",
            "pagead2.googlesyndication.com",
            "static.doubleclick.net",
            "ad.doubleclick.net",
            "s.youtube.com",
            "googleadservices.com"
    ));

    public static void init(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(InetAddress.class, "getAllByName", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String host = (String) param.args[0];
                            if (host != null && isBlocked(host)) {
                                XposedBridge.log("[YTAdBlock] Blocked DNS lookup: " + host);
                                throw new UnknownHostException("Blocked by YTAdBlock: " + host);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(InetAddress.class, "getByName", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String host = (String) param.args[0];
                            if (host != null && isBlocked(host)) {
                                XposedBridge.log("[YTAdBlock] Blocked DNS lookup: " + host);
                                throw new UnknownHostException("Blocked by YTAdBlock: " + host);
                            }
                        }
                    });

            XposedBridge.log("[YTAdBlock] Hooks installed (" + BLOCKED_HOSTS.size() + " hosts)");
        } catch (Throwable t) {
            XposedBridge.log("[YTAdBlock] Failed to install hooks: " + t);
        }
    }

    private static boolean isBlocked(String host) {
        for (String blocked : BLOCKED_HOSTS) {
            if (host.equals(blocked) || host.endsWith("." + blocked)) return true;
        }
        return false;
    }
}
