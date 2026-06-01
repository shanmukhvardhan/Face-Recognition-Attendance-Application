package com.example.faceattendanceapp;

public class StudentModel {
    private String id;
    private String name;
    private String email;
    private String dept;
    public StudentModel(String name, String id, String email, String dept) {
        this.name = name;
        this.id = id;
        this.email = email;
        this.dept = dept;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getDept() { return dept; }
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setDept(String dept) { this.dept = dept; }
}