package com.example.text2speech;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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
    private boolean isPlaying = false;

    // Cảm biến tiệm cận
    private ProximitySensorManager proximitySensorManager;

    // Settings
    private ImageButton settingsBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupTextToSpeech();
        setupSeekBars();
        setupButtons();
        setupSensor();      // khởi tạo cảm biến
        setupSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE);
        boolean isSensorEnabled = sharedPreferences.getBoolean("sensor_enabled", true);

        if (proximitySensorManager != null) {
            // Cập nhật trạng thái cho Manager trước khi gọi register
            proximitySensorManager.setEnabled(isSensorEnabled);

            if (isSensorEnabled) {
                proximitySensorManager.register();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Luôn hủy đăng ký để an toàn và tiết kiệm pin
        if (proximitySensorManager != null) {
            proximitySensorManager.unregister();
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

    //Bind views

    private void bindViews() {
        textInput     = findViewById(R.id.textInput);
        rateSeekBar   = findViewById(R.id.rateSeekBar);
        pitchSeekBar  = findViewById(R.id.pitchSeekBar);
        rateValue     = findViewById(R.id.rateValue);
        pitchValue    = findViewById(R.id.pitchValue);
        playBtn       = findViewById(R.id.playBtn);
        backBtn       = findViewById(R.id.backBtn);
        forwardBtn    = findViewById(R.id.forwardBtn);
        resetBtn      = findViewById(R.id.resetBtn);
        favoritesTab  = findViewById(R.id.favoritesTab);
        textTab       = findViewById(R.id.textTab);
        settingsBtn   = findViewById(R.id.settingsBtn);
    }

    private void setupSettings(){
        settingsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    // TextToSpeech

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(new Locale("vi", "VN"));

            textToSpeech.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override
                public void onDone(String utteranceId) {
                    runOnUiThread(() -> {
                        playBtn.setImageResource(R.drawable.ic_play_button);
                        isPlaying = false;
                    });
                }
                @Override public void onError(String utteranceId) {}
                @Override public void onStart(String utteranceId) {}
            });

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Tiếng Việt không hỗ trợ → dùng tiếng Anh");
                textToSpeech.setLanguage(Locale.US);
            }
        } else {
            Log.e("TTS", "Khởi tạo TTS thất bại");
        }
    }

    // SeekBars

    private void setupSeekBars() {
        rateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rate = Math.max(progress / 100f, 0.1f);
                rateValue.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        pitchSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                pitch = Math.max(progress / 100f, 0.1f);
                pitchValue.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // Buttons

    private void setupButtons() {
        // Play / Pause
        playBtn.setOnClickListener(v -> {
            String text = textInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập văn bản!", Toast.LENGTH_SHORT).show();
                return;
            }
            togglePlayPause();
        });

        // Reset seekbars
        resetBtn.setOnClickListener(v -> {
            rateSeekBar.setProgress(50);
            pitchSeekBar.setProgress(50);
        });

        // Tab Favorites
        favoritesTab.setOnClickListener(v -> navigateToFavorites());

        // Back – dừng và reset icon
        backBtn.setOnClickListener(v -> stopSpeech());

        // Forward – chưa xử lý
        forwardBtn.setOnClickListener(v -> {});
    }

    //Cảm biến tiệm cận

//    Khởi tạo ProximitySensorManager và thiết lập Switch bật/tắt.

    private void setupSensor() {
        SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Khởi tạo lớp quản lý cảm biến, truyền callback xử lý khi kích hoạt
        proximitySensorManager = new ProximitySensorManager(sm, () -> {
            // Callback chạy trên luồng cảm biến → phải dùng runOnUiThread để cập nhật UI
            runOnUiThread(() -> {
                String text = textInput.getText().toString().trim();
                if (text.isEmpty()) return;
                togglePlayPause();
            });
        });
    }

    // Helpers

    // Chuyển đổi trạng thái play ↔ pause
    private void togglePlayPause() {
        if (!isPlaying) {
            // Bắt đầu đọc
            playBtn.setImageResource(R.drawable.ic_pause);
            isPlaying = true;
            String text = textInput.getText().toString().trim();
            if (textToSpeech != null) {
                textToSpeech.stop();
                textToSpeech.setPitch(pitch);
                textToSpeech.setSpeechRate(rate);
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1");
            }
        } else {
            // Tạm dừng
            stopSpeech();
        }
    }

    /** Dừng TTS và reset icon về play */
    private void stopSpeech() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        isPlaying = false;
        playBtn.setImageResource(R.drawable.ic_play_button);
    }

    private void navigateToFavorites() {
        try {
            Intent intent = new Intent(this, FavoritesActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("NAV", "FavoritesActivity chưa tạo");
        }
    }

    // Ẩn bàn phím khi bấm ra ngoài

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        View v = getCurrentFocus();
        if (v != null
                && (ev.getAction() == MotionEvent.ACTION_UP
                || ev.getAction() == MotionEvent.ACTION_MOVE)
                && v instanceof EditText
                && !v.getClass().getName().startsWith("android.webkit.")) {

            int[] coords = new int[2];
            v.getLocationOnScreen(coords);
            float x = ev.getRawX() + v.getLeft() - coords[0];
            float y = ev.getRawY() + v.getTop() - coords[1];

            if (x < v.getLeft() || x > v.getRight()
                    || y < v.getTop() || y > v.getBottom()) {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}