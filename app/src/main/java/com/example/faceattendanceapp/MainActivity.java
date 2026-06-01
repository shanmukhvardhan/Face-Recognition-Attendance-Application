package com.example.faceattendanceapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.firebase.database.DatabaseReference;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView attendanceStatus;
    private FaceEmbedder faceEmbedder;
    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;
    private ProcessCameraProvider cameraProvider;
    private boolean isProcessing = false;
    private boolean isCooldown = false;
    private long scanStartTime = 0;
    private long lastUiUpdateTime = 0;
    private final HashMap<String, float[]> knownEmbeddings = new HashMap<>();
    private String windowStart = "";
    private String windowEnd = "";
    private boolean isTimeLocked = true;
    private Handler timeCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable precisionKillSwitchTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        previewView.setVisibility(android.view.View.INVISIBLE);
        Button markAttendanceBtn = findViewById(R.id.markAttendanceBtn);
        Button adminBtn = findViewById(R.id.adminButton);
        attendanceStatus = findViewById(R.id.statusText);

        faceEmbedder = new FaceEmbedder(this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build();
        faceDetector = FaceDetection.getClient(options);
        keepDatabaseSyncedInMemory();
        observeTimeWindowSettings();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        }

        markAttendanceBtn.setOnClickListener(v -> {
            if (isTimeLocked) {
                Toast.makeText(this, "Attendance window is closed! Contact Admin", Toast.LENGTH_SHORT).show();
                return;
            }
            if (knownEmbeddings.isEmpty()) {
                Toast.makeText(this, "No faces in database!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isCooldown || isProcessing) return;
            isProcessing = true;
            scanStartTime = System.currentTimeMillis();
            attendanceStatus.setText("Scanning face... please hold still.");
            attendanceStatus.setTextColor(android.graphics.Color.WHITE);
        });

        adminBtn.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AdminLoginActivity.class)));
    }
    private void observeTimeWindowSettings() {
        FirebaseDatabase.getInstance().getReference("settings/attendance_window")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && snapshot.hasChild("start_time") && snapshot.hasChild("end_time")) {
                            windowStart = snapshot.child("start_time").getValue(String.class);
                            windowEnd = snapshot.child("end_time").getValue(String.class);
                        } else {
                            windowStart = "00:00";
                            windowEnd = "23:59";
                        }
                        if (windowStart == null) windowStart = "00:00";
                        if (windowEnd == null) windowEnd = "23:59";
                        enforceTimeWindow();
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        attendanceStatus.setText("Network error: Cannot verify time window.");
                    }
                });
    }
    private boolean isWithinAttendanceWindow() {
        if (windowStart == null || windowEnd == null || windowStart.isEmpty() || windowEnd.isEmpty()) {
            return false;
        }
        try {
            java.util.Calendar now = java.util.Calendar.getInstance();
            int currentMinutes = (now.get(java.util.Calendar.HOUR_OF_DAY) * 60) + now.get(java.util.Calendar.MINUTE);
            String[] startParts = windowStart.split(":");
            int startMinutes = (Integer.parseInt(startParts[0].trim()) * 60) + Integer.parseInt(startParts[1].trim());
            String[] endParts = windowEnd.split(":");
            int endMinutes = (Integer.parseInt(endParts[0].trim()) * 60) + Integer.parseInt(endParts[1].trim());
            if (startMinutes > endMinutes) {
                return currentMinutes >= startMinutes || currentMinutes < endMinutes;
            } else {
                return currentMinutes >= startMinutes && currentMinutes < endMinutes;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    private void enforceTimeWindow() {
        boolean isNowValid = isWithinAttendanceWindow();
        Button markAttendanceBtn = findViewById(R.id.markAttendanceBtn);
        if (isNowValid && isTimeLocked) {
            isTimeLocked = false;
            previewView.setVisibility(android.view.View.VISIBLE);
            markAttendanceBtn.setEnabled(true);
            markAttendanceBtn.setAlpha(1.0f);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            }
            armPrecisionKillSwitch();

            attendanceStatus.setText("Number of students registered : " + knownEmbeddings.size());
            attendanceStatus.setTextColor(android.graphics.Color.WHITE);

        } else if (!isNowValid && !isTimeLocked) {
            isTimeLocked = true;
            isProcessing = false;
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }
            previewView.setVisibility(android.view.View.INVISIBLE);
            markAttendanceBtn.setEnabled(false);
            markAttendanceBtn.setAlpha(0.5f);

            attendanceStatus.setText("TIME OUT: Attendance is currently closed.");
            attendanceStatus.setTextColor(android.graphics.Color.RED);

        } else if (!isNowValid && isTimeLocked) {
            attendanceStatus.setText("TIME OUT: Attendance is currently closed.");
            attendanceStatus.setTextColor(android.graphics.Color.RED);
            markAttendanceBtn.setEnabled(false);
            markAttendanceBtn.setAlpha(0.5f);
        }
    }
    private void keepDatabaseSyncedInMemory() {
        attendanceStatus.setText("Syncing database...");
        FirebaseDatabase.getInstance().getReference("embeddings")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        knownEmbeddings.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            try {
                                List<Double> storedList = (List<Double>) child.getValue();
                                if (storedList != null) {
                                    float[] storedArray = new float[storedList.size()];
                                    for (int i = 0; i < storedList.size(); i++) {
                                        storedArray[i] = storedList.get(i).floatValue();
                                    }
                                    knownEmbeddings.put(child.getKey(), storedArray);
                                }
                            } catch (Exception e) {
                                Log.e("DB_LOAD", "Failed to parse embedding", e);
                            }
                        }
                        if (!isProcessing && !isCooldown && !isTimeLocked) {
                            attendanceStatus.setText("Number of Students Registered : "  + knownEmbeddings.size());
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        attendanceStatus.setText("Failed to sync database.");
                    }
                });
    }
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(480, 640))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                cameraProvider.unbindAll();
                if (!isTimeLocked) {
                    cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis);
                }

            } catch (Exception e) {
                Log.e("CAMERA", "Failed to bind camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (!isProcessing || isCooldown || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        long currentTime = System.currentTimeMillis();
        long timeElapsed = currentTime - scanStartTime;
        long timeLeftMillis = 4000 - timeElapsed;

        if (timeElapsed > 4000) {
            isProcessing = false;
            runOnUiThread(() -> {
                attendanceStatus.setText("Timeout: No face detected.");
                Toast.makeText(MainActivity.this, "Timeout: No face found!", Toast.LENGTH_LONG).show();
            });
            imageProxy.close();
            return;
        }

        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), rotationDegrees);

        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    if (!isProcessing) return;

                    if (!faces.isEmpty()) {
                        Face face = faces.get(0);
                        Bitmap rawBitmap = ImageUtils.toBitmap(imageProxy);

                        if (rawBitmap != null) {
                            Bitmap rotatedBitmap = ImageUtils.rotateBitmap(rawBitmap, rotationDegrees);
                            float[] currentEmbedding = faceEmbedder.getEmbedding(rotatedBitmap, face);
                            compareWithRAMCache(currentEmbedding, timeLeftMillis, currentTime);
                        }
                    } else {
                        if (currentTime - lastUiUpdateTime > 250) {
                            lastUiUpdateTime = currentTime;
                            runOnUiThread(() -> attendanceStatus.setText(
                                    String.format("Looking for face... (%.1fs)", timeLeftMillis / 1000.0f)
                            ));
                        }
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void compareWithRAMCache(float[] currentEmbedding, long timeLeftMillis, long currentTime) {
        String bestMatchId = null;
        float lowestDistance = Float.MAX_VALUE;
        float STRICT_THRESHOLD = 0.7f;
        for (Map.Entry<String, float[]> entry : knownEmbeddings.entrySet()) {
            float distance = FaceMathUtils.calculateEuclideanDistance(currentEmbedding, entry.getValue());
            if (distance < lowestDistance) {
                lowestDistance = distance;
                bestMatchId = entry.getKey();
            }
        }

        if (lowestDistance < STRICT_THRESHOLD) {
            isProcessing = false;
            markAttendanceInFirebase(bestMatchId);
        } else {
            if (currentTime - lastUiUpdateTime > 250) {
                lastUiUpdateTime = currentTime;
                runOnUiThread(() -> {
                    attendanceStatus.setText(String.format("Verifying face... (%.1fs)", timeLeftMillis / 1000.0f));
                });
            }
        }
    }

    private void markAttendanceInFirebase(String studentId) {
        isCooldown = true;
        runOnUiThread(() -> attendanceStatus.setText("Verifying with cloud..."));

        String currentDate = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(new java.util.Date());
        DatabaseReference todayAttendanceRef = FirebaseDatabase.getInstance().getReference("attendance").child(currentDate).child(studentId);
        todayAttendanceRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (task.getResult().exists()) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "✅ ID " + studentId + " is already marked present today!", Toast.LENGTH_LONG).show();
                        attendanceStatus.setText("Already marked! ID: " + studentId + "\nResetting...");
                    });
                    unlockSystemAfterDelay();

                } else {
                    todayAttendanceRef.setValue(true).addOnCompleteListener(saveTask -> {
                        if (saveTask.isSuccessful()) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "ATTENDANCE MARKED FOR ID: " + studentId, Toast.LENGTH_LONG).show();
                                attendanceStatus.setText("Success! ID: " + studentId + "\nResetting...");
                            });
                        } else {
                            runOnUiThread(() -> attendanceStatus.setText("Network error: Could not save to database."));
                        }
                        unlockSystemAfterDelay();
                    });
                }
            } else {
                runOnUiThread(() -> attendanceStatus.setText("Database check failed. Try again."));
                unlockSystemAfterDelay();
            }
        });
    }
    private void unlockSystemAfterDelay() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isCooldown = false;
            if (!isTimeLocked) {
                attendanceStatus.setText("Number of Students Registered : " + knownEmbeddings.size());
                attendanceStatus.setTextColor(android.graphics.Color.WHITE);
            }
        }, 3000);
    }

    private void armPrecisionKillSwitch() {
        if (precisionKillSwitchTask != null) {
            timeCheckHandler.removeCallbacks(precisionKillSwitchTask);
        }

        if (windowEnd == null || windowEnd.isEmpty()) return;

        try {
            java.util.Calendar now = java.util.Calendar.getInstance();
            int currentHour = now.get(java.util.Calendar.HOUR_OF_DAY);
            int currentMinute = now.get(java.util.Calendar.MINUTE);
            int currentSecond = now.get(java.util.Calendar.SECOND);

            String[] endParts = windowEnd.split(":");
            int endHour = Integer.parseInt(endParts[0].trim());
            int endMinute = Integer.parseInt(endParts[1].trim());

            int currentSecondsTotal = (currentHour * 3600) + (currentMinute * 60) + currentSecond;
            int endSecondsTotal = (endHour * 3600) + (endMinute * 60);

            long millisUntilClose = (endSecondsTotal - currentSecondsTotal) * 1000L;

            if (millisUntilClose > 0) {
                precisionKillSwitchTask = () -> {
                    enforceTimeWindow();
                    Toast.makeText(MainActivity.this, "Attendance window just closed!", Toast.LENGTH_LONG).show();
                };
                timeCheckHandler.postDelayed(precisionKillSwitchTask, millisUntilClose);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isProcessing = false;
        isCooldown = false;
        isTimeLocked = true;

        previewView.setVisibility(android.view.View.INVISIBLE);
        if (windowStart.isEmpty() || windowEnd.isEmpty()) {
            attendanceStatus.setText("Connecting to server...");
            attendanceStatus.setTextColor(android.graphics.Color.YELLOW);
        }
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            enforceTimeWindow();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (faceDetector != null) faceDetector.close();
        if (timeCheckHandler != null && precisionKillSwitchTask != null) {
            timeCheckHandler.removeCallbacks(precisionKillSwitchTask);
        }
    }
}