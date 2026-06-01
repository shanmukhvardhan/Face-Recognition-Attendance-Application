package com.example.faceattendanceapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

public class ManageStudentsActivity extends AppCompatActivity {

    private EditText searchBar;
    private RecyclerView recyclerView;
    private StudentAdapter adapter;
    private ArrayList<StudentModel> studentList;
    private ArrayList<StudentModel> filteredList;
    private DatabaseReference dbRef;
    private TextView emptyStateText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_students);

        searchBar = findViewById(R.id.searchBar);
        recyclerView = findViewById(R.id.recyclerView);
        ImageView backArrow = findViewById(R.id.backArrow);
        emptyStateText = findViewById(R.id.emptyStateText);
        studentList = new ArrayList<>();
        filteredList = new ArrayList<>();
        dbRef = FirebaseDatabase.getInstance().getReference();

        adapter = new StudentAdapter(filteredList, student -> showStudentOptions(student));
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fetchStudentsFromFirebase();
        setupSearch();

        backArrow.setOnClickListener(v -> finish());
    }

    private void fetchStudentsFromFirebase() {
        dbRef.child("students").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                studentList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    StudentModel student = dataSnapshot.getValue(StudentModel.class);
                    if (student != null) {
                        studentList.add(student);
                    }
                }
                filterList(searchBar.getText().toString());
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ManageStudentsActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSearch() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterList(s.toString());
            }
        });
    }

    private void filterList(String query) {
        filteredList.clear();
        String safeQuery = query.trim().toLowerCase();
        if (safeQuery.isEmpty()) {
            emptyStateText.setText("Search and select a student");
            emptyStateText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
            return;
        }
        for (StudentModel student : studentList) {
            String name = student.getName() != null ? student.getName().toLowerCase() : "";
            String id = student.getId() != null ? student.getId().toLowerCase() : "";
            if (name.contains(safeQuery) || id.contains(safeQuery)) {
                filteredList.add(student);
            }
        }
        if (filteredList.isEmpty()) {
            emptyStateText.setText("No student present with ID or Name: " + query);
            emptyStateText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyStateText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    private void showStudentOptions(StudentModel student) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_student_options, null);
        bottomSheetDialog.setContentView(sheetView);
        bottomSheetDialog.setOnShowListener(dialog -> {
            View bottomSheetInternal = (View) sheetView.getParent();
            if (bottomSheetInternal != null) {
                bottomSheetInternal.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        });
        android.widget.TextView nameText = sheetView.findViewById(R.id.sheetStudentName);
        android.widget.TextView idText = sheetView.findViewById(R.id.sheetStudentId);
        View editBtn = sheetView.findViewById(R.id.btnSheetEdit);
        View deleteBtn = sheetView.findViewById(R.id.btnSheetDelete);
        nameText.setText(student.getName());
        idText.setText("Roll Number: " + student.getId());
        editBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();

            Intent intent = new Intent(this, EditStudentActivity.class);
            intent.putExtra("id", student.getId());
            intent.putExtra("name", student.getName());
            intent.putExtra("email", student.getEmail());
            intent.putExtra("dept", student.getDept());
            startActivity(intent);
        });
        deleteBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            confirmDeletion(student);
        });
        bottomSheetDialog.show();
    }

    private void confirmDeletion(StudentModel student) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.RoundedMaterialDialog)
                .setTitle("Delete Student")
                .setMessage("Are you sure you want to delete " + student.getName() + "? This will erase their profile and biometric data.")
                .setPositiveButton("Delete", (d, which) -> deleteStudent(student))
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.parseColor("#0000FF"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.parseColor("#888888"));

        TextView titleView = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (titleView != null) {
            titleView.setTextColor(android.graphics.Color.BLACK);
        }
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextColor(android.graphics.Color.DKGRAY);
        }
    }
    private void deleteStudent(StudentModel student) {
        String studentId = student.getId();
        if (studentId == null) return;
        dbRef.child("students").child(studentId).removeValue();
        dbRef.child("embeddings").child(studentId).removeValue();
        dbRef.child("images").child(studentId).removeValue();
        Toast.makeText(this, "Student and biometric data deleted", Toast.LENGTH_SHORT).show();
    }
}