
package com.fingerprint.identify;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.wei.android.lib.fingerprintidentify.FingerprintIdentify;
import com.wei.android.lib.fingerprintidentify.base.BaseFingerprint;

import android.app.Activity;
import android.util.Log;

public class RNFingerprintIdentifyModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

  private static final int MAX_ATTEMPTS = 5;
  private final ReactApplicationContext reactContext;
  private FingerprintIdentify mFingerprintIdentify = null;

  public RNFingerprintIdentifyModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNFingerprintIdentify";
  }

  @ReactMethod
  public void initFingerPrintIdentify(final Promise promise) {
    Activity currentActivity = getCurrentActivity();
    if (currentActivity != null) {
      if (mFingerprintIdentify == null) {
        reactContext.addLifecycleEventListener(this);
        mFingerprintIdentify = new FingerprintIdentify(currentActivity, new BaseFingerprint.FingerprintIdentifyExceptionListener() {
          @Override
          public void onCatchException(Throwable exception) {
            reactContext.removeLifecycleEventListener(RNFingerprintIdentifyModule.this);
            Log.d("ReactNative", "ERROR FINGERPRINT: " + exception.getLocalizedMessage());
          }
        });
      }
      sendResponse("ok", null, promise);
    } else {
      sendResponse("failed", "ERROR_INITIALIZED", promise);
    }
  }

  @ReactMethod
  public void startIdentify() {

    if(!isSensorAvailable()) {
      sendEvent("failed", "ERROR_NOT_AVAILABLE");
      return;
    }

    mFingerprintIdentify.resumeIdentify();
    mFingerprintIdentify.startIdentify(MAX_ATTEMPTS, new BaseFingerprint.FingerprintIdentifyListener() {
      @Override
      public void onSucceed() {
        // succeed, release hardware automatically
        sendEvent("ok", null);
      }

      @Override
      public void onNotMatch(int availableTimes) {
        // not match, try again automatically
        sendEvent("failed", "ERROR_NOT_MATCH_AND_CHANCES_LEFT:" + String.valueOf(availableTimes));
      }

      @Override
      public void onFailed() {
        // failed, release hardware automatically
        sendEvent("failed", "ERROR_NOT_MATCH");
      }
    });
  }

  @ReactMethod
  public void cancelIdentify() {
    mFingerprintIdentify.cancelIdentify();
    reactContext.removeLifecycleEventListener(this);
  }

  @ReactMethod
  public void isSensorAvailable(final Promise promise) {
    if(mFingerprintIdentify != null) {
      if(mFingerprintIdentify.isFingerprintEnable()
      && mFingerprintIdentify.isRegisteredFingerprint()
      && mFingerprintIdentify.isHardwareEnable()
      ) {
        sendResponse("ok", null, promise);
      } else {
        if(!mFingerprintIdentify.isHardwareEnable()) {
          sendResponse("failed", "ERROR_HARDWARE", promise);
          return;
        }
        if(!mFingerprintIdentify.isRegisteredFingerprint()) {
          sendResponse("failed", "ERROR_ENROLLED", promise);
          return;
        }
        if(!mFingerprintIdentify.isFingerprintEnable()) {
          sendResponse("failed", "ERROR_PERMISSION", promise);
          return;
        }
      }
    } else {
      sendResponse("failed", "ERROR_INITIALIZED", promise);
    }
  }

  public boolean isSensorAvailable() {
    boolean isSensorAvailable = false;
    if(mFingerprintIdentify != null) {
      isSensorAvailable = mFingerprintIdentify.isFingerprintEnable()
                          && mFingerprintIdentify.isRegisteredFingerprint()
                          && mFingerprintIdentify.isHardwareEnable();
    }
    return isSensorAvailable;
  }

  private void sendEvent(String status, String message) {
    WritableMap params = Arguments.createMap();
    params.putString("status", status);
    params.putString("message", message);
    reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit("FINGERPRINT_IDENTIFY_STATUS", params);
  }

  private void sendResponse(String status, String message, Promise promise) {
       WritableMap response = Arguments.createMap();
       response.putString("status", status);
       response.putString("error", message);
       promise.resolve(response);
  }

  @Override
  public void onHostResume() {
    if (mFingerprintIdentify != null) {
      mFingerprintIdentify.resumeIdentify();
    }
  }

  @Override
  public void onHostPause() {
    if (mFingerprintIdentify != null) {
      mFingerprintIdentify.cancelIdentify();
    }
  }

  @Override
  public void onHostDestroy() {
    this.cancelIdentify();
  }
}
