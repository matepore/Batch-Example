package com.batch.example.demo.batch.reader;

import com.batch.example.demo.entity.Person;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersonDatabaseReader {

    @Bean
    @StepScope
    public JpaPagingItemReader<Person> personItemReader(
            EntityManagerFactory entityManagerFactory) {

        return new JpaPagingItemReaderBuilder<Person>()
                .name("personItemReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT p FROM Person p ORDER BY p.dni")
                .pageSize(50)
                .build();
    }
}