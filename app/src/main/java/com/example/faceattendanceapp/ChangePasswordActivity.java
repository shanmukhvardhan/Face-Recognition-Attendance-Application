package com.example.faceattendanceapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends AppCompatActivity {

    EditText emailEditText, newPasswordEditText, confirmPasswordEditText;
    Button submitButton;
    ImageView backArrow;
    FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        emailEditText = findViewById(R.id.emailEditText);
        newPasswordEditText = findViewById(R.id.newPasswordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        submitButton = findViewById(R.id.submitButton);
        backArrow = findViewById(R.id.backArrow);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            emailEditText.setText(currentUser.getEmail());
            emailEditText.setEnabled(false);
        }

        backArrow.setOnClickListener(v -> onBackPressed());

        submitButton.setOnClickListener(v -> {
            String newPassword = newPasswordEditText.getText().toString().trim();
            String confirmPassword = confirmPasswordEditText.getText().toString().trim();

            if (TextUtils.isEmpty(newPassword) || TextUtils.isEmpty(confirmPassword)) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPassword.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentUser != null) {
                submitButton.setEnabled(false);
                submitButton.setText("Updating...");

                currentUser.updatePassword(newPassword)
                        .addOnCompleteListener(task -> {
                            submitButton.setEnabled(true);
                            submitButton.setText("Change");

                            if (task.isSuccessful()) {
                                Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
            } else {
                Toast.makeText(this, "Critical Error: No active session found.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}