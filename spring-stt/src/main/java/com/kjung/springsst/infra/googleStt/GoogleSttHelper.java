package com.kjung.springsst.infra.googleStt;

import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.kjung.springsst.app.file.util.FileUtil;
import com.kjung.springsst.infra.googleStt.util.SpeechConfigUtil;
import com.kjung.springsst.infra.googleStt.vo.TranscriptionResult;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * Google Cloud Speech-to-Text API 호출을 담당하는 Helper 클래스.
 */
@Slf4j
@Component
public class GoogleSttHelper {

    private final SpeechClient speechClient;

    private final long maxFileSize;

    private final String supportedFormats;

    private final String defaultLanguageCode;

    public GoogleSttHelper(SpeechClient speechClient,
                           @Value("${app.stt.max-file-size-mb:10}") long maxFileSize,
                           @Value("${app.stt.supported-formats:mp3,wav,flac,ogg,m4a}") String supportedFormats,
                           @Value("${app.stt.default-language-code:ko_KR}") String defaultLanguageCode) {
        this.speechClient = speechClient;
        this.maxFileSize = maxFileSize * 1024 * 1024;
        this.supportedFormats = supportedFormats;
        this.defaultLanguageCode = defaultLanguageCode;
    }

    /**
     * 업로드된 오디오 파일에 대해 동기식 음성 인식을 수행합니다.
     * <p>
     * Google Speech-to-Text API를 사용하여 오디오 파일을 텍스트로 변환합니다.
     * 파일의 오디오 형식을 자동으로 감지하고, 실제 샘플 레이트를 추출하여 최적화된 설정으로 인식을 수행합니다.
     * 한국어(ko-KR)를 주 언어로 사용하며, 향상된 모델과 대체 언어 지원이 포함됩니다.
     * </p>
     * <p>
     * <strong>지원 오디오 형식:</strong> WAV, FLAC, MP3, M4A, OGG 등<br>
     * <strong>권장 샘플 레이트:</strong> 16kHz 또는 48kHz<br>
     * <strong>처리 방식:</strong> 동기식 (파일 크기가 클 경우 긴 대기 시간 가능)
     * </p>
     *
     * @param file                       음성 인식할 오디오 파일 (MultipartFile 형식)
     *                                   null이거나 비어있는 파일, 지원하지 않는 형식의 경우 예외 발생
     * @param enableAutomaticPunctuation 자동 구두점 추가 기능 활성화 여부
     *                                   <ul>
     *                                   <li>{@code true}: 마침표, 쉼표, 물음표 등을 자동으로 추가하여 자연스러운 문장 생성</li>
     *                                   <li>{@code false}: 구두점 없는 연속된 텍스트 반환으로 빠른 처리</li>
     *                                   </ul>
     * @param enableWordTimeOffsets      단어별 타임스탬프 정보 포함 여부
     *                                   <ul>
     *                                   <li>{@code true}: 각 단어의 정확한 시작/종료 시간 제공 (자막, 싱크 작업용)</li>
     *                                   <li>{@code false}: 텍스트만 반환하여 처리 속도 향상</li>
     *                                   </ul>
     * @return {@link TranscriptionResult} 음성 인식 결과
     * <ul>
     * <li>인식된 텍스트 (transcript)</li>
     * <li>신뢰도 점수 (confidence)</li>
     * </ul>
     * @throws IllegalArgumentException 파일이 null이거나 비어있는 경우, 지원하지 않는 오디오 형식인 경우
     * @throws RuntimeException         음성 인식 API 호출 실패, 네트워크 오류, 파일 읽기 실패 등의 경우
     */
    public TranscriptionResult recognizeSync(MultipartFile file,
                                             boolean enableAutomaticPunctuation,
                                             boolean enableWordTimeOffsets) {
        // 파일 validation
        validateAudioFile(file);

        try {
            // 오디오 인코딩 결정
            RecognitionConfig.AudioEncoding encoding = determineAudioEncoding(file);

            // 설정 구성
            RecognitionConfig config = SpeechConfigUtil.buildRecognitionConfig(
                    encoding,
                    defaultLanguageCode,
                    enableAutomaticPunctuation,
                    enableWordTimeOffsets
            );

            // 오디오 데이터 설정
            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(ByteString.copyFrom(file.getBytes()))
                    .build();

            return recognizeSyncInternal(config, audio);

        } catch (Exception e) {

            throw new RuntimeException("음성 인식 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 스트리밍 음성 인식 - SSE를 통한 실시간 스트리밍
     *
     * @param file 음성 파일
     * @param enableAutomaticPunctuation 자동 구두점 활성화
     * @param enableWordTimeOffsets 단어 시간 오프셋 활성화
     * @param sseEmitter SSE 스트리밍을 위한 emitter
     */
//    public void streamingRecognizeFile(MultipartFile file,
//                                       boolean enableAutomaticPunctuation,
//                                       boolean enableWordTimeOffsets,
//                                       SseEmitter sseEmitter) {
//        validateAudioFile(file);
//
//        // 별도 스레드에서 비동기 처리
//        CompletableFuture.runAsync(() -> {
//            try {
//                processStreamingRecognition(
//                        file,
//                        enableAutomaticPunctuation,
//                        enableWordTimeOffsets,
//                        sseEmitter);
//
//            } catch (Exception e) {
//                log.error("스트리밍 처리 중 오류 발생", e);
//                sseEmitter.completeWithError(e);
//            }
//        });
//    }


    /**
     * 오디오 파일 유효성 검사
     */
    private void validateAudioFile(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("파일이 비어있습니다.");

        // 파일 크기 검사
        if (file.getSize() > maxFileSize)
            throw new IllegalArgumentException(String.format(
                    "파일 크기가 제한을 초과합니다. (현재: %.2f MB, 최대: %.2f MB)",
                    file.getSize() / 1024.0 / 1024.0,
                    maxFileSize / 1024.0 / 1024.0));

        String filename = file.getOriginalFilename();

        // 지원되는 파일 형식 검사
        validateFileFormat(filename);
    }

    /**
     * 파일 형식 유효성 검사
     */
    private void validateFileFormat(String filename) {
        String extension = FileUtil.getFileExtension(filename).toLowerCase();

        if (extension.isEmpty())
            throw new IllegalArgumentException("파일 확장자가 없습니다.");

        String[] supportedExtensions = supportedFormats.split(",");
        for (String supportedExt : supportedExtensions) {
            if (extension.equals(supportedExt.trim().toLowerCase())) {
                return;
            }
        }

        throw new IllegalArgumentException(
                String.format("지원되지 않는 파일 형식입니다. (입력: %s, 지원 형식: %s)",
                        extension, supportedFormats));
    }

    /**
     * 동기식 음성 인식 (Synchronous Recognition).
     * - 60초 미만의 오디오 파일에 적합
     * - 즉시 결과 반환
     */
    public TranscriptionResult recognizeSyncInternal(RecognitionConfig config, RecognitionAudio audio) {
        try {
            log.debug("Google Speech API 동기식 호출 시작 - 언어: {}, 모델: {}",
                    config.getLanguageCode(), config.getModel());

            // 음성 인식 요청
            RecognizeResponse response = speechClient.recognize(config, audio);
            List<SpeechRecognitionResult> results = response.getResultsList();

            if (results.isEmpty())
                throw new RuntimeException("음성을 인식할 수 없습니다. 오디오 파일을 확인해주세요.");

            // 결과 처리
            return processRecognitionResults(results);

        } catch (Exception e) {
            log.error("Google Speech API 동기식 호출 실패: {}", e.getMessage());
            throw new RuntimeException("음성 인식 API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 실제 스트리밍 인식 처리 로직
     */
//    private void processStreamingRecognition(MultipartFile file,
//                                             boolean enableAutomaticPunctuation,
//                                             boolean enableWordTimeOffsets,
//                                             SseEmitter sseEmitter) {
//        try {
//            // 오디오 인코딩 결정
//            RecognitionConfig.AudioEncoding encoding = determineAudioEncoding(file);
//
//            // 인식 설정 구성
//            RecognitionConfig recConfig = SpeechConfigUtil.buildRecognitionConfig(
//                    encoding,
//                    defaultLanguageCode,
//                    enableAutomaticPunctuation,
//                    enableWordTimeOffsets
//            );
//
//            // 스트리밍 설정 (중간 결과 포함)
//            StreamingRecognitionConfig config = StreamingRecognitionConfig.newBuilder()
//                    .setConfig(recConfig)
//                    .setInterimResults(true)  // 중간 결과도 받기
//                    .setSingleUtterance(false) // 연속 음성 인식
//                    .build();
//
//            // 실시간 스트리밍을 위한 Observer 생성
//            StreamingResponseObserver responseObserver = new StreamingResponseObserver(sseEmitter);
//
//            // BidiStreaming 호출 준비
//            BidiStreamingCallable<StreamingRecognizeRequest, StreamingRecognizeResponse> callable =
//                    speechClient.streamingRecognizeCallable();
//
//            ApiStreamObserver<StreamingRecognizeRequest> requestObserver =
//                    callable.bidiStreamingCall(responseObserver);
//
//            // 첫 번째 요청: 설정만 포함
//            requestObserver.onNext(
//                    StreamingRecognizeRequest.newBuilder()
//                            .setStreamingConfig(config)
//                            .build());
//
//            // 오디오 데이터를 청크 단위로 스트리밍 전송
//            streamAudioData(file, requestObserver);
//
//            // 전송 완료 신호
//            requestObserver.onCompleted();
//
//            log.info("스트리밍 음성 인식 요청 완료 - 파일: {}", file.getOriginalFilename());
//
//        } catch (Exception e) {
//            log.error("스트리밍 음성 인식 처리 중 오류: {}", e.getMessage(), e);
//            throw new RuntimeException("음성 인식 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
//        }
//    }

    /**
     * 오디오 데이터를 청크 단위로 스트리밍 전송
     */
    private void streamAudioData(MultipartFile file,
                                 ApiStreamObserver<StreamingRecognizeRequest> requestObserver) throws Exception {
        byte[] audioData = file.getBytes();
        int chunkSize = 8192; // 8KB 청크

        log.debug("오디오 데이터 스트리밍 시작 - 총 크기: {} bytes, 청크 크기: {} bytes",
                audioData.length, chunkSize);

        // 청크 단위로 나누어 전송
        for (int i = 0; i < audioData.length; i += chunkSize) {
            int end = Math.min(i + chunkSize, audioData.length);
            byte[] chunk = Arrays.copyOfRange(audioData, i, end);

            // 청크 전송
            requestObserver.onNext(
                    StreamingRecognizeRequest.newBuilder()
                            .setAudioContent(ByteString.copyFrom(chunk))
                            .build());

            // 청크 간 약간의 지연 (실제 스트리밍 시뮬레이션)
            try {
                Thread.sleep(50); // 50ms 지연
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("스트리밍 중 인터럽트 발생", e);
            }

            log.trace("오디오 청크 전송 완료: {}/{} bytes", end, audioData.length);
        }

        log.debug("오디오 데이터 스트리밍 완료");
    }


//    /**
//     * MultipartFile로부터 비동기식 음성 인식 수행
//     * - 60초 이상의 긴 오디오 파일에 적합
//     * - 결과를 폴링하여 확인
//     */
//    public Future<TranscriptionResult> recognizeAsync(MultipartFile file, boolean enableAutomaticPunctuation, boolean enableWordTimeOffsets) {
//        try {
//            // 오디오 인코딩 결정
//            RecognitionConfig.AudioEncoding encoding = determineAudioEncoding(file);
//            // AOP에서 로깅하므로 여기서는 제거
//
//            // 인식 설정 구성
//            RecognitionConfig config = SpeechConfigUtil.buildRecognitionConfig(
//                    encoding,
//                    Locale.KOREA.toString(),
//                    enableAutomaticPunctuation,
//                    enableWordTimeOffsets
//            );
//
//            // 오디오 데이터 설정
//            RecognitionAudio audio = RecognitionAudio.newBuilder()
//                    .setContent(ByteString.copyFrom(file.getBytes()))
//                    .build();
//
//            // AOP에서 로깅하므로 여기서는 제거
//
//            return recognizeAsyncInternal(config, audio);
//
//        } catch (Exception e) {
//            // AOP에서 로깅하므로 여기서는 제거
//            throw new RuntimeException("비동기 음성 인식 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
//        }
//    }
//
//    /**
//     * 비동기식 음성 인식 (Asynchronous Recognition)
//     * - 60초 이상의 긴 오디오 파일에 적합
//     * - 결과를 폴링하여 확인
//     */
//    public Future<TranscriptionResult> recognizeAsyncInternal(RecognitionConfig config, RecognitionAudio audio) {
//        try {
//            log.debug("Google Speech API 비동기식 호출 시작 - 언어: {}, 모델: {}",
//                    config.getLanguageCode(), config.getModel());
//
//            // 비동기 음성 인식 요청
//            LongRunningRecognizeResponse response = speechClient.longRunningRecognizeAsync(config, audio).get();
//            List<SpeechRecognitionResult> results = response.getResultsList();
//
//            if (results.isEmpty()) {
//                throw new RuntimeException("음성을 인식할 수 없습니다. 오디오 파일을 확인해주세요.");
//            }
//
//            // CompletableFuture로 결과 반환
//            return java.util.concurrent.CompletableFuture.completedFuture(processRecognitionResults(results));
//
//        } catch (Exception e) {
//            log.error("Google Speech API 비동기식 호출 실패: {}", e.getMessage());
//            throw new RuntimeException("비동기 음성 인식 API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
//        }
//    }

//    /**
//     * 스트리밍 음성 인식 (Streaming Recognition)
//     * - 실시간 오디오 스트림 처리
//     * - WebSocket이나 실시간 처리에 적합
//     */
//    public void recognizeStreaming(StreamObserver<StreamingRecognizeResponse> responseObserver,
//                                   StreamingRecognitionConfig config) {
//        try {
//            log.debug("Google Speech API 스트리밍 호출 시작 - 언어: {}",
//                    config.getConfig().getLanguageCode());
//
//            // 스트리밍 인식 시작
//            StreamObserver<StreamingRecognizeRequest> requestObserver =
//                    speechClient.streamingRecognizeCallable().bidiStreamingCall(responseObserver);
//
//            // 설정 요청 전송
//            StreamingRecognizeRequest configRequest = StreamingRecognizeRequest.newBuilder()
//                    .setStreamingConfig(config)
//                    .build();
//            requestObserver.onNext(configRequest);
//
//            log.debug("스트리밍 음성 인식 설정 완료");
//
//        } catch (Exception e) {
//            log.error("Google Speech API 스트리밍 호출 실패: {}", e.getMessage());
//            responseObserver.onError(e);
//        }
//    }

    /**
     * 음성 인식 결과 처리 (공통 로직)
     */
    private TranscriptionResult processRecognitionResults(List<SpeechRecognitionResult> results) {
        StringBuilder transcription = new StringBuilder();
        float totalConfidence = 0f;
        int resultCount = 0;

        for (SpeechRecognitionResult result : results) {
            if (!result.getAlternativesList().isEmpty()) {
                SpeechRecognitionAlternative alternative = result.getAlternativesList().getFirst();

                // 텍스트 추가
                transcription.append(alternative.getTranscript());

                // 신뢰도 점수 계산
                float confidence = alternative.getConfidence();
                if (confidence > 0) {
                    totalConfidence += confidence;
                    resultCount++;
                }

                log.debug("인식된 텍스트: '{}' (신뢰도: {})",
                        alternative.getTranscript(),
                        confidence > 0 ? confidence : "N/A");
            }
        }

        String finalTranscription = transcription.toString().trim();

        if (finalTranscription.isEmpty())
            throw new RuntimeException("음성 내용을 텍스트로 변환할 수 없습니다.");

        float averageConfidence = resultCount > 0 ? totalConfidence / resultCount : 0f;

        return new TranscriptionResult(finalTranscription, averageConfidence);
    }

    /**
     * 파일 정보에 따른 오디오 인코딩 결정.
     *
     * @param file 대상 파일
     * @return RecognitionConfig.AudioEncoding
     */
    private RecognitionConfig.AudioEncoding determineAudioEncoding(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        log.debug("오디오 인코딩 결정 - ContentType: {}, Filename: {}", contentType, filename);

        // Content-Type 기반 판단
        RecognitionConfig.AudioEncoding encoding = getEncodingByContentType(contentType);

        if (encoding != null) return encoding;

        // 파일 확장자 기반 판단
        if (StringUtils.isNotBlank(filename)) {
            String extension = FileUtil.getFileExtension(filename);

            if (StringUtils.isNotBlank(extension)) return getEncodingByExtension(extension);
        }

        log.warn("오디오 인코딩을 결정할 수 없어 LINEAR16으로 설정합니다. ContentType: {}, Filename: {}", contentType, filename);

        return RecognitionConfig.AudioEncoding.LINEAR16;
    }

    /**
     * Content-Type으로부터 오디오 인코딩 매핑.
     *
     * @param contentType 파일 컨텐츠 타입
     * @return RecognitionConfig.AudioEncoding
     */
    private RecognitionConfig.AudioEncoding getEncodingByContentType(String contentType) {
        if (StringUtils.isBlank(contentType)) return null;

        return switch (contentType.toLowerCase()) {
            case "audio/mpeg", "audio/mp3", "audio/mp4", "audio/m4a" -> RecognitionConfig.AudioEncoding.MP3;
            case "audio/wav", "audio/wave" -> RecognitionConfig.AudioEncoding.LINEAR16;
            case "audio/flac" -> RecognitionConfig.AudioEncoding.FLAC;
            case "audio/ogg" -> RecognitionConfig.AudioEncoding.OGG_OPUS;
            default -> null;
        };
    }

    /**
     * 파일 확장자로부터 오디오 인코딩 매핑
     *
     * @param extension 파일 확장자
     * @return RecognitionConfig.AudioEncoding
     */
    private RecognitionConfig.AudioEncoding getEncodingByExtension(String extension) {
        if (StringUtils.isBlank(extension)) return null;

        return switch (extension.toLowerCase()) {
            case "mp3", "m4a" -> RecognitionConfig.AudioEncoding.MP3;
            case "wav" -> RecognitionConfig.AudioEncoding.LINEAR16;
            case "flac" -> RecognitionConfig.AudioEncoding.FLAC;
            case "ogg" -> RecognitionConfig.AudioEncoding.OGG_OPUS;
            default -> null;
        };
    }

    /**
     * 스트리밍용 RecognitionConfig를 StreamingRecognitionConfig로 변환
     */
    public StreamingRecognitionConfig createStreamingConfig(RecognitionConfig config) {
        return StreamingRecognitionConfig.newBuilder()
                .setConfig(config)
                .setInterimResults(true) // 중간 결과 포함
                .setSingleUtterance(false) // 연속 음성 인식
                .build();
    }
}