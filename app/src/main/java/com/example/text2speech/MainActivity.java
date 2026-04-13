package com.example.text2speech;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
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

public class MainActivity extends AppCompatActivity {

    private EditText    textInput;       // ID: textInput  (EditText nhập văn bản)
    private SeekBar     rateSeekBar;     // ID: rateSeekBar
    private SeekBar     pitchSeekBar;    // ID: pitchSeekBar
    private TextView    rateValue;       // ID: rateValue
    private TextView    pitchValue;      // ID: pitchValue
    private ImageButton playBtn;         // ID: playBtn
    private ImageButton backBtn;         // ID: backBtn
    private ImageButton forwardBtn;      // ID: forwardBtn
    private TextView    resetBtn;        // ID: resetBtn
    private LinearLayout favoritesTab;  // ID: favoritesTab
    private LinearLayout textTab;       // ID: textTab
    private ImageButton settingsBtn;    // ID: settingsBtn
    private TextView    btnOpenTxt;     // ID: btnOpenTxt
    private TextView    btnOpenPdf;     // ID: btnOpenPdf
    private TextView    tvFileName;     // ID: tvFileName

    // ── Service binding (2.2) ─────────────────────────────────────────────────
    private PlaybackService boundTtsService   = null;
    private boolean            isTtsServiceBound = false;

    private final ServiceConnection ttsServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            PlaybackService.LocalBinder lb = (PlaybackService.LocalBinder) binder;
            boundTtsService   = lb.getService();
            isTtsServiceBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            boundTtsService   = null;
            isTtsServiceBound = false;
        }
    };

    // ── File picker (2.3) ─────────────────────────────────────────────────────
    private ActivityResultLauncher<String[]> filePickerLauncher;

    private static final int   REQ_POST_NOTIF = 301;

    // ── SeekBar: progress 0-100 → rate 0.5x–2.0x (step 0.015) ───────────────
    private float currentRate  = 1.0f;
    private float currentPitch = 1.0f;

    // ════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FileReaderHelper.init(this);   // Khởi tạo PDFBox (bắt buộc trước khi đọc PDF)

        bindViews();
        setupFilePicker();             // Đăng ký file picker TRƯỚC onStart
        setupFileButtons();            // 2.3
        setupSeekBars();
        setupButtons();                // Kết nối service
        requestNotificationPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Start + bind: service sống độc lập kể cả khi Activity bị ẩn (2.2)
        Intent svc = new Intent(this, PlaybackService.class);
        startService(svc);
        bindService(svc, ttsServiceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isTtsServiceBound) {
            unbindService(ttsServiceConn);
            isTtsServiceBound = false;
            boundTtsService   = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // KHÔNG stop service ở đây → TTS tiếp tục chạy sau khi đóng app
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
        playBtn      = findViewById(R.id.playBtn);
        backBtn      = findViewById(R.id.backBtn);
        forwardBtn   = findViewById(R.id.forwardBtn);
        resetBtn     = findViewById(R.id.resetBtn);
        favoritesTab = findViewById(R.id.favoritesTab);
        textTab      = findViewById(R.id.textTab);
        settingsBtn  = findViewById(R.id.settingsBtn);

        btnOpenTxt   = findViewById(R.id.btnOpenTxt);
        btnOpenPdf   = findViewById(R.id.btnOpenPdf);
        tvFileName   = findViewById(R.id.tvFileName);
    }

    // ════════════════════════════════════════════════════════════════════
    //  TÍNH NĂNG 2.3 – FILE PICKER
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
                    // Lấy tên file từ path cuối URI
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
                // Giống BE_Nhi: progress 0–100 → rate 0.1–2.0
                currentRate = Math.max(progress / 100f, 0.1f);
                rateValue.setText(progress + "%");
                if (fromUser && isTtsServiceBound && boundTtsService != null) {
                    boundTtsService.setTtsRate(currentRate);
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
                if (fromUser && isTtsServiceBound && boundTtsService != null) {
                    boundTtsService.setTtsPitch(currentPitch);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    // ════════════════════════════════════════════════════════════════════
    //  BUTTONS — playBtn nối vào service
    // ════════════════════════════════════════════════════════════════════

    private void setupButtons() {

        // Play / Pause — gọi service thay vì TTS trực tiếp (để chạy ngầm)
        playBtn.setOnClickListener(v -> {
            String text = textInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập văn bản!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isTtsServiceBound || boundTtsService == null) {
                Toast.makeText(this, "Service chưa sẵn sàng!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (boundTtsService.isCurrentlyReading() || boundTtsService.isCurrentlyPaused()) {
                // Đang đọc hoặc tạm dừng → toggle
                boundTtsService.togglePauseResume();
                syncPlayIcon();
            } else {
                // Chưa đọc → bắt đầu từ đầu
                boundTtsService.setTtsRate(currentRate);
                boundTtsService.setTtsPitch(currentPitch);
                boundTtsService.speakFullText(text);
                playBtn.setImageResource(R.drawable.ic_pause);
            }
        });

        // Reset seekbars
        resetBtn.setOnClickListener(v -> {
            rateSeekBar.setProgress(50);
            pitchSeekBar.setProgress(50);
        });

        // Back = dừng hẳn
        backBtn.setOnClickListener(v -> {
            if (isTtsServiceBound && boundTtsService != null) {
                boundTtsService.stopReadingAndService();
            }
            playBtn.setImageResource(R.drawable.ic_play_button);
        });

        // Forward — chưa implement
        forwardBtn.setOnClickListener(v -> {});

        // Favorites tab
        favoritesTab.setOnClickListener(v -> {
            // Placeholder
        });

        // Settings
        settingsBtn.setOnClickListener(v -> {
            // Placeholder hoặc mở SettingsActivity nếu có
        });
    }

    /** Đồng bộ icon play/pause theo trạng thái service */
    private void syncPlayIcon() {
        if (!isTtsServiceBound || boundTtsService == null) return;
        if (boundTtsService.isCurrentlyReading()) {
            playBtn.setImageResource(R.drawable.ic_pause);
        } else {
            playBtn.setImageResource(R.drawable.ic_play_button);
        }
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