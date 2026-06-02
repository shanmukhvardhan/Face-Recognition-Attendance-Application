# 📱 Enterprise Face Attendance System

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Language-Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Firebase](https://img.shields.io/badge/Database-Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![TensorFlow Lite](https://img.shields.io/badge/AI_Model-TensorFlow_Lite-FF6F00?style=for-the-badge&logo=tensorflow&logoColor=white)

## 📖 Overview
The **Face Attendance System** is an edge-computing mobile application designed to completely automate student or employee attendance using advanced mathematical facial recognition. 

Instead of relying on easily faked ID cards or manual roll calls, this system uses the device's camera to map a person's facial structure, verify it against a secure cloud database in real-time, and log their attendance—all in less than 2 seconds. 

It is built with a strong emphasis on **Data Integrity**, **Concurrency Management**, and **System Security**, acting as a standalone smart-terminal for administrators.

---

## ✨ Key Features
* **Biometric Registration:** Captures high-quality facial images, converts them into mathematical vectors (embeddings) using AI, and maps them to student IDs.
* **Fraud Prevention:** Uses Euclidean Distance ($L_2$) calculations to ensure no two students can register with the same face.
* **Dynamic Time Gates:** Administrators can set strict "Attendance Windows" (e.g., 09:00 to 17:00). The system automatically locks the camera and refuses scans outside of these hours.
* **Atomic Transactions:** Uses a Deferred Commit Pattern. If a registration is cancelled halfway, no partial or "zombie" data is left in the database.
* **Secure Admin Dashboard:** Protected by cryptographic Firebase Authentication (Email/Password), allowing admins to safely manage records and configuration.

---

## 🛠️ Technology Stack
* **Frontend UI:** XML, Material Design 3, ViewBinding
* **Language:** Java (JDK 11)
* **Camera Architecture:** CameraX (v1.1.0)
* **Machine Learning:** Google ML Kit (Face Detection), TensorFlow Lite (MobileFaceNet)
* **Backend Database:** Firebase Realtime Database (NoSQL)
* **Authentication:** Firebase Auth

---

## 🏗️ System Architecture Highlights (For Developers)
* **Fail-Closed Security:** The app assumes the attendance window is closed until Firebase explicitly returns a valid time interval.
* **Mutex Threading Locks:** The camera frame analyzer implements boolean concurrency locks to prevent the app from processing 30 frames per second and crashing the UI thread.
* **Read-Before-Write Idempotency:** The scanner checks if a student is already marked present today *before* attempting to write to the database, saving network bandwidth.

---

## 🚀 How to Deploy (Step-by-Step Guide)
If you want to clone this project and run it yourself, follow these instructions. 

### 1. Prerequisites
* Download and install [Android Studio](https://developer.android.com/studio).
* Ensure you have a physical Android device (or emulator with a webcam) running Android 7.0 (API 24) or higher.

### 2. Clone the Repository
Open your terminal and run:
`git clone https://github.com/YOUR_USERNAME/face-attendance-system.git`
Open the folder in Android Studio and let Gradle sync.

### 3. Setup Your Own Firebase Database
For security reasons, the `google-services.json` file is ignored in this repository. You must create your own:
1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a new project.
2. Add an Android App to the project (Use the package name `com.example.faceattendanceapp`).
3. Download the `google-services.json` file and place it in your local `app/` directory.
4. Enable **Authentication** (Email/Password provider).
5. Enable **Realtime Database**.

### 4. Configure Firebase Security Rules
To ensure the app's atomic writes and admin controls function correctly, go to the **Rules** tab of your Realtime Database and paste this exact JSON schema:

```json
{
  "rules": {
    "settings": {
      ".read": "true",
      ".write": "auth != null" 
    },
    "students": {
      ".read": "true",
      "$studentId": {
        ".write": "true",
        ".validate": "!newData.exists() || newData.hasChildren(['dept', 'email', 'id', 'name'])"
      }
    },
    "attendance": {
      ".read": "true",
      "$date": {
        ".write": "true"
      }
    },
    "images": {
      ".read": "true",
      "$studentId": {
        ".write": "true",
        ".validate": "!newData.exists() || newData.isString()"
      }
    },
    "embeddings": {
      ".read": "true",
      "$studentId": {
        ".write": "true",
        ".validate": "!newData.exists() || newData.hasChildren()"
      }
    }
  }
}
```
### 5. Build and Run
1.Connect your Android device via USB.
2.Click the Run (Play) button in Android Studio.
3.Upon first launch, use the Admin panel to register your first admin email/password, then begin registering faces!

## 👨‍💻 Developed By
- Shanmukh Vardhan(https://github.com/shanmukhvardhan)
- Shanmukha Varun(https://github.com/Shanmukha-varun)
