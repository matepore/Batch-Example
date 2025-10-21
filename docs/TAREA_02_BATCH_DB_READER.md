# Tarea 2: Batch con Lectura desde Base de Datos

## 📋 Descripción

Esta tarea implementa un job batch que lee registros de la tabla `person` en PostgreSQL, los transforma y los almacena en la tabla `raw_data` como registros JSONB con type 'CSV_FILE'. Es ideal para migrar o procesar datos estructurados hacia un formato flexible.

## 🎯 Objetivos

- ✅ Configurar un `JpaPagingItemReader` para leer desde la tabla `person`
- ✅ Implementar paginación eficiente para grandes volúmenes (150 registros)
- ✅ Procesar datos en chunks para optimizar memoria
- ✅ Transformar registros Person a formato JSON
- ✅ Escribir resultados en la tabla `raw_data` con campo JSONB y type='CSV_FILE'

## 🏗️ Arquitectura

```
Scheduler/Controller → Batch Job
                          ↓
        Tabla Person → Reader (Paginado)
                          ↓
                     Processor (Person → JSON)
                          ↓
                     Writer → Tabla RawData (JSONB, type='CSV_FILE')
```

## 📝 Componentes a Implementar

### 1. Entidad de Origen (Person)

**Ubicación**: `src/main/java/com/batch/example/demo/entity/Person.java`

```java
@Entity
@Table(name = "person", schema = "batch_example")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Person {
    
    @Id
    @Column(name = "dni")
    private String dni;
    
    @Column(name = "nombre")
    private String nombre;
    
    @Column(name = "apellido")
    private String apellido;
    
    @Column(name = "edad")
    private Integer edad;
}
```

### 2. Entidad de Destino (RawData)

**Ubicación**: `src/main/java/com/batch/example/demo/entity/RawData.java`

```java
@Data
@Entity
@Table(name = "raw_data", schema = "batch_example")
@AllArgsConstructor
@NoArgsConstructor
public class RawData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "process_id")
    private Long processId;

    @Column(name = "type")
    private String type;

    @Type(JsonBinaryType.class)
    @Column(name = "data", columnDefinition = "jsonb")
    private JsonNode data;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "status")
    private String status;
}
```

### 3. Database Reader

**Ubicación**: `src/main/java/com/batch/example/demo/batch/reader/PersonDatabaseReader.java`

```java
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
```

### 4. Data Processor

**Ubicación**: `src/main/java/com/batch/example/demo/batch/processor/PersonToRawDataProcessor.java`

```java
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
```

### 5. Database Writer

**Ubicación**: `src/main/java/com/batch/example/demo/batch/writer/RawDataWriter.java`

```java
@Configuration
public class RawDataWriter {
    
    @Bean
    public JpaItemWriter<RawData> rawDataWriter(
            EntityManagerFactory entityManagerFactory) {
        
        JpaItemWriter<RawData> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(entityManagerFactory);
        
        return writer;
    }
}
```

## 🚀 Instrucciones de Implementación

### Paso 1: Verificar las tablas en la base de datos

Las tablas ya están creadas en el script `01_batch_example_container.sql`:

```sql
-- Tabla de origen (ya existe)
CREATE TABLE IF NOT EXISTS batch_example.person (
    nombre   VARCHAR(100),
    apellido VARCHAR(100),
    edad     INTEGER,
    dni      VARCHAR(50) PRIMARY KEY
);

-- Tabla de destino (ya existe)
CREATE TABLE IF NOT EXISTS batch_example.raw_data (
    process_id SERIAL PRIMARY KEY,
    type VARCHAR(255),
    data JSONB,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    status VARCHAR(45)
);

-- Índice recomendado para mejor performance
CREATE INDEX IF NOT EXISTS idx_person_dni ON batch_example.person(dni);
CREATE INDEX IF NOT EXISTS idx_raw_data_type ON batch_example.raw_data(type);
```

