package com.kjung.springsst.infra.googleStt.util;

import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Google Cloud Speech-to-Text RecognitionConfig 생성을 위한 유틸리티 클래스.
 */
@UtilityClass
public class SpeechConfigUtil {

    // 모델 상수
    private static final String DEFAULT_MODEL = "default";
    private static final String COMMAND_AND_SEARCH_MODEL = "command_and_search";
    private static final String LATEST_LONG_MODEL = "latest_long";

    // 샘플레이트 상수
    private static final int DEFAULT_SAMPLE_RATE = 16000;
    private static final int HIGH_SAMPLE_RATE = 48000;

    /**
     * RecognitionConfig 생성
     */
    public RecognitionConfig buildRecognitionConfig(
            AudioEncoding encoding,
            String languageCode,
            boolean enableAutomaticPunctuation,
            boolean enableWordTimeOffsets,
            List<String> alternativeLanguageCodes) {

        RecognitionConfig.Builder configBuilder = RecognitionConfig.newBuilder()
                .setEncoding(encoding)
                .setLanguageCode(languageCode)
                .setEnableAutomaticPunctuation(enableAutomaticPunctuation)
                .setEnableWordTimeOffsets(enableWordTimeOffsets)
                .setSampleRateHertz(DEFAULT_SAMPLE_RATE)
                .addAllAlternativeLanguageCodes(alternativeLanguageCodes)
                .setModel(DEFAULT_MODEL)
                .setUseEnhanced(true);

        // 인코딩별 추가 설정
        configureByEncoding(configBuilder, encoding);

        return configBuilder.build();
    }

    /**
     * 인코딩별 추가 설정 적용
     */
    private void configureByEncoding(RecognitionConfig.Builder configBuilder, AudioEncoding encoding) {
        switch (encoding) {
            case LINEAR16:
                configBuilder.setSampleRateHertz(HIGH_SAMPLE_RATE);
                configBuilder.setModel(DEFAULT_MODEL); // latest_long 대신 default 사용
                break;
            case FLAC:
            case MP3:
            case OGG_OPUS:
            case WEBM_OPUS:
            default:
                // 기본 설정 유지
                break;
        }
    }
}