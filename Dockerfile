FROM openjdk:10

# Building
ADD build/libs/ditto-v0.1.0.jar /ditto.jar
ADD clientdata /clientdata
ADD serverdata /serverdata

# Running
CMD ["java","-jar","ditto.jar"]