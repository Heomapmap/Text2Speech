package com.example.text2speech.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.text2speech.ui.MainActivity;
import com.example.text2speech.R;

import java.util.Locale;

/**
 * ════════════════════════════════════════════════════════════════════
 *  PlaybackService  –  TÍNH NĂNG: Chạy ngầm
 * ════════════════════════════════════════════════════════════════════
 *  Cách dùng từ Activity:
 *    1. bindService() + startService() trong onCreate/onStart
 *    2. Gọi ttsService.speakFullText(text)
 *    3. Gọi ttsService.togglePauseResume() khi nhấn nút
 *    4. unbindService() trong onStop
 */
public class PlaybackService extends Service implements TextToSpeech.OnInitListener {

    private static final String TAG = "PlaybackService";

    // ── Notification constants ────────────────────────────────────────────────
    /** ID channel – đặt tên riêng để không đụng với nhánh BE_Nhi */
    static final String NOTIF_CHANNEL_ID = "tts_bg_channel_vq";
    static final int    NOTIF_ID          = 2200;

    // ── Broadcast actions cho các nút trên notification ──────────────────────
    // Dùng package name đầy đủ để tránh xung đột với receiver khác
    public static final String ACTION_TOGGLE     = "com.example.text2speech.vq.TOGGLE";
    public static final String ACTION_STOP       = "com.example.text2speech.vq.STOP";
    // Nhận text bôi đen từ ContextMenuActivity
    public static final String ACTION_SPEAK_NEW  = "com.example.text2speech.vq.SPEAK_NEW";
    public static final String EXTRA_TEXT_TO_SPEAK = "text_to_speak";

    // ── TextToSpeech engine ───────────────────────────────────────────────────
    private TextToSpeech ttsEngine;
    private boolean      isTtsEngineReady = false;

    // ── Trạng thái phát ──────────────────────────────────────────────────────
    /** Mảng câu được split từ văn bản gốc */
    private String[] sentenceArray   = new String[0];
    /** Chỉ số câu đang đọc */
    private int      currentSentIdx  = 0;
    /** true = engine đang phát âm */
    private boolean  isReading       = false;
    /** true = người dùng nhấn Dừng tạm (nhớ vị trí câu) */
    private boolean  isPaused        = false;

    // ── Cài đặt phát âm ──────────────────────────────────────────────────────
    /** Tốc độ đọc (0.5 – 2.0, mặc định 1.0) */
    private float ttsRate  = 1.0f;
    /** Cao độ giọng (0.5 – 2.0, mặc định 1.0) */
    private float ttsPitch = 1.0f;
    /** Voice đang chọn — null = dùng voice mặc định của engine */
    private android.speech.tts.Voice selectedVoice = null;

    /** Text đang chờ TTS engine init xong để đọc */
    private String pendingTextToSpeak = null;

    private NotificationManager notifManager;
    /** Handler để post callback về main thread (UtteranceProgressListener chạy trên bg thread) */
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    // ── Binder ───────────────────────────────────────────────────────────────
    private final IBinder localBinder = new LocalBinder();

