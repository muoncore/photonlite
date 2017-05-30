
.PHONY: build

all: build

build:
	./gradlew build

clean:
	./gradlew clean

test:
	./gradlew check
