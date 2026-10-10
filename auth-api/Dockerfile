# 1. Construir el .jar con Maven (el contexto de build es la raíz del repo)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY . .
RUN chmod +x mvnw
RUN ./mvnw -pl auth-api -am clean package -DskipTests -B

# 2. Imagen final, liviana, solo con el .jar ya construido
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/auth-api/target/auth-api-*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-Xmx400m", "-jar", "app.jar"]