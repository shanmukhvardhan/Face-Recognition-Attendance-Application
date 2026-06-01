package com.example.faceattendanceapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;

import com.google.mlkit.vision.face.Face;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FaceEmbedder {
    private final Interpreter tfLite;

    private final Bitmap reusableScaledBitmap;
    private final Canvas canvas;
    private final Matrix matrix;
    private final float[][] reusableEmbeddingOutput;
    private final ByteBuffer inputBuffer;

    public FaceEmbedder(Context context) {
        try {
            tfLite = new Interpreter(FileUtil.loadMappedFile(context, "mobile_face_net.tflite"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load TFLite model", e);
        }

        reusableScaledBitmap = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(reusableScaledBitmap);
        matrix = new Matrix();
        reusableEmbeddingOutput = new float[1][192];
        inputBuffer = ByteBuffer.allocateDirect(112 * 112 * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
    }

    public float[] getEmbedding(Bitmap fullFrameBitmap, Face detectedFace) {
        Rect bounds = detectedFace.getBoundingBox();
        int x = Math.max(bounds.left, 0);
        int y = Math.max(bounds.top, 0);
        int width = Math.min(bounds.width(), fullFrameBitmap.getWidth() - x);
        int height = Math.min(bounds.height(), fullFrameBitmap.getHeight() - y);

        matrix.reset();

        float scaleX = 112f / width;
        float scaleY = 112f / height;

        matrix.postTranslate(-x, -y);
        matrix.postScale(scaleX, scaleY);

        canvas.drawBitmap(fullFrameBitmap, matrix, null);

        populateBufferFromBitmap(reusableScaledBitmap, inputBuffer);
        tfLite.run(inputBuffer, reusableEmbeddingOutput);

        float[] finalResult = new float[192];
        System.arraycopy(reusableEmbeddingOutput[0], 0, finalResult, 0, 192);
        return finalResult;
    }

    private void populateBufferFromBitmap(Bitmap bitmap, ByteBuffer buffer) {
        buffer.rewind();
        int[] intValues = new int[112 * 112];
        bitmap.getPixels(intValues, 0, 112, 0, 0, 112, 112);

        int pixel = 0;
        for (int i = 0; i < 112; ++i) {
            for (int j = 0; j < 112; ++j) {
                int val = intValues[pixel++];
                buffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                buffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                buffer.putFloat((val & 0xFF) / 255.0f);
            }
        }
    }
}