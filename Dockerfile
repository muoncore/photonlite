FROM java:openjdk-8u45-jdk

MAINTAINER Simplicity Itself

RUN mkdir /applocal

COPY build/libs/photon-lite-1.0.jar /applocal/

WORKDIR /applocal

CMD ["/usr/bin/java", "-Duser.language=en", "-Duser.country=GB", "-Xmx700m", "-jar", "/applocal/photon-lite-1.0.jar"]
