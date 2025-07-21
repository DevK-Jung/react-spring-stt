package com.kjung.springsst.app.speech.service;

import com.kjung.springsst.app.speech.dto.SttRequest;
import com.kjung.springsst.app.speech.dto.SttResponse;
import com.kjung.springsst.infra.googleStt.GoogleSttHelper;
import com.kjung.springsst.infra.googleStt.vo.TranscriptionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttService {

    private final GoogleSttHelper googleSttHelper;

    /**
     * 동기식 음성 인식 수행 (Google Cloud Speech-to-Text)
     */
    public SttResponse transcribeAudioFile(SttRequest sttRequest) {

        long startTime = System.currentTimeMillis();

        TranscriptionResult transcriptionResult = googleSttHelper.recognizeSync(sttRequest.getFile(), sttRequest.isEnableAutomaticPunctuation(), sttRequest.isEnableWordTimeOffsets());

        long processingTime = System.currentTimeMillis() - startTime;

        return SttResponse.createSuccessResponse(sttRequest, transcriptionResult, processingTime);

    }

}