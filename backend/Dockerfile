# ─── Etap 1: Runtime ───────────────────────────────────────────────
# Używamy od razu środowiska JRE
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Utwórz użytkownika bez uprawnień roota
RUN addgroup -S bankeurob && adduser -S bankeurob -G bankeurob

# Skopiuj JAR z lokalnego buildu (z pominięciem budowania w kontenerze)
COPY build/libs/bankeurob-backend.jar app.jar

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
