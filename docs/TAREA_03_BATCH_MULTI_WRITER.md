# Tarea 3: Batch con Escritura en Múltiples Destinos

## 📋 Descripción

Esta tarea implementa un job batch que escribe datos procesados simultáneamente en múltiples destinos: base de datos PostgreSQL y archivos (CSV, JSON, XML). Es ideal para escenarios de generación de reportes, backups, o cuando necesitas sincronizar datos entre diferentes sistemas.

## 🎯 Objetivos

- ✅ Implementar `CompositeItemWriter` para escritura múltiple
- ✅ Escribir simultáneamente en PostgreSQL
- ✅ Generar archivos CSV de salida
- ✅ Generar archivos JSON de salida
- ✅ Configurar rutas de salida dinámicas
- ✅ Manejar errores en escritura múltiple

## 🏗️ Arquitectura

```
Reader → Processor → CompositeWriter
                          ├→ DatabaseWriter (PostgreSQL)
                          ├→ FileWriter (CSV)
                          ├→ FileWriter (JSON)
                          └→ FileWriter (XML)
```

## 📝 Componentes a Implementar

### 1. Modelo de Datos Unificado

**Ubicación**: `src/main/java/com/batch/example/demo/model/ExportDto.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportDto {
    private Long id;
    private String name;
    private Integer age;
    private String email;
    private String category;
    private LocalDateTime exportDate;
    private Map<String, Object> metadata;
    
    // Métodos de utilidad para conversión
    public String toCsvLine() {
        return String.format("%d,%s,%d,%s,%s,%s",
            id, name, age, email, category, 
            exportDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
    
    public String toJson() {
        // Implementar serialización JSON
        return new ObjectMapper().writeValueAsString(this);
    }
}
```

### 2. Entidad de Exportación

**Ubicación**: `src/main/java/com/batch/example/demo/entity/ExportLog.java`

```java
@Entity
@Table(name = "export_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "record_id")
    private Long recordId;
    
    @Column(name = "record_name")
    private String recordName;
    
    @Column(name = "export_date")
    private LocalDateTime exportDate;
    
    @Column(name = "export_format")
    private String exportFormat; // DB, CSV, JSON, XML
    
    @Column(name = "file_path")
    private String filePath;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ExportStatus status;
    
    @Column(name = "error_message")
    private String errorMessage;
}

enum ExportStatus {
    SUCCESS, FAILED, PARTIAL
}
```

### 3. Database Writer

**Ubicación**: `src/main/java/com/batch/example/demo/batch/writer/DatabaseExportWriter.java`

```java
@Component
@Slf4j
public class DatabaseExportWriter implements ItemWriter<ExportDto> {
    
    @Autowired
    private ExportLogRepository exportLogRepository;
    
    @Override
    public void write(Chunk<? extends ExportDto> chunk) throws Exception {
        log.info("Writing {} records to database", chunk.size());
        
        List<ExportLog> logs = chunk.getItems().stream()
            .map(dto -> {
                ExportLog log = new ExportLog();
                log.setRecordId(dto.getId());
                log.setRecordName(dto.getName());
                log.setExportDate(dto.getExportDate());
                log.setExportFormat("DATABASE");
                log.setStatus(ExportStatus.SUCCESS);
                return log;
            })
            .collect(Collectors.toList());
        
        exportLogRepository.saveAll(logs);
        log.info("Successfully written {} records to database", logs.size());
    }
}
```

### 4. CSV File Writer

**Ubicación**: `src/main/java/com/batch/example/demo/batch/writer/CsvExportWriter.java`

```java
@Component
@Slf4j
public class CsvExportWriter implements ItemWriter<ExportDto> {
    
    @Value("${batch.export.csv.path:./exports/csv}")
    private String exportPath;
    
    private final Object lock = new Object();
    
    @Override
    public void write(Chunk<? extends ExportDto> chunk) throws Exception {
        log.info("Writing {} records to CSV", chunk.size());
        
        String fileName = String.format("export_%s.csv", 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        Path filePath = Paths.get(exportPath, fileName);
        
        // Crear directorio si no existe
        Files.createDirectories(filePath.getParent());
        
        synchronized (lock) {
            try (BufferedWriter writer = Files.newBufferedWriter(filePath, 
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                
                // Escribir header si es un archivo nuevo
                if (Files.size(filePath) == 0) {
                    writer.write("id,name,age,email,category,export_date");
                    writer.newLine();
                }
                
                for (ExportDto dto : chunk.getItems()) {
                    writer.write(dto.toCsvLine());
                    writer.newLine();
                }
                
                writer.flush();
                log.info("Successfully written {} records to CSV: {}", 
                    chunk.size(), filePath);
            }
        }
    }
}
```

