package com.example.text2speech;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * MainActivity
 *
 * BE_VanQuan đóng góp:
 *   - PlaybackService binding  → TTS chạy ngầm (2.2)
 *   - FileReaderHelper + file picker → import TXT/PDF (2.3)
 *
 * BE_Nhi đóng góp:
 *   - ProximitySensorManager   → cảm biến tiệm cận (2.4)
 *   - SettingsActivity         → bật/tắt sensor
 *   - FavoritesActivity        → tab yêu thích
 */
public class MainActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────
    private EditText     textInput;
    private SeekBar      rateSeekBar;
    private SeekBar      pitchSeekBar;
    private TextView     rateValue;
    private TextView     pitchValue;
    private TextView     resetBtn;
    private ImageButton  playBtn;
    private ImageButton  backBtn;
    private ImageButton  forwardBtn;
    private ImageButton  settingsBtn;
    private LinearLayout favoritesTab;
    private LinearLayout textTab;
    private TextView btnOpenTxt;   // chip chọn file TXT
    private TextView btnOpenPdf;   // chip chọn file PDF
    private TextView tvFileName;   // hiển thị tên file đã chọn
    private float currentRate  = 1.0f;
    private float currentPitch = 1.0f;

    // ════════════════════════════════════════════════════════════════════
    //  PLAYBACK SERVICE (BE_VanQuan)
    // ════════════════════════════════════════════════════════════════════

    private PlaybackService boundService   = null;
    private boolean         isServiceBound = false;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            boundService   = ((PlaybackService.LocalBinder) binder).getService();
            isServiceBound = true;
            syncPlayIcon();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            boundService   = null;
            isServiceBound = false;
        }
    };

    // ════════════════════════════════════════════════════════════════════
    // FILE PICKER (BE_VanQuan)
    // ════════════════════════════════════════════════════════════════════

    private ActivityResultLauncher<String[]> filePickerLauncher;

    // ════════════════════════════════════════════════════════════════════
    // PROXIMITY SENSOR (BE_Nhi)
    // ════════════════════════════════════════════════════════════════════

    private ProximitySensorManager proximitySensorManager;

    // ── Hằng ─────────────────────────────────────────────────────────────────
    private static final int    REQ_POST_NOTIF  = 301;
    private static final String PREFS_NAME      = "AppSettings";
    private static final String PREF_SENSOR_KEY = "sensor_enabled";

    // ════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FileReaderHelper.init(this);  // Khởi tạo PDFBox trước khi đọc PDF

        bindViews();
        setupFilePicker();     // Phải đăng ký trước onStart
        setupFileButtons();    // chip TXT / PDF
        setupSeekBars();
        setupButtons();        // Nối service
        setupSensor();         // Proximity sensor
        setupSettings();       // Mở SettingsActivity

        requestNotificationPermission(); // Android 13+
    }

    @Override
    protected void onStart() {
        super.onStart();
        // startService đảm bảo service sống kể cả khi Activity bị ẩn
        Intent svcIntent = new Intent(this, PlaybackService.class);
        startService(svcIntent);
        bindService(svcIntent, serviceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Đọc lại trạng thái sensor từ SharedPreferences
        // → người dùng có thể đã thay đổi trong SettingsActivity
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean sensorEnabled = prefs.getBoolean(PREF_SENSOR_KEY, true);

        if (proximitySensorManager != null) {
            proximitySensorManager.setEnabled(sensorEnabled);
            if (sensorEnabled) {
                proximitySensorManager.register();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Luôn hủy đăng ký sensor khi Activity không hiển thị (tiết kiệm pin)
        if (proximitySensorManager != null) {
            proximitySensorManager.unregister();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind nhưng KHÔNG stopService → PlaybackService tiếp tục chạy ngầm
        if (isServiceBound) {
            unbindService(serviceConn);
            isServiceBound = false;
            boundService   = null;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  BIND VIEWS
    // ════════════════════════════════════════════════════════════════════

    private void bindViews() {
        textInput    = findViewById(R.id.textInput);
        rateSeekBar  = findViewById(R.id.rateSeekBar);
        pitchSeekBar = findViewById(R.id.pitchSeekBar);
        rateValue    = findViewById(R.id.rateValue);
        pitchValue   = findViewById(R.id.pitchValue);
        resetBtn     = findViewById(R.id.resetBtn);
        playBtn      = findViewById(R.id.playBtn);
        backBtn      = findViewById(R.id.backBtn);
        forwardBtn   = findViewById(R.id.forwardBtn);
        settingsBtn  = findViewById(R.id.settingsBtn);
        favoritesTab = findViewById(R.id.favoritesTab);
        textTab      = findViewById(R.id.textTab);
        btnOpenTxt  = findViewById(R.id.btnOpenTxt);
        btnOpenPdf  = findViewById(R.id.btnOpenPdf);
        tvFileName  = findViewById(R.id.tvFileName);
    }

    // ════════════════════════════════════════════════════════════════════
    // FILE PICKER (BE_VanQuan)
    // ════════════════════════════════════════════════════════════════════

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) readAndShowFile(uri); }
        );
    }

    private void setupFileButtons() {
        btnOpenTxt.setOnClickListener(v ->
                filePickerLauncher.launch(new String[]{"text/plain"}));
        btnOpenPdf.setOnClickListener(v ->
                filePickerLauncher.launch(new String[]{"application/pdf"}));
    }

    private void readAndShowFile(Uri uri) {
        tvFileName.setText("Đang đọc...");
        textInput.setText("");

        FileReaderHelper.readFile(this, uri, new FileReaderHelper.ReadCallback() {
            @Override
            public void onSuccess(String text) {
                runOnUiThread(() -> {
                    textInput.setText(text);
                    String path = uri.getPath();
                    String name = (path != null && path.contains("/"))
                            ? path.substring(path.lastIndexOf('/') + 1) : uri.toString();
                    tvFileName.setText(name);
                    Toast.makeText(MainActivity.this, "Đã tải file!", Toast.LENGTH_SHORT).show();
                });
            }
            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    tvFileName.setText("Lỗi đọc file");
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════
    //  SEEKBARS
    // ════════════════════════════════════════════════════════════════════

    private void setupSeekBars() {
        rateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                currentRate = Math.max(progress / 100f, 0.1f);
                rateValue.setText(progress + "%");
                // Cập nhật service nếu đang chạy
                if (fromUser && isServiceBound && boundService != null) {
                    boundService.setTtsRate(currentRate);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        pitchSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                currentPitch = Math.max(progress / 100f, 0.1f);
                pitchValue.setText(progress + "%");
                if (fromUser && isServiceBound && boundService != null) {
                    boundService.setTtsPitch(currentPitch);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    // ════════════════════════════════════════════════════════════════════
    //  BUTTONS
    // ════════════════════════════════════════════════════════════════════

    private void setupButtons() {

        // Play / Pause → gọi service để chạy ngầm được
        playBtn.setOnClickListener(v -> {
            String text = textInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập văn bản!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isServiceBound || boundService == null) {
                Toast.makeText(this, "Service chưa sẵn sàng!", Toast.LENGTH_SHORT).show();
                return;
            }
            handlePlayPause(text);
        });

        // Reset
        resetBtn.setOnClickListener(v -> {
            rateSeekBar.setProgress(50);
            pitchSeekBar.setProgress(50);
        });

        // Back = dừng hẳn
        backBtn.setOnClickListener(v -> stopPlayback());

        // Forward — chưa implement
        forwardBtn.setOnClickListener(v -> {});

        // Favorites tab — mở FavoritesActivity (BE_Nhi)
        favoritesTab.setOnClickListener(v -> navigateToFavorites());
    }

    private void handlePlayPause(String text) {
        boolean reading = boundService.isCurrentlyReading();
        boolean paused  = boundService.isCurrentlyPaused();

        if (!reading && !paused) {
            // Chưa đọc gì → bắt đầu từ đầu
            boundService.setTtsRate(currentRate);
            boundService.setTtsPitch(currentPitch);
            boundService.speakFullText(text);
            playBtn.setImageResource(R.drawable.ic_pause);
        } else {
            // Đang đọc / đang pause → toggle
            boundService.togglePauseResume();
            syncPlayIcon();
        }
    }

    private void stopPlayback() {
        if (isServiceBound && boundService != null) {
            boundService.stopReadingAndService();
        }
        playBtn.setImageResource(R.drawable.ic_play_button);
    }

    private void syncPlayIcon() {
        if (!isServiceBound || boundService == null) return;
        if (boundService.isCurrentlyReading()) {
            playBtn.setImageResource(R.drawable.ic_pause);
        } else {
            playBtn.setImageResource(R.drawable.ic_play_button);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PROXIMITY SENSOR
    // ════════════════════════════════════════════════════════════════════

    private void setupSensor() {
        SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        proximitySensorManager = new ProximitySensorManager(sm, () -> {
            // Callback chạy trên sensor thread → cần runOnUiThread (BE_Nhi)
            runOnUiThread(() -> {
                String text = textInput.getText().toString().trim();
                if (text.isEmpty() || !isServiceBound || boundService == null) return;
                // Kích hoạt cảm biến → toggle play/pause qua service
                handlePlayPause(text);
            });
        });
    }

    // ════════════════════════════════════════════════════════════════════
    //  SETTINGS
    // ════════════════════════════════════════════════════════════════════

    private void setupSettings() {
        settingsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    private void navigateToFavorites() {
        Intent intent = new Intent(this, FavoritesActivity.class);
        startActivity(intent);
    }

    // ════════════════════════════════════════════════════════════════════
    //  PERMISSION — Android 13+
    // ════════════════════════════════════════════════════════════════════

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIF);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  ẨN BÀN PHÍM KHI BẤM RA NGOÀI
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        View focused = getCurrentFocus();
        if (focused instanceof EditText
                && (ev.getAction() == MotionEvent.ACTION_UP
                || ev.getAction() == MotionEvent.ACTION_MOVE)) {

            int[] loc = new int[2];
            focused.getLocationOnScreen(loc);
            float x = ev.getRawX() + focused.getLeft() - loc[0];
            float y = ev.getRawY() + focused.getTop()  - loc[1];

            if (x < focused.getLeft() || x > focused.getRight()
                    || y < focused.getTop()  || y > focused.getBottom()) {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}