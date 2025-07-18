package com.kjung.springsst.app.speech.service;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.kjung.springsst.app.speech.dto.SttRequest;
import com.kjung.springsst.app.speech.dto.SttResponse;
import com.kjung.springsst.app.speech.dto.TranscriptionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
public class SttService {

    private final SpeechClient speechClient;
    private final String supportedFormats;
    private final long maxFileSize;

    public SttService(SpeechClient speechClient,
                      @Value("${app.stt.supported-formats:mp3,wav,flac,ogg,m4a}") String supportedFormats,
                      @Value("${app.stt.max-file-size-mb:10}") long maxFileSizeMb) {
        this.speechClient = speechClient;
        this.supportedFormats = supportedFormats;
        this.maxFileSize = maxFileSizeMb * 1024 * 1024; // MB를 bytes로 변환
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
                    sttRequest.getLanguageCode());

            // Google Cloud Speech-to-Text 호출
            TranscriptionResult transcriptionResult = performSpeechRecognition(sttRequest);

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("음성 인식 완료: {} (처리시간: {}ms, 신뢰도: {})",
                    sttRequest.getFile().getOriginalFilename(),
                    processingTime,
                    transcriptionResult.getAverageConfidence());

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

        // 인식 설정 구성
        RecognitionConfig.Builder configBuilder = RecognitionConfig.newBuilder()
                .setEncoding(encoding)
                .setLanguageCode(sttRequest.getLanguageCode())
                .setEnableAutomaticPunctuation(sttRequest.isEnableAutomaticPunctuation())
                .setEnableWordTimeOffsets(sttRequest.isEnableWordTimeOffsets())
                .setSampleRateHertz(16000)
                .setUseEnhanced(true); // 향상된 모델 사용

        // 인코딩별 추가 설정
        switch (encoding) {
            case LINEAR16:
                configBuilder.setSampleRateHertz(48000);
                configBuilder.setModel("latest_long");
                break;
            case FLAC:
//                configBuilder.setSampleRateHertz(16000);
                configBuilder.setModel("latest_long");
                break;
            case MP3:
//                configBuilder.setSampleRateHertz(16000);
                configBuilder.setModel("latest_long");
                break;
            case OGG_OPUS:
//                configBuilder.setSampleRateHertz(16000);
                configBuilder.setModel("latest_long");
                break;
            case WEBM_OPUS:
//                configBuilder.setSampleRateHertz(16000);
                configBuilder.setModel("latest_long");
                break;
            default:
//                configBuilder.setSampleRateHertz(16000);
                configBuilder.setModel("latest_long");
        }

        // 여러 언어 후보 추가 (한국어인 경우)
        if (sttRequest.getLanguageCode().startsWith("ko")) {
            configBuilder.addAlternativeLanguageCodes("en-US");
        }

        RecognitionConfig config = configBuilder.build();

