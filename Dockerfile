FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY target/profile-service-0.0.1-SNAPSHOT.jar .

EXPOSE 8002

# Spring profiles
ENV SPRING_PROFILES_ACTIVE=production,global

# Disable Eureka registration/fetch
#ENV EUREKA_CLIENT_REGISTER_WITH_EUREKA=false
#ENV EUREKA_CLIENT_FETCH_REGISTRY=false


# Disable all metrics to avoid cgroup crash
ENV MANAGEMENT_METRICS_ENABLE_ALL=false
ENV MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info

CMD ["java","-Xms384m","-Xmx384m","-Dspring.main.allow-bean-definition-overriding=true","-jar","profile-service-0.0.1-SNAPSHOT.jar"]