### 5. JSON File Writer

**Ubicación**: `src/main/java/com/batch/example/demo/batch/writer/JsonExportWriter.java`

```java
@Component
@Slf4j
public class JsonExportWriter implements ItemWriter<ExportDto> {
    
    @Value("${batch.export.json.path:./exports/json}")
    private String exportPath;
    
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    
    @Override
    public void write(Chunk<? extends ExportDto> chunk) throws Exception {
        log.info("Writing {} records to JSON", chunk.size());
        
        String fileName = String.format("export_%s.json", 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        Path filePath = Paths.get(exportPath, fileName);
        
        Files.createDirectories(filePath.getParent());
        
        Map<String, Object> jsonOutput = new HashMap<>();
        jsonOutput.put("exportDate", LocalDateTime.now());
        jsonOutput.put("totalRecords", chunk.size());
        jsonOutput.put("records", chunk.getItems());
        
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(filePath.toFile(), jsonOutput);
        
        log.info("Successfully written {} records to JSON: {}", 
            chunk.size(), filePath);
    }
}
```

### 6. Composite Writer Configuration

**Ubicación**: `src/main/java/com/batch/example/demo/batch/writer/CompositeWriterConfig.java`

```java
@Configuration
@Slf4j
public class CompositeWriterConfig {
    
    @Bean
    public CompositeItemWriter<ExportDto> multiDestinationWriter(
            DatabaseExportWriter databaseWriter,
            CsvExportWriter csvWriter,
            JsonExportWriter jsonWriter) {
        
        CompositeItemWriter<ExportDto> compositeWriter = new CompositeItemWriter<>();
        
        // Lista de writers delegados
        List<ItemWriter<? super ExportDto>> delegates = new ArrayList<>();
        delegates.add(databaseWriter);
        delegates.add(csvWriter);
        delegates.add(jsonWriter);
        
        compositeWriter.setDelegates(delegates);
        
        log.info("Configured composite writer with {} delegates", delegates.size());
        
        return compositeWriter;
    }
    
    @Bean
    public CompositeItemWriter<ExportDto> selectiveWriter(
            @Value("${batch.export.enabled.database:true}") boolean dbEnabled,
            @Value("${batch.export.enabled.csv:true}") boolean csvEnabled,
            @Value("${batch.export.enabled.json:true}") boolean jsonEnabled,
            DatabaseExportWriter databaseWriter,
            CsvExportWriter csvWriter,
            JsonExportWriter jsonWriter) {
        
        CompositeItemWriter<ExportDto> compositeWriter = new CompositeItemWriter<>();
        List<ItemWriter<? super ExportDto>> delegates = new ArrayList<>();
        
        if (dbEnabled) {
            delegates.add(databaseWriter);
            log.info("Database writer enabled");
        }
        if (csvEnabled) {
            delegates.add(csvWriter);
            log.info("CSV writer enabled");
        }
        if (jsonEnabled) {
            delegates.add(jsonWriter);
            log.info("JSON writer enabled");
        }
        
        compositeWriter.setDelegates(delegates);
        return compositeWriter;
    }
}
```

## 🚀 Instrucciones de Implementación

### Paso 1: Crear tabla de logs

```sql
CREATE TABLE export_log (
    id BIGSERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL,
    record_name VARCHAR(255),
    export_date TIMESTAMP NOT NULL,
    export_format VARCHAR(50) NOT NULL,
    file_path VARCHAR(500),
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_export_log_date ON export_log(export_date);
CREATE INDEX idx_export_log_format ON export_log(export_format);
CREATE INDEX idx_export_log_status ON export_log(status);
```

### Paso 2: Configurar properties

```yaml
# application.yml
batch:
  export:
    csv:
      path: ./exports/csv
    json:
      path: ./exports/json
    xml:
      path: ./exports/xml
    enabled:
      database: true
      csv: true
      json: true
      xml: false
```

