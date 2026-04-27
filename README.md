# React + Spring Boot STT Study

Google Cloud Speech-to-Text API를 활용한 음성 인식(STT) 학습 프로젝트입니다.
동기, 비동기, SSE 스트리밍, WebSocket 스트리밍 등 다양한 방식의 STT 구현을 비교합니다.

## 기술 스택

### Frontend (`react-stt/`)
- React 19, TypeScript
- Vite 7
- React Router DOM 7

### Backend (`spring-stt/`)
- Spring Boot 3.5.3, Java 21
- Google Cloud Speech-to-Text API
- Spring WebSocket, Spring WebFlux (SSE)
- Springdoc OpenAPI (Swagger)
- Lombok

## 프로젝트 구조

```
react-spring-stt/
├── react-stt/          # React 프론트엔드
│   ├── src/
│   │   └── pages/stt/
│   │       ├── SttList.tsx         # STT 방식 선택 목록
│   │       ├── SttSync.tsx         # 동기 방식
│   │       ├── SttAsync.tsx        # 비동기 방식
│   │       ├── SttStreaming.tsx     # SSE 스트리밍 방식
│   │       └── SttStreaming_2.tsx   # WebSocket 실시간 스트리밍 방식
│   └── public/
│       └── linear16-processor.js   # AudioWorklet: PCM Linear16 변환
└── spring-stt/         # Spring Boot 백엔드
    └── src/main/java/com/kjung/springsst/
        ├── app/speech/
        │   ├── controller/
        │   │   ├── SttController.java            # REST API 엔드포인트
        │   │   └── SpeechWebSocketHandler.java   # WebSocket 핸들러
        │   └── service/SttService.java
        └── infra/googleStt/
            ├── GoogleSTTService.java             # Google STT 서비스
            ├── GoogleSttHelper.java
            ├── StreamingRecognizeClient.java
            └── StreamingResponseObserver.java
```

## STT 구현 방식

| 방식 | 경로 | 설명 |
|------|------|------|
| **동기** | `/stt/sync` | 오디오 파일 업로드 후 결과 반환 |
| **비동기** | `/stt/async` | 오디오 파일 업로드 후 비동기 처리 |
| **SSE 스트리밍** | `/stt/streaming` | 파일 업로드 후 Server-Sent Events로 결과 스트리밍 |
| **WebSocket 스트리밍** | `/stt/streaming2` | 마이크 실시간 입력 → WebSocket → Google STT 실시간 변환 |

### WebSocket 스트리밍 흐름 (SttStreaming_2)

```
브라우저 마이크
  → AudioWorklet (Linear16 PCM 변환, 16000Hz, 1ch)
  → WebSocket (ws://localhost:8099/ws/speech)
  → Google Cloud STT Streaming API
  → 결과 JSON { transcript, isFinal } → 브라우저 화면 출력
```

- `isFinal: false` → 회색 잠정 텍스트로 표시
- `isFinal: true` → 확정 텍스트로 누적

## API 엔드포인트

| Method | URL | 설명 |
|--------|-----|------|
| `POST` | `/api/v1/speech/convert` | 동기/비동기 STT 변환 (multipart) |
| `POST` | `/api/v1/speech/stream` | SSE 스트리밍 STT (multipart, text/event-stream) |
| `WS` | `/ws/speech` | WebSocket 실시간 STT |

Swagger UI: `http://localhost:8099/swagger-ui/index.html`

## 실행 방법

### 사전 요구사항
- Java 21
- Node.js 18+
- Google Cloud 서비스 계정 키 (`stt.json`)

### 1. Google Cloud 인증 설정

Google Cloud 콘솔에서 Speech-to-Text API를 활성화하고 서비스 계정 키를 발급받습니다.

```bash
# 환경 변수로 설정 (권장)
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/stt.json
```

또는 `application.yml`의 주석을 해제하여 classpath 방식으로 설정합니다.

### 2. 백엔드 실행

```bash
cd spring-stt
./gradlew bootRun
# http://localhost:8099
```

### 3. 프론트엔드 실행

```bash
cd react-stt
npm install
npm run dev
# http://localhost:5173
```

## 주요 설정 (`application.yml`)

```yaml
server:
  port: 8099

app:
  stt:
    supported-formats: mp3,wav,flac,ogg,m4a
    max-duration-seconds: 600
    default-language-code: ko_KR
```
