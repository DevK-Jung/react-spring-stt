package com.kjung.springsst.app.speech.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SttResponse {
    private boolean success;
    private String originalFilename;
    private String transcribedText;
    private Float confidenceScore;
    private Long processingTimeMs;
    private String languageCode;
    private Long fileSize;
    private String errorMessage;

    // 추가 정보
    private String encoding;
    private Integer resultCount;
}