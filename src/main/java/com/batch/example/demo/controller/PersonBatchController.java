package com.batch.example.demo.controller;

import com.batch.example.demo.model.Status;
import com.batch.example.demo.repository.RawDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/batch")
@Slf4j
public class PersonBatchController {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("personToRawDataJob")
    private Job personToRawDataJob;

    @Autowired
    private RawDataRepository rawDataRepository;

    @PostMapping("/process-person-to-rawdata")
    public ResponseEntity<Status> processPersonToRawData() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(personToRawDataJob, params);

            Status status = Status.builder()
                    .status(execution.getStatus().toString())
                    .message("Person to raw_data migration completed")
                    .recordsProcessed(execution.getStepExecutions()
                            .stream()
                            .mapToLong(StepExecution::getWriteCount)
                            .sum())
                    .build();

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Error processing person to raw_data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Status.builder()
                            .status("FAILED")
                            .message(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/rawdata-status")
    public ResponseEntity<Map<String, Object>> getRawDataStatus() {
        long totalRawData = rawDataRepository.count();
        long csvFileType = rawDataRepository.countByType("CSV_FILE");

        return ResponseEntity.ok(Map.of(
                "total", totalRawData,
                "csvFileRecords", csvFileType,
                "lastRecord", rawDataRepository.findTopByOrderByCreatedAtDesc()
                        .map(rd -> Map.of(
                                "processId", rd.getProcessId(),
                                "type", rd.getType(),
                                "createdAt", rd.getCreatedAt(),
                                "status", rd.getStatus()
                        ))
                        .orElse(Map.of("message", "No records found"))
        ));
    }
}