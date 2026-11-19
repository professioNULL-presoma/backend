# 원룸 계약서 분석 백엔드

프리소마 팀 프로젝트의 Spring Boot 백엔드.

## 첫 실행 및 빌드

Java 21과 저장소에 포함된 Gradle Wrapper를 사용한다. 첫 배포 확인용으로는 DB 없이 실행할 수 있다.

```sh
./gradlew bootRun
curl -i http://localhost:8080/health
```

응답은 HTTP 200과 `{"status":"ok"}` 본문이다.

빌드는 `./gradlew build`로 확인한다. 실행 가능한 Jar는 `build/libs/`에 생성된다.

## 첫 수동 배포

Docker가 설치된 서버에서 저장소 루트를 열고 실행한다.

```sh
docker build -t presoma-backend .
docker run -d --name presoma-backend -p 8080:8080 presoma-backend
curl -i http://localhost:8080/health
```

외부에서 배포를 확인할 때는 브라우저에서 `http://<서버 IP>:8080/health`를 연다. `/health`는 서버 프로세스의 응답을 확인하는 경로다.

## 로컬 PostgreSQL 개발

Docker Compose가 필요하다. 저장소 루트에서 예시 파일을 복사하고 `.env`의 `DB_PASSWORD`에 로컬 전용 비밀번호를 입력한다. `.env`는 Git에서 제외된다.

```sh
cp .env.example .env
docker compose up -d --wait
./gradlew bootRun --args='--spring.profiles.active=postgres'
```

로컬 DB는 기본 `127.0.0.1:15432`에서 접근할 수 있다. 포트가 겹치면 `.env`의 `DB_PORT`를 바꾼다. DB를 중지하려면 `docker compose down`을 실행한다. 데이터는 Docker 볼륨에 유지된다.

`postgres` 프로필은 `.env` 또는 환경변수에서 DB 접속 정보를 읽는다. `.env`는 저장소 루트에 두고 예시와 같이 따옴표 없이 `KEY=value`로 작성한다. ERD가 확정되면 첫 Flyway SQL을 `src/main/resources/db/migration/V1__설명.sql`로 추가한다. 이후 스키마 변경은 새 마이그레이션으로 관리한다.
