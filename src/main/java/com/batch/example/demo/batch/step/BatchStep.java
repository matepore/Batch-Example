package com.batch.example.demo.batch.step;

import com.batch.example.demo.batch.processor.PersonProcessor;
import com.batch.example.demo.batch.reader.CSVReader;
import com.batch.example.demo.batch.writer.RawDataWriter;
import com.batch.example.demo.entity.RawData;
import com.batch.example.demo.model.PersonDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class BatchStep {
    @Value("${spring.batch.job.chunk}")
    private Integer chunk;
    private final PersonProcessor personProcessor;
    private final CSVReader csvReader;
    private final RawDataWriter rawDataWriter;

    @Bean(name = "batchStepBean")
    public Step processPerson (JobRepository jobRepository,
                               PlatformTransactionManager platformTransactionManager){
        return new StepBuilder("batchStep", jobRepository)
                .<PersonDto, RawData>chunk(chunk, platformTransactionManager)
                .reader(csvReader.reader())
                .processor(personProcessor)
                .writer(rawDataWriter)
                .build();
    }
}
