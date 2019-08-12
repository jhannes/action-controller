
Running with Docker:

```cmd
docker run -it -v maven-repo:/root/.m2 -v "%cd%":/proj -w /proj maven:3.3.9-jdk-8-alpine mvn test
```

