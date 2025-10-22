package com.batch.example.demo.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BatchConfig {
    private final JobRepository jobRepository;
    @Bean(name = "batchJobBean")
    public Job batchJob(JobRepository jobRepository, @Qualifier(value = "batchStepBean") Step batchStep){
        return new JobBuilder("BatchJob", jobRepository)
                .start(batchStep)
                .build();
    }

    @Bean
    public Job personToRawDataJob(
            JobRepository jobRepository,
            Step personToRawDataStep) {

        return new JobBuilder("personToRawDataJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(personToRawDataStep)
                .build();
    }
}