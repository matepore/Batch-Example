# Tarea 4: Batch con Validación y Manejo de Errores

## 📋 Descripción

Esta tarea implementa un job batch robusto con validación de datos, políticas de skip y retry, listeners personalizados y manejo completo de errores. Es esencial para procesos batch en producción donde la confiabilidad y el registro detallado son críticos.

## 🎯 Objetivos

- ✅ Implementar validación de datos en el processor
- ✅ Configurar skip policies para errores recuperables
- ✅ Configurar retry policies para errores transitorios
- ✅ Crear listeners personalizados para auditoría
- ✅ Registrar errores detallados en base de datos
- ✅ Generar reportes de errores
- ✅ Implementar compensación de transacciones

## 🏗️ Arquitectura

```
Reader → Validator → Processor → Writer
           ↓            ↓          ↓
        Skip         Retry      Write
        Policy       Policy     Listener
           ↓            ↓          ↓
      Error Log    Error Log  Error Log
```

## 📝 Componentes a Implementar

### 1. Modelo de Error

**Ubicación**: `src/main/java/com/batch/example/demo/entity/BatchError.java`

```java
@Entity
@Table(name = "batch_error")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchError {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "job_execution_id")
    private Long jobExecutionId;
    
    @Column(name = "step_execution_id")
    private Long stepExecutionId;
    
    @Column(name = "error_type")
    @Enumerated(EnumType.STRING)
    private ErrorType errorType; // VALIDATION, SKIP, RETRY, WRITE
    
    @Column(name = "error_phase")
    private String errorPhase; // READ, PROCESS, WRITE
    
    @Column(name = "item_data", columnDefinition = "TEXT")
    private String itemData;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;
    
    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "resolved")
    private Boolean resolved = false;
}

enum ErrorType {
    VALIDATION, SKIP, RETRY, WRITE, READ, FATAL
}
```

### 2. Validador de Items

**Ubicación**: `src/main/java/com/batch/example/demo/batch/validator/PersonValidator.java`

```java
@Component
@Slf4j
public class PersonValidator implements ItemProcessor<PersonDto, PersonDto> {
    
    @Autowired
    private BatchErrorRepository errorRepository;
    
    @Override
    public PersonDto process(PersonDto person) throws ValidationException {
        List<String> errors = new ArrayList<>();
        
        // Validar nombre
        if (person.getName() == null || person.getName().trim().isEmpty()) {
            errors.add("Name is required");
        } else if (person.getName().length() > 100) {
            errors.add("Name exceeds maximum length of 100 characters");
        }
        
        // Validar edad
        if (person.getAge() == null) {
            errors.add("Age is required");
        } else if (person.getAge() < 0 || person.getAge() > 150) {
            errors.add("Age must be between 0 and 150");
        }
        
        // Validar email
        if (person.getEmail() != null && !isValidEmail(person.getEmail())) {
            errors.add("Invalid email format");
        }
        
        if (!errors.isEmpty()) {
            String errorMsg = String.join(", ", errors);
            log.warn("Validation failed for person: {} - Errors: {}", person, errorMsg);
            throw new ValidationException(errorMsg, person);
        }
        
        log.debug("Validation passed for person: {}", person.getName());
        return person;
    }
    
    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }
}

@Getter
public class ValidationException extends Exception {
    private final PersonDto item;
    
    public ValidationException(String message, PersonDto item) {
        super(message);
        this.item = item;
    }
}
```

### 3. Processor con Manejo de Errores

**Ubicación**: `src/main/java/com/batch/example/demo/batch/processor/ResilientPersonProcessor.java`

