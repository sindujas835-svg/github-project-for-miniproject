# Setup & Installation Guide

This guide provides exact, step-by-step instructions to configure, run, and evaluate the **AI-Powered Intelligent Traffic Management System** on a Windows environment.

---

## 1. Prerequisites & Environment

Make sure you have the following installed on your machine:
- **Operating System:** Windows 10 or Windows 11 (64-bit)
- **Java Development Kit (JDK):** Java 21 LTS (Temurin 21 or Oracle JDK 21)
  - Verify by opening PowerShell and running:
    ```powershell
    java -version
    ```
- **MySQL Database Server:** MySQL 8.0+ running on port `3306`
  - Verify by opening MySQL Command Line or checking Windows Services (`services.msc`).
- **Web Browser:** Google Chrome, Microsoft Edge, or Firefox.

---

## 2. MySQL Database Setup

1. Open PowerShell or MySQL Workbench and log in as root:
   ```powershell
   mysql -u root -p
   ```
2. Create the project database:
   ```sql
   CREATE DATABASE IF NOT EXISTS ambulance_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   SHOW DATABASES;
   ```
3. (Optional) Run the provided schema script located at `database/schema.sql`:
   ```powershell
   Get-Content database\schema.sql | mysql -u root -p ambulance_db
   ```
   > **Note:** Spring Boot Hibernate is configured with `spring.jpa.hibernate.ddl-auto=update`, which automatically validates and creates all 7 tables and indexes on first startup even if you skip manual schema import.

4. Verify credentials in `traffic-backend/traffic-backend/src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/ambulance_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
   spring.datasource.username=root
   spring.datasource.password=root
   ```
   *(If your MySQL root password is not `root`, update this property accordingly).*

---

## 3. Storage Configuration (`D:\maven-repo`)

To prevent filling up the `C:` drive with heavy native libraries (OpenCV native DLLs and ONNX Runtime binaries), the Maven wrapper is pre-configured with:
```
-Dmaven.repo.local=D:\maven-repo
```
in `traffic-backend/traffic-backend/.mvn/maven.config`. Ensure your `D:` drive has at least 2 GB of available space.

---

## 4. Building and Running the Backend

1. Open PowerShell and navigate to the backend folder:
   ```powershell
   cd C:\Users\shri\Downloads\traffic-routing-prototype\traffic-backend\traffic-backend
   ```
2. Build the application and run all automated test suites:
   ```powershell
   .\mvnw.cmd test
   ```
   You should see:
   ```
   [INFO] Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
   [INFO] BUILD SUCCESS
   ```
3. Start the Spring Boot Application:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
4. The backend will initialize:
   - OpenCV 4.9.0 native binaries loaded
   - Microphone input line detected
   - MySQL tables verified
   - Non-blocking traffic state machine started on `http://localhost:8080`
   - WebSocket STOMP endpoint ready at `ws://localhost:8080/ws-traffic`

---

## 5. Running the Frontend Dashboard

1. Simply open `index.html` in your web browser:
   - Double-click `C:\Users\shri\Downloads\traffic-routing-prototype\index.html`, OR
   - Start a lightweight static server from PowerShell:
     ```powershell
     cd C:\Users\shri\Downloads\traffic-routing-prototype
     python -m http.server 3000
     ```
     and open `http://localhost:3000` in Chrome/Edge.
2. The dashboard will automatically detect the backend and turn the header badge green:
   `Backend: Connected (Live Java State Machine)`

---

## 6. (Optional) Using Real Physical Webcams and Microphones

The project includes an automatic **Transparent Simulation Fallback Mode**:
- If no physical webcam or ONNX model file is present, the system runs safely in simulation fallback without crashing.
- Every simulated detection is tagged with `is_simulated = true`.

To activate real physical AI inference:
1. **Webcam:** Plug in any standard USB webcam. In `application.properties`:
   ```properties
   traffic.camera.enabled=true
   traffic.camera.device-index=0
   ```
2. **ONNX Vision Weights:** Place your fine-tuned `yolov8n_ambulance.onnx` file at:
   `traffic-backend/traffic-backend/models/vision/yolov8n_ambulance.onnx`
3. **ONNX Audio Weights:** Place your `siren_classifier.onnx` file at:
   `traffic-backend/traffic-backend/models/audio/siren_classifier.onnx`
4. Restart the Spring Boot backend. The system will automatically detect the files and transition from simulation fallback to live neural network inference.
