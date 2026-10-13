# Uni-Class - Spring Boot Backend Core 🛡️⚙️

> Hệ thống máy chủ phụ trách xử lý logic nghiệp vụ, quản lý lớp học, xác thực phân quyền và lõi thuật toán **Điểm danh Dynamic QR chống gian lận thời gian thực**.

---

## 📌 1. Giới thiệu tổng quan

**Uni-Class Backend** là dịch vụ trung tâm xử lý dữ liệu cho hệ sinh thái Uni-Class. Hệ thống phục vụ các yêu cầu API RESTful từ Mobile App (Java Android) và Web Admin (React Vite), đồng thời duy trì kênh WebSocket tương tác hai chiều để trình chiếu mã QR xoay vòng 30 giây và cập nhật Live Dashboard điểm danh tức thì.

---

## 🔐 2. Cơ chế Điểm danh & Chống gian lận 3 lớp

Điểm nhấn kỹ thuật của hệ thống là khả năng ngăn chặn hoàn toàn việc chụp ảnh QR gửi ra ngoài hoặc điểm danh hộ:

```
[Giảng viên App]                     [Backend Server]                     [Sinh viên App]
       |                                     |                                   |
       |--- 1. Bắt đầu phiên điểm danh ----->|                                   |
       |<-- 2. Đẩy QR Token mới (mỗi 30s) ---| (WebSocket STOMP)                 |
       |                                     |                                   |
       |                                     |<-- 3. Gửi Payload quét mã --------|
       |                                     |     (QR Token + GPS + BSSID + ID) |
       |                                     |                                   |
       |                                     |--- 4. Kiểm tra 3 lớp -------------|
       |                                     |   - Lớp 1: Token còn hạn 30s?     |
       |                                     |   - Lớp 2: BSSID & GPS phòng học? |
       |                                     |   - Lớp 3: Android ID chưa quét?  |
       |                                     |                                   |
       |<-- 5. Đẩy SV vào Live Dashboard ----| (Realtime Push)                   |
       |      (Supabase Realtime / Socket)   |---> 6. Trả kết quả thành công --->|
```

1. **Xoay vòng mã 30s (Rotating Dynamic QR):**
   * Server tạo JWT token tạm thời chứa: `sessionId`, `salt`, `expireAt = now + 30s`.
   * Đẩy token xuống app giảng viên qua WebSocket. Khi hết hạn 30 giây, token cũ bị thu hồi ngay lập tức, vô hiệu hóa việc chụp ảnh gửi qua tin nhắn.
2. **Đối soát Vị trí & Hạ tầng (GPS Haversine & WiFi BSSID):**
   * Giảng viên cấu hình tọa độ phòng học $(lat_0, lng_0)$, bán kính cho phép $R$ (ví dụ: $30m$) và địa chỉ MAC phát sóng WiFi phòng học (`BSSID`).
   * Khi quét, mobile app sinh viên bắt buộc gửi kèm WiFi BSSID hiện tại và tọa độ GPS $(lat_1, lng_1)$. Server sử dụng công thức Haversine để đối chiếu khoảng cách:
     $$d = 2r \arcsin\left(\sqrt{\sin^2\left(\frac{\Delta lat}{2}\right) + \cos(lat_0)\cos(lat_1)\sin^2\left(\frac{\Delta lng}{2}\right)}\right)$$
     Nếu $d > R$ hoặc BSSID không khớp, yêu cầu bị từ chối ngay lập tức.
3. **Ràng buộc Thiết bị Phần cứng (Device Binding):**
   * Ứng dụng gửi kèm `Android ID` (mã băm duy nhất gắn với phần cứng thiết bị).
   * Trong một phiên điểm danh, một `Android ID` chỉ được ghi nhận một lần duy nhất. Nếu sinh viên đăng xuất rồi đăng nhập tài khoản khác trên cùng một điện thoại để điểm danh hộ, server sẽ đánh dấu gian lận và khóa phiên nộp.

---

## 💻 3. Tech Stack

* **Ngôn ngữ & Nền tảng:** Java 17+, Spring Boot 3.x.
* **Bảo mật:** Spring Security 6, JWT Authentication, Role-based Access Control (`ROLE_STUDENT`, `ROLE_LECTURER`, `ROLE_ADMIN`).
* **Thời gian thực:** Spring WebSocket, STOMP protocol.
* **Cơ sở dữ liệu & Đám mây:**
  * Supabase PostgreSQL (lưu trữ toàn bộ schema nghiệp vụ).
  * Supabase Storage (quản lý file tài liệu bài giảng, tệp nộp bài).
  * Supabase Realtime (đồng bộ dữ liệu không cần polling).
* **Push Notification:** Firebase Admin SDK (FCM Topic & Direct Message).
* **Triển khai & Vận hành:** Docker, Docker Compose, Nginx, Certbot SSL.

---

## 🗄️ 4. Thiết kế Thực thể Dữ liệu (Schema tóm tắt)

* `users`: `id`, `email`, `role`, `full_name`, `avatar_url`, `created_at`.
* `classes`: `id`, `code`, `name`, `semester`, `invite_code`, `lecturer_id`, `status` (`ACTIVE`, `ARCHIVED`).
* `class_members`: `class_id`, `student_id`, `joined_at`.
* `materials`: `id`, `class_id`, `title`, `file_url`, `file_type`, `created_at`.
* `assignments`: `id`, `class_id`, `title`, `description`, `deadline`, `attachment_url`.
* `submissions`: `id`, `assignment_id`, `student_id`, `file_url`, `submitted_at`, `score`, `feedback`.
* `attendance_sessions`: `id`, `class_id`, `wifi_bssid`, `latitude`, `longitude`, `radius_meters`, `status` (`OPEN`, `CLOSED`), `created_at`.
* `attendance_records`: `id`, `session_id`, `student_id`, `device_id`, `verified_bssid`, `status` (`PRESENT`, `ABSENT`), `scanned_at`.

---

## 📁 5. Cấu trúc thư mục nguồn

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

---

## 🚀 6. Hướng dẫn Triển khai & Cài đặt

### 1. Khai báo biến môi trường (`.env` hoặc `application-prod.yml`)
```properties
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

# Supabase PostgreSQL
SPRING_DATASOURCE_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_database_password

# JWT
JWT_SECRET=your_jwt_super_secret_key_at_least_256_bits
JWT_EXPIRATION_MS=86400000

# Firebase FCM
FIREBASE_CONFIG_PATH=/app/config/firebase-service-account.json

# CORS
CORS_ALLOWED_ORIGINS=https://admin.yourdomain.edu.vn,http://localhost:5173
```

### 2. Triển khai bằng Docker Compose
```yaml
version: '3.8'

services:
  backend:
    build: .
    container_name: Uni-Class-backend
    restart: always
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    volumes:
      - ./firebase-service-account.json:/app/config/firebase-service-account.json:ro
```

Chạy dịch vụ:
```bash
docker compose up -d --build
```