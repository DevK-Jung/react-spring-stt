package com.kjung.springsst.app.speech.service;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.kjung.springsst.app.file.util.FileUtil;
import com.kjung.springsst.app.speech.dto.SttRequest;
import com.kjung.springsst.app.speech.dto.SttResponse;
import com.kjung.springsst.infra.googleStt.vo.TranscriptionResult;
import com.kjung.springsst.infra.googleStt.util.SpeechConfigUtil;
import com.kjung.springsst.infra.googleStt.GoogleSttHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class SttService {

    private final GoogleSttHelper googleSttHelper;
    private final String supportedFormats;
    private final long maxFileSize;
    private final String defaultLanguageCode;

    public SttService(GoogleSttHelper googleSttHelper,
                      @Value("${app.stt.supported-formats:mp3,wav,flac,ogg,m4a}") String supportedFormats,
                      @Value("${app.stt.max-file-size-mb:10}") long maxFileSizeMb,
                      @Value("${app.stt.default-language-code:ko_KR}") String defaultLanguageCode) {
        this.googleSttHelper = googleSttHelper;
        this.supportedFormats = supportedFormats;
        this.maxFileSize = maxFileSizeMb * 1024 * 1024; // MB를 bytes로 변환
        this.defaultLanguageCode = defaultLanguageCode;
    }

    /**
     * 동기식 음성 인식 수행 (Google Cloud Speech-to-Text)
     */
    public SttResponse transcribeAudioFile(SttRequest sttRequest) {
        try {
            // 파일 유효성 검사
            validateAudioFile(sttRequest.getFile());

            long startTime = System.currentTimeMillis();

            log.info("음성 인식 시작: {} (크기: {} bytes, 언어: {})",
                    sttRequest.getFile().getOriginalFilename(),
                    sttRequest.getFile().getSize(),
                    defaultLanguageCode
            );

            // Google Cloud Speech-to-Text 호출
            TranscriptionResult transcriptionResult = performSpeechRecognition(sttRequest);

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("음성 인식 완료: {} (처리시간: {}ms, 신뢰도: {})",
                    sttRequest.getFile().getOriginalFilename(),
                    processingTime,
                    transcriptionResult.averageConfidence());

            return createSuccessResponse(sttRequest, transcriptionResult, processingTime);

        } catch (Exception e) {
            log.error("음성 인식 실패: {} - {}",
                    sttRequest.getFile().getOriginalFilename(), e.getMessage());

            return createErrorResponse(sttRequest, e.getMessage());
        }
    }

    /**
     * 실제 Google Cloud Speech-to-Text API 호출
     */
    private TranscriptionResult performSpeechRecognition(SttRequest sttRequest) throws Exception {
        MultipartFile file = sttRequest.getFile();

        // 오디오 인코딩 결정
        RecognitionConfig.AudioEncoding encoding = determineAudioEncoding(file);

        log.info("선택된 오디오 인코딩: {}", encoding);

        // 인식 설정 구성 (유틸리티 클래스 사용)
        RecognitionConfig config = SpeechConfigUtil.buildRecognitionConfig(
                encoding,
                Locale.KOREA.toString(),
                sttRequest.isEnableAutomaticPunctuation(),
                sttRequest.isEnableWordTimeOffsets(),
                getSupportedLanguages()
        );

        // 오디오 데이터 설정
        RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(file.getBytes()))
                .build();

        log.debug("Google Speech API 호출 - 파일: {}, 인코딩: {}, 언어: {}, 파일크기: {}MB",
                file.getOriginalFilename(), encoding, defaultLanguageCode,
                file.getSize() / 1024.0 / 1024.0);

        // GoogleSttHelper를 사용한 음성 인식 요청
        return googleSttHelper.recognizeSync(config, audio);
    }


    /**
     * 파일 정보에 따른 오디오 인코딩 결정 (개선된 버전)
     */
    private RecognitionConfig.AudioEncoding determineAudioEncoding(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        log.debug("오디오 인코딩 결정 - ContentType: {}, FileName: {}", contentType, filename);

        // Content-Type 우선 확인
        RecognitionConfig.AudioEncoding encodingByContentType = getEncodingByContentType(contentType);
        if (encodingByContentType != null)
            return encodingByContentType;

        // 파일 확장자로 판단
        String extension = FileUtil.getFileExtension(filename).toLowerCase();
        RecognitionConfig.AudioEncoding encodingByExtension = getEncodingByExtension(extension);
        if (encodingByExtension != null) {
            return encodingByExtension;
        }

        log.warn("알 수 없는 오디오 형식 - ContentType: {}, FileName: {}. LINEAR16으로 처리합니다.",
                contentType, filename);

        return RecognitionConfig.AudioEncoding.LINEAR16;
    }

    /**
     * Content-Type으로 인코딩 결정 (개선된 버전)
     */
    private RecognitionConfig.AudioEncoding getEncodingByContentType(String contentType) {
        if (contentType == null) return null;

        return switch (contentType.toLowerCase()) {
            case "audio/wav", "audio/wave", "audio/x-wav" -> RecognitionConfig.AudioEncoding.LINEAR16;
            case "audio/flac", "audio/x-flac" -> RecognitionConfig.AudioEncoding.FLAC;
            case "audio/ogg", "audio/ogg; codecs=opus" -> RecognitionConfig.AudioEncoding.OGG_OPUS;
            case "audio/mp3", "audio/mpeg" -> RecognitionConfig.AudioEncoding.MP3;
            // M4A는 AMR 또는 WEBM_OPUS로 처리 (Google Cloud Speech-to-Text API 호환)
            case "audio/mp4", "audio/m4a", "audio/x-m4a" -> RecognitionConfig.AudioEncoding.WEBM_OPUS;
            default -> null;
        };
    }

    /**
     * 파일 확장자로 인코딩 결정 (개선된 버전)
     */
    private RecognitionConfig.AudioEncoding getEncodingByExtension(String extension) {
        return switch (extension) {
            case "wav" -> RecognitionConfig.AudioEncoding.LINEAR16;
            case "flac" -> RecognitionConfig.AudioEncoding.FLAC;
            case "ogg" -> RecognitionConfig.AudioEncoding.OGG_OPUS;
            case "mp3" -> RecognitionConfig.AudioEncoding.MP3;
            case "m4a", "mp4" -> RecognitionConfig.AudioEncoding.WEBM_OPUS;
            default -> null;
        };
    }

    /**
     * 오디오 파일 유효성 검사
     */
    private void validateAudioFile(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("파일이 비어있습니다.");

        // 파일 크기 검사
        if (file.getSize() > maxFileSize)
            throw new IllegalArgumentException(String.format(
                    "파일 크기가 제한을 초과합니다. (현재: %.2f MB, 최대: %.2f MB)",
                    file.getSize() / 1024.0 / 1024.0,
                    maxFileSize / 1024.0 / 1024.0));

        String filename = file.getOriginalFilename();

        // 지원되는 파일 형식 검사
        validateFileFormat(filename);

        log.debug("파일 유효성 검사 통과: {} ({} bytes)", filename, file.getSize());
    }

    /**
     * 파일 형식 유효성 검사
     */
    private void validateFileFormat(String filename) {
        String extension = FileUtil.getFileExtension(filename).toLowerCase();
        if (extension.isEmpty())
            throw new IllegalArgumentException("파일 확장자가 없습니다.");

        String[] supportedExtensions = supportedFormats.split(",");
        for (String supportedExt : supportedExtensions) {
            if (extension.equals(supportedExt.trim().toLowerCase())) {
                return; // 지원되는 형식 발견
            }
        }

        throw new IllegalArgumentException(
                String.format("지원되지 않는 파일 형식입니다. (입력: %s, 지원 형식: %s)",
                        extension, supportedFormats));
    }


    /**
     * 성공 응답 생성
     */
    private SttResponse createSuccessResponse(SttRequest request, TranscriptionResult result, long processingTime) {
        return SttResponse.builder()
                .success(true)
                .originalFilename(request.getFile().getOriginalFilename())
                .transcribedText(result.transcription())
                .confidenceScore(result.averageConfidence())
                .processingTimeMs(processingTime)
                .languageCode(defaultLanguageCode)
                .fileSize(request.getFile().getSize())
                .build();
    }

    /**
     * 에러 응답 생성
     */
    private SttResponse createErrorResponse(SttRequest request, String errorMessage) {
        return SttResponse.builder()
                .success(false)
                .originalFilename(request.getFile().getOriginalFilename())
                .errorMessage(errorMessage)
                .fileSize(request.getFile().getSize())
                .build();
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