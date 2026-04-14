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
import android.speech.tts.Voice;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.text2speech.ui.MainActivity;
import com.example.text2speech.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * PlaybackService – TTS chạy ngầm.
 *
 * Bổ sung so với bản gốc:
 *  - skipBack()        : lùi lại câu trước (backBtn)
 *  - skipForward()     : bỏ qua câu hiện tại, đọc câu tiếp (forwardBtn)
 *  - loadText()        : load text vào bộ nhớ mà KHÔNG đọc ngay
 *  - resumeFromIndex() : bắt đầu đọc từ câu chỉ định (restore lịch sử)
 *  - getAllAvailableVoices(): tất cả ngôn ngữ, nhóm vi → en → khác
 */
public class PlaybackService extends Service implements TextToSpeech.OnInitListener {

    private static final String TAG = "PlaybackService";

    static final String NOTIF_CHANNEL_ID = "tts_bg_channel_vq";
    static final int    NOTIF_ID          = 2200;

    public static final String ACTION_TOGGLE       = "com.example.text2speech.vq.TOGGLE";
    public static final String ACTION_STOP         = "com.example.text2speech.vq.STOP";
    public static final String ACTION_SPEAK_NEW    = "com.example.text2speech.vq.SPEAK_NEW";
    public static final String EXTRA_TEXT_TO_SPEAK = "text_to_speak";

    private TextToSpeech ttsEngine;
    private boolean      isTtsEngineReady = false;

    private String[] sentenceArray   = new String[0];
    private int      currentSentIdx  = 0;
    private boolean  isReading       = false;
    private boolean  isPaused        = false;
    /** Toàn bộ text đang được load (dùng để MainActivity so sánh khi bấm Play) */
    private String   currentFullText = "";

    private float  ttsRate       = 1.0f;
    private float  ttsPitch      = 1.0f;
    private Voice  selectedVoice = null;
    private Locale currentLocale = new Locale("vi", "VN");

    private String pendingTextToSpeak = null;

