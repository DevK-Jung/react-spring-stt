package com.kjung.springsst.app.speech.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.speech.v1.SpeechClient;
import com.kjung.springsst.infra.googleStt.StreamingRecognizeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


@Slf4j
@Component
@RequiredArgsConstructor
public class SpeechWebSocketHandler extends BinaryWebSocketHandler {

    private final SpeechClient speechClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, StreamingRecognizeClient> clientStreams = new ConcurrentHashMap<>();

    public StreamingRecognizeClient createStreamingClient(Consumer<String> transcriptConsumer) {
        return new StreamingRecognizeClient(speechClient, transcriptConsumer);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());

        // Google STT 스트리밍 클라이언트 생성
        StreamingRecognizeClient client = createStreamingClient(
                transcript -> {
                    try {
                        Map<String, String> response = Map.of("transcript", transcript);
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                    } catch (IOException e) {
                        log.error("Error sending transcript", e);
                    }
                }
        );

        clientStreams.put(session.getId(), client);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        ByteBuffer buffer = message.getPayload();
        byte[] audioData = new byte[buffer.remaining()];
        buffer.get(audioData);

        StreamingRecognizeClient client = clientStreams.get(session.getId());
        if (client != null) {
            client.sendAudioData(audioData);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error: {}", session.getId(), exception);
        closeClient(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: {}", session.getId());
        closeClient(session.getId());
    }

    private void closeClient(String sessionId) {
        StreamingRecognizeClient client = clientStreams.remove(sessionId);
        if (client != null) {
            client.close();
        }
    }
}