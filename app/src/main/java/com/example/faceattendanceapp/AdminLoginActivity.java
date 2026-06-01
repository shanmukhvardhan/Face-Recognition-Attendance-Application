package com.example.faceattendanceapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

public class AdminLoginActivity extends AppCompatActivity {

    private EditText emailField, passwordField;
    private Button loginBtn;
    private ImageView backbtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        emailField = findViewById(R.id.email);
        passwordField = findViewById(R.id.password);
        loginBtn = findViewById(R.id.loginButton);
        backbtn = findViewById(R.id.backArrow);

        TextView forgotPasswordText = findViewById(R.id.forgotPasswordText);
        forgotPasswordText.setOnClickListener(v -> showForgotPasswordDialog());

        backbtn.setOnClickListener(v -> {
            Intent intent = new Intent(AdminLoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        loginBtn.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(AdminLoginActivity.this, "Please enter both fields", Toast.LENGTH_SHORT).show();
                return;
            }

            loginBtn.setEnabled(false);
            loginBtn.setText("Verifying...");

            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        loginBtn.setEnabled(true);
                        loginBtn.setText("Login");

                        if (task.isSuccessful()) {
                            Toast.makeText(AdminLoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(AdminLoginActivity.this, AdminDashboardActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(AdminLoginActivity.this, "Invalid Credentials", Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private void showForgotPasswordDialog() {
        EditText emailBox = new EditText(this);
        emailBox.setHint("Enter your admin email");
        emailBox.setPadding(40, 40, 40, 40);

        android.graphics.drawable.GradientDrawable borderShape = new android.graphics.drawable.GradientDrawable();
        borderShape.setColor(android.graphics.Color.parseColor("#FAFAFA"));
        borderShape.setStroke(5, android.graphics.Color.parseColor("#4285F4"));
        borderShape.setCornerRadius(24f);
        emailBox.setBackground(borderShape);

        emailBox.setTextColor(android.graphics.Color.BLACK);
        emailBox.setHintTextColor(android.graphics.Color.DKGRAY);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(64, 24, 64, 0);
        emailBox.setLayoutParams(params);
        container.addView(emailBox);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.RoundedMaterialDialog)
                .setTitle("Reset Password")
                .setMessage("Enter your admin email address to receive a secure password reset link.")
                .setView(container)
                .setPositiveButton("Send Link", (d, which) -> {
                    String email = emailBox.getText().toString().trim();
                    if (email.isEmpty()) {
                        Toast.makeText(this, "Email cannot be empty", Toast.LENGTH_SHORT).show();
                    } else {
                        sendResetEmailViaFirebase(email);
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.parseColor("#0000FF"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.parseColor("#ED1515")); // Clean standard red
        TextView titleView = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (titleView != null) {
            titleView.setTextColor(android.graphics.Color.BLACK);
        }

        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextColor(android.graphics.Color.DKGRAY);
        }
    }

    private void sendResetEmailViaFirebase(String email) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Reset link sent! Check your inbox.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}