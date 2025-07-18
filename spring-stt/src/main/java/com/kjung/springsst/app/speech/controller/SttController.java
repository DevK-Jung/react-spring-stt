package com.kjung.springsst.app.speech.controller;

import com.kjung.springsst.app.speech.dto.SttRequest;
import com.kjung.springsst.app.speech.dto.SttResponse;
import com.kjung.springsst.app.speech.service.SttService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/speech")
public class SttController {

    private final SttService sttService;


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SttResponse speechToText(@ModelAttribute SttRequest request) {
        return sttService.transcribeAudioFile(request);
    }


}
