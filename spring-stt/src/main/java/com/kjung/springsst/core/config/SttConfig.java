package com.kjung.springsst.core.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Slf4j
@Configuration
public class SttConfig {


    private final Resource gcsCredentials;

    public SttConfig(@Value("classpath:stt.json") Resource gcsCredentials) {
        this.gcsCredentials = gcsCredentials;
    }

    @Bean
    public SpeechClient speechClient() throws IOException {
        try {

            GoogleCredentials credentials = GoogleCredentials.fromStream(gcsCredentials.getInputStream());

            SpeechSettings speechSettings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();

            return SpeechClient.create(speechSettings);

        } catch (Exception e) {
            // 개발 환경에서 credentials가 없을 경우 기본 인증 사용
            log.error("Google credentials 파일을 찾을 수 없습니다. 기본 인증을 사용합니다.");
            return SpeechClient.create();
        }
    }
}
