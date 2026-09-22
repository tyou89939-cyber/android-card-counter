package com.example.cardcounter;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Finds a suit glyph in the captured frame with OpenCV template matching.
 *
 * Replace the starter files in app/src/main/assets/templates/ with tightly
 * cropped grayscale screenshots from the target card UI. Keeping the same
 * glyph, padding, and antialiasing as the target produces the best result.
 */
public final class TemplateCardDetector {
    private static final String TAG = "TemplateCardDetector";
    private static final double MIN_SCORE = 0.84;
    private final Map<Suit, Mat> templates = new EnumMap<>(Suit.class);

    public TemplateCardDetector(Context context) {
        loadTemplates(context.getAssets());
    }

    public Detection detect(Bitmap bitmap) {
        if (templates.isEmpty() || bitmap == null) {
            return Detection.none();
        }

        Mat sourceRgba = new Mat();
        Mat sourceGray = new Mat();
        try {
            Utils.bitmapToMat(bitmap, sourceRgba);
            Imgproc.cvtColor(sourceRgba, sourceGray, Imgproc.COLOR_RGBA2GRAY);

            // Downsampling limits CPU cost while retaining glyph-scale detail.
            double scale = Math.min(1.0, 720.0 / Math.max(sourceGray.width(), sourceGray.height()));
            if (scale < 1.0) {
                Mat resized = new Mat();
                Imgproc.resize(sourceGray, resized, new Size(), scale, scale, Imgproc.INTER_AREA);
                sourceGray.release();
                sourceGray = resized;
            }

            Suit bestSuit = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Map.Entry<Suit, Mat> entry : templates.entrySet()) {
                Mat originalTemplate = entry.getValue();
                for (double templateScale : new double[]{0.65, 0.8, 1.0, 1.2, 1.4, 1.6}) {
                    Mat template = new Mat();
                    Imgproc.resize(originalTemplate, template, new Size(), templateScale, templateScale,
                            Imgproc.INTER_AREA);
                    if (template.cols() >= sourceGray.cols() || template.rows() >= sourceGray.rows()
                            || template.cols() < 4 || template.rows() < 4) {
                        template.release();
                        continue;
                    }
                    Mat result = new Mat();
                    Imgproc.matchTemplate(sourceGray, template, result, Imgproc.TM_CCOEFF_NORMED);
                    Core.MinMaxLocResult match = Core.minMaxLoc(result);
                    result.release();
                    template.release();
                    if (match.maxVal > bestScore) {
                        bestScore = match.maxVal;
                        bestSuit = entry.getKey();
                    }
                }
            }

            return bestSuit != null && bestScore >= MIN_SCORE
                    ? new Detection(bestSuit, bestScore)
                    : Detection.none();
        } finally {
            sourceRgba.release();
            sourceGray.release();
        }
    }

    public void close() {
        for (Mat template : templates.values()) {
            template.release();
        }
        templates.clear();
    }

    private void loadTemplates(AssetManager assets) {
        for (Suit suit : Suit.values()) {
            String filename = "templates/" + suit.name().toLowerCase() + ".pgm";
            try (InputStream input = assets.open(filename)) {
                byte[] bytes = readAll(input);
                Mat encoded = new Mat(1, bytes.length, CvType.CV_8U);
                encoded.put(0, 0, bytes);
                Mat decoded = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_GRAYSCALE);
                encoded.release();
                if (decoded != null && !decoded.empty()) {
                    templates.put(suit, decoded);
                }
            } catch (IOException | RuntimeException error) {
                Log.w(TAG, "Could not load " + filename + "; add a calibrated template to enable matching.", error);
            }
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public static final class Detection {
        private final Suit suit;
        private final double score;

        private Detection(Suit suit, double score) {
            this.suit = suit;
            this.score = score;
        }

        public static Detection none() {
            return new Detection(null, 0.0);
        }

        public boolean isMatch() {
            return suit != null;
        }

        public Suit getSuit() {
            return suit;
        }

        public double getScore() {
            return score;
        }
    }
}