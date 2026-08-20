package com.nexaflow.testfixture.locale;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Deterministic Locale setting editor used only by Android integration validation.
 * It returns a bounded primitive bundle immediately and retains no user data.
 */
public final class LocalePluginEditActivity extends Activity {
    public static final String EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE";
    public static final String EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.STRING_BLURB";
    public static final String KEY_OUTCOME = "fixtureOutcome";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finishWithDeterministicConfiguration();
    }

    private void finishWithDeterministicConfiguration() {
        Bundle configuration = new Bundle();
        configuration.putString(KEY_OUTCOME, "success");
        configuration.putString("fixtureVersion", "1");
        Intent result = new Intent()
                .putExtra(EXTRA_BUNDLE, configuration)
                .putExtra(EXTRA_STRING_BLURB, "NexaFlow validation: success");
        setResult(RESULT_OK, result);
        finish();
    }
}
