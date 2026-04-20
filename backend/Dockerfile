# ─── Etap 1: Build ─────────────────────────────────────────────────
FROM gradle:8.7-jdk21-alpine AS build
WORKDIR /app

# Kopiuj pliki konfiguracyjne Gradle (cache warstw Dockera)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Pobierz zależności (osobna warstwa cache)
RUN gradle dependencies --no-daemon || true

# Kopiuj kod źródłowy i zbuduj
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ─── Etap 2: Runtime ───────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Utwórz użytkownika bez uprawnień roota
RUN addgroup -S bankeurob && adduser -S bankeurob -G bankeurob

# Skopiuj JAR z etapu build
COPY --from=build /app/build/libs/*.jar app.jar

# Zmień właściciela pliku
RUN chown bankeurob:bankeurob app.jar

USER bankeurob

EXPOSE 8080

# Uruchom z profilem produkcyjnym i optymalizacjami JVM
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