### Paso 2: Verificar datos de prueba

La tabla `person` ya contiene 150 registros insertados desde el script `02_batch_example_container_data.sql`.
Puedes verificar con:

```sql
-- Ver cantidad de registros
SELECT COUNT(*) FROM batch_example.person;

-- Ver algunos ejemplos
SELECT * FROM batch_example.person LIMIT 10;
```

### Paso 3: Configurar el Step

```java
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
                    log.info("Starting person to raw_data migration step");
                }
                
                @Override
                public ExitStatus afterStep(StepExecution stepExecution) {
                    log.info("Completed. Read: {}, Written: {}", 
                        stepExecution.getReadCount(),
                        stepExecution.getWriteCount());
                    return stepExecution.getExitStatus();
                }
            })
            .build();
}
```

### Paso 4: Configurar el Job

```java
@Bean
public Job personToRawDataJob(
        JobRepository jobRepository,
        Step personToRawDataStep) {
    
    return new JobBuilder("personToRawDataJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(personToRawDataStep)
            .build();
}
```

### Paso 5: Crear el Controller

```java
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
```

## 🧪 Pruebas

### 1. Probar con cURL

```bash
# Ejecutar el job
curl -X POST "http://localhost:8080/example-batch/batch/process-person-to-rawdata"

# Verificar el estado
curl -X GET "http://localhost:8080/example-batch/batch/rawdata-status"
```

### 2. Verificar resultados en la base de datos

```sql
-- Ver registros procesados en raw_data
SELECT 
    process_id,
    type,
    data,
    created_at,
    created_by,
    status
FROM batch_example.raw_data 
WHERE type = 'CSV_FILE'
ORDER BY created_at DESC 
LIMIT 10;

-- Verificar la cantidad total de registros migrados
SELECT 
    COUNT(*) as total_registros,
    type
FROM batch_example.raw_data
GROUP BY type;

-- Ver el contenido JSON de algunos registros
SELECT 
    process_id,
    data->>'dni' as dni,
    data->>'nombre' as nombre,
    data->>'apellido' as apellido,
    data->>'edad' as edad,
    created_at
FROM batch_example.raw_data
WHERE type = 'CSV_FILE'
LIMIT 20;

-- Verificar que se procesaron todos los registros de person
SELECT 
    (SELECT COUNT(*) FROM batch_example.person) as total_person,
    (SELECT COUNT(*) FROM batch_example.raw_data WHERE type = 'CSV_FILE') as total_raw_data;
```

## 💡 Recomendaciones

### Performance

1. **Page Size Óptimo**: Para 150 registros, un pageSize de 50 es adecuado
   ```java
   .pageSize(50) // Procesa los 150 registros en 3 páginas
   ```

2. **Índices en la Base de Datos**:
   - Índice en `person.dni` (ya es PK, tiene índice automático)
   - Índice en `raw_data.type` para consultas rápidas por tipo

3. **Chunk Size**: Configurar igual al page size para consistencia
   ```java
   .chunk(50, transactionManager) // Mismo tamaño que el page size
   ```

4. **JSONB Performance**: El tipo JSONB de PostgreSQL es eficiente
   - Permite indexación de campos dentro del JSON
   - Queries rápidas con operadores JSON de PostgreSQL

### Conversión a JSON

1. **ObjectMapper**: Usar el ObjectMapper configurado de Spring
   ```java
   @Autowired
   private ObjectMapper objectMapper;
   
   JsonNode jsonData = objectMapper.valueToTree(person);
   ```

2. **Campos del JSON**: El JSON contendrá todos los campos de Person
   ```json
   {
     "dni": "12345678A",
     "nombre": "Juan",
     "apellido": "Perez",
     "edad": 30
   }
   ```

3. **Validación**: Asegurarse de que no haya valores null problemáticos

### Manejo de Datos