### Paso 3: Implementar el Step

```java
@Bean
public Step multiWriterStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        ItemReader<SourceData> reader,
        ItemProcessor<SourceData, ExportDto> processor,
        CompositeItemWriter<ExportDto> multiDestinationWriter) {
    
    return new StepBuilder("multiWriterStep", jobRepository)
            .<SourceData, ExportDto>chunk(100, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(multiDestinationWriter)
            .faultTolerant()
            .skipLimit(10)
            .skip(IOException.class)
            .skip(DataAccessException.class)
            .listener(new ItemWriteListener<ExportDto>() {
                @Override
                public void beforeWrite(Chunk<? extends ExportDto> items) {
                    log.debug("About to write {} items", items.size());
                }
                
                @Override
                public void afterWrite(Chunk<? extends ExportDto> items) {
                    log.info("Successfully wrote {} items to all destinations", 
                        items.size());
                }
                
                @Override
                public void onWriteError(Exception exception, 
                                        Chunk<? extends ExportDto> items) {
                    log.error("Error writing {} items: {}", 
                        items.size(), exception.getMessage());
                }
            })
            .build();
}
```

### Paso 4: Implementar el Job

```java
@Bean
public Job multiDestinationExportJob(
        JobRepository jobRepository,
        Step multiWriterStep) {
    
    return new JobBuilder("multiDestinationExportJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(multiWriterStep)
            .listener(new JobExecutionListener() {
                @Override
                public void beforeJob(JobExecution jobExecution) {
                    log.info("Starting multi-destination export job");
                }
                
                @Override
                public void afterJob(JobExecution jobExecution) {
                    log.info("Completed multi-destination export. Status: {}", 
                        jobExecution.getStatus());
                    
                    jobExecution.getStepExecutions().forEach(step -> {
                        log.info("Step: {}, Read: {}, Written: {}, Skipped: {}",
                            step.getStepName(),
                            step.getReadCount(),
                            step.getWriteCount(),
                            step.getSkipCount());
                    });
                }
            })
            .build();
}
```

### Paso 5: Crear Controller

```java
@RestController
@RequestMapping("/batch")
@Slf4j
public class ExportBatchController {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    @Qualifier("multiDestinationExportJob")
    private Job exportJob;
    
    @PostMapping("/export")
    public ResponseEntity<ExportResponse> startExport(
            @RequestParam(required = false) String format) {
        
        try {
            JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis());
            
            if (format != null) {
                builder.addString("exportFormat", format);
            }
            
            JobExecution execution = jobLauncher.run(exportJob, builder.toJobParameters());
            
            ExportResponse response = ExportResponse.builder()
                .jobId(execution.getId())
                .status(execution.getStatus().toString())
                .startTime(execution.getStartTime())
                .message("Export job started successfully")
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error starting export job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ExportResponse.builder()
                    .status("FAILED")
                    .message(e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/export/{jobId}/status")
    public ResponseEntity<JobStatusResponse> getExportStatus(
            @PathVariable Long jobId) {
        
        // Implementar consulta del estado del job
        return ResponseEntity.ok(JobStatusResponse.builder()
            .jobId(jobId)
            .status("COMPLETED")
            .build());
    }
}
```

## 🧪 Pruebas

### 1. Ejecutar exportación completa

```bash
curl -X POST "http://localhost:8080/example-batch/batch/export"
```

### 2. Exportar a formato específico

```bash
curl -X POST "http://localhost:8080/example-batch/batch/export?format=csv"
```

### 3. Verificar archivos generados

```bash
# Windows
dir exports\csv
dir exports\json

# Linux/Mac
ls -la exports/csv
ls -la exports/json
```

### 4. Verificar en base de datos

```sql
SELECT export_format, status, COUNT(*) as cantidad
FROM export_log
GROUP BY export_format, status
ORDER BY export_format;

-- Ver últimas exportaciones
SELECT * FROM export_log 
ORDER BY export_date DESC 
LIMIT 20;
```

## 💡 Recomendaciones

### Performance

1. **Chunk Size Apropiado**: Para múltiples writers, usar chunks menores
   ```java
   .chunk(100) // En lugar de 5000
   ```

2. **Buffering en Archivos**: Usar BufferedWriter con buffer grande
   ```java
   new BufferedWriter(writer, 8192 * 4)
   ```

