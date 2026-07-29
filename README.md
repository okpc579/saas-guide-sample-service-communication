# 서비스 간 통신 타임아웃 예제

## 1. 예제 목적

Java 21과 Spring Boot 3.4.x로 신청 서비스가 자격 검증 서비스를 동기 REST 호출하는 최소 예제다. 애플리케이션 HTTP client의 연결·응답 timeout과 Service Mesh의 전체 요청 timeout이 적용되는 위치와 책임을 비교한다.

## 2. 서비스 구성

| 모듈 | 로컬 포트 | 역할 |
|---|---:|---|
| `application-service` | 8080 | Context 구성, 자격 호출, 결과 반영, 오류 일반화 |
| `eligibility-service` | 8081 | 내부 자격 GET API와 교육용 정상·부적격·지연·오류 시나리오 |

신청은 자격 검증이 성공하고 `eligible=true`일 때만 인메모리 저장소에 저장된다. 저장소는 실패 시 업무 처리가 완료되지 않음을 자동 테스트로 확인하기 위한 최소 구현이며 외부 조회 API는 제공하지 않는다.

## 3. 서비스 간 REST 호출과 Context 전달

`POST /api/applications`는 `RequestContextFilter`에서 `ServiceRequestContext`를 만든 뒤 `EligibilityClient`가 `GET /internal/eligibilities/{applicantId}`를 호출한다. `X-Tenant-Id`는 필수다. `X-Request-Id`와 `X-Trace-Id`가 없으면 UUID를 생성한다. 세 값은 하위 호출에 전달되고 처리 중 MDC에 등록되며 `finally`에서 제거된다.

`X-Trace-Id`는 헤더 전달 교육용 값이며 OpenTelemetry span이나 실제 Trace Backend 조회를 구현하지 않는다.

## 4. 애플리케이션 레벨 타임아웃

`RestClientConfiguration`은 외부화된 `clients.eligibility.connect-timeout`과 `read-timeout`을 적용한다.

- `connectTimeout`: 하위 서비스와 연결을 수립하는 대기 상한
- `readTimeout`: 연결 후 응답 데이터를 기다리는 대기 상한

연결 timeout, read timeout과 그 밖의 전송 실패는 외부에 원인을 노출하지 않고 `503 ELIGIBILITY_SERVICE_UNAVAILABLE`로 변환한다. 하위 서비스의 5xx도 모두 같은 오류로 변환한다. 예상하지 않은 하위 4xx는 일시적 장애로 오인하지 않고 `502 DOWNSTREAM_RESPONSE_INVALID`로 변환한다.

## 5. Service Mesh 레벨 타임아웃

`deploy/istio/eligibility-timeout-virtual-service.yaml`의 `VirtualService.timeout: 2s`는 Mesh data plane에서 eligibility-service 요청 전체의 상한을 설정한다. Mesh가 반환한 504를 업무 오류로 해석하고 일반화된 503으로 바꾸는 책임은 여전히 애플리케이션에 있다.

이 YAML은 이미 Istio가 설치되고 sidecar 주입 및 서비스 DNS가 구성된 환경에 적용하는 정책 조각이다. 이 저장소는 Namespace, Deployment, Service 또는 전체 Kubernetes 배포를 제공하지 않는다. Mesh 비교 시 `mesh-timeout` 프로필의 앱 read timeout은 10초이므로 Mesh 2초 제한이 먼저 동작한다.

## 6. 적용 위치 비교

| 비교 항목 | 애플리케이션 레벨 | Service Mesh 레벨 |
|---|---|---|
| 정책 적용 위치 | `RestClient` request factory | `VirtualService`와 data plane |
| 값 변경 방식 | 외부 설정 변경 후 적용 방식에 따라 재시작 또는 재배포 | Mesh 정책 변경 |
| 호출별 세부 설정 | client/호출별 connect·read 값 구성 가능 | 서비스·route 단위 전체 요청 정책 |
| 여러 서비스 공통 적용 | 각 애플리케이션 설정 필요 | 중앙 정책으로 공통 적용 가능 |
| 오류 인식과 변환 책임 | client 예외와 HTTP 상태를 인식하고 업무 오류로 변환 | timeout 응답 생성; 최종 업무 오류 변환은 애플리케이션 책임 |
| 적용 전제 | 애플리케이션과 HTTP client | Service Mesh 설치와 traffic interception |

둘을 함께 적용하면 더 짧은 제한이 먼저 동작한다. 비교 목적의 `application-timeout` 프로필은 앱 read timeout 2초를, `mesh-timeout` 프로필은 앱 read timeout 10초를 사용한다.

## 7. 실행 방법

```bash
./mvnw clean verify
./mvnw -pl eligibility-service spring-boot:run
./mvnw -pl application-service spring-boot:run
```

Docker 사용 시:

```bash
./mvnw package
docker compose up --build
```

Compose는 애플리케이션 timeout을 확인하는 로컬 실행 환경이며 Service Mesh가 아니다.

## 8. Postman 검증 시나리오

Local environment를 선택하고 다음 네 폴더를 사용한다.

1. 정상 호출 및 마지막 수신 Context 확인
2. 업무상 부적격과 422 확인
3. 3초 지연에 대한 애플리케이션 read timeout 및 일반화된 503 확인
4. 하위 서비스의 5xx가 일반화된 503으로 변환되는지 확인

교육용 동작 설정은 다음과 같다.

```json
{"scenario":"DELAY","delayMillis":3000}
```

실제 Mesh timeout은 Istio 환경이 있는 경우에만 선택적으로 확인한다.

## 9. 자동 테스트 항목

정상·부적격 처리, Context 전달과 ID 생성, tenant 요청 격리, MDC 정리, read timeout, 하위 서비스의 5xx와 예상하지 않은 하위 4xx 변환, 오류 응답의 내부 URL·하위 본문·예외 클래스·stack trace 미노출, 실패 시 미저장, 두 timeout 프로필 및 VirtualService 2초 설정을 검증한다.

## 10. 구현하지 않은 범위

- 애플리케이션 재시도와 Mesh 재시도
- Circuit Breaker와 Bulkhead
- 비동기 이벤트
- 실제 인증 서버
- OpenTelemetry와 Trace Backend
- 실제 Service Mesh 설치
- 전체 Kubernetes 배포
- 운영용 장애 주입 기능

## 11. 운영 적용 시 추가 고려사항

일시적 오류에 대한 재시도는 호출의 멱등성, 최대 대기 시간, 하위 서비스 부하와 중복 적용 가능성을 고려하여 제한적으로 적용한다. 애플리케이션과 Service Mesh 양쪽에 재시도를 적용하면 총 시도 횟수가 증가할 수 있으므로 정책을 조정해야 한다.

운영에서는 인증된 Tenant Context, 표준 W3C Trace Context, 연결 pool, 관측성, 용량 제한과 장애 격리도 함께 설계해야 한다. 외부 오류 응답에는 내부 URL, 하위 원문, 프록시 제품명, 예외 클래스와 stack trace를 포함하지 않는다.