1. **Type Field**: Siempre establecer `type = "CSV_FILE"` para identificar el origen
2. **Auditoría**: Llenar campos de auditoría (created_by, created_at, status)
3. **Status**: Usar valores consistentes ("PROCESSED", "PENDING", "ERROR")

### Queries Optimizadas para JSONB

```sql
-- Buscar por campo dentro del JSON
SELECT * FROM batch_example.raw_data 
WHERE type = 'CSV_FILE' 
AND data->>'dni' = '12345678A';

-- Índice GIN para mejor performance en queries JSON
CREATE INDEX idx_raw_data_jsonb ON batch_example.raw_data USING GIN (data);

-- Buscar por edad mayor a 30
SELECT * FROM batch_example.raw_data 
WHERE type = 'CSV_FILE' 
AND (data->>'edad')::INTEGER > 30;
```

### Paginación Eficiente

1. **Usar JpaPagingItemReader** en lugar de JpaCursorItemReader para grandes volúmenes
2. **Ordenar por ID** para paginación consistente
3. **Evitar ORDER BY en columnas no indexadas**

### Procesamiento Incremental

1. **Marcar registros procesados**: Usar un flag booleano
2. **Timestamp de procesamiento**: Para auditoría
3. **Procesar solo nuevos registros**: Filtrar en la query

```java
.queryString("SELECT s FROM SourceData s WHERE s.processed = false AND s.createdDate > :lastProcessed ORDER BY s.id")
```

### Manejo de Transacciones

1. **Chunk-oriented Processing**: Cada chunk es una transacción
2. **Rollback en errores**: Spring Batch maneja automáticamente
3. **Skip Policy**: Para registros problemáticos

```java
.faultTolerant()
.skipLimit(100)
.skip(DataIntegrityViolationException.class)
.retryLimit(3)
.retry(DeadlockLoserDataAccessException.class)
```

### Queries Optimizadas

1. **Evitar N+1 queries**: Usar JOIN FETCH si hay relaciones
   ```java
   "SELECT s FROM SourceData s LEFT JOIN FETCH s.details WHERE s.processed = false"
   ```

2. **Proyecciones**: Si no necesitas todos los campos
   ```java
   "SELECT NEW com.batch.example.demo.dto.SourceDTO(s.id, s.name, s.email) FROM SourceData s"
   ```

3. **Native Queries**: Para casos complejos
   ```java
   .queryString("SELECT * FROM source_data WHERE processed = false")
   .nativeQuery(true)
   ```

## 🔍 Monitoreo y Métricas

### Logs Detallados

```properties
logging.level.org.springframework.batch=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.com.batch.example.demo.batch.processor=DEBUG
```

### Queries de Monitoreo

```sql
-- Ver progreso del procesamiento
SELECT 
    (SELECT COUNT(*) FROM batch_example.person) as total_personas,
    (SELECT COUNT(*) FROM batch_example.raw_data WHERE type = 'CSV_FILE') as personas_procesadas,
    ROUND(
        (SELECT COUNT(*)::DECIMAL FROM batch_example.raw_data WHERE type = 'CSV_FILE') * 100.0 / 
        (SELECT COUNT(*) FROM batch_example.person), 
        2
    ) as porcentaje_completado;

-- Ver últimos registros procesados
SELECT 
    process_id,
    data->>'dni' as dni,
    data->>'nombre' as nombre,
    data->>'apellido' as apellido,
    created_at,
    status
FROM batch_example.raw_data
WHERE type = 'CSV_FILE'
ORDER BY created_at DESC
LIMIT 20;

-- Verificar distribución de edades procesadas
SELECT 
    CASE 
        WHEN (data->>'edad')::INTEGER < 18 THEN 'Menor de 18'
        WHEN (data->>'edad')::INTEGER BETWEEN 18 AND 30 THEN '18-30'
        WHEN (data->>'edad')::INTEGER BETWEEN 31 AND 50 THEN '31-50'
        WHEN (data->>'edad')::INTEGER > 50 THEN 'Mayor de 50'
    END as rango_edad,
    COUNT(*) as cantidad
FROM batch_example.raw_data
WHERE type = 'CSV_FILE'
GROUP BY rango_edad
ORDER BY cantidad DESC;

-- Ver performance del job desde las tablas de Spring Batch
SELECT 
    je.job_execution_id,
    je.start_time,
    je.end_time,
    EXTRACT(EPOCH FROM (je.end_time - je.start_time)) as duracion_segundos,
    se.read_count,
    se.write_count,
    se.commit_count,
    se.read_skip_count,
    se.write_skip_count
FROM batch_example.batch_job_execution je
JOIN batch_example.batch_step_execution se ON je.job_execution_id = se.job_execution_id
WHERE je.job_instance_id = (
    SELECT job_instance_id 
    FROM batch_example.batch_job_instance 
    WHERE job_name = 'personToRawDataJob'
    ORDER BY job_instance_id DESC 
    LIMIT 1
);
```

