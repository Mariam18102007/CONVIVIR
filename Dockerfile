FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV DB_PATH=/app/data/propiedad_horizontal.db
ENV UPLOADS_DIR=/app/data/uploads

RUN mkdir -p /app/data/uploads

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
