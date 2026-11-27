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

`postgres` 프로필은 `.env` 또는 환경변수에서 DB 접속 정보를 읽는다. `.env`는 저장소 루트에 두고 예시와 같이 따옴표 없이 `KEY=value`로 작성한다. Flyway가 첫 스키마를 적용하며 이후 변경은 새 마이그레이션으로 관리한다.

## 회원가입·로그인

`postgres` 프로필에서 인증 API를 사용할 수 있다. 로그인용 `AUTH_JWT_SECRET`에는 `openssl rand -hex 32` 등으로 생성한 비밀값을 넣는다. 데이터베이스에는 현재 시행 중인 가입 개인정보 동의 문서(`SIGNUP_PRIVACY`)가 있어야 한다.

| API | 데이터 |
|---|---|
| `GET /api/consent-documents/SIGNUP_PRIVACY` | 현재 시행 중인 가입 동의 문서 |
| `POST /api/auth/signup` | `email`, `password`, `nickname`, `agreedConsentDocumentIds`를 받고 201과 `data.userId` 반환 |
| `POST /api/auth/login` | `email`, `password`를 받고 200과 `data.accessToken`, `data.user` 반환 |
| `GET /api/auth/me` | `Authorization: Bearer <accessToken>`으로 현재 회원의 `id`, `nickname` 확인 |

가입에는 동의 문서 조회 응답의 `id` 하나를 배열로 보낸다. 닉네임은 공백 제거 후 2~20자, 비밀번호는 영문과 숫자를 포함한 8자 이상이며 UTF-8 기준 최대 72바이트다. 로그인 토큰은 1시간 동안 유효하다.
