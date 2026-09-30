# Image d'exécution : construire d'abord le jar avec "mvn clean package -DskipTests" à la racine
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/api-gateway.jar app.jar
ENV JAVA_OPTS="-Xms64m -Xmx256m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
