package com.example.faceattendanceapp;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

public class ChangeIntervalActivity extends AppCompatActivity {

    private EditText startTimeEditText, endTimeEditText;
    private Button changeButton;
    private ImageView backArrow;

    private int startHour, startMinute, endHour, endMinute;

    private DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_interval);

        startTimeEditText = findViewById(R.id.startTime);
        endTimeEditText = findViewById(R.id.endTime);
        changeButton = findViewById(R.id.changeButton);
        backArrow = findViewById(R.id.backArrow);
        dbRef = FirebaseDatabase.getInstance().getReference("settings/attendance_window");
        fetchCurrentInterval();

        backArrow.setOnClickListener(v -> onBackPressed());
        startTimeEditText.setOnClickListener(v -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(startHour)
                    .setMinute(startMinute)
                    .setTitleText("Select Start Time")
                    .setTheme(R.style.CustomTimePickerTheme)
                    .build();

            picker.addOnPositiveButtonClickListener(dialog -> {
                startHour = picker.getHour();
                startMinute = picker.getMinute();
                startTimeEditText.setText(String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute));
            });

            picker.show(getSupportFragmentManager(), "START_TIME_PICKER");
        });
        endTimeEditText.setOnClickListener(v -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(endHour)
                    .setMinute(endMinute)
                    .setTitleText("Select End Time")
                    .setTheme(R.style.CustomTimePickerTheme)
                    .build();

            picker.addOnPositiveButtonClickListener(dialog -> {
                endHour = picker.getHour();
                endMinute = picker.getMinute();
                endTimeEditText.setText(String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute));
            });

            picker.show(getSupportFragmentManager(), "END_TIME_PICKER");
        });

        changeButton.setOnClickListener(v -> saveIntervalToFirebase());
    }

    private void fetchCurrentInterval() {
        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String currentStart = snapshot.child("start_time").getValue(String.class);
                    String currentEnd = snapshot.child("end_time").getValue(String.class);

                    try {
                        if (currentStart != null) {
                            startTimeEditText.setText(currentStart);
                            String[] startParts = currentStart.split(":"); // Splits "10:30" into ["10", "30"]
                            startHour = Integer.parseInt(startParts[0]);
                            startMinute = Integer.parseInt(startParts[1]);
                        }
                        if (currentEnd != null) {
                            endTimeEditText.setText(currentEnd);
                            String[] endParts = currentEnd.split(":");
                            endHour = Integer.parseInt(endParts[0]);
                            endMinute = Integer.parseInt(endParts[1]);
                        }
                    } catch (Exception e) {
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChangeIntervalActivity.this, "Failed to load current times.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveIntervalToFirebase() {
        String start = startTimeEditText.getText().toString().trim();
        String end = endTimeEditText.getText().toString().trim();

        if (start.isEmpty() || end.isEmpty()) {
            Toast.makeText(this, "Please select both start and end times", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("start_time", start);
        updates.put("end_time", end);
        changeButton.setEnabled(false);
        changeButton.setText("Saving...");

        dbRef.updateChildren(updates).addOnCompleteListener(task -> {
            changeButton.setEnabled(true);
            changeButton.setText("Change");

            if (task.isSuccessful()) {
                Toast.makeText(this, "Interval updated successfully!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Network Error: Could not save.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}