```java
@Component
@Slf4j
public class ResilientPersonProcessor implements ItemProcessor<PersonDto, RawData> {
    
    @Autowired
    private ExternalApiService externalApiService; // Servicio que puede fallar
    
    @Autowired
    private BatchErrorRepository errorRepository;
    
    @Value("${batch.processor.max-retries:3}")
    private int maxRetries;
    
    @Override
    public RawData process(PersonDto person) throws Exception {
        log.debug("Processing person: {}", person.getName());
        
        try {
            // Simular llamada a API externa (puede fallar)
            String enrichedData = externalApiService.enrichPersonData(person);
            
            RawData rawData = new RawData();
            rawData.setName(person.getName());
            rawData.setAge(person.getAge());
            rawData.setEmail(person.getEmail());
            rawData.setEnrichedData(enrichedData);
            rawData.setProcessedDate(LocalDateTime.now());
            
            log.debug("Successfully processed person: {}", person.getName());
            return rawData;
            
        } catch (TransientException e) {
            // Error transitorio - será reintenado por Spring Batch
            log.warn("Transient error processing {}: {}", person.getName(), e.getMessage());
            throw e;
            
        } catch (Exception e) {
            // Error permanente - será skipped
            log.error("Permanent error processing {}: {}", person.getName(), e.getMessage());
            throw new ProcessingException("Failed to process person", e, person);
        }
    }
}

@Getter
public class ProcessingException extends Exception {
    private final PersonDto item;
    
    public ProcessingException(String message, Throwable cause, PersonDto item) {
        super(message, cause);
        this.item = item;
    }
}

public class TransientException extends Exception {
    public TransientException(String message) {
        super(message);
    }
}
```

### 4. Skip Listener

**Ubicación**: `src/main/java/com/batch/example/demo/batch/listener/CustomSkipListener.java`

```java
@Component
@Slf4j
public class CustomSkipListener implements SkipListener<PersonDto, RawData> {
    
    @Autowired
    private BatchErrorRepository errorRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public void onSkipInRead(Throwable t) {
        log.error("Skipped item during READ phase: {}", t.getMessage());
        saveError(null, "READ", t, ErrorType.SKIP);
    }
    
    @Override
    public void onSkipInProcess(PersonDto item, Throwable t) {
        log.error("Skipped item during PROCESS phase. Item: {}, Error: {}", 
            item, t.getMessage());
        saveError(item, "PROCESS", t, ErrorType.SKIP);
    }
    
    @Override
    public void onSkipInWrite(RawData item, Throwable t) {
        log.error("Skipped item during WRITE phase. Item: {}, Error: {}", 
            item, t.getMessage());
        saveError(item, "WRITE", t, ErrorType.SKIP);
    }
    
    private void saveError(Object item, String phase, Throwable t, ErrorType type) {
        try {
            String itemJson = item != null ? objectMapper.writeValueAsString(item) : "N/A";
            String stackTrace = getStackTrace(t);
            
            BatchError error = BatchError.builder()
                .errorType(type)
                .errorPhase(phase)
                .itemData(itemJson)
                .errorMessage(t.getMessage())
                .stackTrace(stackTrace)
                .occurredAt(LocalDateTime.now())
                .resolved(false)
                .build();
            
            errorRepository.save(error);
            
        } catch (Exception e) {
            log.error("Failed to save error to database", e);
        }
    }
    
    private String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
```

### 5. Retry Listener

**Ubicación**: `src/main/java/com/batch/example/demo/batch/listener/CustomRetryListener.java`

```java
@Component
@Slf4j
public class CustomRetryListener implements RetryListener {
    
    @Autowired
    private BatchErrorRepository errorRepository;
    
    private final Map<String, Integer> retryCountMap = new ConcurrentHashMap<>();
    
    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, 
                                                  RetryCallback<T, E> callback) {
        String key = getRetryKey(context);
        retryCountMap.put(key, 0);
        log.debug("Opening retry context for: {}", key);
        return true;
    }
    
    @Override
    public <T, E extends Throwable> void onError(RetryContext context, 
                                                   RetryCallback<T, E> callback, 
                                                   Throwable throwable) {
        String key = getRetryKey(context);
        int count = retryCountMap.getOrDefault(key, 0) + 1;
        retryCountMap.put(key, count);
        
        log.warn("Retry attempt {} for {}: {}", count, key, throwable.getMessage());
        
        // Guardar en BD después de varios reintentos
        if (count >= 2) {
            saveRetryError(context, throwable, count);
        }
    }
    
    @Override
    public <T, E extends Throwable> void close(RetryContext context, 
                                                RetryCallback<T, E> callback, 
                                                Throwable throwable) {
        String key = getRetryKey(context);
        int count = retryCountMap.remove(key);
        
        if (throwable == null) {
            log.info("Retry succeeded after {} attempts for: {}", count, key);
        } else {
            log.error("Retry exhausted after {} attempts for: {}", count, key);
        }
    }
    
    private String getRetryKey(RetryContext context) {
        return context.getAttribute("item") != null ? 
            context.getAttribute("item").toString() : "unknown";
    }
    
    private void saveRetryError(RetryContext context, Throwable t, int count) {
        try {
            BatchError error = BatchError.builder()
                .errorType(ErrorType.RETRY)
                .errorPhase("PROCESS")
                .errorMessage(t.getMessage())
                .occurredAt(LocalDateTime.now())
                .retryCount(count)
                .resolved(false)
                .build();
            
            errorRepository.save(error);
        } catch (Exception e) {
            log.error("Failed to save retry error", e);
        }
    }
}
```

