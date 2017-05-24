
.PHONY: build

all: docker

build:
	./gradlew bootRepackage

clean:
	./gradlew clean
