package com.nunnun.wake.config;

import com.nunnun.wake.storage.S3WakeProofStorage;
import com.nunnun.wake.storage.UnavailableWakeProofStorage;
import com.nunnun.wake.storage.WakeProofStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(AwsS3Properties.class)
public class AwsS3Config {

    @Bean
    @ConditionalOnProperty(prefix = "aws.s3", name = "enabled", havingValue = "true")
    WakeProofStorage s3WakeProofStorage(AwsS3Properties properties) {
        S3Client s3Client = S3Client.builder().region(Region.of(properties.region())).build();
        return new S3WakeProofStorage(s3Client, properties.bucket());
    }

    @Bean
    @ConditionalOnMissingBean(WakeProofStorage.class)
    WakeProofStorage unavailableWakeProofStorage() {
        return new UnavailableWakeProofStorage();
    }
}
