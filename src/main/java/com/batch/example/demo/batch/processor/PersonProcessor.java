package com.batch.example.demo.batch.processor;

import com.batch.example.demo.entity.RawData;
import com.batch.example.demo.model.PersonDto;
import com.batch.example.demo.model.Status;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PersonProcessor implements ItemProcessor<PersonDto, RawData> {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private static final Logger logger = LoggerFactory.getLogger(PersonProcessor.class);

    @Override
    public RawData process(PersonDto person) throws Exception {
        logger.info("Procesando persona: {}", person);
        // Añadir salario de 1000
        person.setSalary(1000.0);
        ObjectNode dataNode = populateDataNode(person);

        // Crear y retornar nuevo RawData
        RawData rawData = new RawData();
        rawData.setData(dataNode);
        rawData.setType("person");
        rawData.setCreatedBy("mateporeSystem");
        rawData.setStatus(Status.NEW.name());
        rawData.setCreatedAt(LocalDateTime.now());

        logger.info("Persona procesada y convertida a RawData: {}", rawData);

        return rawData;
    }

    private ObjectNode populateDataNode(PersonDto item) {
        try {
            ObjectNode dataNode = objectMapper.createObjectNode();

            // Mapeo de campos basado en la estructura actualizada
            dataNode.put("nombre", item.getFirstName());
            dataNode.put("apellido", item.getLastName());
            dataNode.put("edad", item.getAge());
            dataNode.put("dni", item.getDocumentNumber());
            dataNode.put("salario", item.getSalary());
            return dataNode;
        } catch (Exception e) {
            throw e;
        }
    }
}
