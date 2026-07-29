# 서비스 간 통신 타임아웃 예제

## 1. 예제 목적

이 저장소(`saas-guide-sample-service-communication`)는 Java 21과 Spring Boot 3.4.7로 만든 두 서비스가 동기 REST 통신을 할 때 요청 Context와 타임아웃을 어디에서 처리하는지 보여 준다.

- `application-service`가 신청을 받고 `eligibility-service`에 자격을 조회한다.
- 애플리케이션 HTTP client의 연결·응답 타임아웃과 Istio의 전체 요청 타임아웃을 비교한다.
- 하위 서비스의 지연이나 오류를 외부에 안전한 업무 오류로 변환하는 과정을 확인한다.

## 2. 확인할 설계 내용

- 필수 `X-Tenant-Id`와 요청 단위 `X-Request-Id`, `X-Trace-Id`를 하위 호출에 전달한다.
- 요청 ID와 Trace ID가 없으면 UUID를 생성하여 응답 헤더와 하위 요청에 사용한다.
- Context는 처리 중 MDC에 등록되고 요청이 끝나면 제거된다.
- `connect-timeout`은 연결 수립 대기 시간을, `read-timeout`은 연결 후 응답 대기 시간을 제한한다.
- 연결·응답 타임아웃, 전송 실패, 하위 5xx는 `503 ELIGIBILITY_SERVICE_UNAVAILABLE`로 일반화한다.
- 예상하지 않은 하위 4xx나 빈 응답은 `502 DOWNSTREAM_RESPONSE_INVALID`로 변환한다.
- 자격이 없으면 `422 ELIGIBILITY_DENIED`를 반환하고, 성공한 신청만 인메모리 저장소에 저장한다.
- 외부 오류 응답에는 내부 URL, 하위 응답 본문, 예외 클래스, 프록시 제품명이나 stack trace를 노출하지 않는다.

`X-Trace-Id`는 헤더 전달을 설명하기 위한 값이다. OpenTelemetry span 생성이나 Trace Backend 연동은 하지 않는다.

## 3. 구성 요소와 처리 흐름

| 구성 요소 | 호스트 실행 포트 | 역할 |
|---|---:|---|
| `application-service` | 8080 | Context 생성·검증, 자격 서비스 호출, 신청 결과 생성, 오류 일반화 |
| `eligibility-service` | 8081 | 내부 자격 조회 API, 교육용 정상·부적격·지연·오류 동작 제공 |

처리 순서는 다음과 같다.

1. 사용자가 `POST /api/applications`에 `applicantId`와 Context 헤더를 보낸다.
2. `application-service`가 Context를 검증하고 누락된 요청 ID와 Trace ID를 생성한다.
3. `GET /internal/eligibilities/{applicantId}`를 호출하면서 세 Context 헤더를 전달한다.
4. `eligibility-service`가 해당 신청자에 설정된 교육용 동작을 수행한다. 별도 설정이 없으면 `ELIGIBLE`이다.
5. 자격이 있으면 신청을 인메모리에 저장하고 `201`과 `ACCEPTED`를 반환한다. 부적격 또는 통신 실패이면 저장하지 않고 일반화된 오류를 반환한다.

인메모리 저장소의 외부 조회 API는 없다. 저장 여부와 tenant 격리는 자동 테스트에서 확인한다.

## 4. 사전 준비

저장소 루트에서 아래 절차를 실행한다. 명령은 Linux 셸과 Windows Git Bash에서 동일하다.

### Maven으로 직접 실행하는 경우

- JDK 21 (`java -version`으로 확인)
- 두 개의 서비스를 동시에 실행할 수 있는 터미널 2개
- API 확인에 사용할 `curl`
- Maven은 별도 설치하지 않아도 저장소의 Maven Wrapper(`./mvnw`)를 사용한다.

### Docker Compose로 실행하는 경우

- Docker Engine 또는 Docker Desktop
- Docker Compose v2 (`docker compose version`으로 확인)
- API 확인에 사용할 `curl`

Postman 검증에는 Postman과 저장소의 Collection 및 Local environment 파일이 필요하다. Istio YAML은 로컬 Compose 실행에 사용하지 않는다.

