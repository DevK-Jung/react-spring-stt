package com.kjung.springsst.app.speech.controller;

import com.kjung.springsst.app.speech.dto.SttRequest;
import com.kjung.springsst.app.speech.dto.SttResponse;
import com.kjung.springsst.app.speech.service.SttService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/speech")
public class SttController {

    private final SttService sttService;


    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SttResponse convertSpeechToText(SttRequest request) {
        return sttService.convertSpeechToText(request);
    }

    @PostMapping(value = "/stream",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSpeechToText(@RequestPart("audio") MultipartFile audio) {
        return sttService.streamSpeechToText(audio);
    }
}
