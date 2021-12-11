FROM gradle:7.3.1

COPY build.gradle.kts gradle.properties ./

# Cache deps
RUN gradle build

COPY . .

RUN gradle build

CMD ["java", "-jar", "build/libs/app-all.jar", "-Xmx800m"]
