package com.example.faceattendanceapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AdminDashboardActivity extends AppCompatActivity {

    TextView timeTextView;
    Button logoutButton;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        timeTextView = findViewById(R.id.timeTextClock);
        logoutButton = findViewById(R.id.logoutButton);



        // Logout action
        logoutButton.setOnClickListener(v -> {
            Intent intent = new Intent(AdminDashboardActivity.this, AdminLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }





    public void openRegisterStudent(View view) {
        Intent intent = new Intent(this, RegisterStudentActivity.class);
        startActivity(intent);
    }


    public void openChangeInterval(View view) {
        Intent intent = new Intent(this, ChangeIntervalActivity.class);
        startActivity(intent);
    }


    public void openViewAttendance(View view) {
        Intent intent = new Intent(this, ViewAttendanceActivity.class);
        startActivity(intent);
    }


    public void openManageStudents(View view) {
        Intent intent = new Intent(this, ManageStudentsActivity.class);
        startActivity(intent);
    }

    public void openSendMails(View view) {
        Intent intent = new Intent(this, ReportActivity.class);
        startActivity(intent);
    }

    public void openForgotPassword(View view) {
        Intent intent = new Intent(this, ChangePasswordActivity.class);
        startActivity(intent);
    }
}


