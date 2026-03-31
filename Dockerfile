# 1. Aşama: Java kurulumu olan bir temel imaj seçiyoruz
FROM openjdk:17-jdk-slim

# 2. Aşama: Çalışma dizini oluşturuyoruz
WORKDIR /app

# 3. Aşama: GitHub'daki her şeyi sunucuya kopyalıyoruz
COPY . .

# 4. Aşama: Java kodumuzu kütüphanelerle birlikte derliyoruz
RUN javac -cp "kütüphane/*" SmartBoardServer.java

# 5. Aşama: Render'ın atadığı PORT üzerinden sunucuyu başlatıyoruz
CMD ["java", "-cp", ".:kütüphane/*", "SmartBoardServer"]