### 6. Step Execution Listener

**Ubicación**: `src/main/java/com/batch/example/demo/batch/listener/CustomStepListener.java`

```java
@Component
@Slf4j
public class CustomStepListener implements StepExecutionListener {
    
    @Autowired
    private BatchErrorRepository errorRepository;
    
    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("===== Step Starting: {} =====", stepExecution.getStepName());
        log.info("Job Execution ID: {}", stepExecution.getJobExecutionId());
        stepExecution.getExecutionContext().put("startTime", System.currentTimeMillis());
    }
    
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long startTime = stepExecution.getExecutionContext().getLong("startTime");
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("===== Step Completed: {} =====", stepExecution.getStepName());
        log.info("Duration: {} ms", duration);
        log.info("Read Count: {}", stepExecution.getReadCount());
        log.info("Write Count: {}", stepExecution.getWriteCount());
        log.info("Skip Count: {}", stepExecution.getSkipCount());
        log.info("Commit Count: {}", stepExecution.getCommitCount());
        log.info("Rollback Count: {}", stepExecution.getRollbackCount());
        
        // Verificar si hay muchos errores
        long errorCount = errorRepository.countByStepExecutionId(stepExecution.getId());
        if (errorCount > stepExecution.getReadCount() * 0.1) { // Más del 10%
            log.error("High error rate detected: {} errors out of {} reads", 
                errorCount, stepExecution.getReadCount());
            return ExitStatus.FAILED;
        }
        
        return stepExecution.getExitStatus();
    }
}
```

## 🚀 Instrucciones de Implementación

### Paso 1: Crear tabla de errores

```sql
CREATE TABLE batch_error (
    id BIGSERIAL PRIMARY KEY,
    job_execution_id BIGINT,
    step_execution_id BIGINT,
    error_type VARCHAR(50) NOT NULL,
    error_phase VARCHAR(50),
    item_data TEXT,
    error_message TEXT,
    stack_trace TEXT,
    occurred_at TIMESTAMP NOT NULL,
    retry_count INTEGER DEFAULT 0,
    resolved BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_batch_error_job ON batch_error(job_execution_id);
CREATE INDEX idx_batch_error_step ON batch_error(step_execution_id);
CREATE INDEX idx_batch_error_type ON batch_error(error_type);
CREATE INDEX idx_batch_error_resolved ON batch_error(resolved);
CREATE INDEX idx_batch_error_occurred ON batch_error(occurred_at);
```

### Paso 2: Configurar el Step con Fault Tolerance

```java
@Bean
public Step faultTolerantStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        ItemReader<PersonDto> reader,
        PersonValidator validator,
        ResilientPersonProcessor processor,
        ItemWriter<RawData> writer,
        CustomSkipListener skipListener,
        CustomRetryListener retryListener,
        CustomStepListener stepListener) {
    
    return new StepBuilder("faultTolerantStep", jobRepository)
            .<PersonDto, RawData>chunk(100, transactionManager)
            .reader(reader)
            .processor(new CompositeItemProcessor<>(Arrays.asList(validator, processor)))
            .writer(writer)
            // Configuración de Fault Tolerance
            .faultTolerant()
            // Skip Policy
            .skipLimit(100)
            .skip(ValidationException.class)
            .skip(ProcessingException.class)
            .skip(FlatFileParseException.class)
            .noSkip(NullPointerException.class) // No skipear NPE
            // Retry Policy
            .retryLimit(3)
            .retry(TransientException.class)
            .retry(DeadlockLoserDataAccessException.class)
            .retry(OptimisticLockingFailureException.class)
            .noRetry(ValidationException.class) // No reintentar errores de validación
            // Listeners
            .listener(skipListener)
            .listener((Object) retryListener)
            .listener(stepListener)
            .build();
}
```

