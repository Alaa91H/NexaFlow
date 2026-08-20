package com.nexaflow.testfixture.locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Ordered-broadcast Locale condition receiver for external-plugin validation. */
public final class LocalePluginConditionReceiver extends BroadcastReceiver {
    private static final String ACTION_QUERY_CONDITION = "com.twofortyfouram.locale.intent.action.QUERY_CONDITION";
    private static final String EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE";
    private static final int RESULT_CONDITION_SATISFIED = 16;
    private static final int RESULT_CONDITION_UNSATISFIED = 17;
    private static final int RESULT_CONDITION_UNKNOWN = 18;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_QUERY_CONDITION.equals(intent.getAction())) {
            return;
        }
        Bundle configuration = intent.getBundleExtra(EXTRA_BUNDLE);
        String state = configuration == null
                ? "satisfied"
                : configuration.getString(LocalePluginConditionEditActivity.KEY_STATE, "satisfied");
        switch (state) {
            case "unsatisfied":
                setResultCode(RESULT_CONDITION_UNSATISFIED);
                setResultData("fixture condition unsatisfied");
                break;
            case "unknown":
                setResultCode(RESULT_CONDITION_UNKNOWN);
                setResultData("fixture condition unknown");
                break;
            default:
                setResultCode(RESULT_CONDITION_SATISFIED);
                setResultData("fixture condition satisfied");
                break;
        }
    }
}
