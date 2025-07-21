package com.kjung.springsst.infra.googleStt.vo;

/**
 * 음성 인식 결과를 담는 내부 클래스
 */
public record TranscriptionResult(
        String transcription,
        float averageConfidence
) {
}