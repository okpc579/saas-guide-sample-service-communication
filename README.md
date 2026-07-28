# 서비스 간 통신 타임아웃 예제 (가이드 4.1.8)

민원 신청 서비스가 신청 저장 전에 자격 검증 서비스를 동기 `GET`으로 호출하는 독립 실행 예제다. Java 21, Spring Boot 3.4.7, MVC `RestClient`, Spring Retry를 사용하며 DB 없이 인메모리로 동작한다.

## 구성과 흐름

| 모듈 | 포트 | 역할 |
|---|---:|---|
| `application-service` | 8080 | Context 검증, 자격 호출, 제한적 재시도, 신청 저장, 공통 오류 변환 |
| `eligibility-service` | 8081 | 내부 자격 API와 **교육/demo-only** 장애 및 수신 기록 API |

정상 흐름은 `POST /api/applications` → `RequestContextFilter` → `EligibilityRetryClient` → `GET /internal/eligibilities/{applicantId}` → 적격 확인 → 인메모리 저장 → 201이다. 부적격은 정상 HTTP 결과의 `eligible=false`이며 422로 변환되고 저장되지 않는다. 지연·연결 실패·502/503/504는 최대 대기 상한과 제한적 재시도 후 내부 상세를 숨긴 503으로 변환한다.

## Request Context

`X-Tenant-Id`는 필수이며 `[A-Za-z0-9][A-Za-z0-9._-]{0,127}` 형식만 확인한다. Request/Trace ID가 없으면 UUID를 만들고 응답과 하위 호출에 같은 값을 쓴다. 세 값은 request attribute로 명시 전달하고 처리 동안만 MDC에 넣은 뒤 `finally`에서 제거한다. 이 Tenant 검사는 교육용이다. 운영에서는 클라이언트 헤더를 신뢰하지 말고 인증 토큰 또는 신뢰 가능한 게이트웨이 Context와 대조해야 한다. `X-Trace-Id`도 표준 분산 추적이 아니다. 운영에서는 W3C Trace Context, `traceparent`, OpenTelemetry와 실제 Trace Backend를 검토한다.

## 타임아웃과 재시도

`clients.eligibility` 설정이 base URL, connect/read timeout, max attempts, retry delay를 관리한다. connect timeout은 연결 수립 상한, read timeout은 연결 뒤 응답 대기 상한이다. 재시도는 멱등한 자격 **GET**의 연결 오류, read timeout, 502/503/504에만 적용한다. `max-attempts=2`는 최초 1회 + 재시도 1회다. 부적격, 4xx, 검증 오류, 신청 생성에는 재시도하지 않는다.

| 구분 | 애플리케이션 레벨 | Service Mesh 레벨 |
|---|---|---|
| 주체 | HTTP Client가 연결·응답 상한 관리 | 프록시가 전체 요청 상한 관리 |
| 범위 | 호출별 세부 설정 | 서비스·경로 중앙 정책 |
| 오류 인식 | 예외 유형을 직접 인식 | 애플리케이션에는 HTTP 504 등 전달 |
| 변경 | 외부 설정이나 코드; 보통 재시작/재배포 가능성 | 애플리케이션 코드 변경 불필요 |
| 책임 | 업무 오류로 변환 | 반환 오류의 업무 해석은 애플리케이션 책임 |

둘을 반드시 함께 적용할 필요는 없다. 함께 쓰면 짧은 제한이 먼저 동작하도록 값을 조정한다. `application-timeout`은 read 2초/max 2, `mesh-timeout`은 앱의 최종 안전 상한을 10초/max 1로 두고 VirtualService의 전체 요청 2초가 먼저 동작하게 한다. Mesh 504도 일반화된 503 `ELIGIBILITY_SERVICE_UNAVAILABLE`로 바뀌며 제품명, URL, 프록시 메시지, 예외 클래스는 노출하지 않는다.

## 실행

```bash
./mvnw clean verify
./mvnw -pl eligibility-service spring-boot:run
./mvnw -pl application-service spring-boot:run
```

Docker가 있으면 `./mvnw package && docker compose up --build` 또는 `scripts/smoke-test.sh`를 사용한다. Compose는 애플리케이션 타임아웃용이며 Mesh가 아니다.

Istio가 설치된 환경에서만 이미지의 `replace-me`를 교체하고 `kubectl apply -f deploy/istio/`를 적용한다. 이 저장소는 Istio 설치나 실제 Mesh 검증을 제공하지 않는다. 정책 YAML 구조, 504 변환, 프로필 로딩만 일반 테스트 대상이다.

Postman에서는 Local environment를 고르고 폴더 1~5를 순서대로 실행한다. 6번은 실제 Istio 전용이다. 장애 설정 API는 `PUT/DELETE /api/testing/eligibility-behaviors/{id}`, 기록은 `GET /api/testing/received-contexts/{id}`이며 모두 교육/demo-only 기능이지 운영 장애 주입 도구가 아니다.

## 제한사항

문자/알림, 비동기 이벤트, Kafka/RabbitMQ, gRPC, DB, 인증/JWT, OpenTelemetry, Service Mesh 설치, Mesh 재시도, 비멱등 재시도, Circuit Breaker, Bulkhead는 제외한다. 타임아웃은 개별 호출 대기만 제한하며 모든 장애 전파를 해결하지 않는다. 운영에서는 동시 요청 제한, Circuit Breaker, 격리, 용량 설계와 재시도 폭증 방지를 함께 검토해야 한다.
