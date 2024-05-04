FROM ubuntu:20.04

RUN apt-get update && \
    apt-get install -y openjdk-21-jdk && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . /app

RUN mvn package -DskipTests

EXPOSE 7777

CMD [ "java", "-jar", "--enable-preview", "target/chadow-1.0.0.jar", "--server" ]