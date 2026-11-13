package com.ai.documentintelligence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded executor for document ingestion. Bounded on purpose: each ingestion job
 * makes many embedding calls, so unbounded concurrency would hit provider rate limits.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	public static final String INGESTION_EXECUTOR = "ingestionExecutor";

	@Bean(name = INGESTION_EXECUTOR)
	ThreadPoolTaskExecutor ingestionExecutor(AppProperties props) {
		var executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(props.ingestion().concurrency());
		executor.setMaxPoolSize(props.ingestion().concurrency());
		executor.setQueueCapacity(props.ingestion().queueCapacity());
		executor.setThreadNamePrefix("ingest-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		executor.initialize();
		return executor;
	}
}
