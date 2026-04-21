# Stage 1: Build file .jar
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
# Copy file cấu hình maven
COPY pom.xml .
# Copy mã nguồn
COPY src ./src
# Build ứng dụng và bỏ qua test để quá trình build nhanh/tránh lỗi môi trường
RUN mvn clean package -DskipTests

# Stage 2: Môi trường chạy thực tế (Chỉ lấy file .jar để tối ưu dung lượng)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Copy file jar từ Stage 1 sang Stage 2
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Expose port mặc định của ứng dụng
EXPOSE 8080

# Chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]