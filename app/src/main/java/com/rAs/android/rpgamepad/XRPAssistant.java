package com.rAs.android.rpgamepad;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.callbacks.XCallback;

public class XRPAssistant implements IXposedHookLoadPackage, PSGamepadHandler.OnGamepadStateChangeListener {

    private static final String APP_PACKAGE = "com.playstation.remoteplay";
    private static final boolean log = false;
    private static final String TAG = "RP_ASSISTANT";
    @SuppressLint("SdCardPath")
    private static final String PREFS_PATH = "/data/data/com.rAs.android.rpgamepad/shared_prefs/com.rAs.android.rpgamepad_preferences.xml";

    private long prefsLoadMillis = 0;
    private static PSGamepadHandler psGamepadHandler;
    private Context context;

    public static void log(Object text){
        if(!log) return;

        if(text instanceof Throwable) {
            Throwable e = (Throwable)text;
            Log.e(TAG, e.getClass().getName());
            Log.e(TAG, e.getMessage());
            Log.e(TAG, Log.getStackTraceString(e));
        } else if(text == null) {
            Log.w(TAG, "<< null >>");
        } else {
            Log.i(TAG, text.toString());
        }
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        if (lpparam.packageName.equals("android")) {
            try {
                hookHomeButton(lpparam.classLoader);
            } catch (Exception e) {
                log(e);
            }
        }

        if (!lpparam.packageName.equals(APP_PACKAGE)) return;

        log("remoteplay load package");

        XGamepadStateSender.init(
                XposedHelpers.findClass("com.playstation.remoteplay.core.RpNativeCoreLibrary", lpparam.classLoader),
                XposedHelpers.findClass("com.playstation.remoteplay.core.RpNativeCoreLibrary$RPCorePadData", lpparam.classLoader),
                XposedHelpers.findClass("com.playstation.remoteplay.core.RpNativeCoreLibrary$RPCorePadAnalogStick", lpparam.classLoader),
                XposedHelpers.findClass("com.playstation.remoteplay.core.RpNativeCoreLibrary$RPCorePadAnalogButtons", lpparam.classLoader),
                XposedHelpers.findClass("o.\u0142\u04c0", lpparam.classLoader)
        );

        final Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);

        XposedHelpers.findAndHookMethod(activityClass, "onResume", new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity)param.thisObject;

                XSharedPreferences prefs = new XSharedPreferences(XRPAssistant.class.getPackage().getName());
                String orientationStr = prefs.getString("screen_orientation", "-1");
                int orientation = -1;
                try{
                    orientation = Integer.parseInt(orientationStr);
                } catch(Exception e) {}

