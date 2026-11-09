# ==========================================
# STAGE 1: Build file JAR với Maven & JDK 17
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Tải trước dependencies để cache layer (giúp build lại trên VPS cực nhanh)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code và đóng gói
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# STAGE 2: Chạy ứng dụng với JRE 17 Alpine
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Cài đặt tzdata và đặt múi giờ Việt Nam (quan trọng cho hạn OTP 5 phút)
RUN apk add --no-cache tzdata
ENV TZ=Asia/Ho_Chi_Minh

# Tạo user non-root để chạy ứng dụng an toàn và cấp quyền thư mục lưu trữ uploads
RUN addgroup -S edulens && adduser -S edulens -G edulens \
    && mkdir -p /app/uploads \
    && chown -R edulens:edulens /app

# Copy file edulens-backend-1.0.0-SNAPSHOT.jar từ Stage 1
COPY --chown=edulens:edulens --from=build /app/target/*.jar app.jar

USER edulens:edulens

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
