
.PHONY: build

all: build

build:
	./gradlew bootRepackage

clean:
	./gradlew clean

test:
	./gradlew check
