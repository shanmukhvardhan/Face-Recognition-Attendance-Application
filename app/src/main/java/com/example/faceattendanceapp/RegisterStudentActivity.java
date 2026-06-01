package com.example.faceattendanceapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import android.util.Patterns;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import androidx.annotation.NonNull;

public class RegisterStudentActivity extends AppCompatActivity {

    EditText nameInput, idInput, emailInput, deptInput;
    Button submitButton;
    ImageView backArrow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_student);
        nameInput = findViewById(R.id.nameInput);
        idInput = findViewById(R.id.idInput);
        emailInput = findViewById(R.id.emailInput);
        deptInput = findViewById(R.id.deptInput);
        submitButton = findViewById(R.id.submitButton);
        backArrow = findViewById(R.id.backArrow);
        backArrow.setOnClickListener(v -> finish());
        submitButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String id = idInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String dept = deptInput.getText().toString().trim();
            if (name.isEmpty() || id.isEmpty() || email.isEmpty() || dept.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email format (e.g., student@domain.com)", Toast.LENGTH_SHORT).show();
                return;
            }
            showBiometricQualityWarning(name, id, email, dept);
        });
    }

    private void showBiometricQualityWarning(String name, String id, String email, String dept) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.RoundedMaterialDialog)
                .setTitle("Registration Rules")
                .setMessage("For the AI to recognize this student daily, this initial photo must be perfect:\n\n" +
                        "1. Face the camera perfectly straight.\n" +
                        "2. Ensure bright, even lighting on the face.\n" +
                        "3. No heavy shadows, masks, or dark glasses.\n\n" +
                        "A blurry registration will permanently lock the student out.")
                .setPositiveButton("I Understand", (d, which) -> {
                    executeRegistration(name, id, email, dept);
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.parseColor("#0000FF"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.parseColor("#888888"));
        android.widget.TextView titleView = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (titleView != null) {
            titleView.setTextColor(android.graphics.Color.BLACK);
        }
        android.widget.TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextColor(android.graphics.Color.DKGRAY);
        }
    }

    private void executeRegistration(String name, String id, String email, String dept) {
        DatabaseReference studentsNode = FirebaseDatabase.getInstance().getReference("students");
        studentsNode.child(id).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (task.getResult().exists()) {
                    Toast.makeText(this, "Error: Student ID " + id + " already exists!", Toast.LENGTH_LONG).show();
                } else {
                    // GATE 2: Check if the Email already exists
                    studentsNode.orderByChild("email").equalTo(email).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                Toast.makeText(RegisterStudentActivity.this, "Error: Email " + email + " is already registered!", Toast.LENGTH_LONG).show();
                            } else {
                                Intent intent = new Intent(RegisterStudentActivity.this, FaceScanActivity.class);
                                intent.putExtra("studentId", id);
                                intent.putExtra("name", name);
                                intent.putExtra("email", email);
                                intent.putExtra("dept", dept);
                                startActivity(intent);
                                finish();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(RegisterStudentActivity.this, "Database Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } else {
                Toast.makeText(this, "Database connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}