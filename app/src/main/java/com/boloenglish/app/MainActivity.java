package com.boloenglish.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_SPEECH = 101;
    private static final String PREFS = "bolo";
    private static final String KEY_API = "api_key";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private Button modeHindi, modePractice, micButton, checkButton, speakButton, settingsButton;
    private EditText practiceInput;
    private TextView inputEcho, statusText, outMain, outTense, outAlt, outMistake, outTip;
    private LinearLayout resultCard;
    private ProgressBar loading;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean hindiMode = true;
    private String apiKey = "";
    private String lastSpoken = "";
    private String pendingHindi = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        modeHindi = findViewById(R.id.modeHindi);
        modePractice = findViewById(R.id.modePractice);
        micButton = findViewById(R.id.micButton);
        checkButton = findViewById(R.id.checkButton);
        speakButton = findViewById(R.id.speakButton);
        settingsButton = findViewById(R.id.settingsButton);
        practiceInput = findViewById(R.id.practiceInput);
        inputEcho = findViewById(R.id.inputEcho);
        statusText = findViewById(R.id.statusText);
        outMain = findViewById(R.id.outMain);
        outTense = findViewById(R.id.outTense);
        outAlt = findViewById(R.id.outAlt);
        outMistake = findViewById(R.id.outMistake);
        outTip = findViewById(R.id.outTip);
        resultCard = findViewById(R.id.resultCard);
        loading = findViewById(R.id.loading);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        apiKey = prefs.getString(KEY_API, "");

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("en", "US"));
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });

        modeHindi.setOnClickListener(v -> setMode(true));
        modePractice.setOnClickListener(v -> setMode(false));
        micButton.setOnClickListener(v -> startListening());
        checkButton.setOnClickListener(v -> {
            String text = practiceInput.getText().toString().trim();
            if (!text.isEmpty()) {
                inputEcho.setVisibility(View.VISIBLE);
                inputEcho.setText("You: " + text);
                askTutor(practicePrompt(text));
            }
        });
        speakButton.setOnClickListener(v -> {
            if (ttsReady && !lastSpoken.isEmpty()) {
                tts.speak(lastSpoken, TextToSpeech.QUEUE_FLUSH, null, "bd");
            }
        });
        settingsButton.setOnClickListener(v -> showSettings());

        speakButton.setOnLongClickListener(v -> {
            String en = outMain.getText().toString().trim();
            if (!en.isEmpty()) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("english", en));
                Toast.makeText(this, "Copy ho gayi ✓", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        if (apiKey.isEmpty()) {
            showSettings();
        }
    }

    private void setMode(boolean hindi) {
        hindiMode = hindi;
        modeHindi.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                hindi ? getColor(R.color.accent) : getColor(R.color.card_dark)));
        modePractice.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                hindi ? getColor(R.color.card_dark) : getColor(R.color.accent)));
        micButton.setVisibility(hindi ? View.VISIBLE : View.GONE);
        practiceInput.setVisibility(hindi ? View.GONE : View.VISIBLE);
        checkButton.setVisibility(hindi ? View.GONE : View.VISIBLE);
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Hindi me boliye, English Dost seekhayega...");
        try {
            startActivityForResult(intent, REQ_SPEECH);
        } catch (Exception e) {
            Toast.makeText(this, "Is phone pe speech recognition nahi mila", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                pendingHindi = results.get(0);
                inputEcho.setVisibility(View.VISIBLE);
                inputEcho.setText("Aapne kaha: " + pendingHindi);
                askTutor(hindiPrompt(pendingHindi));
            }
        }
    }

    private String hindiPrompt(String hindi) {
        return "You are 'English Dost', a friendly English tutor for Hindi speakers. "
                + "The user said this in Hindi:\n\"" + hindi + "\"\n\n"
                + "Reply in EXACTLY this format, each on its own line, no extra headings, no markdown asterisks:\n"
                + "ENGLISH: <natural English translation; give 2 options separated by ' | '>\n"
                + "TENSE: <which tense is used + 1 short grammar note, in easy Hinglish>\n"
                + "ALTERNATIVES: <2 more natural ways to say the same thing in English, numbered 1. 2.>\n"
                + "MISTAKES: <1 common mistake Hindi speakers make with this sentence, explained briefly in Hinglish>\n"
                + "TIP: <1 short useful tip in Hinglish>\n\n"
                + "Rules: explanations in Hinglish (Hindi words in English letters), examples in clean English. Keep every line short.";
    }

    private String practicePrompt(String english) {
        return "You are 'English Dost', a friendly English tutor for Hindi speakers. "
                + "The user wrote/said this in English (it may contain mistakes):\n\"" + english + "\"\n\n"
                + "Reply in EXACTLY this format, each on its own line, no extra headings, no markdown asterisks:\n"
                + "CORRECT: <the corrected English sentence>\n"
                + "IF WRONG: <what was wrong (grammar/tense/article/preposition), briefly in Hinglish; if fully correct write 'Bilkul sahi! Perfect.'>\n"
                + "BETTER: <a more natural/native way to say it; write '—' if already natural>\n"
                + "TIP: <1 short tip in Hinglish to remember>\n\n"
                + "Rules: keep it short and encouraging.";
    }

    private void askTutor(String prompt) {
        if (apiKey.isEmpty()) {
            showSettings();
            return;
        }
        loading.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.GONE);
        resultCard.setVisibility(View.GONE);
        inputEcho.setVisibility(View.VISIBLE);

        new Thread(() -> {
            String response;
            try {
                response = callGemini(prompt);
            } catch (Exception e) {
                response = null;
                String err = e.getMessage();
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    statusText.setVisibility(View.VISIBLE);
                    statusText.setText("⚠️ " + (err == null ? "Kuch gadbad ho gayi" : err));
                });
                return;
            }
            String finalResponse = response;
            runOnUiThread(() -> {
                loading.setVisibility(View.GONE);
                showResult(finalResponse);
            });
        }).start();
    }

    private String callGemini(String prompt) throws Exception {
        URL url = new URL(API_URL + apiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(45000);

        JSONObject body = new JSONObject();
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", prompt));
        body.put("contents", new JSONArray().put(new JSONObject().put("parts", parts)));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 400) {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            if (code == 400 || code == 401 || code == 403) {
                throw new Exception("API key galat hai ya expire ho gayi. ⚙️ Settings me sahi key daalo.");
            } else if (code == 429) {
                throw new Exception("Free limit cross ho gayi. Thodi der baad try karo.");
            } else {
                throw new Exception("Server error " + code);
            }
        }
        reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        JSONObject json = new JSONObject(sb.toString());
        JSONArray candidates = json.getJSONArray("candidates");
        return candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text");
    }

    private void showResult(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("⚠️ Khali jawab aaya, dobara try karo");
            return;
        }
        String english = "", tense = "", alt = "", mistake = "", tip = "";
        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (t.startsWith("ENGLISH:")) english = t.substring(8).trim();
            else if (t.startsWith("CORRECT:")) english = t.substring(8).trim();
            else if (t.startsWith("TENSE:")) tense = "📘 " + t.substring(6).trim();
            else if (t.startsWith("ALTERNATIVES:")) alt = "💬 " + t.substring(13).trim();
            else if (t.startsWith("MISTAKES:")) mistake = "⚠️ " + t.substring(9).trim();
            else if (t.startsWith("IF WRONG:")) mistake = "⚠️ " + t.substring(9).trim();
            else if (t.startsWith("BETTER:")) {
                String b = t.substring(7).trim();
                if (!b.isEmpty() && !b.equals("—")) alt = "💬 Native tarika: " + b;
            } else if (t.startsWith("TIP:")) tip = "💡 " + t.substring(4).trim();
        }

        if (english.isEmpty()) english = raw.trim();

        // Speak only the first option
        lastSpoken = english.split("\\|")[0].trim();

        outMain.setText(english);
        outTense.setText(tense.isEmpty() ? "" : tense);
        outAlt.setText(alt.isEmpty() ? "" : alt);
        outMistake.setText(mistake.isEmpty() ? "" : mistake);
        outTip.setText(tip.isEmpty() ? "" : tip);
        resultCard.setVisibility(View.VISIBLE);

        if (ttsReady) {
            tts.speak(lastSpoken, TextToSpeech.QUEUE_FLUSH, null, "bd");
        }
    }

    private void showSettings() {
        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
        final EditText input = new EditText(this);
        input.setHint("API key paste karo (AIza...)");
        input.setText(apiKey);
        input.setSingleLine(false);

        new AlertDialog.Builder(this)
                .setTitle("⚙️ Gemini API Key")
                .setMessage("Free key kaise milegi:\n\n1. aistudio.google.com/apikey kholo\n2. Google account se login karo\n3. 'Create API key' dabao\n4. Key copy karke yahan paste karo\n\nKey sirf tere phone me save hoti hai.")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    apiKey = input.getText().toString().trim();
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                            .edit().putString(KEY_API, apiKey).apply();
                    if (!apiKey.isEmpty()) {
                        Toast.makeText(this, "Key save ho gayi ✓", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("🔑 Free key lo", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://aistudio.google.com/apikey")));
                    } catch (Exception ignored) {
                        Toast.makeText(this, "Browser kholo: aistudio.google.com/apikey", Toast.LENGTH_LONG).show();
                    }
                    showSettings();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
