# FinalLMS - Dynamic Learning Management System

## Overview
FinalLMS is a fully dynamic, real-time Learning Management System featuring:
- **Admin Dashboard**: Course, Module, and Video management with **AWS S3** integration.
- **Student Dashboard**: Auto-Guest Login, Course Enrollment, and Video Playback.
- **Payments**: Integrated **Razorpay** for paid course enrollments.
- **Real-Time Data**: All statistics and content are fetched live from the MySQL database.

## Prerequisites
- **Java JDK 17+**
- **Maven**
- **MySQL Server** (Database: `Final_Lms1`)
- **Python** (for simple frontend serving, or any web server)
- **AWS Credentials** (S3 Access)
- **Razorpay Keys** (Test or Live)

## Configuration
Update `backend/src/main/resources/application.properties` with your credentials:
```properties
# Database
spring.datasource.url=jdbc:mysql://localhost:3306/Final_Lms1
spring.datasource.username=YOUR_DB_USER
spring.datasource.password=YOUR_DB_PASS

# AWS S3
aws.access.key.id=YOUR_AWS_KEY
aws.secret.access.key=YOUR_AWS_SECRET
aws.s3.bucket.name=YOUR_BUCKET_NAME

# Razorpay
razorpay.key.id=YOUR_RZP_KEY
razorpay.key.secret=YOUR_RZP_SECRET
```

## How to Run

### 1. Backend
Navigate to the `backend` directory and run:
```bash
mvn spring-boot:run
```
*Server runs on: http://localhost:8082*

### 2. Frontend
Navigate to the `frontend` directory and serve the static files.
Using Python:
```bash
python -m http.server 8000
```
*Access App at: http://localhost:8000*

## Authentication Flows
- **Guest Mode**: Default view. Access content immediately (Auto-Login).
- **Existing User**: Click "Login/Register" -> Enter Phone -> Enter OTP -> Login.
- **New User**: Click "Login/Register" -> Enter Phone -> "New User" detected -> Fill Profile -> Register.

## Features Usage
1.  **Admin Login**: `http://localhost:8000/admin/login.html` (Default: `S@123` / `Rishi@123`)
2.  **Student Access**: `http://localhost:8000/student/index.html` (Auto-Guest Mode)