### Paso 3: Configurar el Job

```java
@Bean
public Job resilientJob(
        JobRepository jobRepository,
        Step faultTolerantStep) {
    
    return new JobBuilder("resilientJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(faultTolerantStep)
            .listener(new JobExecutionListener() {
                @Override
                public void beforeJob(JobExecution jobExecution) {
                    log.info("Starting resilient batch job");
                }
                
                @Override
                public void afterJob(JobExecution jobExecution) {
                    BatchStatus status = jobExecution.getStatus();
                    log.info("Job completed with status: {}", status);
                    
                    if (status == BatchStatus.COMPLETED) {
                        sendSuccessNotification(jobExecution);
                    } else {
                        sendFailureNotification(jobExecution);
                    }
                }
            })
            .build();
}
```

### Paso 4: Crear Servicio de Reportes de Errores

```java
@Service
@Slf4j
public class ErrorReportService {
    
    @Autowired
    private BatchErrorRepository errorRepository;
    
    public ErrorSummary generateErrorReport(Long jobExecutionId) {
        List<BatchError> errors = errorRepository
            .findByJobExecutionId(jobExecutionId);
        
        Map<ErrorType, Long> errorsByType = errors.stream()
            .collect(Collectors.groupingBy(
                BatchError::getErrorType, 
                Collectors.counting()));
        
        Map<String, Long> errorsByPhase = errors.stream()
            .collect(Collectors.groupingBy(
                BatchError::getErrorPhase, 
                Collectors.counting()));
        
        return ErrorSummary.builder()
            .totalErrors(errors.size())
            .errorsByType(errorsByType)
            .errorsByPhase(errorsByPhase)
            .unresolvedErrors(errors.stream()
                .filter(e -> !e.getResolved())
                .count())
            .build();
    }
    
    public void exportErrorsToFile(Long jobExecutionId, String filePath) {
        List<BatchError> errors = errorRepository
            .findByJobExecutionId(jobExecutionId);
        
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath))) {
            writer.write("Error Report for Job: " + jobExecutionId);
            writer.newLine();
            writer.write("Generated at: " + LocalDateTime.now());
            writer.newLine();
            writer.newLine();
            
            for (BatchError error : errors) {
                writer.write("=".repeat(80));
                writer.newLine();
                writer.write("Error ID: " + error.getId());
                writer.newLine();
                writer.write("Type: " + error.getErrorType());
                writer.newLine();
                writer.write("Phase: " + error.getErrorPhase());
                writer.newLine();
                writer.write("Message: " + error.getErrorMessage());
                writer.newLine();
                writer.write("Occurred: " + error.getOccurredAt());
                writer.newLine();
                if (error.getRetryCount() > 0) {
                    writer.write("Retries: " + error.getRetryCount());
                    writer.newLine();
                }
                writer.newLine();
            }
            
            log.info("Error report exported to: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to export error report", e);
        }
    }
}
```

## 🧪 Pruebas

### 1. Datos de Prueba con Errores

```csv
name,age,email
Juan Pérez,30,juan.perez@email.com
,25,invalid@email  # Error: nombre vacío, email inválido
María García,-5,maria@email.com  # Error: edad negativa
Carlos López,200,carlos@email.com  # Error: edad > 150
Ana Martínez,30,  # Error: email inválido
Valid User,35,valid@email.com
```

### 2. Ejecutar Job y Verificar Errores

```bash
curl -X POST "http://localhost:8080/example-batch/batch/run-resilient"
```

### 3. Consultar Errores

```sql
-- Ver todos los errores
SELECT * FROM batch_error ORDER BY occurred_at DESC;

-- Resumen de errores por tipo
SELECT error_type, COUNT(*) as cantidad
FROM batch_error
GROUP BY error_type;

-- Errores no resueltos
SELECT * FROM batch_error 
WHERE resolved = false
ORDER BY occurred_at DESC;
```

## 💡 Recomendaciones

### Configuración de Skip

1. **Skip Limit Razonable**: No demasiado alto
   ```java
   .skipLimit(100) // Máximo 100 errores antes de fallar el job
   ```

