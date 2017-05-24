
all: docker

build:
	./gradlew bootRepackage

clean:
	./gradlew clean
