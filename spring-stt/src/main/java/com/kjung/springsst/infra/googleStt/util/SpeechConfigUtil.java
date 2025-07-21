package com.kjung.springsst.infra.googleStt.util;

import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Locale;

import static com.kjung.springsst.infra.googleStt.constants.GoogleSttModel.DEFAULT_MODEL;
import static com.kjung.springsst.infra.googleStt.constants.GoogleSttModel.LATEST_LONG_MODEL;

/**
 * Google Cloud Speech-to-Text RecognitionConfig 생성을 위한 유틸리티 클래스.
 */
@UtilityClass
public class SpeechConfigUtil {

    private static final int DEFAULT_SAMPLE_RATE = 16000;

    private static final int HIGH_SAMPLE_RATE = 48000;

    /**
     * Google Speech-to-Text API용 RecognitionConfig 객체를 생성합니다.
     * <p>
     * 기본 샘플 레이트(16kHz)와 향상된 모델을 사용하여 음성 인식 설정을 구성합니다.
     * 지원되는 대체 언어 코드가 자동으로 포함되며, 오디오 인코딩에 따른 추가 설정이 적용됩니다.
     * </p>
     *
     * @param encoding                   오디오 인코딩 형식 (예: LINEAR16, FLAC, MP3 등)
     * @param languageCode               주 언어 코드 (예: "ko-KR", "en-US")
     * @param enableAutomaticPunctuation 자동 구두점 추가 여부
     *                                   {@code true}: 마침표, 쉼표, 물음표 등을 자동으로 추가하여 가독성 향상
     *                                   {@code false}: 구두점 없이 연속된 텍스트만 반환
     * @param enableWordTimeOffsets      단어별 타임스탬프 제공 여부
     *                                   {@code true}: 각 단어의 시작/종료 시간 정보 포함 (자막 생성, 오디오 편집용)
     *                                   {@code false}: 전체 텍스트만 반환하여 처리 속도 향상
     * @return 구성된 RecognitionConfig 객체
     * @throws IllegalArgumentException 지원하지 않는 언어 코드나 인코딩이 전달된 경우
     * @see RecognitionConfig
     * @see AudioEncoding
     */
    public RecognitionConfig buildRecognitionConfig(AudioEncoding encoding,
                                                    String languageCode,
                                                    boolean enableAutomaticPunctuation,
                                                    boolean enableWordTimeOffsets) {

        RecognitionConfig.Builder configBuilder = RecognitionConfig.newBuilder()
                .setEncoding(encoding)
                .setLanguageCode(languageCode)
                .setEnableAutomaticPunctuation(enableAutomaticPunctuation)
                .setEnableWordTimeOffsets(enableWordTimeOffsets)
                .setSampleRateHertz(DEFAULT_SAMPLE_RATE)
                .addAllAlternativeLanguageCodes(getSupportedLanguages())
                .setModel(DEFAULT_MODEL)
                .setUseEnhanced(true);

        // 인코딩별 추가 설정
        configureByEncoding(configBuilder, encoding);

        return configBuilder.build();
    }

    /**
     * 인코딩별 추가 설정 적용
     * todo SampleRate 파일 속성으로 부터 조회해서 적용 필요 - 구글에서 지원하는 Sample Rate 검증 필요
     */
    private void configureByEncoding(RecognitionConfig.Builder configBuilder,
                                     AudioEncoding encoding) {
        switch (encoding) {
            case LINEAR16:
                configBuilder.setSampleRateHertz(HIGH_SAMPLE_RATE);
                configBuilder.setModel(LATEST_LONG_MODEL); // latest_long 대신 default 사용
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

    /**
     * 지원되는 언어 코드 목록.
     * <p>
     * 이 기능은 음성 명령이나 검색어와 같은 짧은 말을 텍스트로 변환해야 하는 앱에 적합합니다.
     * 기본 언어 외에도 Speech-to-Text가 지원하는 언어 중 최대 3개의 대체 언어를 목록에 포함할 수 있습니다(총 4개 언어).
     * <p>
     * 음성 텍스트 변환 요청에 대체 언어를 지정할 수 있는 경우라도 languageCode 필드에 기본 언어 코드를 제공해야 합니다.
     * 또한, 요청하는 언어의 수를 최소로 제한해야 합니다.
     * 요청하는 대체 언어 코드가 적을수록 Speech-to-Text가 정확한 언어를 선택할 확률이 높습니다.
     * 단일 언어만 지정할 때 가장 좋은 결과를 얻을 수 있습니다.
     * <p>
     */
    public List<String> getSupportedLanguages() {
        return List.of(
                Locale.ENGLISH.toString(), // "en-US", // 영어(미국)
//                "en-GB", // 영어(영국)
                Locale.JAPANESE.toString() // "ja-JP" // 일본어
//                "zh-CN", // 중국어(간체)
//                "zh-TW", // 중국어(번체)
//                "es-ES", // 스페인어
//                "fr-FR", // 프랑스어
//                "de-DE", // 독일어
//                "it-IT", // 이탈리아어
//                "pt-BR", // 포르투갈어(브라질)
//                "ru-RU", // 러시아어
//                "ar-SA"  // 아랍어
        );
    }
}