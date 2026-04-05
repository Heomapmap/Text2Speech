package com.example.tts;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private EditText textInput;
    private SeekBar rateSeekBar, pitchSeekBar;
    private TextView rateValue, pitchValue, resetBtn;
    private ImageButton playBtn, backBtn, forwardBtn;
    private LinearLayout favoritesTab, textTab;

    private TextToSpeech textToSpeech;
    private float rate = 1.0f;
    private float pitch = 1.0f;

    // 👉 THÊM BIẾN TRẠNG THÁI
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind view
        textInput = findViewById(R.id.textInput);
        rateSeekBar = findViewById(R.id.rateSeekBar);
        pitchSeekBar = findViewById(R.id.pitchSeekBar);
        rateValue = findViewById(R.id.rateValue);
        pitchValue = findViewById(R.id.pitchValue);
        playBtn = findViewById(R.id.playBtn);
        backBtn = findViewById(R.id.backBtn);
        forwardBtn = findViewById(R.id.forwardBtn);
        resetBtn = findViewById(R.id.resetBtn);
        favoritesTab = findViewById(R.id.favoritesTab);
        textTab = findViewById(R.id.textTab);

        // Init TTS
        textToSpeech = new TextToSpeech(this, this);

        // Rate
        rateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rate = Math.max(progress / 100f, 0.1f);
                rateValue.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Pitch
        pitchSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                pitch = Math.max(progress / 100f, 0.1f);
                pitchValue.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // ▶️ PLAY / PAUSE
        playBtn.setOnClickListener(v -> {

            String text = textInput.getText().toString().trim();

            if (!isPlaying) {
                // ▶️ → ⏸
                playBtn.setImageResource(R.drawable.ic_pause);
                isPlaying = true;

                if (!text.isEmpty() && textToSpeech != null) {
                    textToSpeech.stop();
                    textToSpeech.setPitch(pitch);
                    textToSpeech.setSpeechRate(rate);
                    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1");
                }

            } else {
                // ⏸ → ▶️
                playBtn.setImageResource(R.drawable.ic_play_button);
                isPlaying = false;

                if (textToSpeech != null) {
                    textToSpeech.stop();
                }
            }
        });

        // Reset
        resetBtn.setOnClickListener(v -> {
            rateSeekBar.setProgress(50);
            pitchSeekBar.setProgress(50);
        });

        // Tab Favorites
        favoritesTab.setOnClickListener(v -> navigateToFavorites());

        // Back (stop + reset icon)
        backBtn.setOnClickListener(v -> {
            if (textToSpeech != null) {
                textToSpeech.stop();
            }
            isPlaying = false;
            playBtn.setImageResource(R.drawable.ic_play_button);
        });

        // Forward
        forwardBtn.setOnClickListener(v -> {
            // chưa cần xử lý
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(new Locale("vi", "VN"));

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {

                Log.e("TTS", "Vietnamese not supported → fallback English");
                textToSpeech.setLanguage(Locale.US);
            }
        } else {
            Log.e("TTS", "TTS init failed");
        }
    }

    private void navigateToFavorites() {
        try {
            Intent intent = new Intent(this, FavoritesActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("NAV", "FavoritesActivity chưa tạo");
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}