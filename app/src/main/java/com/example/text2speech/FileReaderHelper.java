package com.example.text2speech;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * ════════════════════════════════════════════════════════════════════
 *  FileReaderHelper  –  TÍNH NĂNG 2.3: Đọc file trong máy
 * ════════════════════════════════════════════════════════════════════
 *
 *  Xử lý đọc nội dung từ file được chọn qua Storage Access Framework.
 *  Toàn bộ xử lý diễn ra ON-DEVICE, không upload lên bất kỳ server nào.
 *
 *  Hỗ trợ:
 *    • TXT  – đọc trực tiếp bằng BufferedReader
 *    • PDF  – trích xuất text bằng PdfBox-Android (offline hoàn toàn)
 *
 *  Cách dùng:
 *    FileReaderHelper.init(context);   // Gọi 1 lần khi app khởi động
 *    FileReaderHelper.readFile(ctx, uri, callback);
 *
 *  Callback chạy trên BACKGROUND THREAD → UI phải dùng runOnUiThread().
 */
public class FileReaderHelper {

    private static final String TAG = "FileReaderHelper";

    /** Số ký tự tối đa để tránh đọc file quá lớn gây OOM */
    private static final int MAX_CHARS = 50_000;

    /** Flag đánh dấu đã init PDFBox chưa */
    private static boolean isPdfBoxInitialized = false;

    // ── Interface callback ────────────────────────────────────────────────────

    /**
     * Callback trả kết quả sau khi đọc file.
     * Luôn gọi trên background thread – Activity phải dùng runOnUiThread() để update UI.
     */
    public interface ReadCallback {
        /** @param extractedText Nội dung text đã trích xuất */
        void onSuccess(String extractedText);

        /** @param errorMessage Mô tả lỗi hiển thị cho người dùng */
        void onError(String errorMessage);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Khởi tạo thư viện PdfBox (chỉ cần gọi 1 lần).
     * Nên gọi trong Application.onCreate() hoặc đầu MainActivity.onCreate().
     */
    public static void init(Context context) {
        if (!isPdfBoxInitialized) {
            PDFBoxResourceLoader.init(context.getApplicationContext());
            isPdfBoxInitialized = true;
            Log.d(TAG, "PDFBox initialized");
        }
    }

    /**
     * Đọc file từ URI (được chọn qua Storage Access Framework).
     * Tự động nhận diện loại file qua MIME type.
     *
     * Xử lý trên background thread (dùng Thread mới) để không block UI.
     *
     * @param context  Context của Activity
     * @param fileUri  URI từ Intent.getData() sau khi người dùng chọn file
     * @param callback Nhận kết quả hoặc lỗi
     */
    public static void readFile(Context context, Uri fileUri, ReadCallback callback) {
        new Thread(() -> {
            try {
                String mimeType = resolveMimeType(context, fileUri);
                Log.d(TAG, "Đọc file MIME=" + mimeType + " URI=" + fileUri);

                String result;
                if ("application/pdf".equals(mimeType)) {
                    result = extractTextFromPdf(context, fileUri);
                } else {
                    // Mặc định xử lý như text (txt, md, csv, ...)
                    result = extractTextFromTxt(context, fileUri);
                }

                callback.onSuccess(result);

            } catch (Exception e) {
                Log.e(TAG, "Lỗi đọc file: " + e.getMessage(), e);
                callback.onError("Không thể đọc file: " + e.getMessage());
            }
        }, "FileReader-Thread").start();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Xác định MIME type của file từ ContentResolver.
     * Fallback: đoán từ extension nếu ContentResolver trả về null.
     */
    private static String resolveMimeType(Context context, Uri uri) {
        ContentResolver cr   = context.getContentResolver();
        String          mime = cr.getType(uri);

        if (mime == null || mime.isEmpty()) {
            // Fallback: đoán từ extension trong path
            String path = uri.getPath();
            if (path != null) {
                String ext = MimeTypeMap.getFileExtensionFromUrl(path.toLowerCase());
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            }
        }
        return mime != null ? mime : "text/plain";
    }

    // ── TXT Reader ────────────────────────────────────────────────────────────

    /**
     * Đọc file văn bản thuần (TXT, MD, ...).
     * Hỗ trợ UTF-8, giới hạn MAX_CHARS để tránh OOM.
     */
    private static String extractTextFromTxt(Context context, Uri uri) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (InputStream    inputStream = context.getContentResolver().openInputStream(uri);
             InputStreamReader isr      = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader  reader     = new BufferedReader(isr)) {

            char[] buffer = new char[4096];
            int    charsRead;

            while ((charsRead = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, charsRead);
                // Giới hạn để tránh file khổng lồ làm đầy bộ nhớ
                if (sb.length() >= MAX_CHARS) {
                    sb.append("\n\n[...Nội dung bị cắt bớt do file quá lớn...]");
                    break;
                }
            }
        }

        String result = sb.toString().trim();
        if (result.isEmpty()) {
            throw new IOException("File rỗng hoặc không đọc được nội dung");
        }
        return result;
    }

    // ── PDF Reader ────────────────────────────────────────────────────────────

    /**
     * Trích xuất text từ file PDF sử dụng PdfBox-Android.
     *
     *  Lưu ý quan trọng:
     *  • Chỉ hoạt động với PDF có text layer (PDF được tạo từ Word, LaTeX, v.v.)
     *  • PDF scan (ảnh chụp) cần thêm OCR engine, nằm ngoài scope tính năng này
     *  • Toàn bộ xử lý LOCAL, không có byte nào ra ngoài mạng
     */
    private static String extractTextFromPdf(Context context, Uri uri) throws IOException {
        if (!isPdfBoxInitialized) {
            PDFBoxResourceLoader.init(context.getApplicationContext());
            isPdfBoxInitialized = true;
        }

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             PDDocument  pdfDocument = PDDocument.load(inputStream)) {

            if (pdfDocument.isEncrypted()) {
                throw new IOException("File PDF được mã hóa, không thể đọc");
            }

            int totalPages = pdfDocument.getNumberOfPages();
            if (totalPages == 0) {
                throw new IOException("File PDF không có trang nào");
            }

            Log.d(TAG, "PDF có " + totalPages + " trang");

            // PDFTextStripper trích xuất toàn bộ text theo thứ tự đọc tự nhiên
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);  // sắp xếp text theo vị trí vật lý trên trang
            stripper.setStartPage(1);
            stripper.setEndPage(totalPages);

            String extractedText = stripper.getText(pdfDocument).trim();

            if (extractedText.isEmpty()) {
                throw new IOException(
                        "Không trích xuất được text. PDF này có thể là ảnh scan (cần OCR)."
                );
            }

            // Giới hạn độ dài để tránh OOM khi đọc PDF rất dài
            if (extractedText.length() > MAX_CHARS) {
                extractedText = extractedText.substring(0, MAX_CHARS)
                        + "\n\n[...Nội dung bị cắt bớt: " + totalPages + " trang, giới hạn "
                        + MAX_CHARS + " ký tự...]";
            }

            return extractedText;
        }
    }
}