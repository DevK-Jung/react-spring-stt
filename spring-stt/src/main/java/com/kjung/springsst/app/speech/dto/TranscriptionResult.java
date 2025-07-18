package com.kjung.springsst.app.speech.dto;

import lombok.Getter;

/**
 * 음성 인식 결과를 담는 내부 클래스
 */
@Getter
public class TranscriptionResult {
    private final String transcription;
    private final float averageConfidence;

    public TranscriptionResult(String transcription, float averageConfidence) {
        this.transcription = transcription;
        this.averageConfidence = averageConfidence;
    }
}