# 1. Aşama: Derleme (Build) için JDK içeren bir imaj seçiyoruz
FROM eclipse-temurin:17-jdk AS build

# Docker içinde UTF-8 desteğini aktif ediyoruz (Türkçe karakterli klasörler için kritik)
ENV LANG C.UTF-8
ENV LC_ALL C.UTF-8

WORKDIR /app

# GitHub'daki tüm dosyaları kopyala
COPY . .

# Klasör isminde Türkçe karakter (ü) olduğu için terminal hatalarını önlemek adına 
# joker karakter (*) kullanarak derleme yapıyoruz.
# Not: Eğer klasör ismini GitHub'da "lib" yaparsanız bu kısım çok daha stabil çalışır.
RUN javac -cp "*/*" SmartBoardServer.java

# 2. Aşama: Çalıştırma (Runtime) için daha hafif bir imaj seçiyoruz
FROM eclipse-temurin:17-jre

WORKDIR /app

# Sadece gerekli dosyaları ilk aşamadan kopyalıyoruz
COPY --from=build /app/*.class .
COPY --from=build /app/kütüphane ./kütüphane

# Render'ın atadığı PORT üzerinden sunucuyu başlatıyoruz
# Port ayarı Java kodunuzda System.getenv("PORT") ile okunmalıdır.
CMD ["java", "-cp", ".:kütüphane/*", "SmartBoardServer"]
