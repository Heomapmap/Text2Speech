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

    // ── Hằng SharedPreferences ────────────────────────────────────────────────
    private static final String TTS_PREF_NAME      = "tts_pref";
    private static final String KEY_LAST_FILE_PATH  = "last_file_path";
    private static final String KEY_LAST_FILE_NAME  = "last_file_name";

    // ── Hằng khác ─────────────────────────────────────────────────────────────
    private static final String PREFS_NAME      = "AppSettings";
    private static final String PREF_SENSOR_KEY = "sensor_enabled";
    private static final int    REQ_POST_NOTIF       = 301;

    // ════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Khởi tạo PDFBox trước khi đọc PDF
        FileReaderHelper.init(this);

        // Khởi tạo Room Database và SharedPreferences
        db      = AppDatabase.getDatabase(getApplicationContext());
        ttsPref = getSharedPreferences(TTS_PREF_NAME, MODE_PRIVATE);

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