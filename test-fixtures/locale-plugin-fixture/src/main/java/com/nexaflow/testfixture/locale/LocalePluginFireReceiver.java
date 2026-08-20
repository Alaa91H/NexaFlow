package com.nexaflow.testfixture.locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Ordered-broadcast Locale setting receiver for integration validation only.
 * Outcomes are controlled by a primitive config value and never perform a device side effect.
 */
public final class LocalePluginFireReceiver extends BroadcastReceiver {
    private static final String ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING";
    private static final String EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE";
    private static final int RESULT_CODE_OK = 0;
    private static final int RESULT_CODE_PENDING = 1;
    private static final int RESULT_CODE_CANCELED = 2;
    private static final int RESULT_CODE_FAILED = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_FIRE_SETTING.equals(intent.getAction())) {
            return;
        }
        Bundle configuration = intent.getBundleExtra(EXTRA_BUNDLE);
        String outcome = configuration == null
                ? "success"
                : configuration.getString(LocalePluginEditActivity.KEY_OUTCOME, "success");
        switch (outcome) {
            case "failure":
                setResultCode(RESULT_CODE_FAILED);
                setResultData("fixture requested failure");
                break;
            case "pending":
                setResultCode(RESULT_CODE_PENDING);
                setResultData("fixture requested pending");
                break;
            case "cancelled":
                setResultCode(RESULT_CODE_CANCELED);
                setResultData("fixture requested cancellation");
                break;
            case "timeout":
                try {
                    Thread.sleep(2_000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                setResultCode(RESULT_CODE_OK);
                setResultData("fixture completed after host timeout");
                break;
            default:
                setResultCode(RESULT_CODE_OK);
                setResultData("fixture action completed");
                break;
        }
    }
}
