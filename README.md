# Uni-Class - Spring Boot Backend Core

> Hệ thống máy chủ phụ trách xử lý logic nghiệp vụ, quản lý lớp học, xác thực phân quyền và lõi thuật toán **Điểm danh Dynamic QR chống gian lận thời gian thực**.

---

## 1. Giới thiệu tổng quan

**Uni-Class Backend** là dịch vụ trung tâm xử lý dữ liệu cho hệ sinh thái Uni-Class. Hệ thống phục vụ các yêu cầu API RESTful từ Mobile App (Java Android) và Web Admin (React Vite), đồng thời duy trì kênh WebSocket tương tác hai chiều để trình chiếu mã QR xoay vòng 30 giây và cập nhật Live Dashboard điểm danh tức thì.

---

## 2. Tech Stack

- **Ngôn ngữ & Nền tảng:** Java 17+, Spring Boot 3.x.
- **Bảo mật:** Spring Security 6, JWT Authentication, Role-based Access Control (`ROLE_STUDENT`, `ROLE_LECTURER`, `ROLE_ADMIN`).
- **Thời gian thực:** Spring WebSocket, STOMP protocol.
- **Cơ sở dữ liệu & Đám mây:**
  - Supabase PostgreSQL (lưu trữ toàn bộ schema nghiệp vụ).
  - Supabase Storage (quản lý file tài liệu bài giảng, tệp nộp bài).
  - Supabase Realtime (đồng bộ dữ liệu không cần polling).
- **Push Notification:** Firebase Admin SDK (FCM Topic & Direct Message).
- **Triển khai & Vận hành:** Docker, Docker Compose, Nginx, Certbot SSL.

---

## 3. Cấu trúc thư mục nguồn

```
backend/src/main/
├── java/vn/edu/uit/Uni-Class/
│   ├── config/              # SecurityConfig, WebSocketConfig, FirebaseConfig
│   ├── controller/          # REST Controllers (Auth, Class, Attendance, Assignment)
│   ├── dto/                 # Request & Response Data Transfer Objects
│   │   ├── request/
│   │   └── response/
│   ├── entity/              # JPA Entities (mapping Supabase PostgreSQL)
│   ├── exception/           # Custom Exceptions & Global Exception Handler
│   ├── repository/          # Spring Data JPA Repositories
│   ├── security/            # JwtAuthenticationFilter, CustomUserDetailsService
│   ├── service/             # Business Logic Layer
│   │   ├── AttendanceService.java
│   │   ├── AssignmentService.java
│   │   ├── ClassService.java
│   │   └── NotificationService.java
│   └── websocket/           # WebSocket Handlers, STOMP Channels
└── resources/
    ├── application.yml      # Cấu hình Spring Boot mặc định
    ├── application-dev.yml  # Môi trường Development cục bộ
    ├── application-prod.yml # Môi trường Production VPS
    └── firebase-service-account.json (chứa credentials FCM)
```
