FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S osg && adduser -S osg -G osg
COPY target/gateway-service-1.0.0-SNAPSHOT.jar app.jar
USER osg
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]