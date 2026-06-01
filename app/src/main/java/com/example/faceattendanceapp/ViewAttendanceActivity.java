package com.example.faceattendanceapp;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;

public class ViewAttendanceActivity extends AppCompatActivity {

    private EditText dateEditText;
    private ImageView backArrow;
    private TextView attendanceTextView;
    private DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_attendance);

        dateEditText = findViewById(R.id.dateEditText);
        backArrow = findViewById(R.id.backArrow);
        attendanceTextView = findViewById(R.id.attendanceTextView);
        dbRef = FirebaseDatabase.getInstance().getReference();
        dateEditText.setOnClickListener(view -> openDatePicker());
        backArrow.setOnClickListener(v -> finish());
        String today = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(new java.util.Date());
        dateEditText.setText(today);
        fetchAttendanceData(today);
    }
    private void openDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                R.style.CustomDatePickerTheme,
                (DatePicker view, int selectedYear, int selectedMonth, int selectedDay) -> {
                    String formattedDate = String.format("%02d-%02d-%04d", selectedDay, selectedMonth + 1, selectedYear);
                    dateEditText.setText(formattedDate);
                    fetchAttendanceData(formattedDate);
                },
                year, month, day
        );

        datePickerDialog.show();
    }

    private void fetchAttendanceData(String dateKey) {
        attendanceTextView.setText("Fetching roster and logs from cloud...");
        dbRef.child("embeddings").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot embeddingsSnapshot) {
                if (!embeddingsSnapshot.exists()) {
                    attendanceTextView.setText("No students registered in the system.");
                    return;
                }
                java.util.List<String> allStudents = new java.util.ArrayList<>();
                for (DataSnapshot student : embeddingsSnapshot.getChildren()) {
                    allStudents.add(student.getKey());
                }
                java.util.Collections.sort(allStudents, (s1, s2) -> {
                    try {
                        return Integer.compare(Integer.parseInt(s1), Integer.parseInt(s2));
                    } catch (NumberFormatException e) {
                        return s1.compareTo(s2);
                    }
                });
                dbRef.child("attendance").child(dateKey).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot attendanceSnapshot) {
                        if (!attendanceSnapshot.exists()) {
                            attendanceTextView.setText("No attendance records found for " + dateKey + ".");
                            return;
                        }
                        StringBuilder records = new StringBuilder();
                        int presentCount = 0;
                        int absentCount = 0;
                        for (String studentId : allStudents) {
                            if (attendanceSnapshot.hasChild(studentId)) {
                                records.append("✅  ID: ").append(studentId).append("   -   Present\n\n");
                                presentCount++;
                            } else {
                                records.append("❌  ID: ").append(studentId).append("   -   Absent\n\n");
                                absentCount++;
                            }
                        }
                        String summary = "Total Registered: " + allStudents.size() + "\n" +
                                "Total Present: " + presentCount + "\n" +
                                "Total Absent: " + absentCount + "\n\n" +
                                "--------------------------------------\n\n";
                        records.insert(0, summary);
                        attendanceTextView.setText(records.toString());
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        attendanceTextView.setText("Database connection failed: " + error.getMessage());
                    }
                });
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                attendanceTextView.setText("Database connection failed: " + error.getMessage());
            }
        });
    }
}