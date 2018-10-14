FROM openjdk:10

# 1. Transfer Gradle files
ADD gradle /gradle
ADD gradlew /gradlew
ADD build.gradle /build.gradle
ADD settings.gradle /settings.gradle

# 2. Transfer source
ADD client-holocore /client-holocore
ADD pswgcommon /pswgcommon
ADD src /src
ADD clientdata /clientdata
ADD serverdata /serverdata

# 3. Construct JAR using the Gradle wrapper
RUN ./gradlew clean build

# Running
CMD ["java","-jar","build/libs/ditto.jar.jar"]