// ========== STT Service 구현 ==========
package com.example.stt.service;

import com.example.stt.dto.SttRequest;
import com.example.stt.dto.SttResponse;
import com.example.stt.entity.AudioFile;
import com.example.stt.entity.TranscriptionStatus;
import com.example.stt.repository.AudioFileRepository;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SttService {

    private static final Logger logger = LoggerFactory.getLogger(SttService.class);

    @Autowired
    private AudioFileRepository audioFileRepository;

    @Autowired
    private SpeechClient speechClient;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.stt.supported-formats}")
    private String supportedFormats;

    @Value("${app.stt.max-duration-seconds:600}")
    private int maxDurationSeconds;

    /**
     * 오디오 파일 업로드 및 저장
     */
//    public AudioFile uploadAudioFile(MultipartFile file) throws IOException {
//        // 파일 유효성 검사
//        validateAudioFile(file);
//
//        // 고유 파일명 생성
//        String originalFilename = file.getOriginalFilename();
//        String extension = getFileExtension(originalFilename);
//        String uniqueFilename = UUID.randomUUID().toString() + "_" + originalFilename;
//        String filePath = uploadDir + File.separator + uniqueFilename;
//
//        // 파일 저장
//        Path destinationPath = Paths.get(filePath);
//        Files.copy(file.getInputStream(), destinationPath);
//
//        // 데이터베이스에 저장
//        AudioFile audioFile = new AudioFile(
//                uniqueFilename,
//                originalFilename,
//                filePath,
//                file.getSize(),
//                file.getContentType()
//        );
//
//        audioFile = audioFileRepository.save(audioFile);
//
//        logger.info("파일 업로드 완료: {} (ID: {})", originalFilename, audioFile.getId());
//
//        return audioFile;
//    }

    /**
     * 동기식 음성 인식 수행 (Google Cloud Speech-to-Text)
     */
    public SttResponse transcribeAudioFile(MultipartFile file, SttRequest sttRequest) {
//        AudioFile audioFile = audioFileRepository.findById(audioFileId)
//                .orElseThrow(() -> new RuntimeException("오디오 파일을 찾을 수 없습니다: " + audioFileId));

        try {
            // 상태를 처리 중으로 변경
//            audioFile.setStatus(TranscriptionStatus.PROCESSING);
//            audioFile.setLanguageCode(sttRequest.getLanguageCode());
//            audioFileRepository.save(audioFile);

            long startTime = System.currentTimeMillis();

            // Google Cloud Speech-to-Text 호출
            String transcription = performSpeechRecognition(audioFile, sttRequest);

            long processingTime = System.currentTimeMillis() - startTime;

            // 결과 저장
            audioFile.setTranscribedText(transcription);
            audioFile.setStatus(TranscriptionStatus.COMPLETED);
            audioFile.setProcessingTimeMs(processingTime);

            // 신뢰도 점수는 결과에서 추출 (여기서는 간단히 0.9로 설정)
            audioFile.setConfidenceScore(0.9f);

            audioFile = audioFileRepository.save(audioFile);

            logger.info("음성 인식 완료: {} (처리시간: {}ms)",
                    audioFile.getOriginalFilename(), processingTime);

            return convertToSttResponse(audioFile);

        } catch (Exception e) {
            logger.error("음성 인식 실패: {}", audioFile.getOriginalFilename(), e);

            // 실패 상태로 업데이트
            audioFile.setStatus(TranscriptionStatus.FAILED);
            audioFileRepository.save(audioFile);

            throw new RuntimeException("음성 인식 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 실제 Google Cloud Speech-to-Text API 호출
     */
    private String performSpeechRecognition(AudioFile audioFile, SttRequest sttRequest) throws Exception {
        // 파일 읽기
        Path audioPath = Paths.get(audioFile.getFilePath());
        byte[] audioData = Files.readAllBytes(audioPath);
        ByteString audioBytes = ByteString.copyFrom(audioData);

        // 오디오 인코딩 결정
        RecognitionConfig.AudioEncoding encoding = determineAudioEncoding(audioFile.getMimeType());

        // 인식 설정 구성
        RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(encoding)
                .setLanguageCode(sttRequest.getLanguageCode())
                .setSampleRateHertz(16000) // 일반적인 샘플레이트
                .setEnableAutomaticPunctuation(sttRequest.isEnableAutomaticPunctuation())
                .setEnableWordTimeOffsets(sttRequest.isEnableWordTimeOffsets())
                .setModel("latest_long") // 긴 오디오용 모델
                .setUseEnhanced(true) // 향상된 모델 사용
                .build();

        // 오디오 데이터 설정
        RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(audioBytes)
                .build();

        // 음성 인식 요청
        RecognizeResponse response = speechClient.recognize(config, audio);
        List<SpeechRecognitionResult> results = response.getResultsList();

        // 결과 텍스트 조합
        StringBuilder transcription = new StringBuilder();
        float totalConfidence = 0f;
        int resultCount = 0;

        for (SpeechRecognitionResult result : results) {
            if (!result.getAlternativesList().isEmpty()) {
                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                transcription.append(alternative.getTranscript()).append(" ");

                // 신뢰도 점수 평균 계산
                totalConfidence += alternative.getConfidence();
                resultCount++;
            }
        }

        // 평균 신뢰도 저장
        if (resultCount > 0) {
            audioFile.setConfidenceScore(totalConfidence / resultCount);
        }

        String finalTranscription = transcription.toString().trim();

        if (finalTranscription.isEmpty()) {
            throw new RuntimeException("음성을 인식할 수 없습니다. 오디오 품질을 확인해주세요.");
        }

        return finalTranscription;
    }

    /**
     * 파일 MIME 타입에 따른 오디오 인코딩 결정
     */
    private RecognitionConfig.AudioEncoding determineAudioEncoding(String mimeType) {
        if (mimeType == null) {
            return RecognitionConfig.AudioEncoding.LINEAR16; // 기본값
        }

        switch (mimeType.toLowerCase()) {
            case "audio/wav":
            case "audio/wave":
                return RecognitionConfig.AudioEncoding.LINEAR16;
            case "audio/flac":
                return RecognitionConfig.AudioEncoding.FLAC;
            case "audio/ogg":
                return RecognitionConfig.AudioEncoding.OGG_OPUS;
            case "audio/mp3":
            case "audio/mpeg":
                return RecognitionConfig.AudioEncoding.MP3;
            case "audio/mp4":
            case "audio/m4a":
                return RecognitionConfig.AudioEncoding.MP3; // MP4는 MP3로 처리
            default:
                logger.warn("알 수 없는 MIME 타입: {}. LINEAR16으로 처리합니다.", mimeType);
                return RecognitionConfig.AudioEncoding.LINEAR16;
        }
    }

    /**
     * 오디오 파일 유효성 검사
     */
    private void validateAudioFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("파일이 비어있습니다.");
        }

        // 파일 크기 검사 (50MB 제한)
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new RuntimeException("파일 크기가 50MB를 초과합니다.");
        }

        // 파일 확장자 검사
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            throw new RuntimeException("파일명이 올바르지 않습니다.");
        }

        String extension = getFileExtension(filename).toLowerCase();
        String[] supportedExtensions = supportedFormats.split(",");

        boolean isSupported = false;
        for (String supportedExt : supportedExtensions) {
            if (extension.equals(supportedExt.trim())) {
                isSupported = true;
                break;
            }
        }

        if (!isSupported) {
            throw new RuntimeException("지원되지 않는 파일 형식입니다. 지원 형식: " + supportedFormats);
        }
    }

    /**
     * 파일 확장자 추출
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * 모든 오디오 파일 조회
     */
    public List<SttResponse> getAllAudioFiles() {
        List<AudioFile> audioFiles = audioFileRepository.findAll();
        return audioFiles.stream()
                .map(this::convertToSttResponse)
                .collect(Collectors.toList());
    }

    /**
     * 특정 오디오 파일 조회
     */
    public SttResponse getAudioFileById(Long id) {
        AudioFile audioFile = audioFileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("오디오 파일을 찾을 수 없습니다: " + id));

        return convertToSttResponse(audioFile);
    }

    /**
     * 상태별 오디오 파일 조회
     */
    public List<SttResponse> getAudioFilesByStatus(TranscriptionStatus status) {
        List<AudioFile> audioFiles = audioFileRepository.findByStatusOrderByCreatedAtDesc(status);
        return audioFiles.stream()
                .map(this::convertToSttResponse)
                .collect(Collectors.toList());
    }

    /**
     * 오디오 파일 삭제
     */
    public void deleteAudioFile(Long id) throws IOException {
        AudioFile audioFile = audioFileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("오디오 파일을 찾을 수 없습니다: " + id));

        // 파일 시스템에서 파일 삭제
        Path filePath = Paths.get(audioFile.getFilePath());
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }

        // 데이터베이스에서 레코드 삭제
        audioFileRepository.delete(audioFile);

        logger.info("파일 삭제 완료: {} (ID: {})", audioFile.getOriginalFilename(), id);
    }

    /**
     * 검색 기능
     */
    public List<SttResponse> searchAudioFiles(String keyword) {
        List<AudioFile> audioFiles = audioFileRepository.findByOriginalFilenameContainingIgnoreCase(keyword);
        return audioFiles.stream()
                .map(this::convertToSttResponse)
                .collect(Collectors.toList());
    }

    /**
     * 통계 정보 조회
     */
    public SttStatistics getStatistics() {
        long totalFiles = audioFileRepository.count();
        long completedFiles = audioFileRepository.findByStatusOrderByCreatedAtDesc(TranscriptionStatus.COMPLETED).size();
        long failedFiles = audioFileRepository.findByStatusOrderByCreatedAtDesc(TranscriptionStatus.FAILED).size();

        Float avgConfidence = audioFileRepository.getAverageConfidenceScore();
        Double avgProcessingTime = audioFileRepository.getAverageProcessingTime();

        return new SttStatistics(
                totalFiles,
                completedFiles,
                failedFiles,
                avgConfidence != null ? avgConfidence : 0f,
                avgProcessingTime != null ? avgProcessingTime.longValue() : 0L
        );
    }

    /**
     * Entity를 DTO로 변환
     */
    private SttResponse convertToSttResponse(AudioFile audioFile) {
        return new SttResponse(
                audioFile.getId(),
                audioFile.getOriginalFilename(),
                audioFile.getTranscribedText(),
                audioFile.getStatus(),
                audioFile.getConfidenceScore(),
                audioFile.getProcessingTimeMs(),
                audioFile.getCreatedAt(),
                audioFile.getLanguageCode()