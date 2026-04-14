package com.example.text2speech.ui;

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
import android.text.InputType;
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
import androidx.core.content.ContextCompat;

import com.example.text2speech.core.ProximitySensorManager;
import com.example.text2speech.R;
import com.example.text2speech.core.FileReaderHelper;
import com.example.text2speech.data.AppDatabase;
import com.example.text2speech.data.ReadingHistory;
import com.example.text2speech.service.PlaybackService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MainActivity
 *
 * Tính năng bổ sung / sửa lỗi:
 *  1. Nhiều ngôn ngữ: Voice picker dùng getAllAvailableVoices() – vi → en → khác.
 *  2. Lưu lịch sử khi import file (đã có, giữ nguyên).
 *  3. Nút "Lưu" (btnSaveText): dialog đặt tên → lưu text nhập tay vào lịch sử.
 *  4. backBtn  → boundService.skipBack()    (lùi câu trước)
 *  5. forwardBtn → boundService.skipForward() (bỏ qua câu tiếp)
 *  6. Nhận Intent từ HistoriesActivity: load lại file + resume đúng vị trí câu.
 */
public class MainActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────────
    private EditText     textInput;
    private SeekBar      rateSeekBar, pitchSeekBar;
    private TextView     rateValue, pitchValue;
    private TextView     resetBtn;
    private ImageButton  playBtn, backBtn, forwardBtn, settingsBtn;
    private LinearLayout historiesTab, textTab;
    private LinearLayout voiceLayout;
    private TextView     tvVoiceName, tvVoiceInfo;
    private TextView     btnOpenTxt, btnOpenPdf, tvFileName;
    /** Nút lưu text nhập tay vào lịch sử */
    private TextView     btnSaveText;
    /** Nút AC: xóa toàn bộ ô text và dừng phát */
    private TextView     btnClearText;

    private float currentRate  = 1.0f;
    private float currentPitch = 1.0f;

    // ── Service binding ───────────────────────────────────────────────────────
    private PlaybackService boundService   = null;
    private boolean         isServiceBound = false;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            boundService   = ((PlaybackService.LocalBinder) binder).getService();
            isServiceBound = true;
            syncPlayIcon();
            playBtn.postDelayed(() -> refreshVoiceDisplay(), 800);
            // Nếu onCreate() đã nhận Intent lịch sử nhưng service chưa bind → xử lý ngay
            handleHistoryIntentIfPending();
            handleHistoryTextIfPending();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            boundService = null; isServiceBound = false;
        }
    };

    // ── File picker ──────────────────────────────────────────────────────────
    private ActivityResultLauncher<String[]> filePickerLauncher;
    private Uri    currentFileUri  = null;
    private String currentFileName = "";

    // ── Proximity sensor ─────────────────────────────────────────────────────
    private ProximitySensorManager proximitySensorManager;

    // ── Room DB + SharedPreferences ──────────────────────────────────────────
    private AppDatabase       db;
    private SharedPreferences ttsPref;

    private static final String TTS_PREF_NAME     = "tts_pref";
    private static final String KEY_LAST_FILE_PATH = "last_file_path";
    private static final String KEY_LAST_FILE_NAME = "last_file_name";
    private static final String APP_SETTINGS_PREFS = "AppSettings";
    private static final String KEY_SENSOR_ENABLED = "sensor_enabled";
    private static final int    REQ_POST_NOTIF      = 301;

    // ── Intent từ HistoriesActivity (lưu tạm nếu service chưa bind) ──────────
    private String pendingHistoryFilePath  = null;
    private String pendingHistoryFileName  = null;
    private int    pendingHistoryLastIndex = 0;
    // TEXT entry từ lịch sử (render về MainActivity thay vì phát thẳng)
    private String pendingHistoryTextContent = null;
    private String pendingHistoryTextName    = null;

    // ════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FileReaderHelper.init(this);
        db      = AppDatabase.getDatabase(getApplicationContext());
        ttsPref = getSharedPreferences(TTS_PREF_NAME, MODE_PRIVATE);

        bindViews();
        setupFilePicker();
        setupFileButtons();
        setupSeekBars();
        setupButtons();
        setupSensor();
        setupSettings();
        loadReadingState();

        // Kiểm tra xem có Intent từ HistoriesActivity không
        parseHistoryIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        parseHistoryIntent(intent);
        handleHistoryIntentIfPending();
        handleHistoryTextIfPending();
    }

    /** Đọc extra từ Intent của HistoriesActivity (nếu có) */
    private void parseHistoryIntent(Intent intent) {
        if (intent == null) return;

        // Trường hợp TEXT entry từ lịch sử
        String textContent = intent.getStringExtra("history_text_content");
        if (textContent != null && !textContent.isEmpty()) {
            pendingHistoryTextContent = textContent;
            pendingHistoryTextName    = intent.getStringExtra("history_text_name");
            return; // không xử lý FILE intent trong cùng một Intent
        }

        // Trường hợp FILE entry từ lịch sử
        String filePath = intent.getStringExtra("history_file_path");
        if (filePath == null || filePath.isEmpty()) return;
        pendingHistoryFilePath  = filePath;
        pendingHistoryFileName  = intent.getStringExtra("history_file_name");
        pendingHistoryLastIndex = intent.getIntExtra("history_last_index", 0);
    }

    /**
     * Xử lý TEXT entry từ lịch sử khi service đã bind:
     * điền text vào EditText rồi bắt đầu phát — giống như nhập tay và bấm Play.
     */
    private void handleHistoryTextIfPending() {
        if (pendingHistoryTextContent == null) return;
        if (!isServiceBound || boundService == null) return;

        String content = pendingHistoryTextContent;
        String name    = pendingHistoryTextName;

        pendingHistoryTextContent = null;
        pendingHistoryTextName    = null;

        textInput.setText(content);
        tvFileName.setText(name != null && !name.isEmpty() ? name : "Lịch sử text");

        // Dừng bất kỳ phát âm cũ nào
        if (boundService.isCurrentlyReading() || boundService.isCurrentlyPaused()) {
            boundService.forceStopPlayback();
        }

        boundService.setTtsRate(currentRate);
        boundService.setTtsPitch(currentPitch);
        boundService.speakFullText(content);
        playBtn.setImageResource(R.drawable.ic_pause);
    }

    /**
     * Xử lý Intent lịch sử FILE khi service đã bind.
     * Đọc lại file từ URI, load text vào service, rồi resume đúng vị trí.
     */
    private void handleHistoryIntentIfPending() {
        if (pendingHistoryFilePath == null) return;
        if (!isServiceBound || boundService == null) return;  // chờ bind xong

        String filePath = pendingHistoryFilePath;
        String fileName = pendingHistoryFileName;
        int    lastIdx  = pendingHistoryLastIndex;

        pendingHistoryFilePath  = null;
        pendingHistoryFileName  = null;
        pendingHistoryLastIndex = 0;

        // Hiển thị tên file ngay
        tvFileName.setText(fileName != null ? fileName : "Đang tải...");

        try {
            Uri uri = Uri.parse(filePath);
            currentFileUri  = uri;
            currentFileName = fileName != null ? fileName : "";

            tvFileName.setText("Đang tải...");
            FileReaderHelper.readFile(this, uri, new FileReaderHelper.ReadCallback() {
                @Override
                public void onSuccess(String text) {
                    runOnUiThread(() -> {
                        textInput.setText(text);
                        tvFileName.setText(currentFileName);

                        // Load vào service rồi resume đúng vị trí
                        if (isServiceBound && boundService != null) {
                            boundService.setTtsRate(currentRate);
                            boundService.setTtsPitch(currentPitch);
                            boundService.loadText(text);
                            if (lastIdx > 0) {
                                Toast.makeText(MainActivity.this,
                                        "Tiếp tục từ câu " + (lastIdx + 1),
                                        Toast.LENGTH_SHORT).show();
                            }
                            boundService.resumeFromIndex(lastIdx);
                            syncPlayIcon();
                        }
                    });
                }
                @Override
                public void onError(String msg) {
                    runOnUiThread(() -> {
                        tvFileName.setText("Lỗi tải file");
                        Toast.makeText(MainActivity.this, "Không tải được file: " + msg,
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "URI không hợp lệ: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
        SharedPreferences p = getSharedPreferences(APP_SETTINGS_PREFS, MODE_PRIVATE);
        boolean enabled = p.getBoolean(KEY_SENSOR_ENABLED, true);
        if (proximitySensorManager != null) {
            proximitySensorManager.setEnabled(enabled);
            if (enabled) proximitySensorManager.register();
        }
        // Sync icon play/pause mỗi khi Activity hiển thị lại
        // (vd: sau khi quay lại từ HistoriesActivity đang phát TEXT)
        if (isServiceBound && boundService != null) {
            syncPlayIcon();
            // Nếu service đang đọc text từ lịch sử (TEXT entry) mà ô EditText trống
            // → hiển thị text đó vào ô để người dùng thấy và có thể dừng
            String loaded = boundService.getCurrentFullText();
            if (!loaded.isEmpty()) {
                String inBox = textInput.getText().toString().trim();
                if (inBox.isEmpty()) {
                    textInput.setText(loaded);
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (proximitySensorManager != null) proximitySensorManager.unregister();
        if (!currentFileName.isEmpty() && currentFileUri != null
                && isServiceBound && boundService != null) {
            saveReadingState(currentFileName, currentFileUri.toString(),
                    boundService.getCurrentSentenceIndex());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
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
        historiesTab = findViewById(R.id.historiesTab);
        textTab      = findViewById(R.id.textTab);
        voiceLayout  = findViewById(R.id.voiceLayout);
        tvVoiceName  = findViewById(R.id.voiceName);
        tvVoiceInfo  = findViewById(R.id.voiceInfo);
        btnOpenTxt   = findViewById(R.id.btnOpenTxt);
        btnOpenPdf   = findViewById(R.id.btnOpenPdf);
        tvFileName   = findViewById(R.id.tvFileName);
        btnSaveText  = findViewById(R.id.btnSaveText);
        btnClearText = findViewById(R.id.btnClearText);
    }

    // ════════════════════════════════════════════════════════════════════
    //  FILE PICKER
    // ════════════════════════════════════════════════════════════════════

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) readAndShowFile(uri); });
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
                    currentFileUri  = uri;
                    currentFileName = name;
                    // Lưu lịch sử khi import file
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
    //  [TÍNH NĂNG 3] LƯU TEXT NHẬP TAY VÀO LỊCH SỬ
    // ════════════════════════════════════════════════════════════════════

    private void promptSaveTextToHistory() {
        String text = textInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Ô text đang trống!", Toast.LENGTH_SHORT).show();
            return;
        }
        String dateStr = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                .format(new Date());
        String defaultName = (text.length() > 25 ? text.substring(0, 25) + "…" : text)
                + " (" + dateStr + ")";

        EditText nameInput = new EditText(this);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setText(defaultName);
        nameInput.selectAll();
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        nameInput.setPadding(pad, pad / 2, pad, pad / 2);

        new AlertDialog.Builder(this)
                .setTitle("Đặt tên cho đoạn text")
                .setView(nameInput)
                .setPositiveButton("Lưu", (d, w) -> {
                    String title = nameInput.getText().toString().trim();
                    if (title.isEmpty()) title = defaultName;
                    saveTextToHistory(title, text);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void saveTextToHistory(String title, String content) {
        long now      = System.currentTimeMillis();
        String fakeUri = "text://" + now;
        new Thread(() -> {
            ReadingHistory h = new ReadingHistory(
                    title, fakeUri, 0, now, "TEXT", content);
            db.historyDao().insert(h);
            runOnUiThread(() ->
                    Toast.makeText(this, "Đã lưu \"" + title + "\"!", Toast.LENGTH_SHORT).show());
        }).start();
    }

    // ════════════════════════════════════════════════════════════════════
    //  SEEKBARS
    // ════════════════════════════════════════════════════════════════════

    private void setupSeekBars() {
        rateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                currentRate = Math.max(p / 100f, 0.1f);
                rateValue.setText(p + "%");
                if (fromUser && isServiceBound && boundService != null)
                    boundService.setTtsRate(currentRate);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        pitchSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                currentPitch = Math.max(p / 100f, 0.1f);
                pitchValue.setText(p + "%");
                if (fromUser && isServiceBound && boundService != null)
                    boundService.setTtsPitch(currentPitch);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    // ════════════════════════════════════════════════════════════════════
    //  BUTTONS — bao gồm backBtn & forwardBtn
    // ════════════════════════════════════════════════════════════════════

    private void setupButtons() {
        // ── Play / Pause ──────────────────────────────────────────────
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

        // ── ← Back: lùi câu trước ─────────────────────────────────────
        backBtn.setOnClickListener(v -> {
            if (!isServiceBound || boundService == null) return;
            boolean hasContent = boundService.getSentenceCount() > 0;
            if (!hasContent) {
                // Chưa load text: load rồi đọc từ đầu
                String text = textInput.getText().toString().trim();
                if (!text.isEmpty()) {
                    boundService.setTtsRate(currentRate);
                    boundService.setTtsPitch(currentPitch);
                    boundService.speakFullText(text);
                }
                return;
            }
            boundService.skipBack();
            syncPlayIcon();
        });

        // ── → Forward: bỏ qua câu hiện tại ──────────────────────────
        forwardBtn.setOnClickListener(v -> {
            if (!isServiceBound || boundService == null) return;
            boolean hasContent = boundService.getSentenceCount() > 0;
            if (!hasContent) return;  // chưa load gì thì forward không có nghĩa
            boundService.skipForward();
            syncPlayIcon();
        });

        // ── Reset seekbar ─────────────────────────────────────────────
        resetBtn.setOnClickListener(v -> {
            rateSeekBar.setProgress(50);
            pitchSeekBar.setProgress(50);
        });

        // ── Histories tab ─────────────────────────────────────────────
        historiesTab.setOnClickListener(v ->
                startActivity(new Intent(this, HistoriesActivity.class)));

        // ── Voice picker ──────────────────────────────────────────────
        voiceLayout.setOnClickListener(v -> showVoicePicker());

        // ── Lưu text nhập tay ─────────────────────────────────────────
        if (btnSaveText != null) {
            btnSaveText.setOnClickListener(v -> promptSaveTextToHistory());
        }

        // ── AC: xóa toàn bộ ô text + dừng phát ──────────────────────
        if (btnClearText != null) {
            btnClearText.setOnClickListener(v -> {
                textInput.setText("");
                tvFileName.setText("Chưa chọn file");
                currentFileUri  = null;
                currentFileName = "";
                if (isServiceBound && boundService != null
                        && (boundService.isCurrentlyReading() || boundService.isCurrentlyPaused())) {
                    boundService.forceStopPlayback();
                    playBtn.setImageResource(R.drawable.ic_play_button);
                }
                Toast.makeText(this, "Đã xóa nội dung", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void handlePlayPause(String text) {
        boolean reading = boundService.isCurrentlyReading();
        boolean paused  = boundService.isCurrentlyPaused();

        // Nếu text trong ô khác với text đang chạy → dừng hẳn rồi đọc lại
        String loadedText  = boundService.getCurrentFullText();
        boolean textChanged = !text.trim().equals(loadedText.trim());

        if (textChanged && (reading || paused)) {
            // Dừng phát âm cũ, giữ service sống để đọc ngay text mới
            boundService.forceStopPlayback();
        }

        if (textChanged || (!reading && !paused)) {
            // Đọc text mới từ đầu
            boundService.setTtsRate(currentRate);
            boundService.setTtsPitch(currentPitch);
            boundService.speakFullText(text);
            playBtn.setImageResource(R.drawable.ic_pause);
        } else {
            // Cùng text đang đọc/pause → toggle pause/resume
            boundService.togglePauseResume();
            syncPlayIcon();
        }
    }

    private void syncPlayIcon() {
        if (!isServiceBound || boundService == null) return;
        playBtn.setImageResource(
                boundService.isCurrentlyReading() ? R.drawable.ic_pause : R.drawable.ic_play_button);
    }

    // ════════════════════════════════════════════════════════════════════
    //  VOICE PICKER — đa ngôn ngữ
    // ════════════════════════════════════════════════════════════════════

    private void refreshVoiceDisplay() {
        if (!isServiceBound || boundService == null) return;
        String voiceName = boundService.getCurrentVoiceName();
        Locale locale    = boundService.getCurrentLocale();
        if ("Default".equals(voiceName)) {
            tvVoiceName.setText(locale.getDisplayLanguage(Locale.getDefault()));
            tvVoiceInfo.setText("Offline · " + locale.getDisplayName());
        } else {
            String shortName = voiceName.replaceAll("^[a-z]{2}-[a-z]{2}-x-", "");
            tvVoiceName.setText(locale.getDisplayLanguage() + " · " + shortName);
            tvVoiceInfo.setText("Offline ★");
        }
    }

    private void showVoicePicker() {
        if (!isServiceBound || boundService == null) {
            Toast.makeText(this, "Service chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Voice> voices = boundService.getAllAvailableVoices();
        if (voices == null || voices.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy voice — kiểm tra cài đặt TTS hệ thống",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[voices.size()];
        for (int i = 0; i < voices.size(); i++) {
            Voice v = voices.get(i);
            String langDisplay = v.getLocale().getDisplayLanguage(Locale.getDefault());
            String shortName   = v.getName()
                    .replaceAll("^[a-z]{2}-[a-z]{2}-x-", "")
                    .replaceAll("^[a-z]{2}_[a-z]{2}-", "");
            String typeTag = v.isNetworkConnectionRequired() ? "  [online]" : "  ★";
            labels[i] = langDisplay + "  ·  " + shortName + typeTag;
        }
        new AlertDialog.Builder(this)
                .setTitle("Chọn ngôn ngữ / giọng đọc")
                .setItems(labels, (dialog, which) -> {
                    Voice chosen = voices.get(which);
                    boundService.setVoiceByName(chosen.getName());
                    String langDisplay = chosen.getLocale().getDisplayLanguage(Locale.getDefault());
                    String shortName   = chosen.getName()
                            .replaceAll("^[a-z]{2}-[a-z]{2}-x-", "")
                            .replaceAll("^[a-z]{2}_[a-z]{2}-", "");
                    tvVoiceName.setText(langDisplay + " · " + shortName);
                    tvVoiceInfo.setText(chosen.isNetworkConnectionRequired() ? "Online" : "Offline ★");
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ════════════════════════════════════════════════════════════════════
    //  PROXIMITY SENSOR
    // ════════════════════════════════════════════════════════════════════

    private void setupSensor() {
        SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        proximitySensorManager = new ProximitySensorManager(sm, () ->
                runOnUiThread(() -> {
                    String text = textInput.getText().toString().trim();
                    if (text.isEmpty() || !isServiceBound || boundService == null) return;
                    handlePlayPause(text);
                }));
    }

    // ════════════════════════════════════════════════════════════════════
    //  SETTINGS
    // ════════════════════════════════════════════════════════════════════

    private void setupSettings() {
        settingsBtn.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    // ════════════════════════════════════════════════════════════════════
    //  SAVE / LOAD READING STATE
    // ════════════════════════════════════════════════════════════════════

    public void saveReadingState(String fileName, String filePath, int currentIndex) {
        ttsPref.edit()
                .putString(KEY_LAST_FILE_PATH, filePath)
                .putString(KEY_LAST_FILE_NAME, fileName)
                .apply();
        new Thread(() -> {
            ReadingHistory existing = db.historyDao().getHistoryByPath(filePath);
            if (existing == null) {
                db.historyDao().insert(new ReadingHistory(fileName, filePath, currentIndex));
            } else {
                existing.lastReadIndex = currentIndex;
                existing.timestamp     = System.currentTimeMillis();
                db.historyDao().update(existing);
            }
        }).start();
    }

    private void loadReadingState() {
        String savedPath = ttsPref.getString(KEY_LAST_FILE_PATH, "");
        String savedName = ttsPref.getString(KEY_LAST_FILE_NAME, "");
        if (savedPath.isEmpty()) return;
        currentFileName = savedName;
        tvFileName.setText(savedName.isEmpty() ? "File từ phiên trước" : savedName);
        new Thread(() -> {
            ReadingHistory history = db.historyDao().getHistoryByPath(savedPath);
            if (history == null) return;
            int    idx  = history.lastReadIndex;
            String name = history.fileName;
            runOnUiThread(() -> {
                tvFileName.setText(name);
                if (idx > 0)
                    Toast.makeText(this,
                            "Phiên trước đọc đến câu " + (idx + 1) + " — nhấn Play để tiếp tục",
                            Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    // ════════════════════════════════════════════════════════════════════
    //  ẨN BÀN PHÍM KHI BẤM RA NGOÀI EditText
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