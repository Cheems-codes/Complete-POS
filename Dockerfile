# ── Stage 1: Compile ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy all Java source files
COPY *.java ./

# Download PostgreSQL JDBC driver and jBCrypt
ADD https://jdbc.postgresql.org/download/postgresql-42.7.3.jar ./postgresql.jar
ADD https://repo1.maven.org/maven2/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar ./jbcrypt.jar

# Compile all Java files
RUN javac -cp ".:postgresql.jar:jbcrypt.jar" *.java

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy compiled classes + drivers from build stage
COPY --from=build /app/*.class ./
COPY --from=build /app/postgresql.jar ./
COPY --from=build /app/jbcrypt.jar ./

# Copy the HTML UI file
COPY pos_system.html ./

# Render.com uses PORT environment variable
EXPOSE 8080

# Start the server
CMD ["java", "-cp", ".:postgresql.jar:jbcrypt.jar", "PosServer"]
