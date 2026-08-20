package com.nexaflow.testfixture.locale;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Deterministic Locale condition editor used exclusively by integration validation. */
public final class LocalePluginConditionEditActivity extends Activity {
    private static final String EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE";
    private static final String EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.STRING_BLURB";
    public static final String KEY_STATE = "fixtureConditionState";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle configuration = new Bundle();
        configuration.putString(KEY_STATE, "satisfied");
        Intent result = new Intent()
                .putExtra(EXTRA_BUNDLE, configuration)
                .putExtra(EXTRA_STRING_BLURB, "NexaFlow validation: condition satisfied");
        setResult(RESULT_OK, result);
        finish();
    }
}
