package com.batch.example.demo.batch.step;

import com.batch.example.demo.entity.Person;
import com.batch.example.demo.entity.RawData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

public class PersonToRawDataStep {
    private static final Logger logger = LoggerFactory.getLogger(PersonToRawDataStep.class);

    @Bean
    public Step personToRawDataStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<Person> personItemReader,
            ItemProcessor<Person, RawData> personToRawDataProcessor,
            JpaItemWriter<RawData> rawDataWriter) {

        return new StepBuilder("personToRawDataStep", jobRepository)
                .<Person, RawData>chunk(50, transactionManager)
                .reader(personItemReader)
                .processor(personToRawDataProcessor)
                .writer(rawDataWriter)
                .faultTolerant()
                .skipLimit(10)
                .skip(Exception.class)
                .listener(new StepExecutionListener() {
                    @Override
                    public void beforeStep(StepExecution stepExecution) {
                        logger.info("Starting person to raw_data migration step");
                    }

                    @Override
                    public ExitStatus afterStep(StepExecution stepExecution) {
                        logger.info("Completed. Read: {}, Written: {}",
                                stepExecution.getReadCount(),
                                stepExecution.getWriteCount());
                        return stepExecution.getExitStatus();
                    }
                })
                .build();
    }
}