FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src

RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Tuned for small (512 MB) containers such as Render's free tier:
#  - MaxRAMPercentage=65 gives the heap ~330 MB while leaving room for metaspace,
#    thread stacks and native/off-heap memory. The JVM default of ~25% (≈128 MB)
#    is too small once the catalog grows and causes OutOfMemory crash loops.
#  - SerialGC has the lowest memory/CPU overhead for a small single-instance heap.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-XX:+UseSerialGC", "-Xss512k", "-jar", "/app/app.jar"]