## 5. 가장 빠른 실행 방법

### 단계 1. Compose 환경 시작과 정상 호출 검증

목적:
두 이미지를 빌드하고 컨테이너를 시작한 뒤, application-service가 준비될 때까지 기다려 실제 서비스 간 정상 호출을 한 번 검증한다. 스크립트가 종료되면 `docker compose down`을 자동 실행하므로 이 방법은 일회성 확인에 적합하다.

명령:

```bash
./scripts/smoke-test.sh
```

명령이 오류 없이 종료되면 health check, 신청 API 호출과 응답의 `ACCEPTED` 확인을 모두 통과한 것이다. 스크립트가 출력하는 Docker 빌드·실행 로그는 정상이다. 이후 Postman까지 실행하려면 다음 명령으로 컨테이너를 계속 실행해 둔다.

```bash
docker compose up --build -d
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8081/actuator/health
```

두 health 응답에서 `"status":"UP"`을 확인한 다음 [7. Postman 검증 절차](#7-postman-검증-절차)를 진행한다.

## 6. 상세 실행 절차

Docker 없이 소스에서 실행하려면 다음 순서를 따른다.

### 단계 1. 전체 코드 빌드와 테스트

목적:
두 모듈을 컴파일하고 모든 자동 테스트 및 패키징 검증을 먼저 수행한다.

명령:

```bash
./mvnw clean verify
```

마지막에 `BUILD SUCCESS`가 표시되어야 한다.

### 단계 2. 자격 검증 서비스 시작

목적:
하위 서비스와 교육용 동작 설정 API를 로컬 8081 포트에 시작한다.

명령(터미널 1):

```bash
./mvnw -pl eligibility-service spring-boot:run
```

다른 터미널에서 준비 상태를 확인한다.

```bash
curl -fsS http://localhost:8081/actuator/health
```

### 단계 3. 신청 서비스 시작

목적:
기본값인 `http://localhost:8081`, 연결 타임아웃 1초, 응답 타임아웃 2초로 상위 서비스를 로컬 8080 포트에 시작한다.

명령(터미널 2):

```bash
./mvnw -pl application-service spring-boot:run
```

별도 터미널에서 준비 상태를 확인한다.

```bash
curl -fsS http://localhost:8080/actuator/health
```

### 단계 4. 기본 정상 호출 확인

목적:
별도 동작을 설정하지 않은 신청자는 기본적으로 적격이며, Context가 포함된 신청이 생성되는지 확인한다.

명령:

```bash
curl -i -X POST http://localhost:8080/api/applications \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-a' \
  -H 'X-Request-Id: request-001' \
  -H 'X-Trace-Id: trace-001' \
  -d '{"applicantId":"APPLICANT-001"}'
```

`HTTP/1.1 201`과 응답 본문의 `"status":"ACCEPTED"`를 확인한다. `X-Request-Id`와 `X-Trace-Id`를 생략하면 서버가 생성한 값을 응답 헤더에서 확인할 수 있다. `X-Tenant-Id`는 생략할 수 없다.

## 7. Postman 검증 절차

### 단계 1. Collection과 환경 가져오기

목적:
저장소에 포함된 실제 요청과 테스트 스크립트, 로컬 URL 및 Context 값을 Postman에 등록한다.

Postman의 **Import**에서 다음 두 파일을 가져온다.

- `postman/service-communication.postman_collection.json`
- `postman/local.postman_environment.json`

환경으로 **Local**을 선택한다. 이 환경은 `application_base_url`, `eligibility_base_url`, `tenant_id`, `request_id`, `trace_id`, `applicant_id`를 제공한다. Collection은 다음 요청을 위해 환경 변수를 자동 저장하거나 변경하지 않으며 모든 폴더가 같은 `applicant_id`를 사용한다.

### 단계 2. 폴더를 순서대로 실행

목적:
각 폴더 안의 요청을 위에서 아래로 실행하여 정상, 업무 오류, 타임아웃, 하위 5xx 변환을 차례로 확인한다. Collection Runner로 전체 Collection을 실행하거나 각 폴더에서 **Run folder**를 사용한다.

1. **`1. 정상 호출 및 Context 전달`**
   - `동작 설정`: 신청자를 `ELIGIBLE`로 설정한다.
   - `민원 신청`: `201`인지 테스트한다.
   - `마지막 수신 Context 확인`: 자격 서비스가 받은 `tenantId`, `requestId`, `traceId`가 Local 환경 값과 같은지 테스트한다.
2. **`2. 업무상 부적격`**
   - `동작 설정`: 같은 신청자를 `INELIGIBLE`로 바꾼다.
   - `민원 신청`: `422`, `ELIGIBILITY_DENIED`, 내부 상세 정보 미노출을 테스트한다.
3. **`3. 애플리케이션 readTimeout`**
   - `동작 설정`: `DELAY`, `delayMillis: 3000`을 설정한다.
   - `민원 신청`: 기본 read timeout 2초가 3초 지연보다 먼저 발생하여 `503`, `ELIGIBILITY_SERVICE_UNAVAILABLE`, 내부 상세 정보 미노출이 되는지 테스트한다.
4. **`4. 하위 서비스 5xx 일반화`**
   - `동작 설정`: 신청자를 `ERROR`로 설정한다.
   - `민원 신청`: 하위 503이 외부의 일반화된 `503 ELIGIBILITY_SERVICE_UNAVAILABLE`로 변환되고 내부 상세가 숨겨지는지 테스트한다.

각 폴더는 반드시 `동작 설정`부터 실행한다. 실패 시나리오의 동작은 인메모리에 남아 다음 수동 호출에도 적용되므로, 전체 실행 후 단계 3에서 삭제한다. Collection 폴더 자체에는 cleanup 요청이 없다.

### 단계 3. 교육용 동작 초기화

목적:
마지막 `ERROR` 설정을 삭제하여 해당 신청자가 기본 `ELIGIBLE` 동작으로 돌아가게 한다.

명령:

```bash
curl -i -X DELETE http://localhost:8081/api/testing/eligibility-behaviors/APPLICANT-001 \
  -H 'X-Tenant-Id: tenant-a' \
  -H 'X-Request-Id: request-001' \
  -H 'X-Trace-Id: trace-001'
```

`HTTP/1.1 204`를 확인한다. Local 환경의 `applicant_id`를 바꿨다면 URL 마지막 값도 동일하게 바꾼다.

## 8. 자동 테스트

### 단계 1. 전체 Maven 검증

목적:
정상·부적격 처리, Context 전달과 ID 생성, tenant 격리, MDC 정리, read timeout, 하위 5xx와 예상하지 않은 4xx 변환, 내부 정보 미노출, 실패 시 미저장, 프로필 설정을 검증한다.

명령:

```bash
./mvnw clean verify
```

### 단계 2. Istio Manifest 정적 검증

목적:
VirtualService의 종류, host, 대상 포트와 2초 timeout이 의도대로 유지되는지 저장소 스크립트로 확인한다. Kubernetes나 Istio 설치 없이 실행할 수 있다.

명령:

```bash
./scripts/validate-manifests.sh
```

### 단계 3. Compose 통합 Smoke Test

목적:
실제 컨테이너 빌드, 서비스 health check, 정상 서비스 간 호출을 검증하고 컨테이너를 자동 정리한다.

명령:

```bash
./scripts/smoke-test.sh
```

## 9. 설정과 환경변수

`application-service/src/main/resources/application.yml`의 기본 설정과 대응 환경변수는 다음과 같다.

| 설정 | 환경변수 | 기본값 | 의미 |
|---|---|---|---|
| `clients.eligibility.base-url` | `ELIGIBILITY_BASE_URL` | `http://localhost:8081` | 자격 서비스 기준 URL |
| `clients.eligibility.connect-timeout` | `ELIGIBILITY_CONNECT_TIMEOUT` | `1s` | 연결 수립 제한 |
| `clients.eligibility.read-timeout` | `ELIGIBILITY_READ_TIMEOUT` | `2s` | 응답 읽기 제한 |

예를 들어 read timeout을 5초로 바꾸어 실행하려면 application-service를 중지한 뒤 다음과 같이 다시 시작한다.

```bash
ELIGIBILITY_READ_TIMEOUT=5s ./mvnw -pl application-service spring-boot:run
```

프로필별 설정은 다음과 같다.

- `application-timeout`: read timeout 2초
- `mesh-timeout`: read timeout 10초. Istio의 2초 전체 요청 제한이 먼저 동작하는 비교용 설정

프로필을 선택하려면 다음과 같이 실행한다. `ELIGIBILITY_READ_TIMEOUT`이 설정되어 있으면 환경변수 값이 우선하므로 제거하고 비교한다.

```bash
SPRING_PROFILES_ACTIVE=application-timeout ./mvnw -pl application-service spring-boot:run
SPRING_PROFILES_ACTIVE=mesh-timeout ./mvnw -pl application-service spring-boot:run
```

두 명령은 동시에 실행하는 명령이 아니다. 실행 중인 application-service를 중지한 후 원하는 프로필 하나로 시작한다.

## 10. YAML·Docker 관련 절차

### Docker Compose

Compose는 두 서비스를 컨테이너 내부 8080 포트로 실행하고, 호스트에는 application-service를 8080, eligibility-service를 8081로 공개한다. application-service의 자격 서비스 URL은 Compose DNS인 `http://eligibility-service:8080`으로 지정된다. 이 구성은 애플리케이션 타임아웃 확인용이며 Service Mesh가 아니다.

### 단계 1. Compose 시작

목적:
두 서비스를 빌드하고 백그라운드에서 실행한다.

명령:

```bash
docker compose up --build -d
docker compose ps
```

### Istio VirtualService

`deploy/istio/eligibility-timeout-virtual-service.yaml`은 `service-communication` Namespace의 `eligibility-service:8080` 요청 전체에 2초 timeout을 지정하는 정책 조각이다. 이 저장소에는 Namespace, Deployment, Service, sidecar 설정이나 Istio 설치 구성이 없으므로 YAML만으로 애플리케이션을 배포할 수 없다.

### 단계 2. Istio 환경에서 정책 적용

목적:
이미 Istio가 설치되어 있고, `service-communication` Namespace와 sidecar가 주입된 두 워크로드 및 `eligibility-service` Service가 준비된 클러스터에만 timeout 정책을 적용한다.

먼저 저장소 자체 검증을 수행한다.

```bash
./scripts/validate-manifests.sh
```

클러스터의 전제 조건을 별도로 준비하고 현재 `kubectl` context가 올바른지 확인한 뒤 적용한다.

```bash
kubectl apply -f deploy/istio/eligibility-timeout-virtual-service.yaml
kubectl -n service-communication get virtualservice eligibility-service
```

Mesh와 애플리케이션 제한을 비교할 때 application-service는 `mesh-timeout` 프로필로 배포해야 한다. 3초 지연을 호출하면 Istio의 2초 제한이 애플리케이션의 10초 read timeout보다 먼저 동작한다. Mesh의 504를 최종 `503` 업무 오류로 일반화하는 책임은 application-service에 남는다.

## 11. 초기화와 정리

### 단계 1. 교육용 상태 초기화

목적:
서비스는 신청자별 동작과 수신 Context를 메모리에 보관한다. 특정 동작만 기본값으로 되돌리려면 실행 중인 eligibility-service에 DELETE를 호출한다.

명령:

```bash
curl -i -X DELETE http://localhost:8081/api/testing/eligibility-behaviors/APPLICANT-001 \
  -H 'X-Tenant-Id: tenant-a' \
  -H 'X-Request-Id: request-001' \
  -H 'X-Trace-Id: trace-001'
```

모든 인메모리 데이터는 서비스를 재시작해도 초기화된다.

### 단계 2. 실행 환경 종료

목적:
Compose 컨테이너와 네트워크를 제거한다. 데이터 volume은 정의되어 있지 않다.

명령:

```bash
docker compose down
```

Maven으로 실행했다면 각 서비스 터미널에서 `Ctrl+C`로 종료한다.

Istio 정책을 적용했다면 다음 명령으로 정책만 제거한다.

```bash
kubectl delete -f deploy/istio/eligibility-timeout-virtual-service.yaml
```

## 12. 구현하지 않은 범위

- 애플리케이션 또는 Mesh 재시도
- Circuit Breaker와 Bulkhead
- 비동기 이벤트 처리
- 실제 인증 서버와 인증된 Tenant Context
- OpenTelemetry 및 Trace Backend
- 실제 Service Mesh 설치와 전체 Kubernetes 배포
- 운영용 장애 주입 API
- 외부 데이터베이스와 신청 조회 API

`/api/testing/**` API는 예제 시나리오 제어 전용이며 운영 환경에 노출할 기능이 아니다.

## 13. 운영 적용 시 추가 고려사항

- 재시도는 호출의 멱등성, 최대 총 대기 시간, 하위 서비스 부하를 고려해 제한한다. 애플리케이션과 Mesh 양쪽의 재시도를 중복 적용하면 총 시도 횟수가 늘어난다.
- 인증된 Tenant Context와 표준 W3C Trace Context를 사용하고 헤더를 신뢰하기 전에 검증한다.
- 연결 pool, 메트릭·로그·분산 추적, 용량 제한, rate limit과 장애 격리를 설계한다.
- 애플리케이션과 Mesh의 타임아웃 예산을 호출 체인의 상위부터 하위까지 일관되게 조정한다.
- 내부 URL, 하위 원문, 프록시 제품명, 예외 클래스와 stack trace가 외부 오류에 포함되지 않도록 유지한다.

## 14. 문제 해결

### `Connection refused` 또는 health check 실패

- `docker compose ps` 또는 두 Spring Boot 터미널에서 서비스가 실행 중인지 확인한다.
- 호스트에서 8080 또는 8081을 이미 사용 중이면 기존 프로세스를 종료한 후 다시 시작한다.
- Maven 직접 실행에서는 eligibility-service를 먼저 시작하고 8081 health가 `UP`인 뒤 application-service를 시작한다.

### 신청 호출이 `400 CONTEXT_INVALID`를 반환함

`X-Tenant-Id`가 없거나 Context 값 형식이 잘못된 경우다. 값은 영문자 또는 숫자로 시작하고 영문자, 숫자, `.`, `_`, `-`만 포함하며 최대 128자여야 한다. `X-Tenant-Id`를 반드시 보내고 Postman에서는 **Local** 환경 선택 여부를 확인한다.

### 정상 호출이 계속 `422` 또는 `503`을 반환함

이전 Postman 실패 시나리오의 `INELIGIBLE`, `DELAY`, `ERROR` 동작이 같은 `applicant_id`에 남아 있는 경우다. [11. 초기화와 정리](#11-초기화와-정리)의 DELETE를 실행하거나 eligibility-service를 재시작한다.

### Postman 요청 URL에 `{{...}}`가 그대로 표시됨

`postman/local.postman_environment.json`을 가져오고 우측 상단 환경에서 **Local**을 선택한다. 환경의 `application_base_url`과 `eligibility_base_url`이 각각 `http://localhost:8080`, `http://localhost:8081`인지 확인한다.

### Docker Compose의 application-service만 자격 서비스에 연결하지 못함

Compose 내부에서는 `localhost:8081`이 아니라 `ELIGIBILITY_BASE_URL=http://eligibility-service:8080`을 사용해야 한다. 저장소의 `docker-compose.yml` 값을 임의로 로컬 URL로 덮어쓰지 않았는지 확인하고 `docker compose logs application-service eligibility-service`로 양쪽 로그를 확인한다.

### Istio timeout이 관찰되지 않음

Compose에는 Istio가 없다. Istio가 설치된 클러스터에서 Namespace, Service, 두 워크로드와 sidecar 주입을 먼저 준비하고 VirtualService를 적용했는지 확인한다. 또한 application-service가 `mesh-timeout` 프로필(앱 read timeout 10초)을 사용해야 Mesh의 2초 제한이 먼저 드러난다.
