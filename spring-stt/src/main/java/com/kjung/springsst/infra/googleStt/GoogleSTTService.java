package com.kjung.springsst.infra.googleStt;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleSTTService {

    private final SpeechClient speechClient;

    public StreamingRecognizeClient createStreamingClient(Consumer<Map<String, Object>> resultConsumer) {
        return new StreamingRecognizeClient(speechClient, resultConsumer);
    }

    public static class StreamingRecognizeClient {
        private final ClientStream<StreamingRecognizeRequest> clientStream;
        private final Consumer<Map<String, Object>> resultConsumer;
        private boolean isFirstRequest = true;

        public StreamingRecognizeClient(SpeechClient speechClient, Consumer<Map<String, Object>> resultConsumer) {
            this.resultConsumer = resultConsumer;

            ResponseObserver<StreamingRecognizeResponse> responseObserver = new ResponseObserver<>() {
                @Override
                public void onStart(StreamController controller) {
                    log.debug("STT streaming started");
                }

                @Override
                public void onResponse(StreamingRecognizeResponse response) {
                    response.getResultsList().forEach(result -> {
                        String transcript = result.getAlternatives(0).getTranscript();
                        boolean isFinal = result.getIsFinal(); // isFinal 플래그 획득

                        log.debug("Transcript: {} (isFinal: {})", transcript, isFinal);

                        // isFinal 정보를 포함하는 Map 생성
                        Map<String, Object> data = new HashMap<>();
                        data.put("transcript", transcript);
                        data.put("isFinal", isFinal);

                        // Map 형태로 데이터 전송
                        resultConsumer.accept(data);
                    });
                }

                @Override
                public void onError(Throwable t) {
                    log.error("STT streaming error", t);
                }

                @Override
                public void onComplete() {
                    log.debug("STT streaming completed");
                }
            };

            clientStream = speechClient.streamingRecognizeCallable().splitCall(responseObserver);
        }

        public void sendAudioData(byte[] audioData) {
            try {
                if (isFirstRequest) {
                    // 첫 번째 요청에 설정 정보 포함
                    RecognitionConfig config = RecognitionConfig.newBuilder()
                            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                            .setSampleRateHertz(16000)
                            .setLanguageCode("ko-KR")
                            .build();

                    StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()
                            .setConfig(config)
                            .setInterimResults(true) // 중간 응답 받기
                            .build();

                    StreamingRecognizeRequest request = StreamingRecognizeRequest.newBuilder()
                            .setStreamingConfig(streamingConfig)
                            .build();

                    clientStream.send(request);
                    isFirstRequest = false;
                }

                // 오디오 데이터 전송
                StreamingRecognizeRequest request = StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(ByteString.copyFrom(audioData))
                        .build();

                clientStream.send(request);
            } catch (Exception e) {
                log.error("Error sending audio data", e);
            }
        }

        public void close() {
            try {
                clientStream.closeSend();
            } catch (Exception e) {
                log.error("Error closing STT stream", e);
            }
        }
    }
}