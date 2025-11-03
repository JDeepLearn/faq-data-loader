# ---------- build stage ----------
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -q -DskipTests clean package

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre
# Add non-root user
RUN useradd -r -u 10001 appuser
USER 10001

WORKDIR /work
COPY --from=build /app/target/spring-cb-faq-uploader-*.jar /work/app.jar
COPY --chown=10001:10001 src/main/resources/log4j2.xml /work/log4j2.xml

# Env knobs (override in K8s/CI)
ENV JAVA_OPTS="-Dlog4j.configurationFile=/work/log4j2.xml -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /work/app.jar"]
