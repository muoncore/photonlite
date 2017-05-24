
all: docker

docker: clean build
	docker build -t back-end .

build:
	./gradlew bootRepackage

clean:
	./gradlew clean
