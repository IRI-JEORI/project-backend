package com.nunnun.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.nunnun.notification.push.FirebasePushSender;
import com.nunnun.notification.push.PushSender;
import com.nunnun.notification.push.UnavailablePushSender;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {

    @Bean
    @ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true")
    FirebaseApp firebaseApp(FirebaseProperties properties) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        FirebaseOptions.Builder options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault());
        if (StringUtils.hasText(properties.projectId())) {
            options.setProjectId(properties.projectId());
        }
        return FirebaseApp.initializeApp(options.build());
    }

    @Bean
    @ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true")
    PushSender firebasePushSender(FirebaseApp firebaseApp) {
        return new FirebasePushSender(FirebaseMessaging.getInstance(firebaseApp));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "firebase",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    PushSender unavailablePushSender() {
        return new UnavailablePushSender();
    }
}
