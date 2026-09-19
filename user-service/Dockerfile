FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S osg && adduser -S osg -G osg \
    && mkdir -p /tmp/osgateway-uploads /app/uploads \
    && chown -R osg:osg /app /tmp/osgateway-uploads
COPY target/user-service-1.0.0-SNAPSHOT.jar app.jar
USER osg
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]
