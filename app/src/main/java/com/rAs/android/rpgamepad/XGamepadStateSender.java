package com.rAs.android.rpgamepad;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XposedHelpers;

public class XGamepadStateSender {
	private static Object padData;

	private static Class<?> joystickState;
	private static Class<?> triggerState;

	private static Method setPadDataCallback;

	private static Field padDataHolderField;

	private static Object padDataHolder;

	static void init(Class<?> nativeClass, Class<?> padDataClass, Class<?> joystickStateClass, Class<?> triggerStateClass, Class<?> padDataHolderClass) {
		try {
			padData = padDataClass.newInstance();
			joystickState = joystickStateClass;
			triggerState = triggerStateClass;
			setPadDataCallback = nativeClass.getMethod("rpCoreSetPadData", padDataClass);
			padDataHolderField = padDataHolderClass.getDeclaredField("\u02cb\u0971");

		} catch (Exception e) {
			XRPAssistant.log(e);
		}
	}

	static void setPadDataHolder(Object holder) {
		padDataHolder = holder;
	}

	static void applyGamepadState(PSGamepadHandler handler) {
		PSGamepadValues gamepadValues = handler.getGamepadValues();
		try {
			XposedHelpers.setObjectField(padData, "mButtons", gamepadValues.getButtonState());
			Object leftStick = joystickState.newInstance();
			XposedHelpers.setObjectField(leftStick, "mX", (short) gamepadValues.getLeftAxisX());
			XposedHelpers.setObjectField(leftStick, "mY", (short) gamepadValues.getLeftAxisY());
			XposedHelpers.setObjectField(padData, "mLeftStick", leftStick);

			Object rightStick = joystickState.newInstance();
			XposedHelpers.setObjectField(rightStick, "mX", (short) gamepadValues.getRightAxisX());
			XposedHelpers.setObjectField(rightStick, "mY", (short) gamepadValues.getRightAxisY());
			XposedHelpers.setObjectField(padData, "mRightStick", rightStick);

			Object triggers = triggerState.newInstance();
			XposedHelpers.setObjectField(triggers, "mL2", (short) gamepadValues.getLeftTrigger());
			XposedHelpers.setObjectField(triggers, "mR2", (short) gamepadValues.getRightTrigger());
			XposedHelpers.setObjectField(padData, "mAnalogButtons", triggers);

			runGamepadStateChanged();
		} catch (Exception e) {
			e.printStackTrace();
			XRPAssistant.log(e);
		}
	}

	private static void runGamepadStateChanged() {
		if (padDataHolder == null) {
			XRPAssistant.log("Can't send gamepad data, remote play session has not activated, yet.");
			return;
		}
		try {
			padDataHolderField.set(padDataHolder, padData);
			setPadDataCallback.invoke(null, padData);
		} catch (Exception e) {
			XRPAssistant.log(e);
		}
	}
}
