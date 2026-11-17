package com.boloenglish.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_SPEECH = 101;

    private TextView hindiText;
    private TextView englishText;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private Translator translator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        hindiText = findViewById(R.id.hindiText);
        englishText = findViewById(R.id.englishText);
        Button micButton = findViewById(R.id.micButton);
        Button copyButton = findViewById(R.id.copyButton);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("en", "US"));
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.HINDI)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build();
        translator = Translation.getClient(options);

        micButton.setOnClickListener(v -> startListening());

        copyButton.setOnClickListener(v -> {
            String english = englishText.getText().toString().trim();
            if (!english.isEmpty() && !english.startsWith("English") && !english.startsWith("...")) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("english", english));
                Toast.makeText(this, "English copy ho gayi ✓", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Hindi me boliye...");
        try {
            startActivityForResult(intent, REQ_SPEECH);
        } catch (Exception e) {
            Toast.makeText(this, "Is phone pe speech recognition nahi mila hai", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String hindi = results.get(0);
                hindiText.setText(hindi);
                englishText.setText("...translating...");
                translate(hindi);
            }
        }
    }

    private void translate(String hindi) {
        translator.downloadModelIfNeeded()
                .addOnSuccessListener(unused -> translator.translate(hindi)
                        .addOnSuccessListener(english -> {
                            englishText.setText(english);
                            if (ttsReady) {
                                tts.speak(english, TextToSpeech.QUEUE_FLUSH, null, "boloid");
                            }
                        })
                        .addOnFailureListener(e ->
                                englishText.setText("Translation fail: " + e.getMessage())))
                .addOnFailureListener(e ->
                        englishText.setText("Pehli baar internet chahiye (model download): " + e.getMessage()));
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        translator.close();
        super.onDestroy();
    }
}