    /**
     * LocalBinder cho phép MainActivity lấy reference đến Service
     * mà không cần truyền qua Intent (binding pattern).
     */
    public class LocalBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        // Khởi tạo TTS – callback onInit() được gọi sau khi engine sẵn sàng
        ttsEngine = new TextToSpeech(this, this);
    }

    /**
     * Callback từ TextToSpeech.OnInitListener.
     * Chạy trên main thread, được gọi sau khi TTS engine bind xong.
     */
    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS engine khởi tạo thất bại (status=" + status + ")");
            return;
        }

        // Ưu tiên tiếng Việt, fallback sang tiếng Anh nếu device không hỗ trợ
        int langResult = ttsEngine.setLanguage(new Locale("vi", "VN"));
        if (langResult == TextToSpeech.LANG_MISSING_DATA
                || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Tiếng Việt không hỗ trợ → fallback tiếng Anh");
            ttsEngine.setLanguage(Locale.US);
        }

        // Lắng nghe tiến trình từng câu để tự động chuyển câu tiếp theo
        ttsEngine.setOnUtteranceProgressListener(new UtteranceProgressListener() {

            @Override
            public void onStart(String utteranceId) {
                // Chạy trên background thread → cần post về main thread
                mainThreadHandler.post(() -> {
                    isReading = true;
                    isPaused  = false;
                    updateNotification(); // cập nhật icon pause trên notification
                });
            }

            @Override
            public void onDone(String utteranceId) {
                // Câu hiện tại xong → tự động chuyển câu tiếp
                mainThreadHandler.post(() -> {
                    currentSentIdx++;
                    speakCurrentSentence();
                });
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS lỗi tại utterance: " + utteranceId);
                mainThreadHandler.post(() -> {
                    currentSentIdx++;
                    speakCurrentSentence(); // bỏ qua câu lỗi, đọc câu tiếp
                });
            }
        });

        isTtsEngineReady = true;
        Log.d(TAG, "TTS engine sẵn sàng");

        // Nếu có text đang chờ (gọi speakFullText() trước khi engine init xong)
        if (pendingTextToSpeak != null) {
            speakFullText(pendingTextToSpeak);
            pendingTextToSpeak = null;
        }
    }

    /**
     * Xử lý Intent từ nút bấm trên Notification.
     * Service phải được start (không chỉ bind) để nhận onStartCommand.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_TOGGLE:
                    togglePauseResume();
                    break;
                case ACTION_STOP:
                    stopReadingAndService();
                    break;
                // Text bôi đen từ ContextMenuActivity → đọc ngay
                case ACTION_SPEAK_NEW:
                    String newText = intent.getStringExtra(EXTRA_TEXT_TO_SPEAK);
                    if (newText != null && !newText.isEmpty()) {
                        startForeground(NOTIF_ID, buildNotification());
                        speakFullText(newText);
                    }
                    break;
            }
        }
        // START_STICKY: hệ thống tự restart service nếu bị kill, truyền intent=null
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ttsEngine != null) {
            ttsEngine.stop();
            ttsEngine.shutdown();
            ttsEngine = null;
        }
        Log.d(TAG, "Service destroyed");
    }

    // ════════════════════════════════════════════════════════════════════
    //  PUBLIC API – Được gọi từ MainActivity thông qua Binder
    // ════════════════════════════════════════════════════════════════════

    /**
     * Bắt đầu đọc văn bản từ đầu.
     * Tự động split thành từng câu theo dấu câu và xuống dòng.
     *
     * @param fullText Toàn bộ văn bản cần đọc
     */
    public void speakFullText(String fullText) {
        if (fullText == null || fullText.trim().isEmpty()) return;

        // Nếu TTS chưa sẵn sàng → lưu tạm, sẽ đọc sau khi onInit() chạy xong
        if (!isTtsEngineReady) {
            pendingTextToSpeak = fullText;
            return;
        }

        // Split câu theo dấu câu kết thúc (. ! ?) hoặc ký tự xuống dòng
        // Regex: sau dấu câu kết thúc có khoảng trắng → tách câu
        sentenceArray  = fullText.trim().split("(?<=[.!?。])\\s+|\\n+");
        currentSentIdx = 0;
        isReading      = false;
        isPaused       = false;

        // Đưa Service lên foreground trước khi đọc (hiện notification)
        startForeground(NOTIF_ID, buildNotification());
        speakCurrentSentence();
    }

    /**
     * Toggle Pause ↔ Resume.
     * Khi Pause: dừng engine nhưng GIỮ nguyên currentSentIdx.
     * Khi Resume: tiếp tục từ câu đang dừng.
     */
    public void togglePauseResume() {
        if (isPaused) {
            // Resume: đọc lại từ câu bị dừng
            isPaused = false;
            speakCurrentSentence();
        } else if (isReading) {
            // Pause: stop engine, đánh dấu dừng
            ttsEngine.stop();
            isReading = false;
            isPaused  = true;
            updateNotification();
        }
        // Nếu không phải isReading và không isPaused → chưa có gì để toggle
    }

    /**
     * Dừng hoàn toàn và reset về đầu.
     */
    public void stopReadingAndService() {
        if (ttsEngine != null) ttsEngine.stop();
        isReading      = false;
        isPaused       = false;
        currentSentIdx = 0;
        // Xóa notification và đưa service ra khỏi foreground
        stopForeground(true);
        stopSelf();
    }

    /**
     * Cập nhật tốc độ đọc.
     * Có hiệu lực ngay cho câu tiếp theo.
     *
     * @param rate 0.5 (chậm) – 2.0 (nhanh), mặc định 1.0
     */
    public void setTtsRate(float rate) {
        ttsRate = rate;
        if (ttsEngine != null && isTtsEngineReady) {
            ttsEngine.setSpeechRate(rate);
        }
    }

    /**
     * Cập nhật cao độ giọng đọc.
     *
     * @param pitch 0.5 (thấp) – 2.0 (cao), mặc định 1.0
     */
    public void setTtsPitch(float pitch) {
        ttsPitch = pitch;
        if (ttsEngine != null && isTtsEngineReady) {
            ttsEngine.setPitch(pitch);
        }
    }

    /**
     * Chọn voice theo tên (lấy từ getAvailableVoices()).
     * Chỉ áp dụng offline voice — không gọi bất kỳ API nào.
     *
     * @param voiceName Tên voice, ví dụ "vi-vn-x-vic-local"
     */
    public void setVoiceByName(String voiceName) {
        if (ttsEngine == null || !isTtsEngineReady || voiceName == null) return;
        try {
            java.util.Set<android.speech.tts.Voice> voices = ttsEngine.getVoices();
            if (voices == null) return;
            for (android.speech.tts.Voice v : voices) {
                if (v.getName().equals(voiceName)) {
                    selectedVoice = v;
                    ttsEngine.setVoice(v);
                    Log.d(TAG, "Voice đã chọn: " + v.getName()
                            + " | " + v.getLocale().getDisplayLanguage()
                            + " | online=" + v.isNetworkConnectionRequired());
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "setVoice lỗi: " + e.getMessage());
        }
    }

    /**
     * Lấy danh sách tất cả voice có sẵn trên thiết bị (offline + online).
     * Trả về null nếu engine chưa sẵn sàng.
     */
    public java.util.List<android.speech.tts.Voice> getAvailableVoices() {
        if (ttsEngine == null || !isTtsEngineReady) return null;
        try {
            java.util.Set<android.speech.tts.Voice> voices = ttsEngine.getVoices();
            if (voices == null) return null;
            java.util.List<android.speech.tts.Voice> list = new java.util.ArrayList<>(voices);
            // Sắp xếp: offline trước, theo locale
            list.sort((a, b) -> {
                boolean aOffline = !a.isNetworkConnectionRequired();
                boolean bOffline = !b.isNetworkConnectionRequired();
                if (aOffline != bOffline) return aOffline ? -1 : 1;
                return a.getLocale().toString().compareTo(b.getLocale().toString());
            });
            return list;
        } catch (Exception e) {
            Log.e(TAG, "getVoices lỗi: " + e.getMessage());
            return null;
        }
    }

    /** @return Tên voice đang chọn, hoặc "Default" nếu chưa chọn */
    public String getCurrentVoiceName() {
        return selectedVoice != null ? selectedVoice.getName() : "Default";
    }

    /** @return true nếu đang đọc (engine đang phát âm) */
    public boolean isCurrentlyReading() { return isReading; }

    /** @return true nếu đang dừng tạm (nhớ vị trí, có thể resume) */
    public boolean isCurrentlyPaused()  { return isPaused;  }

    /** @return Chỉ số câu đang đọc (dùng để highlight phía Activity nếu cần) */
    public int getCurrentSentenceIndex() { return currentSentIdx; }

    // ════════════════════════════════════════════════════════════════════
    //  INTERNAL HELPERS
    // ════════════════════════════════════════════════════════════════════

    /**
     * Đọc câu tại vị trí currentSentIdx.
     * Nếu đã hết mảng → kết thúc tự nhiên, xóa notification "ongoing".
     */
    private void speakCurrentSentence() {
        // Hết câu → kết thúc
        if (currentSentIdx >= sentenceArray.length) {
            isReading = false;
            isPaused  = false;
            updateNotification(); // notification chuyển sang "Hoàn thành" + không ongoing
            return;
        }

        String sentence = sentenceArray[currentSentIdx].trim();

        // Bỏ qua câu rỗng (do split tạo ra)
        if (sentence.isEmpty()) {
            currentSentIdx++;
            speakCurrentSentence();
            return;
        }

        // Áp dụng rate & pitch trước mỗi câu (người dùng có thể thay đổi giữa chừng)
        ttsEngine.setSpeechRate(ttsRate);
        ttsEngine.setPitch(ttsPitch);

        // Bundle params để truyền utteranceId (bắt buộc để UtteranceProgressListener hoạt động)
        Bundle ttsParams = new Bundle();
        String utteranceId = "vq_sent_" + currentSentIdx;

        // QUEUE_FLUSH: xóa queue cũ, đọc ngay câu này
        ttsEngine.speak(sentence, TextToSpeech.QUEUE_FLUSH, ttsParams, utteranceId);
    }

    // ════════════════════════════════════════════════════════════════════
    //  NOTIFICATION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Tạo Notification Channel (bắt buộc từ Android 8.0 / API 26).
     * Phải tạo trước khi gọi startForeground().
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Phát âm ngầm TTS",
                    NotificationManager.IMPORTANCE_LOW   // LOW = không có âm thanh thông báo
            );
            channel.setDescription("Điều khiển Text-to-Speech khi chạy ngầm");
            channel.setShowBadge(false);
            notifManager.createNotificationChannel(channel);
        }
    }

    /** Cập nhật notification hiện tại (không tạo mới) */
    private void updateNotification() {
        notifManager.notify(NOTIF_ID, buildNotification());
    }

    /**
     * Build Notification với 2 action button:
     *   [Tiếp tục / Tạm dừng]  [Dừng hẳn]
     *
     * Notification ở trạng thái "ongoing" khi đang đọc hoặc tạm dừng
     * → người dùng không thể vuốt xóa, phải nhấn Dừng.
     */
    private Notification buildNotification() {

        // Tap vào notification → mở lại MainActivity
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openAppPending = PendingIntent.getActivity(
                this, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Nút Toggle (Play/Pause) → gửi action đến onStartCommand
        Intent toggleIntent = new Intent(this, PlaybackService.class);
        toggleIntent.setAction(ACTION_TOGGLE);
        PendingIntent togglePending = PendingIntent.getService(
                this, 1, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Nút Dừng hẳn
        Intent stopIntent = new Intent(this, PlaybackService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Chọn icon & label phù hợp với trạng thái
        int    toggleIconRes;
        String toggleLabel;
        String statusText;

        if (isReading) {
            toggleIconRes = R.drawable.ic_pause;
            toggleLabel   = "Tạm dừng";
            statusText    = "Đang đọc câu " + (currentSentIdx + 1) + "/" + sentenceArray.length;
        } else if (isPaused) {
            toggleIconRes = R.drawable.ic_play_button;
            toggleLabel   = "Tiếp tục";
            statusText    = "Tạm dừng tại câu " + (currentSentIdx + 1);
        } else {
            toggleIconRes = R.drawable.ic_play_button;
            toggleLabel   = "Đọc lại";
            statusText    = "Đã đọc xong";
        }

        return new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play_button)
                .setContentTitle("Text to Speech")
                .setContentText(statusText)
                .setContentIntent(openAppPending)
                // Nút 1: Toggle pause/resume
                .addAction(toggleIconRes, toggleLabel, togglePending)
                // Nút 2: Stop hoàn toàn
                .addAction(R.drawable.ic_pause, "Dừng", stopPending)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                // ongoing=true → không thể vuốt xóa khi đang đọc/pause
                .setOngoing(isReading || isPaused)
                // Hiện trên màn hình khóa
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }
}