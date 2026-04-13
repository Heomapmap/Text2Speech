package com.example.text2speech;

import android.Manifest;
import android.app.AlertDialog;
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
import android.speech.tts.Voice;
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

import java.util.List;

/**
 * MainActivity
 * BE_VanQuan:
 *   - PlaybackService binding  → TTS chạy ngầm
 *   - FileReaderHelper + file picker → import TXT/PDF
 * BE_Nhi:
 *   - ProximitySensorManager   → cảm biến tiệm cận
 *   - SettingsActivity         → bật/tắt sensor
 *   - FavoritesActivity        → tab yêu thích
 * BE_Phong:
 *   - AppDatabase (Room)       → lưu lịch sử đọc
 *   - saveReadingState()       → lưu vị trí câu + tên file
 *   - loadReadingState()       → khôi phục phiên đọc trước
 */
public class MainActivity extends AppCompatActivity {

    // ── Views  ───────────────────────────────────────
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
    private TextView     btnOpenTxt;   // chip chọn file TXT
    private TextView     btnOpenPdf;   // chip chọn file PDF
    private TextView     tvFileName;   // hiển thị tên file đã chọn

    // ── Views voice selector (layout gốc BE_Nhi đã có sẵn 3 ID này) ─────────
    private LinearLayout voiceLayout;   // id: voiceLayout — click → dialog
    private TextView     tvVoiceName;   // id: voiceName   — tên voice hiện tại
    private TextView     tvVoiceInfo;   // id: voiceInfo   — "Offline" / "Online"

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
            // Delay nhỏ cho TTS engine init xong rồi mới query voices
            playBtn.postDelayed(() -> refreshVoiceDisplay(), 800);
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
    /** URI file đang mở — dùng để lưu lịch sử */
    private Uri    currentFileUri  = null;
    private String currentFileName = "";

    // ════════════════════════════════════════════════════════════════════
    // PROXIMITY SENSOR (BE_Nhi)
    // ════════════════════════════════════════════════════════════════════

    private ProximitySensorManager proximitySensorManager;

    // ════════════════════════════════════════════════════════════════════
    //  ROOM DATABASE + SHAREDPREFERENCES (BE_Phong)
    // ════════════════════════════════════════════════════════════════════

    /** Room database */
    private AppDatabase db;
    /** SharedPreferences nhẹ để lưu nhanh path file cuối */
    private SharedPreferences ttsPref;

    private static final String TTS_PREF_NAME     = "tts_pref";
    private static final String KEY_LAST_FILE_PATH = "last_file_path";
    private static final String KEY_LAST_FILE_NAME = "last_file_name";
    private static final String APP_SETTINGS_PREFS = "AppSettings";
    private static final String KEY_SENSOR_ENABLED = "sensor_enabled";
    private static final int    REQ_POST_NOTIF      = 301;

    // ════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FileReaderHelper.init(this);                                      // 2.3
        db      = AppDatabase.getDatabase(getApplicationContext());       // 2.5
        ttsPref = getSharedPreferences(TTS_PREF_NAME, MODE_PRIVATE);     // 2.5

        bindViews();
        setupFilePicker();     // Phải đăng ký trước onStart
        setupFileButtons();    // chip TXT / PDF
        setupSeekBars();
        setupButtons();        // Nối service
        setupSensor();         // Proximity sensor
        setupSettings();       // Mở SettingsActivity

