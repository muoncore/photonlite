FROM java:openjdk-8u45-jdk

MAINTAINER Simplicity Itself

RUN mkdir /applocal

COPY build/libs/backend-1.0.jar /applocal/

WORKDIR /applocal

CMD ["/usr/bin/java", "-Duser.language=en", "-Duser.country=GB", "-Xmx1200m", "-jar", "/applocal/backend-1.0.jar"]
