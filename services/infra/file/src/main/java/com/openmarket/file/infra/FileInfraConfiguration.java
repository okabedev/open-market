package com.openmarket.file.infra;

import com.openmarket.file.command.application.FileStoragePort;
import com.openmarket.file.command.application.ImageProcessorPort;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** file의 어댑터: 로컬 저장소, 이미지 처리, 조회 DAO의 QueryDSL. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileProperties.class)
public class FileInfraConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }

    @Bean
    FileStoragePort localFileStorage(FileProperties properties) {
        return new LocalFileStorageAdapter(properties.storageRoot());
    }

    @Bean
    ImageProcessorPort imageProcessor() {
        return new ImageIoImageProcessorAdapter();
    }
}
