# ── Stage 1: Compile ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy all Java source files
COPY *.java ./

# Download PostgreSQL JDBC driver
ADD https://jdbc.postgresql.org/download/postgresql-42.7.3.jar ./postgresql.jar

# Compile all Java files
RUN javac -cp ".:postgresql.jar" *.java

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy compiled classes + driver from build stage
COPY --from=build /app/*.class ./
COPY --from=build /app/postgresql.jar ./

# Copy the HTML UI file
COPY pos_system.html ./

# Render.com uses PORT environment variable
EXPOSE 8080

# Start the server
CMD ["java", "-cp", ".:postgresql.jar", "PosServer"]
