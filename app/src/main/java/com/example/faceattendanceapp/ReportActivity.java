package com.example.faceattendanceapp;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

public class ReportActivity extends AppCompatActivity {

    private Button btnStartDate, btnEndDate, btnGenerateSend;
    private Date startDate, endDate;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private static class StudentStats {
        String name, email;
        int present = 0;
        StudentStats(String name, String email) { this.name = name; this.email = email; }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate = findViewById(R.id.btnEndDate);
        btnGenerateSend = findViewById(R.id.btnGenerateSend);

        btnStartDate.setOnClickListener(v -> showDatePicker(true));
        btnEndDate.setOnClickListener(v -> showDatePicker(false));

        btnGenerateSend.setOnClickListener(v -> {
            if (startDate == null || endDate == null) {
                Toast.makeText(this, "Please select both dates", Toast.LENGTH_SHORT).show();
                return;
            }
            if (startDate.after(endDate)) {
                Toast.makeText(this, "Start date must be before End date", Toast.LENGTH_SHORT).show();
                return;
            }

            btnGenerateSend.setEnabled(false);
            btnGenerateSend.setText("Calculating...");
            fetchDataAndGenerateExcel();
        });
    }
    private void showDatePicker(boolean isStart) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, R.style.CustomDatePickerTheme, (view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, dayOfMonth, 0, 0, 0); // Set time to midnight for clean math

            if (isStart) {
                startDate = selected.getTime();
                btnStartDate.setText("Start: " + sdf.format(startDate));
            } else {
                endDate = selected.getTime();
                btnEndDate.setText("End: " + sdf.format(endDate));
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void fetchDataAndGenerateExcel() {
        FirebaseDatabase.getInstance().getReference("students")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot studentSnapshot) {
                        HashMap<String, StudentStats> statsMap = new HashMap<>();
                        ArrayList<String> emailList = new ArrayList<>();

                        for (DataSnapshot snap : studentSnapshot.getChildren()) {
                            String id = snap.getKey();
                            String name = snap.child("name").getValue(String.class);
                            String rawEmail = snap.child("email").getValue(String.class);

                            statsMap.put(id, new StudentStats(name != null ? name : "Unknown", rawEmail));
                            if (rawEmail != null) {
                                String cleanEmail = rawEmail.trim();
                                if (android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                                    emailList.add(cleanEmail);
                                } else {
                                    android.util.Log.w("REPORT_ENGINE", "Skipped corrupted legacy email: " + cleanEmail);
                                }
                            }
                        }
                        calculateAttendanceAndExport(statsMap, emailList);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ReportActivity.this, "Database Error", Toast.LENGTH_SHORT).show();
                        resetButton();
                    }
                });
    }
    private void calculateAttendanceAndExport(HashMap<String, StudentStats> statsMap, ArrayList<String> emailList) {
        FirebaseDatabase.getInstance().getReference("attendance")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot attendanceSnapshot) {
                        int totalClassesInRange = 0;
                        for (DataSnapshot dateNode : attendanceSnapshot.getChildren()) {
                            try {
                                Date nodeDate = sdf.parse(dateNode.getKey());
                                if (nodeDate != null && !nodeDate.before(startDate) && !nodeDate.after(endDate)) {
                                    totalClassesInRange++;
                                    for (DataSnapshot studentNode : dateNode.getChildren()) {
                                        String presentId = studentNode.getKey();
                                        if (statsMap.containsKey(presentId)) {
                                            statsMap.get(presentId).present++;
                                        }
                                    }
                                }
                            } catch (Exception ignored) {} // Ignore badly formatted dates in DB
                        }
                        createAndSendCSV(statsMap, totalClassesInRange, emailList);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { resetButton(); }
                });
    }
    private void createAndSendCSV(HashMap<String, StudentStats> statsMap, int totalClasses, ArrayList<String> emailList) {
        try {
            File reportDir = new File(getCacheDir(), "reports");
            if (!reportDir.exists()) reportDir.mkdirs();
            File csvFile = new File(reportDir, "Attendance_Report_" + System.currentTimeMillis() + ".csv");
            FileWriter writer = new FileWriter(csvFile);
            writer.append("Student ID,Name,Total Classes,Days Present,Days Absent,Attendance Percentage\n");
            for (String id : statsMap.keySet()) {
                StudentStats stats = statsMap.get(id);
                int absent = totalClasses - stats.present;
                float percentage = (totalClasses == 0) ? 0 : ((float) stats.present / totalClasses) * 100;
                writer.append(id).append(",")
                        .append(stats.name).append(",")
                        .append(String.valueOf(totalClasses)).append(",")
                        .append(String.valueOf(stats.present)).append(",")
                        .append(String.valueOf(absent)).append(",")
                        .append(String.format(Locale.getDefault(), "%.1f%%", percentage)).append("\n");
            }
            writer.flush();
            writer.close();
            triggerAutomatedEmail(csvFile, emailList);

        } catch (Exception e) {
            Toast.makeText(this, "Failed to build Excel file", Toast.LENGTH_SHORT).show();
            resetButton();
        }
    }
    private void triggerAutomatedEmail(File csvFile, ArrayList<String> emailList) {
        if (emailList.isEmpty()) {
            Toast.makeText(this, "No valid emails found to send the report.", Toast.LENGTH_SHORT).show();
            resetButton();
            return;
        }
        Toast.makeText(this, "Sending reports....", Toast.LENGTH_SHORT).show();
        String allRecipients = TextUtils.join(",", emailList);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                EmailSender.sendEmailWithAttachment(
                        allRecipients,
                        "Attendance Report: " + sdf.format(startDate) + " to " + sdf.format(endDate),
                        "Please find the attached Face Attendance report for the selected dates.",
                        csvFile
                );
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(ReportActivity.this, "Emails sent successfully!", Toast.LENGTH_LONG).show();
                    resetButton();
                });
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(ReportActivity.this, "Failed to send: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    resetButton();
                });
            }
        });
    }
    private void resetButton() {
        btnGenerateSend.setEnabled(true);
        btnGenerateSend.setText("Generate & Send Excel");
    }
}