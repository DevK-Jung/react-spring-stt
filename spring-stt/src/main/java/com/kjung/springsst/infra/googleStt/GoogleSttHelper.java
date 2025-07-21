package com.kjung.springsst.infra.googleStt;

import com.google.cloud.speech.v1.*;
import com.kjung.springsst.infra.googleStt.vo.TranscriptionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Future;

/**
 * Google Cloud Speech-to-Text API 호출을 담당하는 Helper 클래스
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleSttHelper {

    private final SpeechClient speechClient;

    /**
     * 동기식 음성 인식 (Synchronous Recognition).
     * - 60초 미만의 오디오 파일에 적합
     * - 즉시 결과 반환
     */
    public TranscriptionResult recognizeSync(RecognitionConfig config, RecognitionAudio audio) {
        try {
            log.debug("Google Speech API 동기식 호출 시작 - 언어: {}, 모델: {}",
                    config.getLanguageCode(), config.getModel());

            // 음성 인식 요청
            RecognizeResponse response = speechClient.recognize(config, audio);
            List<SpeechRecognitionResult> results = response.getResultsList();

            if (results.isEmpty()) throw new RuntimeException("음성을 인식할 수 없습니다. 오디오 파일을 확인해주세요.");

            // 결과 처리
            return processRecognitionResults(results);

        } catch (Exception e) {
            log.error("Google Speech API 동기식 호출 실패: {}", e.getMessage());
            throw new RuntimeException("음성 인식 API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 비동기식 음성 인식 (Asynchronous Recognition)
     * - 60초 이상의 긴 오디오 파일에 적합
     * - 결과를 폴링하여 확인
     */
    public Future<TranscriptionResult> recognizeAsync(RecognitionConfig config, RecognitionAudio audio) {
        try {
            log.debug("Google Speech API 비동기식 호출 시작 - 언어: {}, 모델: {}",
                    config.getLanguageCode(), config.getModel());

            // 비동기 음성 인식 요청
            LongRunningRecognizeResponse response = speechClient.longRunningRecognizeAsync(config, audio).get();
            List<SpeechRecognitionResult> results = response.getResultsList();

            if (results.isEmpty()) {
                throw new RuntimeException("음성을 인식할 수 없습니다. 오디오 파일을 확인해주세요.");
            }

            // CompletableFuture로 결과 반환
            return java.util.concurrent.CompletableFuture.completedFuture(processRecognitionResults(results));

        } catch (Exception e) {
            log.error("Google Speech API 비동기식 호출 실패: {}", e.getMessage());
            throw new RuntimeException("비동기 음성 인식 API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

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
                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);

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
        if (finalTranscription.isEmpty()) {
            throw new RuntimeException("음성 내용을 텍스트로 변환할 수 없습니다.");
        }

        float averageConfidence = resultCount > 0 ? totalConfidence / resultCount : 0f;

        log.info("음성 인식 완료 - 텍스트 길이: {}, 평균 신뢰도: {}",
                finalTranscription.length(), averageConfidence);

        return new TranscriptionResult(finalTranscription, averageConfidence);
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