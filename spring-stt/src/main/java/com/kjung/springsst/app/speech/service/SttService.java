package com.kjung.springsst.app.speech.service;

import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.kjung.springsst.app.speech.dto.SttRequest;
import com.kjung.springsst.app.speech.dto.SttResponse;
import com.kjung.springsst.infra.googleStt.GoogleSttHelper;
import com.kjung.springsst.infra.googleStt.vo.TranscriptionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttService {

    private final GoogleSttHelper googleSttHelper;

    private final SpeechClient speechClient;

    public SpeechClient getSpeechClient() {
        return speechClient;
    }

    /**
     * 동기식 음성 인식 수행 (Google Cloud Speech-to-Text).
     */
    public SttResponse convertSpeechToText(SttRequest sttRequest) {

        long startTime = System.currentTimeMillis();

        TranscriptionResult transcriptionResult =
                googleSttHelper.recognizeSync(
                        sttRequest.getFile(),
                        sttRequest.isEnableAutomaticPunctuation(),
                        sttRequest.isEnableWordTimeOffsets()
                );

        long processingTime = System.currentTimeMillis() - startTime;

        return SttResponse.createSuccessResponse(sttRequest, transcriptionResult, processingTime);

    }

    public Flux<String> streamSpeechToText(MultipartFile audio) {
        return Flux.create(sink -> {
            try {
                ByteString audioData = ByteString.copyFrom(audio.getBytes());

                // 스트리밍 설정
                StreamingRecognitionConfig config = StreamingRecognitionConfig.newBuilder()
                        .setConfig(RecognitionConfig.newBuilder()
//                                .setEncoding(RecognitionConfig.AudioEncoding.WEBM_OPUS)
                                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                .setSampleRateHertz(16000)
                                .setLanguageCode("ko-KR")
                                .setEnableAutomaticPunctuation(false)
                                .build())
                        .setInterimResults(false) // 중간 결과도 반환
                        .build();

                BidiStreamingCallable<StreamingRecognizeRequest, StreamingRecognizeResponse> callable =
                        speechClient.streamingRecognizeCallable();

                CountDownLatch finishLatch = new CountDownLatch(1);

                ApiStreamObserver<StreamingRecognizeResponse> responseObserver =
                        new ApiStreamObserver<StreamingRecognizeResponse>() {
                            @Override
                            public void onNext(StreamingRecognizeResponse response) {
                                for (StreamingRecognitionResult result : response.getResultsList()) {
                                    SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                                    sink.next(alternative.getTranscript());

                                    if (result.getIsFinal()) {
                                        sink.complete();
                                        finishLatch.countDown();
                                    }
                                }
                            }

                            @Override
                            public void onError(Throwable t) {
                                sink.error(t);
                                finishLatch.countDown();
                            }

                            @Override
                            public void onCompleted() {
                                sink.complete();
                                finishLatch.countDown();
                            }
                        };

                ApiStreamObserver<StreamingRecognizeRequest> requestObserver =
                        callable.bidiStreamingCall(responseObserver);

                // 첫 번째 요청 - 설정
                StreamingRecognizeRequest.Builder requestBuilder = StreamingRecognizeRequest.newBuilder()
                        .setStreamingConfig(config);
                requestObserver.onNext(requestBuilder.build());

                // 두 번째 요청 - 오디오 데이터
                requestBuilder.clearStreamingConfig();
                requestBuilder.setAudioContent(audioData);
                requestObserver.onNext(requestBuilder.build());

                requestObserver.onCompleted();

                try {
                    finishLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    sink.error(e);
                }
            } catch (Exception e) {
                log.error("streaming error", e);
                sink.error(e);
            }
        });
    }

}