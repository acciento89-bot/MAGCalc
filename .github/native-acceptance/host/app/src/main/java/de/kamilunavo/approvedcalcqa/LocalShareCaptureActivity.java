package de.kamilunavo.approvedcalcqa;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Receives a synthetic QA worksheet locally; performs no networking or external messaging. */
public class LocalShareCaptureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CharSequence text = getIntent().getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (!Intent.ACTION_SEND.equals(getIntent().getAction()) || text == null) { finish(); return; }
        File directory = new File(getFilesDir(), "approvedcalc-native-qa"); directory.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(new File(directory, "last-actual-share.txt"))) {
            stream.write(text.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) { throw new IllegalStateException(error); }
        TextView body = new TextView(this); body.setText("QA payload captured\n\n" + text); body.setTextSize(14); body.setPadding(24, 40, 24, 40);
        ScrollView scroll = new ScrollView(this); scroll.addView(body); setContentView(scroll);
    }
}