2. **Skip Específico**: Solo skipear errores esperados
   ```java
   .skip(ValidationException.class)
   .noSkip(NullPointerException.class) // Nunca skipear NPE
   ```

3. **Skip Policy Personalizada**:
   ```java
   public class CustomSkipPolicy implements SkipPolicy {
       @Override
       public boolean shouldSkip(Throwable t, long skipCount) {
           // Lógica personalizada
           return skipCount < 100 && isRecoverableError(t);
       }
   }
   ```

### Configuración de Retry

1. **Retry Solo Errores Transitorios**:
   ```java
   .retry(TransientException.class)
   .retry(DeadlockLoserDataAccessException.class)
   .noRetry(ValidationException.class)
   ```

2. **Backoff Policy**:
   ```java
   @Bean
   public BackOffPolicy backOffPolicy() {
       ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
       policy.setInitialInterval(1000); // 1 segundo
       policy.setMaxInterval(10000); // 10 segundos
       policy.setMultiplier(2.0);
       return policy;
   }
   ```

3. **Retry Template Personalizado**:
   ```java
   RetryTemplate retryTemplate = RetryTemplate.builder()
       .maxAttempts(3)
       .exponentialBackoff(1000, 2, 10000)
       .retryOn(TransientException.class)
       .build();
   ```

### Validación Efectiva

1. **Validar Temprano**: En el reader o processor temprano
2. **Validaciones por Capas**: Sintáctica → Semántica → Negocio
3. **Mensajes Claros**: Incluir qué falló y por qué
4. **Bean Validation**: Usar anotaciones JSR-303

```java
@Data
public class PersonDto {
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be less than 100 characters")
    private String name;
    
    @NotNull(message = "Age is required")
    @Min(value = 0, message = "Age must be positive")
    @Max(value = 150, message = "Age must be realistic")
    private Integer age;
    
    @Email(message = "Invalid email format")
    private String email;
}
```

### Logging y Auditoría

1. **Log Levels Apropiados**:
   - ERROR: Fallos críticos
   - WARN: Skips y retries
   - INFO: Progreso del job
   - DEBUG: Detalles de cada item

2. **Structured Logging**: Usar formato JSON
3. **Correlation IDs**: Para trazar items específicos
4. **Log Aggregation**: Usar ELK, Splunk, etc.

## 📊 Dashboard de Monitoreo

### Queries para Dashboard

```sql
-- Tasa de error por hora
SELECT 
    DATE_TRUNC('hour', occurred_at) as hora,
    error_type,
    COUNT(*) as cantidad
FROM batch_error
WHERE occurred_at >= NOW() - INTERVAL '24 hours'
GROUP BY hora, error_type
ORDER BY hora DESC;

-- Top 10 mensajes de error
SELECT 
    error_message,
    COUNT(*) as ocurrencias
FROM batch_error
WHERE occurred_at >= NOW() - INTERVAL '7 days'
GROUP BY error_message
ORDER BY ocurrencias DESC
LIMIT 10;

-- Tasa de éxito por job
SELECT 
    je.job_execution_id,
    je.status,
    se.read_count,
    se.write_count,
    se.skip_count,
    ROUND((se.write_count::numeric / NULLIF(se.read_count, 0) * 100), 2) as success_rate
FROM batch_job_execution je
JOIN batch_step_execution se ON je.job_execution_id = se.job_execution_id
ORDER BY je.start_time DESC
LIMIT 20;
```

## ✅ Checklist de Implementación

- [ ] BatchError entidad y tabla creadas
- [ ] Validador implementado con reglas completas
- [ ] Processor resiliente implementado
- [ ] CustomSkipListener implementado
- [ ] CustomRetryListener implementado
- [ ] CustomStepListener implementado
- [ ] Step configurado con fault tolerance
- [ ] Skip policy configurada apropiadamente
- [ ] Retry policy configurada apropiadamente
- [ ] Job con listeners configurado
- [ ] ErrorReportService implementado
- [ ] Datos de prueba con errores preparados
- [ ] Pruebas de skip realizadas
- [ ] Pruebas de retry realizadas
- [ ] Verificación de logs de errores
- [ ] Dashboard de monitoreo configurado
- [ ] Documentación de errores actualizada

---

**Estado**: 📝 Pendiente
**Última actualización**: 2025-10-18

