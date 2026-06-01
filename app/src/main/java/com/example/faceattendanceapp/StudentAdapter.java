package com.example.faceattendanceapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.StudentViewHolder> {

    public interface OnStudentClickListener {
        void onStudentClick(StudentModel student);
    }
    private List<StudentModel> studentList;
    private OnStudentClickListener listener;
    public StudentAdapter(List<StudentModel> studentList, OnStudentClickListener listener) {
        this.studentList = studentList;
        this.listener = listener;
    }
    @NonNull
    @Override
    public StudentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student_card, parent, false);
        return new StudentViewHolder(view);
    }
    @Override
    public void onBindViewHolder(@NonNull StudentViewHolder holder, int position) {
        StudentModel student = studentList.get(position);
        holder.bind(student, listener);
    }
    @Override
    public int getItemCount() {
        return studentList.size();
    }
    static class StudentViewHolder extends RecyclerView.ViewHolder {
        TextView studentName;
        ImageView editIcon;
        public StudentViewHolder(@NonNull View itemView) {
            super(itemView);
            studentName = itemView.findViewById(R.id.studentName);
            editIcon = itemView.findViewById(R.id.editIcon);
        }
        public void bind(final StudentModel student, final OnStudentClickListener listener) {
            studentName.setText(student.getName());
            itemView.setOnClickListener(v -> listener.onStudentClick(student));
            editIcon.setOnClickListener(v -> listener.onStudentClick(student));
        }
    }
}