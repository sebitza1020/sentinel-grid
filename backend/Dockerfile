## STAGE 1: Build - Compilăm aplicația folosind Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copiem fișierele de configurare pentru a cache-ui dependențele
COPY pom.xml .
# (Opțional) Dacă ai settings.xml, îl copiezi și pe el
RUN mvn dependency:go-offline

# Copiem sursele
COPY src ./src

# Compilăm aplicația (fără teste pentru viteză la deploy)
RUN mvn package -DskipTests

## STAGE 2: Run - Imaginea finală, mică și rapidă
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiem doar ce avem nevoie din Stage 1
COPY --from=build /app/target/quarkus-app/lib/ /app/lib/
COPY --from=build /app/target/quarkus-app/*.jar /app/
COPY --from=build /app/target/quarkus-app/app/ /app/app/
COPY --from=build /app/target/quarkus-app/quarkus/ /app/quarkus/

# Expunem portul 8080
EXPOSE 8080

# Definim variabile de mediu implicite (pot fi suprascrise la rulare)
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

# Comanda de start
CMD ["java", "-jar", "quarkus-run.jar"]