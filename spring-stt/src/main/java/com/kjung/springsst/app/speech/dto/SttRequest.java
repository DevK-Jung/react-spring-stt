package com.kjung.springsst.app.speech.dto;

import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SttRequest {
    private MultipartFile file;

    private String languageCode = "ko-KR";

    private boolean enableAutomaticPunctuation = true;

    private boolean enableWordTimeOffsets = false;

}