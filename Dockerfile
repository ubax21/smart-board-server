# 1. Aşama: Java kurulumu olan güncel ve stabil bir imaj seçiyoruz (Eclipse Temurin)
FROM eclipse-temurin:17-jdk

# 2. Aşama: Çalışma dizini oluşturuyoruz
WORKDIR /app

# 3. Aşama: GitHub'daki her şeyi sunucuya kopyalıyoruz
COPY . .

# 4. Aşama: Java kodumuzu kütüphanelerle birlikte derliyoruz
# Klasör isminizin "kütüphane" olduğundan emin olun (UTF-8 karakter desteği için)
RUN javac -cp "kütüphane/*" SmartBoardServer.java

# 5. Aşama: Render'ın atadığı PORT üzerinden sunucuyu başlatıyoruz
CMD ["java", "-cp", ".:kütüphane/*", "SmartBoardServer"]
