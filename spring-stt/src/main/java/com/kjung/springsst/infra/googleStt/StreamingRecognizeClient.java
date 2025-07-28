package com.kjung.springsst.infra.googleStt;

import com.google.api.gax.rpc.ClientStream;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.protobuf.ByteString;
import com.kjung.springsst.infra.googleStt.observer.StreamingResponseObserver;
import com.kjung.springsst.infra.googleStt.util.SpeechConfigUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class StreamingRecognizeClient {
    private final ClientStream<StreamingRecognizeRequest> clientStream;

    private boolean isFirstRequest = true;

    public StreamingRecognizeClient(SpeechClient speechClient,
                                    Consumer<String> transcriptConsumer) {
        StreamingResponseObserver streamingResponseObserver = new StreamingResponseObserver(transcriptConsumer);
        clientStream = speechClient
                .streamingRecognizeCallable()
                .splitCall(streamingResponseObserver);
    }

    public void sendAudioData(byte[] audioData) {
        try {
            if (isFirstRequest) {
                // 첫 번째 요청에 설정 정보 포함
                RecognitionConfig config = SpeechConfigUtil.buildRecognitionConfig(
                        RecognitionConfig.AudioEncoding.LINEAR16,
                        "ko_KR",
                        true,
                        false
                );

                StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()
                        .setConfig(config)
                        .setInterimResults(false)
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