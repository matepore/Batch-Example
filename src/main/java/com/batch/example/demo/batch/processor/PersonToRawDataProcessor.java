package com.batch.example.demo.batch.processor;

import com.batch.example.demo.entity.Person;
import com.batch.example.demo.entity.RawData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class PersonToRawDataProcessor implements ItemProcessor<Person, RawData> {

    private final ObjectMapper objectMapper;

    public PersonToRawDataProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RawData process(Person person) throws Exception {
        log.debug("Processing person with DNI: {}", person.getDni());

        // Convertir Person a JSON
        JsonNode jsonData = objectMapper.valueToTree(person);

        RawData rawData = new RawData();
        rawData.setType("CSV_FILE");
        rawData.setData(jsonData);
        rawData.setCreatedAt(LocalDateTime.now());
        rawData.setCreatedBy("BATCH_SYSTEM");
        rawData.setStatus("PROCESSED");

        return rawData;
    }
}