        // 오디오 데이터 설정
        RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(file.getBytes()))
                .build();

        log.debug("Google Speech API 호출 - 파일: {}, 인코딩: {}, 언어: {}, 파일크기: {}MB",
                file.getOriginalFilename(), encoding, sttRequest.getLanguageCode(),
                file.getSize() / 1024.0 / 1024.0);

        // 음성 인식 요청
        RecognizeResponse response = speechClient.recognize(config, audio);
        List<SpeechRecognitionResult> results = response.getResultsList();

        if (results.isEmpty()) {
            // 더 자세한 오류 정보 제공
            throw new RuntimeException(String.format(
                    "음성을 인식할 수 없습니다. 다음 사항을 확인해주세요:\n" +
                            "1. 파일에 명확한 음성이 포함되어 있는지 확인\n" +
                            "2. 배경 소음이 적은지 확인\n" +
                            "3. 오디오 품질이 충분한지 확인\n" +
                            "4. 언어 코드(%s)가 올바른지 확인\n" +
                            "파일 정보: %s (%.2fMB, %s 인코딩)",
                    sttRequest.getLanguageCode(),
                    file.getOriginalFilename(),
                    file.getSize() / 1024.0 / 1024.0,
                    encoding.name()));
        }

        // 결과 텍스트 조합 및 신뢰도 계산
        return processRecognitionResults(results, sttRequest.isEnableAutomaticPunctuation());
    }

    /**
     * 음성 인식 결과 처리
     */
    private TranscriptionResult processRecognitionResults(List<SpeechRecognitionResult> results,
                                                          boolean enableAutomaticPunctuation) {
        StringBuilder transcription = new StringBuilder();
        float totalConfidence = 0f;
        int resultCount = 0;

        for (SpeechRecognitionResult result : results) {
            if (!result.getAlternativesList().isEmpty()) {
                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);

                // 텍스트 추가
                transcription.append(alternative.getTranscript());

                // 자동 구두점이 비활성화된 경우 공백 추가
                if (!enableAutomaticPunctuation && !alternative.getTranscript().endsWith(" ")) {
                    transcription.append(" ");
                }

                // 신뢰도 점수 계산
                float confidence = alternative.getConfidence();
                boolean hasConfidence = confidence > 0;
                if (hasConfidence) {
                    totalConfidence += confidence;
                    resultCount++;
                }

                log.debug("인식된 텍스트: '{}' (신뢰도: {:.2f})",
                        alternative.getTranscript(),
                        hasConfidence ? alternative.getConfidence() : 0f);
            }
        }

        String finalTranscription = transcription.toString().trim();
        if (finalTranscription.isEmpty()) {
            throw new RuntimeException("음성 내용을 텍스트로 변환할 수 없습니다.");
        }

        float averageConfidence = resultCount > 0 ? totalConfidence / resultCount : 0f;

        return new TranscriptionResult(finalTranscription, averageConfidence);
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
        String extension = getFileExtension(filename).toLowerCase();
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
            // M4A는 WEBM_OPUS로 처리 (Google Cloud Speech-to-Text API 호환)
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
        String extension = getFileExtension(filename).toLowerCase();
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
     * 파일 확장자 추출
     */
    private String getFileExtension(String filename) {
        if (filename == null) return "";

        int lastDotIndex = filename.lastIndexOf('.');

        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1)
            return "";

        return filename.substring(lastDotIndex + 1);
    }

    /**
     * 성공 응답 생성
     */
    private SttResponse createSuccessResponse(SttRequest request, TranscriptionResult result, long processingTime) {
        return SttResponse.builder()
                .success(true)
                .originalFilename(request.getFile().getOriginalFilename())
                .transcribedText(result.getTranscription())
                .confidenceScore(result.getAverageConfidence())
                .processingTimeMs(processingTime)
                .languageCode(request.getLanguageCode())
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
     * 지원되는 언어 코드 목록
     */
    public String[] getSupportedLanguages() {
        return new String[]{
                "ko-KR", // 한국어
                "en-US", // 영어(미국)
                "en-GB", // 영어(영국)
                "ja-JP", // 일본어
                "zh-CN", // 중국어(간체)
                "zh-TW", // 중국어(번체)
                "es-ES", // 스페인어
                "fr-FR", // 프랑스어
                "de-DE", // 독일어
                "it-IT", // 이탈리아어
                "pt-BR", // 포르투갈어(브라질)
                "ru-RU", // 러시아어
                "ar-SA"  // 아랍어
        };
    }

    /**
     * 지원되는 파일 형식 목록
     */
    public String[] getSupportedFormats() {
        return supportedFormats.split(",");
    }

    /**
     * 간단한 음성 인식 (기본 설정)
     */
    public String simpleTranscribe(MultipartFile file, String languageCode) {
        SttRequest request = SttRequest.builder()
                .file(file)
                .languageCode(languageCode != null ? languageCode : "ko-KR")
                .enableAutomaticPunctuation(true)
                .enableWordTimeOffsets(false)
                .build();

        SttResponse response = transcribeAudioFile(request);

        if (response.isSuccess()) {
            return response.getTranscribedText();
        } else {
            throw new RuntimeException(response.getErrorMessage());
        }
    }


}