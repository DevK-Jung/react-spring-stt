package com.kjung.springsst.core.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class SttConfig {

    private final String credentialsPath;

    public SttConfig(@Value("${google.cloud.speech.credentials.path}") String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    @Bean
    public SpeechClient speechClient() throws IOException {
        try {
//            ClassPathResource resource = new ClassPathResource("google-credentials.json");
            ClassPathResource resource = new ClassPathResource(credentialsPath);
            InputStream credentialStream = resource.getInputStream();

            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialStream);

            SpeechSettings speechSettings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();

            return SpeechClient.create(speechSettings);

        } catch (Exception e) {
            // 개발 환경에서 credentials가 없을 경우 기본 인증 사용
            System.out.println("Google credentials 파일을 찾을 수 없습니다. 기본 인증을 사용합니다.");
            return SpeechClient.create();
        }
    }
}
