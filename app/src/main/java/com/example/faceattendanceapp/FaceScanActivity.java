package com.example.faceattendanceapp;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceScanActivity extends AppCompatActivity {

    private PreviewView previewView;
    private FaceEmbedder faceEmbedder;
    private boolean isCaptured = false;
    private ExecutorService cameraExecutor;
    private Bitmap latestFrameBitmap = null;
    private Face latestDetectedFace = null;
    private FaceDetector faceDetector;
    private static final float FRAUD_THRESHOLD = 0.7f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_student);

        previewView = findViewById(R.id.previewView);
        faceEmbedder = new FaceEmbedder(this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build();
        faceDetector = FaceDetection.getClient(options);

        startCamera();

        Button captureButton = findViewById(R.id.submitButton);
        captureButton.setOnClickListener(v -> captureAndSaveFace());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @SuppressLint("UnsafeOptInUsageError")
                    @Override
                    public void analyze(ImageProxy imageProxy) {
                        if (isCaptured || imageProxy.getImage() == null) {
                            imageProxy.close();
                            return;
                        }

                        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), rotationDegrees);

                        faceDetector.process(image)
                                .addOnSuccessListener(faces -> {
                                    if (!faces.isEmpty()) {
                                        latestDetectedFace = faces.get(0);
                                        Bitmap rawBitmap = ImageUtils.toBitmap(imageProxy);

                                        if (rawBitmap != null) {
                                            latestFrameBitmap = ImageUtils.rotateBitmap(rawBitmap, rotationDegrees);
                                        }
                                    }
                                })
                                .addOnCompleteListener(task -> imageProxy.close());
                    }
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

            } catch (Exception e) {
                Toast.makeText(this, "Camera failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureAndSaveFace() {
        if (latestFrameBitmap == null || latestDetectedFace == null) {
            Toast.makeText(this, "No face detected yet. Please look at the camera.", Toast.LENGTH_SHORT).show();
            return;
        }

        isCaptured = true;
        Toast.makeText(this, "Verifying Biometric Uniqueness...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            float[] embedding = faceEmbedder.getEmbedding(latestFrameBitmap, latestDetectedFace);
            String base64Image = convertBitmapToBase64(latestFrameBitmap);
            new Handler(Looper.getMainLooper()).post(() -> saveToFirebase(embedding, base64Image));
        });
    }

    private String convertBitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
        byte[] byteArray = outputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }


    private void saveToFirebase(float[] newEmbedding, String base64Image) {
        String studentId = getIntent().getStringExtra("studentId");
        if (studentId == null) {
            Toast.makeText(this, "Student ID not found", Toast.LENGTH_SHORT).show();
            isCaptured = false;
            return;
        }

        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        rootRef.child("embeddings").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                boolean isDuplicateFace = false;
                String duplicateId = "";
                for (DataSnapshot snapshot : task.getResult().getChildren()) {
                    String existingId = snapshot.getKey();
                    if (existingId == null || existingId.equals(studentId)) continue;

                    List<Object> rawList = (List<Object>) snapshot.getValue();
                    if (rawList != null) {
                        List<Float> existingEmbedding = new ArrayList<>();
                        for (Object obj : rawList) {
                            if (obj instanceof Number) {
                                existingEmbedding.add(((Number) obj).floatValue());
                            }
                        }

                        if (existingEmbedding.size() == newEmbedding.length) {
                            float distance = calculateL2Distance(newEmbedding, existingEmbedding);
                            if (distance < FRAUD_THRESHOLD) {
                                isDuplicateFace = true;
                                duplicateId = existingId;
                                break;
                            }
                        }
                    }
                }
                if (isDuplicateFace) {
                    Toast.makeText(this, "Fraud Alert: Face already registered to ID " + duplicateId, Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    commitBiometrics(rootRef, studentId, newEmbedding, base64Image);
                }

            } else if (task.isSuccessful() && !task.getResult().exists()) {
                commitBiometrics(rootRef, studentId, newEmbedding, base64Image);
            } else {
                Toast.makeText(this, "Database Error during verification", Toast.LENGTH_SHORT).show();
                isCaptured = false;
            }
        });
    }

    private void commitBiometrics(DatabaseReference rootRef, String studentId, float[] newEmbedding, String base64Image) {
        String name = getIntent().getStringExtra("name");
        String email = getIntent().getStringExtra("email");
        String dept = getIntent().getStringExtra("dept");

        java.util.HashMap<String, String> studentTextData = new java.util.HashMap<>();
        studentTextData.put("id", studentId);
        studentTextData.put("name", name != null ? name : "Unknown");
        studentTextData.put("email", email != null ? email : "Unknown");
        studentTextData.put("dept", dept != null ? dept : "Unknown");

        List<Float> embeddingList = new ArrayList<>();
        for (float val : newEmbedding) {
            embeddingList.add(val);
        }

        java.util.HashMap<String, Object> atomicUpdate = new java.util.HashMap<>();
        atomicUpdate.put("/students/" + studentId, studentTextData);
        atomicUpdate.put("/embeddings/" + studentId, embeddingList);
        atomicUpdate.put("/images/" + studentId, base64Image);

        // 5. Execute
        rootRef.updateChildren(atomicUpdate).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(FaceScanActivity.this, "Student & Biometrics Registered Successfully!", Toast.LENGTH_SHORT).show();
                new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1000);
            } else {
                Toast.makeText(FaceScanActivity.this, "Network Error. Transaction Failed.", Toast.LENGTH_SHORT).show();
                isCaptured = false;
            }
        });
    }

    private float calculateL2Distance(float[] emb1, List<Float> emb2) {
        float sum = 0;
        for (int i = 0; i < emb1.length; i++) {
            float diff = emb1[i] - emb2.get(i);
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (faceDetector != null) {
            faceDetector.close();
        }
    }
}