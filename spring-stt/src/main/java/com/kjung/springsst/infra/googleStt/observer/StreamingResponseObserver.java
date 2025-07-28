package com.kjung.springsst.infra.googleStt.observer;

import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class StreamingResponseObserver implements ResponseObserver<StreamingRecognizeResponse> {

    private final Consumer<String> transcriptConsumer;

    @Override
    public void onStart(StreamController controller) {
        log.debug("STT streaming started");
    }

    @Override
    public void onResponse(StreamingRecognizeResponse response) {
        response.getResultsList().forEach(result -> {
            String transcript = result.getAlternatives(0).getTranscript();
            log.debug("Transcript: {}", transcript);
            transcriptConsumer.accept(transcript);
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
}
