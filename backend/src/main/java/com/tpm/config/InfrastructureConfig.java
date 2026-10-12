package com.tpm.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cross-cutting infrastructure switches. Kept off the main class so test slices
 * (e.g. {@code @WebMvcTest}) don't pull in caching / scheduling.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableAsync
@EnableScheduling
public class InfrastructureConfig {}
