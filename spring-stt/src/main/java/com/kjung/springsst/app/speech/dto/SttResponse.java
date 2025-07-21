package com.kjung.springsst.app.speech.dto;

import com.kjung.springsst.infra.googleStt.vo.TranscriptionResult;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
public class SttResponse {
    private boolean success;
    private String originalFilename;
    private String transcribedText;
    private Float confidenceScore;
    private Long processingTimeMs;
    private Long fileSize;
    private String errorMessage;

    // 추가 정보
    private String encoding;
    private Integer resultCount;

    public static SttResponse createSuccessResponse(SttRequest request,
                                                    TranscriptionResult result,
                                                    long processingTime) {
        return SttResponse.builder()
                .success(true)
                .originalFilename(request.getFile().getOriginalFilename())
                .transcribedText(result.transcription())
                .confidenceScore(result.averageConfidence())
                .processingTimeMs(processingTime)
                .fileSize(request.getFile().getSize())
                .build();
    }

    /**
     * 에러 응답 생성
     */
    private static SttResponse createErrorResponse(SttRequest request,
                                                   String errorMessage) {
        return SttResponse.builder()
                .success(false)
                .originalFilename(request.getFile().getOriginalFilename())
                .errorMessage(errorMessage)
                .fileSize(request.getFile().getSize())
                .build();
    }
}