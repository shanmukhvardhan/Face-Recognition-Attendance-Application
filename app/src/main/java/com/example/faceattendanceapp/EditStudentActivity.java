package com.example.faceattendanceapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class EditStudentActivity extends AppCompatActivity {

    private EditText editStudentName, editStudentEmail, editStudentDept, editStudentId;
    private Button saveButton;
    private String studentId;
    private DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_student);

        editStudentName = findViewById(R.id.editStudentName);
        editStudentEmail = findViewById(R.id.editStudentEmail);
        editStudentDept = findViewById(R.id.editStudentDept);
        editStudentId = findViewById(R.id.editStudentId);
        saveButton = findViewById(R.id.saveButton);

        ImageView backArrow = findViewById(R.id.backArrow);
        if (backArrow != null) backArrow.setOnClickListener(v -> finish());

        dbRef = FirebaseDatabase.getInstance().getReference("students");

        studentId = getIntent().getStringExtra("id");
        if (studentId == null) {
            Toast.makeText(this, "Critical Error: Student ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        editStudentId.setText(studentId);
        editStudentId.setEnabled(false);

        editStudentName.setText(getIntent().getStringExtra("name"));
        editStudentEmail.setText(getIntent().getStringExtra("email"));
        editStudentDept.setText(getIntent().getStringExtra("dept"));

        saveButton.setOnClickListener(v -> updateStudentInFirebase());
    }

    private void updateStudentInFirebase() {
        String newName = editStudentName.getText().toString().trim();
        String newEmail = editStudentEmail.getText().toString().trim();
        String newDept = editStudentDept.getText().toString().trim();

        if (newName.isEmpty() || newEmail.isEmpty() || newDept.isEmpty()) {
            Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", newName);
        updates.put("email", newEmail);
        updates.put("dept", newDept);

        dbRef.child(studentId).updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Student updated successfully", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Failed to update database", Toast.LENGTH_SHORT).show();
            }
        });
    }
}