### Métricas Importantes

- **Throughput**: Registros procesados por segundo (150 registros / duración)
- **Read Count**: Total de registros leídos de la tabla person
- **Write Count**: Total de registros escritos en raw_data
- **Skip Count**: Registros que fallaron y fueron saltados
- **Commit Count**: Número de chunks procesados exitosamente

## 🐛 Troubleshooting

### Problema: OutOfMemoryError

**Solución**:
1. Reducir chunk size
2. Reducir page size
3. Aumentar memoria JVM: `-Xmx2g`
4. Verificar que no haya memory leaks

### Problema: Lectura lenta

**Soluciones**:
1. Verificar índices en columnas de filtro
2. Optimizar la query
3. Aumentar fetch size
4. Considerar partitioning

### Problema: Deadlocks

**Soluciones**:
1. Configurar retry policy
2. Reducir chunk size
3. Ajustar isolation level
4. Revisar índices

### Problema: Registros duplicados

**Solución**:
- Asegurar que la query tenga ORDER BY consistente
- Usar RunIdIncrementer para evitar re-procesamiento
- Verificar que no haya ejecuciones concurrentes

## 📊 Optimización Avanzada

### Partitioning

Para procesar millones de registros, usar partitioning:

```java
@Bean
public Step masterStep(
        JobRepository jobRepository,
        Partitioner partitioner,
        Step workerStep,
        TaskExecutor taskExecutor) {
    
    return new StepBuilder("masterStep", jobRepository)
            .partitioner("workerStep", partitioner)
            .step(workerStep)
            .gridSize(10)
            .taskExecutor(taskExecutor)
            .build();
}

@Bean
public Partitioner partitioner(@Value("${batch.partition.size}") int gridSize) {
    return new ColumnRangePartitioner(sourceDataRepository, gridSize);
}
```

### Procesamiento Paralelo

```java
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(25);
    executor.setThreadNamePrefix("batch-");
    executor.initialize();
    return executor;
}
```

## ✅ Checklist de Implementación

- [ ] Entidades de origen y destino creadas
- [ ] Tablas creadas en PostgreSQL
- [ ] Índices creados para optimización
- [ ] DatabaseReader configurado con paginación
- [ ] DataProcessor implementado
- [ ] Writer configurado (simple o composite)
- [ ] Step configurado con chunk processing
- [ ] Job configurado con incrementer
- [ ] Controller con endpoint creado
- [ ] Repository methods implementados
- [ ] Datos de prueba insertados
- [ ] Pruebas unitarias creadas
- [ ] Pruebas de integración realizadas
- [ ] Performance testing realizado
- [ ] Documentación actualizada
- [ ] Logs y monitoreo configurados
- [ ] Manejo de errores implementado

---

**Estado**: 🚧 En Progreso
**Última actualización**: 2025-10-18

