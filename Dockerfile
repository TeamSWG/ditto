FROM openjdk:10

# Building
ADD build/libs/ditto.jar /ditto.jar
ADD clientdata /clientdata
ADD serverdata /serverdata

# Running
CMD ["java","-jar","ditto.jar"]