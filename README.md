# Backend

프리소마 팀 프로젝트의 Spring Boot 백엔드.

## 로컬 실행

Java 21이 필요하다.

```sh
./gradlew bootRun
curl -i http://localhost:8080/health
```

응답은 HTTP 200과 `{"status":"ok"}` 본문이다.

## Docker로 실행

```sh
docker build -t presoma-backend .
docker run --rm -p 8080:8080 presoma-backend
```
