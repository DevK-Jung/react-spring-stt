package com.kjung.springsst.infra.googleStt.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 단어별 시간 정보 DTO
 */
@Getter
@RequiredArgsConstructor
public class WordTimeInfo {
    private final String word;
    private final double startTime;
    private final double endTime;
    
}
