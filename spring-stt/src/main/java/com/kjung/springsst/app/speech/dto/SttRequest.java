package com.kjung.springsst.app.speech.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class SttRequest {
    private MultipartFile file;

    private boolean enableAutomaticPunctuation = true;

    private boolean enableWordTimeOffsets = false;

}