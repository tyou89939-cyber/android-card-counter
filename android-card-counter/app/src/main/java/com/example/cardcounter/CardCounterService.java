package com.example.cardcounter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import org.opencv.android.OpenCVLoader;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CardCounterService extends Service {
    public static final String ACTION_START = "com.example.cardcounter.START";
    public static final String ACTION_STOP = "com.example.cardcounter.STOP";
    public static final String ACTION_RESET = "com.example.cardcounter.RESET";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final String TAG = "CardCounterService";
    private static final String CHANNEL_ID = "card_counter_capture";
    private static final int NOTIFICATION_ID = 1001;
    private static final long FRAME_INTERVAL_MS = 220L;
    private static final long DETECTION_COOLDOWN_MS = 1400L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private SuitCounts counts;
    private OverlayController overlay;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private TemplateCardDetector detector;
    private boolean analyzing;
    private Suit lastSuit;
    private long lastDetectionAt;
    private int stableFrames;
    private long lastFrameAt;

    @Override
    public void onCreate() {
        super.onCreate();
        counts = new SuitCounts(this);
        overlay = new OverlayController(this);
        createNotificationChannel();
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV native library failed to initialize.");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopCapture();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_RESET.equals(action)) {
            counts.reset();
            overlay.update(counts.snapshot());
            return START_STICKY;
        }
        if (ACTION_START.equals(action)) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = getParcelableExtraCompat(intent, EXTRA_RESULT_DATA);
            if (resultData != null) {
                startCapture(resultCode, resultData);
            } else {
                Log.e(TAG, "Screen capture permission data is missing.");
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void startCapture(int resultCode, Intent resultData) {
        stopCapture();
        startForegroundCompat();

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(resultCode, resultData);
        if (projection == null) {
            Log.e(TAG, "MediaProjection could not be created.");
            stopSelf();
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(reader -> scheduleAnalysis(reader), mainHandler);
        virtualDisplay = projection.createVirtualDisplay(
                "CardCounterCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                mainHandler);
        detector = new TemplateCardDetector(this);
        overlay.show();
        overlay.update(counts.snapshot());
        counts.setRunning(true);
    }

    private void scheduleAnalysis(ImageReader reader) {
        long now = System.currentTimeMillis();
        if (now - lastFrameAt < FRAME_INTERVAL_MS || analyzing) {
            return;
        }
        Image image = reader.acquireLatestImage();
        if (image == null) {
            return;
        }
        lastFrameAt = now;
        analyzing = true;
        analysisExecutor.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = imageToBitmap(image);
                TemplateCardDetector.Detection detection = detector.detect(bitmap);
                handleDetection(detection);
            } catch (RuntimeException error) {
                Log.w(TAG, "Frame analysis failed.", error);
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
                image.close();
                mainHandler.post(() -> analyzing = false);
            }
        });
    }

    private void handleDetection(TemplateCardDetector.Detection detection) {
        if (!detection.isMatch()) {
            stableFrames = 0;
            lastSuit = null;
            return;
        }
        if (detection.getSuit() == lastSuit) {
            stableFrames++;
        } else {
            lastSuit = detection.getSuit();
            stableFrames = 1;
        }

        long now = System.currentTimeMillis();
        if (stableFrames >= 2 && now - lastDetectionAt >= DETECTION_COOLDOWN_MS) {
            counts.decrement(detection.getSuit());
            lastDetectionAt = now;
            mainHandler.post(() -> overlay.update(counts.snapshot()));
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        Bitmap bitmap = Bitmap.createBitmap(
                image.getWidth() + rowPadding / pixelStride,
                image.getHeight(),
                Bitmap.Config.ARGB_8888);
        buffer.rewind();
        bitmap.copyPixelsFromBuffer(buffer);
        Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
        bitmap.recycle();
        return cropped;
    }

    private void stopCapture() {
        counts.setRunning(false);
        if (detector != null) {
            detector.close();
            detector = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        overlay.hide();
    }

    @Override
    public void onDestroy() {
        stopCapture();
        analysisExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundCompat() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_channel_description));
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    @SuppressWarnings("deprecation")
    private static Intent getParcelableExtraCompat(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(key, Intent.class);
        }
        return intent.getParcelableExtra(key);
    }
}