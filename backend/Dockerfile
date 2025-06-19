# Use a Java 17 base image
FROM eclipse-temurin:17-jdk

# Copy the built jar into the image
COPY target/backend-0.0.1-SNAPSHOT.jar app.jar

# Expose port 8080
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "/app.jar"]