3. **Async Writing**: Considerar escritura asíncrona para archivos
   ```java
   @Async
   public CompletableFuture<Void> writeAsync(List<ExportDto> items)
   ```

### Manejo de Errores

1. **Skip Policy por Writer**: Continuar si un writer falla
   ```java
   .skip(IOException.class) // Para fallos de archivo
   .skip(DataAccessException.class) // Para fallos de DB
   ```

2. **Fallback Writer**: Si un writer falla, intentar con otro
   ```java
   public class FallbackWriter implements ItemWriter<ExportDto> {
       // Implementar lógica de fallback
   }
   ```

3. **Retry en Escritura**:
   ```java
   .retryLimit(3)
   .retry(TransientDataAccessException.class)
   ```

### Gestión de Archivos

1. **Rotación de Archivos**: Crear nuevo archivo por chunk o por tiempo
2. **Compresión**: Comprimir archivos grandes automáticamente
3. **Limpieza**: Implementar job de limpieza de archivos antiguos

```java
@Scheduled(cron = "0 0 2 * * *") // 2 AM diario
public void cleanOldExports() {
    // Eliminar archivos > 30 días
}
```

### Transacciones

1. **Configurar correctamente**: Cada writer puede tener su transacción
2. **Rollback parcial**: Si un writer falla, otros pueden completarse
3. **Compensación**: Implementar lógica de compensación si es necesario

### Seguridad

1. **Validar Rutas**: Prevenir path traversal
2. **Permisos de Archivos**: Configurar correctamente
3. **Encriptación**: Encriptar archivos sensibles
4. **Auditoría**: Registrar todas las exportaciones

## 📊 Monitoreo

### Métricas a Rastrear

```java
@Component
public class ExportMetrics {
    
    @Autowired
    private MeterRegistry registry;
    
    public void recordExport(String format, int count, boolean success) {
        Counter.builder("batch.export.records")
            .tag("format", format)
            .tag("status", success ? "success" : "failure")
            .register(registry)
            .increment(count);
    }
}
```

### Queries de Análisis

```sql
-- Reportes de exportación por formato
SELECT 
    export_format,
    DATE(export_date) as fecha,
    COUNT(*) as total_registros,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as exitosos,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as fallidos
FROM export_log
WHERE export_date >= CURRENT_DATE - INTERVAL '7 days'
GROUP BY export_format, DATE(export_date)
ORDER BY fecha DESC, export_format;

-- Tamaño de archivos generados
SELECT 
    file_path,
    export_date,
    status
FROM export_log
WHERE export_format IN ('CSV', 'JSON')
ORDER BY export_date DESC;
```

## 🐛 Troubleshooting

### Problema: Archivos no se crean

**Soluciones**:
1. Verificar permisos del directorio
2. Crear directorios antes: `Files.createDirectories()`
3. Revisar logs para errores de I/O

### Problema: OutOfMemory al escribir archivos grandes

**Soluciones**:
1. Reducir chunk size
2. Usar streaming en lugar de cargar todo en memoria
3. Implementar flush periódico

### Problema: Inconsistencia entre destinos

**Solución**: Implementar transacción distribuida o patrón Saga

### Problema: Writer lento ralentiza todo el proceso

**Solución**:
1. Identificar el writer problemático
2. Optimizar ese writer específicamente
3. Considerar escritura asíncrona
4. Usar ThreadPoolTaskExecutor

## ✅ Checklist de Implementación

- [ ] ExportDto modelo creado
- [ ] ExportLog entidad y tabla creadas
- [ ] DatabaseExportWriter implementado
- [ ] CsvExportWriter implementado
- [ ] JsonExportWriter implementado
- [ ] CompositeWriterConfig configurado
- [ ] Properties de configuración añadidas
- [ ] Step con composite writer configurado
- [ ] Job de exportación configurado
- [ ] Controller de exportación creado
- [ ] Listeners de logging implementados
- [ ] Manejo de errores configurado
- [ ] Directorios de exportación creados
- [ ] Pruebas realizadas para cada formato
- [ ] Verificación de archivos generados
- [ ] Verificación en base de datos
- [ ] Documentación actualizada

---

**Estado**: 📝 Pendiente
**Última actualización**: 2025-10-18