        // Khôi phục file đọc dở từ phiên trước
        loadReadingState();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent svcIntent = new Intent(this, PlaybackService.class);
        startService(svcIntent);   // service sống độc lập dù Activity bị ẩn
        bindService(svcIntent, serviceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Đọc lại trạng thái sensor từ SharedPreferences
        // → người dùng có thể đã thay đổi trong SettingsActivity
        SharedPreferences p = getSharedPreferences(APP_SETTINGS_PREFS, MODE_PRIVATE);
        boolean enabled = p.getBoolean(KEY_SENSOR_ENABLED, true);
        if (proximitySensorManager != null) {
            proximitySensorManager.setEnabled(enabled);
            if (enabled) proximitySensorManager.register();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Luôn hủy đăng ký sensor khi Activity không hiển thị (tiết kiệm pin)
        if (proximitySensorManager != null) proximitySensorManager.unregister();

        // Lưu vị trí câu đang đọc khi rời Activity
        if (!currentFileName.isEmpty() && currentFileUri != null
                && isServiceBound && boundService != null) {
            int idx = boundService.getCurrentSentenceIndex();
            saveReadingState(currentFileName, currentFileUri.toString(), idx);
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
        voiceLayout  = findViewById(R.id.voiceLayout);
        tvVoiceName  = findViewById(R.id.voiceName);
        tvVoiceInfo  = findViewById(R.id.voiceInfo);
        btnOpenTxt   = findViewById(R.id.btnOpenTxt);
        btnOpenPdf   = findViewById(R.id.btnOpenPdf);
        tvFileName   = findViewById(R.id.tvFileName);
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

                    // Lấy tên file từ URI
                    String path = uri.getPath();
                    String name = (path != null && path.contains("/"))
                            ? path.substring(path.lastIndexOf('/') + 1)
                            : uri.toString();

                    tvFileName.setText(name);
                    currentFileUri  = uri;
                    currentFileName = name;

                    // Lưu file mới vào lịch sử, index = 0 (đọc từ đầu)
                    saveReadingState(name, uri.toString(), 0);

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
                if (fromUser && isServiceBound && boundService != null)
                    boundService.setTtsRate(currentRate);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        pitchSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                currentPitch = Math.max(progress / 100f, 0.1f);
                pitchValue.setText(progress + "%");
                if (fromUser && isServiceBound && boundService != null)
                    boundService.setTtsPitch(currentPitch);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    // ════════════════════════════════════════════════════════════════════
    //  BUTTONS
    // ════════════════════════════════════════════════════════════════════

    private void setupButtons() {
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

        resetBtn.setOnClickListener(v -> {
            rateSeekBar.setProgress(50);
            pitchSeekBar.setProgress(50);
        });

        backBtn.setOnClickListener(v -> stopPlayback());

        forwardBtn.setOnClickListener(v -> {});
        // Favorites tab — mở FavoritesActivity (BE_Nhi)
        favoritesTab.setOnClickListener(v ->
                startActivity(new Intent(this, FavoritesActivity.class)));

        // Voice picker: click vào row VOICE → AlertDialog danh sách giọng offline
        voiceLayout.setOnClickListener(v -> showVoicePicker());
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
        playBtn.setImageResource(
                boundService.isCurrentlyReading() ? R.drawable.ic_pause : R.drawable.ic_play_button);
    }

    // ════════════════════════════════════════════════════════════════════
    //  VOICE PICKER (offline, dùng engine TTS có sẵn của Android)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Cập nhật row VOICE hiển thị đúng tên và loại voice thật từ engine.
     * Được gọi sau khi service bind xong (~800ms sau onServiceConnected).
     */
    private void refreshVoiceDisplay() {
        if (!isServiceBound || boundService == null) return;
        String voiceName = boundService.getCurrentVoiceName();
        if ("Default".equals(voiceName)) {
            // Engine chưa chọn voice cụ thể → hiện tên locale mặc định
            tvVoiceName.setText("Mặc định hệ thống");
            tvVoiceInfo.setText("Offline · Vietnamese");
        } else {
            tvVoiceName.setText(voiceName);
            tvVoiceInfo.setText("Offline");
        }
    }

    /**
     * Hiện AlertDialog danh sách voice offline.
     * Lấy từ PlaybackService.getAvailableVoices() — hoàn toàn không gọi API.
     * Voice sắp xếp: offline trước, rồi theo ngôn ngữ.
     */
    private void showVoicePicker() {
        if (!isServiceBound || boundService == null) {
            Toast.makeText(this, "Service chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Voice> voices = boundService.getAvailableVoices();

        if (voices == null || voices.isEmpty()) {
            Toast.makeText(this,
                    "Không tìm thấy voice — kiểm tra cài đặt TTS trong hệ thống",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Tạo mảng label hiển thị trong dialog
        String[] labels = new String[voices.size()];
        for (int i = 0; i < voices.size(); i++) {
            Voice v = voices.get(i);
            String lang    = v.getLocale().getDisplayName();
            String typeTag = v.isNetworkConnectionRequired() ? "  [online]" : "  ★ offline";
            // Rút gọn tên kỹ thuật cho dễ đọc (bỏ prefix "vi-vn-x-")
            String shortName = v.getName().replaceAll("^[a-z]{2}-[a-z]{2}-x-", "");
            labels[i] = lang + " · " + shortName + typeTag;
        }

        new AlertDialog.Builder(this)
                .setTitle("Chọn giọng đọc")
                .setItems(labels, (dialog, which) -> {
                    Voice chosen = voices.get(which);
                    boundService.setVoiceByName(chosen.getName());

                    // Cập nhật UI row VOICE ngay lập tức
                    tvVoiceName.setText(chosen.getLocale().getDisplayName()
                            + " · " + chosen.getName()
                            .replaceAll("^[a-z]{2}-[a-z]{2}-x-", ""));
                    tvVoiceInfo.setText(chosen.isNetworkConnectionRequired()
                            ? "Online" : "Offline ★");
                })
                .setNegativeButton("Hủy", null)
                .show();
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
        settingsBtn.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    // ════════════════════════════════════════════════════════════════════
    //  SAVE / LOAD READING STATE (BE_Phong)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Lưu trạng thái đọc vào Room Database + SharedPreferences.
     *
     * @param fileName     Tên file hiển thị (ví dụ "Triết Mác-Lênin.pdf")
     * @param filePath     Đường dẫn / URI string của file
     * @param currentIndex Vị trí câu đang đọc (từ PlaybackService.getCurrentSentenceIndex())
     */
    public void saveReadingState(String fileName, String filePath, int currentIndex) {
        // SharedPreferences: lưu nhanh path file cuối
        ttsPref.edit()
                .putString(KEY_LAST_FILE_PATH, filePath)
                .putString(KEY_LAST_FILE_NAME, fileName)
                .apply();

        // Room: lưu tiến độ đọc chi tiết
        new Thread(() -> {
            ReadingHistory existing = db.historyDao().getHistoryByPath(filePath);
            if (existing == null) {
                db.historyDao().insert(new ReadingHistory(fileName, filePath, currentIndex));
            } else {
                existing.lastReadIndex = currentIndex;
                db.historyDao().update(existing);
            }
        }).start();
    }

    /**
     * Khôi phục trạng thái đọc từ phiên trước.
     * Copy nguyên logic từ Text2SpeechActivity (BE_Phong).
     *
     * Gọi trong onCreate() — nếu người dùng từng đọc file nào thì
     * hiển thị lại tên file và cuộn đến câu đã đọc dở.
     */
    private void loadReadingState() {
        String savedPath = ttsPref.getString(KEY_LAST_FILE_PATH, "");
        String savedName = ttsPref.getString(KEY_LAST_FILE_NAME, "");

        if (savedPath.isEmpty()) return; // Chưa từng đọc file nào

        // Cập nhật tên file ngay trên main thread (nhẹ)
        currentFileName = savedName;
        tvFileName.setText(savedName.isEmpty() ? "File từ phiên trước" : savedName);

        // Truy vấn Room trên background thread
        new Thread(() -> {
            ReadingHistory history = db.historyDao().getHistoryByPath(savedPath);
            if (history == null) return;

            int    savedIndex = history.lastReadIndex;
            String savedFile  = history.fileName;

            runOnUiThread(() -> {
                tvFileName.setText(savedFile);
                // TODO: Nếu sau này dùng RecyclerView thay EditText,
                // gọi adapter.setHighlightPosition(savedIndex) và
                // recyclerView.scrollToPosition(savedIndex) tại đây.
                // Hiện tại hiển thị thông báo cho người dùng biết có thể tiếp tục.
                if (savedIndex > 0) {
                    Toast.makeText(this,
                            "Phiên trước đọc đến câu " + (savedIndex + 1)
                                    + " — nhấn Play để tiếp tục",
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
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