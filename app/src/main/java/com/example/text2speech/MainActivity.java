package com.example.text2speech;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
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

import com.example.text2speech.R;

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

    //  THÊM BIẾN TRẠNG THÁI
    private boolean isPlaying = false;

    // Cảm biến
    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private SensorEventListener proximityEventListener;
    private long lastSensorTime = 0;


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

        // Init
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
            int speech = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);

            if (text.isEmpty()){
                Toast.makeText(this, "Vui lòng nhập văn bản!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isPlaying) {
                // ▶️ → ⏸
                playBtn.setImageResource(R.drawable.ic_pause);
                isPlaying = true;

                if ( textToSpeech != null) {
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

        // thiết lập cảm biến
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        if (proximitySensor==null){
            Log.e("Sensor", "Máy không hỗ trợ cảm biến tiệm cận!");
        }
        proximityEventListener = new SensorEventListener() {
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }

            @Override
            public void onSensorChanged(SensorEvent event) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastSensorTime < 1000) return;

                if (event.values[0]<proximitySensor.getMaximumRange()){
                    lastSensorTime = currentTime;
                    if (textToSpeech != null && isPlaying){
                        textToSpeech.stop();
                        isPlaying = false;
                        runOnUiThread(() -> playBtn.setImageResource(R.drawable.ic_play_button));
                    }else {
                        String text = textInput.getText().toString().trim();
                        if (!text.isEmpty() && textToSpeech != null) {
                            isPlaying = true;
                            runOnUiThread(()->playBtn.setImageResource(R.drawable.ic_pause));
                            textToSpeech.setPitch(pitch);
                            textToSpeech.setSpeechRate(rate);
                            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1");
                        }
                    }

                }
            }
        };

    }

    // tắt bàn phím khi bấm ra ngoài
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        View v = getCurrentFocus();

        if (v != null && (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_MOVE) &&
                v instanceof EditText &&
                !v.getClass().getName().startsWith("android.webkit.")) {
            int[] scrcoords = new int[2];
            v.getLocationOnScreen(scrcoords);
            float x = ev.getRawX() + v.getLeft() - scrcoords[0];
            float y = ev.getRawY() + v.getTop() - scrcoords[1];

            if (x < v.getLeft() || x > v.getRight() || y < v.getTop() || y > v.getBottom()) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(new Locale("vi", "VN"));
            textToSpeech.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override
                public void onDone(String utteranceId) {
                    runOnUiThread(()->{
                        playBtn.setImageResource(R.drawable.ic_play_button);
                        isPlaying = false;
                    });
                }

                @Override
                public void onError(String utteranceId) {

                }

                @Override
                public void onStart(String utteranceId) {

                }
            });

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

    @Override
    protected void onResume() {
        super.onResume();
        if (proximitySensor != null) {
            sensorManager.registerListener(proximityEventListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (proximitySensor != null) {
            sensorManager.unregisterListener(proximityEventListener);
        }
    }
}