    private NotificationManager notifManager;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private final IBinder localBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public PlaybackService getService() { return PlaybackService.this; }
    }

    @Override public IBinder onBind(Intent intent) { return localBinder; }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        ttsEngine = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS engine init thất bại (status=" + status + ")");
            return;
        }
        int langResult = ttsEngine.setLanguage(new Locale("vi", "VN"));
        if (langResult == TextToSpeech.LANG_MISSING_DATA
                || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            ttsEngine.setLanguage(Locale.US);
            currentLocale = Locale.US;
        }

        ttsEngine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {
                mainThreadHandler.post(() -> {
                    isReading = true;
                    isPaused  = false;
                    updateNotification();
                });
            }
            @Override public void onDone(String id) {
                mainThreadHandler.post(() -> {
                    currentSentIdx++;
                    speakCurrentSentence();
                });
            }
            @Override public void onError(String id) {
                mainThreadHandler.post(() -> {
                    currentSentIdx++;
                    speakCurrentSentence();
                });
            }
        });

        isTtsEngineReady = true;
        if (pendingTextToSpeak != null) {
            speakFullText(pendingTextToSpeak);
            pendingTextToSpeak = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_TOGGLE:   togglePauseResume(); break;
                case ACTION_STOP:     stopReadingAndService(); break;
                case ACTION_SPEAK_NEW:
                    String text = intent.getStringExtra(EXTRA_TEXT_TO_SPEAK);
                    if (text != null && !text.isEmpty()) {
                        startForeground(NOTIF_ID, buildNotification());
                        speakFullText(text);
                    }
                    break;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ttsEngine != null) { ttsEngine.stop(); ttsEngine.shutdown(); ttsEngine = null; }
    }

    // ════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ════════════════════════════════════════════════════════════════════

    /** Đọc toàn bộ text từ đầu (câu 0) */
    public void speakFullText(String fullText) {
        if (fullText == null || fullText.trim().isEmpty()) return;
        if (!isTtsEngineReady) { pendingTextToSpeak = fullText; return; }
        currentFullText = fullText.trim();
        sentenceArray   = splitSentences(fullText);
        currentSentIdx  = 0;
        isReading = false; isPaused = false;
        startForeground(NOTIF_ID, buildNotification());
        speakCurrentSentence();
    }

    /**
     * Load text vào bộ nhớ mà KHÔNG đọc ngay.
     * Gọi trước resumeFromIndex() khi restore file từ lịch sử.
     */
    public void loadText(String fullText) {
        if (fullText == null || fullText.trim().isEmpty()) return;
        if (ttsEngine != null) ttsEngine.stop();
        currentFullText = fullText.trim();
        sentenceArray   = splitSentences(fullText);
        isReading = false; isPaused = false;
    }

    /**
     * Bắt đầu đọc từ câu chỉ định.
     * Dùng sau loadText() để resume từ vị trí lưu trong lịch sử.
     */
    public void resumeFromIndex(int index) {
        if (sentenceArray.length == 0) return;
        currentSentIdx = (index >= 0 && index < sentenceArray.length) ? index : 0;
        isReading = false; isPaused = false;
        startForeground(NOTIF_ID, buildNotification());
        speakCurrentSentence();
    }

    /** Toggle Pause ↔ Resume */
    public void togglePauseResume() {
        if (isPaused) {
            isPaused = false;
            speakCurrentSentence();
        } else if (isReading) {
            ttsEngine.stop();
            isReading = false; isPaused = true;
            updateNotification();
        }
    }

    /**
     * ← Lùi về câu trước (backBtn).
     * Nếu đang ở câu đầu → đọc lại câu đầu.
     * Hoạt động cả khi đang đọc lẫn đang pause.
     */
    public void skipBack() {
        if (sentenceArray.length == 0) return;
        if (ttsEngine != null) ttsEngine.stop();
        currentSentIdx = Math.max(0, currentSentIdx - 1);
        isReading = false; isPaused = false;
        startForeground(NOTIF_ID, buildNotification());
        speakCurrentSentence();
    }

    /**
     * → Bỏ qua câu hiện tại, đọc câu tiếp (forwardBtn).
     * Nếu đang ở câu cuối → kết thúc.
     * Hoạt động cả khi đang đọc lẫn đang pause.
     */
    public void skipForward() {
        if (sentenceArray.length == 0) return;
        if (ttsEngine != null) ttsEngine.stop();
        currentSentIdx++;
        isReading = false; isPaused = false;
        if (currentSentIdx >= sentenceArray.length) {
            stopReadingAndService();
            return;
        }
        startForeground(NOTIF_ID, buildNotification());
        speakCurrentSentence();
    }

    /** Dừng hoàn toàn và dọn dẹp */
    public void stopReadingAndService() {
        if (ttsEngine != null) ttsEngine.stop();
        isReading = false; isPaused = false; currentSentIdx = 0;
        currentFullText = "";
        stopForeground(true);
        stopSelf();
    }

    /**
     * Dừng phát âm và reset trạng thái NHƯNG không kill service.
     * Dùng khi muốn đọc text mới ngay lập tức mà không cần bind lại.
     */
    public void forceStopPlayback() {
        if (ttsEngine != null) ttsEngine.stop();
        isReading = false; isPaused = false; currentSentIdx = 0;
        currentFullText = "";
        stopForeground(true);
    }

    public void setTtsRate(float rate) {
        ttsRate = rate;
        if (ttsEngine != null && isTtsEngineReady) ttsEngine.setSpeechRate(rate);
    }

    public void setTtsPitch(float pitch) {
        ttsPitch = pitch;
        if (ttsEngine != null && isTtsEngineReady) ttsEngine.setPitch(pitch);
    }

    public void setVoiceByName(String voiceName) {
        if (ttsEngine == null || !isTtsEngineReady || voiceName == null) return;
        List<Voice> voices = getAllAvailableVoices();
        if (voices == null) return;
        for (Voice v : voices) {
            if (v.getName().equals(voiceName)) {
                selectedVoice = v; currentLocale = v.getLocale();
                ttsEngine.setVoice(v); ttsEngine.setLanguage(v.getLocale());
                return;
            }
        }
    }

    public void setLanguageOnly(Locale locale) {
        if (ttsEngine == null || !isTtsEngineReady || locale == null) return;
        currentLocale = locale; selectedVoice = null;
        ttsEngine.setLanguage(locale);
    }

    /**
     * Tất cả voice có trên máy, nhóm: vi → en → ngôn ngữ khác.
     * Trong mỗi nhóm: offline trước, rồi sort theo tên.
     */
    public List<Voice> getAllAvailableVoices() {
        if (ttsEngine == null || !isTtsEngineReady) return null;
        try {
            Set<Voice> all = ttsEngine.getVoices();
            if (all == null) return null;
            List<Voice> vi = new ArrayList<>(), en = new ArrayList<>(), others = new ArrayList<>();
            for (Voice v : all) {
                String lang = v.getLocale().getLanguage();
                if      (lang.equals("vi")) vi.add(v);
                else if (lang.equals("en")) en.add(v);
                else                        others.add(v);
            }
            sortVoices(vi); sortVoices(en); sortVoices(others);
            List<Voice> result = new ArrayList<>();
            result.addAll(vi); result.addAll(en); result.addAll(others);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Lỗi lấy voice: " + e.getMessage());
            return null;
        }
    }

    @Deprecated
    public List<Voice> getAvailableVoices() { return getAllAvailableVoices(); }

    public String  getCurrentFullText()          { return currentFullText; }
    public String  getCurrentVoiceName()          { return selectedVoice != null ? selectedVoice.getName() : "Default"; }
    public Locale  getCurrentLocale()         { return currentLocale; }
    public boolean isCurrentlyReading()       { return isReading; }
    public boolean isCurrentlyPaused()        { return isPaused; }
    public int     getCurrentSentenceIndex()  { return currentSentIdx; }
    public int     getSentenceCount()         { return sentenceArray.length; }

    // ════════════════════════════════════════════════════════════════════
    //  INTERNAL
    // ════════════════════════════════════════════════════════════════════

    private String[] splitSentences(String text) {
        return text.trim().split("(?<=[.!?。])\\s+|\\n+");
    }

    private void speakCurrentSentence() {
        if (currentSentIdx >= sentenceArray.length) {
            isReading = false; isPaused = false;
            updateNotification(); return;
        }
        String sentence = sentenceArray[currentSentIdx].trim();
        if (sentence.isEmpty()) { currentSentIdx++; speakCurrentSentence(); return; }
        ttsEngine.setSpeechRate(ttsRate);
        ttsEngine.setPitch(ttsPitch);
        Bundle params = new Bundle();
        ttsEngine.speak(sentence, TextToSpeech.QUEUE_FLUSH, params, "vq_sent_" + currentSentIdx);
    }

    private void sortVoices(List<Voice> list) {
        list.sort((a, b) -> {
            boolean aOff = !a.isNetworkConnectionRequired();
            boolean bOff = !b.isNetworkConnectionRequired();
            if (aOff != bOff) return aOff ? -1 : 1;
            return a.getName().compareTo(b.getName());
        });
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    NOTIF_CHANNEL_ID, "Phát âm ngầm TTS", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Điều khiển Text-to-Speech khi chạy ngầm");
            ch.setShowBadge(false);
            notifManager.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        PendingIntent openApp = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Intent tI = new Intent(this, PlaybackService.class).setAction(ACTION_TOGGLE);
        PendingIntent tPI = PendingIntent.getService(this, 1, tI, PendingIntent.FLAG_IMMUTABLE);
        Intent sI = new Intent(this, PlaybackService.class).setAction(ACTION_STOP);
        PendingIntent sPI = PendingIntent.getService(this, 2, sI, PendingIntent.FLAG_IMMUTABLE);

        String status = isReading
                ? "Đang đọc câu " + (currentSentIdx + 1) + "/" + sentenceArray.length
                : isPaused ? "Tạm dừng tại câu " + (currentSentIdx + 1) : "Sẵn sàng";

        return new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setContentTitle("Text to Speech")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_voice)
                .setContentIntent(openApp)
                .addAction(R.drawable.ic_pause, isReading ? "Dừng" : "Tiếp tục", tPI)
                .addAction(R.drawable.ic_back_button, "Kết thúc", sPI)
                .setOngoing(isReading || isPaused)
                .build();
    }

    private void updateNotification() {
        if (notifManager != null) notifManager.notify(NOTIF_ID, buildNotification());
    }
}