                if(orientation != -1) {
                    activity.setRequestedOrientation(orientation);
                }
            }
        });

        XposedHelpers.findAndHookMethod("o.\u0142\u04c0", lpparam.classLoader, "onKey", View.class, int.class, KeyEvent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (psGamepadHandler == null) return;

                KeyEvent event = (KeyEvent) param.args[2];

                // Don't hook on-screen controller events
                if (event.getDevice().isVirtual()) {
                    return;
                }

                try {
                    psGamepadHandler.setGamepadKeyState(event);
                } catch (Exception e) {
                    log(e);
                }
                param.setResult(true);
            }
        });

        XposedHelpers.findAndHookMethod(activityClass, "dispatchGenericMotionEvent", MotionEvent.class, new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if(psGamepadHandler == null) return;

                MotionEvent event = (MotionEvent)param.args[0];

                try{
                    psGamepadHandler.setGamepadAxisState(event);
                } catch(Exception e) {
                    log(e);
                }
            }
        });

        // Show Info
        Class<?> padDataClass = XposedHelpers.findClass("com.playstation.remoteplay.core.RpNativeCoreLibrary$RPCorePadData", lpparam.classLoader);

        XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.playstation.remoteplay.core.RpNativeCoreLibrary", lpparam.classLoader), "rpCoreSetPadData", padDataClass, new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XRPAssistant.log("Buttons: " + XposedHelpers.getObjectField(param.args[0], "mButtons"));

                XRPAssistant.log("Left X: " + XposedHelpers.getObjectField(XposedHelpers.getObjectField(param.args[0], "mLeftStick"), "mX"));
                XRPAssistant.log("Left Y: " + XposedHelpers.getObjectField(XposedHelpers.getObjectField(param.args[0], "mLeftStick"), "mY"));

                XRPAssistant.log("Right X: " + XposedHelpers.getObjectField(XposedHelpers.getObjectField(param.args[0], "mRightStick"), "mX"));
                XRPAssistant.log("Right Y: " + XposedHelpers.getObjectField(XposedHelpers.getObjectField(param.args[0], "mRightStick"), "mY"));

                XRPAssistant.log("L2: " + XposedHelpers.getObjectField(XposedHelpers.getObjectField(param.args[0], "mAnalogButtons"), "mL2"));
                XRPAssistant.log("R2: " + XposedHelpers.getObjectField(XposedHelpers.getObjectField(param.args[0], "mAnalogButtons"), "mR2"));
            }
        });

        Class<?> vibDataClass = XposedHelpers.findClass("com.playstation.remoteplay.core.RpNativeCoreLibrary$RPCorePadVibrationParam", lpparam.classLoader);

        XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.playstation.remoteplay.core.RpNativeCoreLibrary", lpparam.classLoader), "rpCoreGetPadVibrationParam", vibDataClass, int.class, new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                if ((boolean) param.getResult()) {
                    XRPAssistant.log("Good?: " + param.getResult());
                    XRPAssistant.log("M1: " + XposedHelpers.getObjectField(param.args[0], "mLargeMotor"));
                    XRPAssistant.log("M2: " + XposedHelpers.getObjectField(param.args[0], "mSmallMotor"));
                }
            }
        });

        final Class<?> rpActivityMainClass = XposedHelpers.findClass("com.playstation.remoteplay.RpActivityMain", lpparam.classLoader);

        XposedHelpers.findAndHookMethod(rpActivityMainClass, "onResume", new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if(System.currentTimeMillis() - prefsLoadMillis < 10000)
                    return;

                prefsLoadMillis = System.currentTimeMillis();

                //XposedBridge.log("prefs psGamepadHandler : " + (psGamepadHandler == null ? null : psGamepadHandler.hashCode()));

                if(psGamepadHandler != null) {
                    psGamepadHandler.setOnGamepadStateChangeListener(null);
                    psGamepadHandler = null;
                }

                Activity activity = (Activity) param.thisObject;
                String msg = "Load Profile Failed.";

                try {
                    if (context == null)
                        context = activity.getApplicationContext();

                    File prefsFile = new File(PREFS_PATH);

                    XposedBridge.log("prefs path = " + PREFS_PATH);

                    if(prefsFile.exists()) {
                        XposedBridge.log("prefs file exists.");
                    } else {
                        XposedBridge.log("prefs file not exists.");
                    }

                    XSharedPreferences prefs = new XSharedPreferences(prefsFile);

                    Map<String, ?> prefsValues = prefs.getAll();
                    int mappingCount = 0;

                    for(String key : prefsValues.keySet()) {
                        if(key.contains("analog_") || key.startsWith("button_") || key.startsWith("dpad_"))
                            mappingCount++;

                        XposedBridge.log("prefs [" + key + "] => [" + prefsValues.get(key) + "]");
                    }

                    XposedBridge.log("prefs mapping count = " + mappingCount);

                    String lastProfile = null;
                    if (mappingCount == 0) {
                        msg = "No Mapping Profile.";
                    } else {
                        lastProfile = prefs.getString("last_profile", null);
                        msg = lastProfile == null || lastProfile.isEmpty() ? "[ Default ]" : "[ " + lastProfile + " ]";

                        psGamepadHandler = new PSGamepadHandler(activity, null, prefs);
                        psGamepadHandler.setOnGamepadStateChangeListener(XRPAssistant.this);
                    }

                } catch (Exception e) {
                    log(e);
                } finally {
                    Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();

                }
			}
		});

        // Hook method to tell app that controller is being used
        XposedHelpers.findAndHookMethod("o.\u017f\u04c0", lpparam.classLoader, "\u02cb", Context.class, InputDevice.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (XRPAssistant.psGamepadHandler != null) {
                    param.setResult(true);
                }
            }
        });

        // Prevent Remote Play from handling input events...
        XposedHelpers.findAndHookMethod("o.\u0142\u04c0", lpparam.classLoader, "\u02cf", InputDevice.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // ...UNLESS it was sourced from the touchscreen controller
                InputDevice device = (InputDevice) param.args[0];
                if (!device.isVirtual()) {
                    param.setResult(null);
                }
            }
        });

        // Remote Play reads from an object containing an RPCorePadData object every 16ms.
        // When that class is instantiated, store it onto the state sender.
        // If we don't do this, then the pad data will get reset
        XposedHelpers.findAndHookConstructor("o.\u0142\u04c0", lpparam.classLoader, Context.class, new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XGamepadStateSender.setPadDataHolder(param.thisObject);
            }
        });

        // Hook methods to defeat root detection
        XposedHelpers.findAndHookMethod("o.\u0456\u0433", lpparam.classLoader, "\u0971", int.class, char.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Method method = ((Class) param.getResult()).getDeclaredMethods()[0];
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(true);
                    }
                });
            }
        });

        XposedHelpers.findAndHookMethod("o.\u0197\u0399", lpparam.classLoader, "\u02cb", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Class clazz = XposedHelpers.findClass("o.\u0196\u0268", param.thisObject.getClass().getClassLoader());
                Object obj = XposedHelpers.getStaticObjectField(clazz, "\u02cf");
                XposedHelpers.setBooleanField(obj, "\u0971\u02ca", false);
            }
        });

        new XFakeWifiEnabler(lpparam).apply();
    }

    private void hookHomeButton(ClassLoader classLoader) {
        String[] WINDOW_MANAGER_CLASS_NAMES;
        if (Build.VERSION.SDK_INT >= 23) {
            WINDOW_MANAGER_CLASS_NAMES = new String[]{
                    "com.android.server.policy.OemPhoneWindowManager", // OxygenOS Devices
                    "com.android.server.policy.PhoneWindowManager"
            };
        } else {
            WINDOW_MANAGER_CLASS_NAMES = new String[]{
                    "com.android.internal.policy.impl.PhoneWindowManager"
            };
        }
        Class<?> windowManagerClass = null;
        for (String className : WINDOW_MANAGER_CLASS_NAMES) {
            windowManagerClass = XposedHelpers.findClassIfExists(className, classLoader);
            if (windowManagerClass != null) {
                break;
            }
        }

        String CLASS_WINDOW_STATE = Build.VERSION.SDK_INT >= 28 ? "com.android.server.policy.WindowManagerPolicy$WindowState"
                : "android.view.WindowManagerPolicy$WindowState";
        Class<?> windowStateClass = XposedHelpers.findClassIfExists(CLASS_WINDOW_STATE, classLoader);
        if (windowManagerClass == null || windowStateClass == null) {
            log("Could not find Window Manager class, home key interception will not work!");
            return;
        }

        XC_MethodHook dispatchKeyHook = new XC_MethodHook(XCallback.PRIORITY_HIGHEST) {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                KeyEvent event;
                if (param.method.getName().equals("interceptKeyBeforeDispatching")) {
                    event = (KeyEvent) param.args[1];
                } else {
                    event = (KeyEvent) param.args[0];
                }
                log(String.format("%s %s", param.method.getName(), event));

                // If we're getting a home key from a controller...
                if (event.getKeyCode() == KeyEvent.KEYCODE_HOME &&
                        (event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD &&
                        (event.getSource() & InputDevice.SOURCE_DPAD) != InputDevice.SOURCE_DPAD) {
                    Context mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                    String topPackage = am.getRunningTasks(1).get(0).topActivity.getPackageName();
                    log(topPackage);

                    // ...and our apps are in focus, send the event up to them rather than have it be swallowed up by the OS.
                    if (topPackage.equals("com.rAs.android.rpgamepad") || topPackage.equals("com.playstation.remoteplay")) {
                        log(String.format("%s sending event to app", param.method.getName()));
                        if (param.method.getName().equals("interceptKeyBeforeDispatching")) {
                            param.setResult(0L);
                        } else {
                            param.setResult(1);
                        }
                    }
                }
            }

            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                log(String.format("%s result: %s", param.method.getName(), param.getResult()));
            }
        };

        XposedHelpers.findAndHookMethod(windowManagerClass, "interceptKeyBeforeQueueing",
                KeyEvent.class, int.class, dispatchKeyHook);

        XposedHelpers.findAndHookMethod(windowManagerClass, "interceptKeyBeforeDispatching",
                windowStateClass, KeyEvent.class, int.class, dispatchKeyHook);

    }

    @Override
    public void onGamepadStateChange(boolean sensor) {
        XGamepadStateSender.applyGamepadState(psGamepadHandler);
    }
}
