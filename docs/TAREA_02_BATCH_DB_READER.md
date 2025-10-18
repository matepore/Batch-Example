# Tarea 2: Batch con Lectura desde Base de Datos

## 📋 Descripción

Esta tarea implementa un job batch que lee registros directamente desde una tabla PostgreSQL y los procesa en lotes. Es ideal para escenarios donde los datos ya están en la base de datos y necesitas procesarlos, transformarlos o migrarlos.

## 🎯 Objetivos

- ✅ Configurar un `JpaPagingItemReader` para leer desde PostgreSQL
- ✅ Implementar paginación eficiente para grandes volúmenes
- ✅ Procesar datos en chunks para optimizar memoria
- ✅ Transformar y enriquecer los datos durante el procesamiento
- ✅ Escribir resultados en una tabla destino o archivo

## 🏗️ Arquitectura

```
Scheduler/Controller → Batch Job
                          ↓
        PostgreSQL → Reader (Paginado)
                          ↓
                     Processor (Transformación)
                          ↓
                     Writer → Tabla Destino/Archivo
```

## 📝 Componentes a Implementar

### 1. Entidad de Origen

**Ubicación**: `src/main/java/com/batch/example/demo/entity/SourceData.java`

```java
@Entity
@Table(name = "source_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name")
    private String name;
    
    @Column(name = "age")
    private Integer age;
    
    @Column(name = "email")
    private String email;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "created_date")
    private LocalDateTime createdDate;
    
    @Column(name = "processed")
    private Boolean processed = false;
}
```

### 2. Entidad de Destino

**Ubicación**: `src/main/java/com/batch/example/demo/entity/ProcessedData.java`

```java
@Entity
@Table(name = "processed_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String fullName;
    private Integer age;
    private String normalizedEmail;
    private String category;
    private LocalDateTime processedDate;
    private Long sourceId;
}
```

### 3. Database Reader

**Ubicación**: `src/main/java/com/batch/example/demo/batch/reader/DatabaseReader.java`

```java
@Configuration
public class DatabaseReader {
    
    @Bean
    @StepScope
    public JpaPagingItemReader<SourceData> databaseItemReader(
            EntityManagerFactory entityManagerFactory) {
        
        return new JpaPagingItemReaderBuilder<SourceData>()
                .name("databaseItemReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT s FROM SourceData s WHERE s.processed = false ORDER BY s.id")
                .pageSize(1000)
                .build();
    }
}
```

### 4. Data Processor

**Ubicación**: `src/main/java/com/batch/example/demo/batch/processor/DataProcessor.java`

```java
@Component
@Slf4j
public class DataProcessor implements ItemProcessor<SourceData, ProcessedData> {
    
    @Override
    public ProcessedData process(SourceData source) throws Exception {
        log.debug("Processing record with id: {}", source.getId());
        
        ProcessedData processed = new ProcessedData();
        processed.setFullName(source.getName().toUpperCase());
        processed.setAge(source.getAge());
        processed.setNormalizedEmail(source.getEmail().toLowerCase().trim());
        processed.setCategory(categorizeByAge(source.getAge()));
        processed.setProcessedDate(LocalDateTime.now());
        processed.setSourceId(source.getId());
        
        return processed;
    }
    
    private String categorizeByAge(Integer age) {
        if (age < 18) return "MINOR";
        if (age < 30) return "YOUNG_ADULT";
        if (age < 60) return "ADULT";
        return "SENIOR";
    }
}
```

### 5. Database Writer

**Ubicación**: `src/main/java/com/batch/example/demo/batch/writer/ProcessedDataWriter.java`

```java
@Configuration
public class ProcessedDataWriter {
    
    @Bean
    public JpaItemWriter<ProcessedData> processedDataWriter(
            EntityManagerFactory entityManagerFactory) {
        
        JpaItemWriter<ProcessedData> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(entityManagerFactory);
        
        return writer;
    }
    
    @Bean
    public ItemWriter<ProcessedData> compositeWriter(
            JpaItemWriter<ProcessedData> jpaWriter,
            EntityManagerFactory entityManagerFactory) {
        
        // Writer adicional para actualizar el flag 'processed' en la tabla origen
        ItemWriter<ProcessedData> updateSourceWriter = items -> {
            EntityManager em = entityManagerFactory.createEntityManager();
            EntityTransaction tx = em.getTransaction();
            
            try {
                tx.begin();
                for (ProcessedData item : items) {
                    em.createQuery("UPDATE SourceData s SET s.processed = true WHERE s.id = :id")
                      .setParameter("id", item.getSourceId())
                      .executeUpdate();
                }
                tx.commit();
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            } finally {
                em.close();
            }
        };
        
        CompositeItemWriter<ProcessedData> compositeWriter = new CompositeItemWriter<>();
        compositeWriter.setDelegates(Arrays.asList(jpaWriter, updateSourceWriter));
        
        return compositeWriter;
    }
}
```

## 🚀 Instrucciones de Implementación

### Paso 1: Crear las tablas en la base de datos

```sql
-- Tabla de origen
CREATE TABLE source_data (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    age INTEGER,
    email VARCHAR(255),
    status VARCHAR(50),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE
);

-- Tabla de destino
CREATE TABLE processed_data (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255),
    age INTEGER,
    normalized_email VARCHAR(255),
    category VARCHAR(50),
    processed_date TIMESTAMP,
    source_id BIGINT,
    FOREIGN KEY (source_id) REFERENCES source_data(id)
);

-- Índices para mejor performance
CREATE INDEX idx_source_data_processed ON source_data(processed);
CREATE INDEX idx_source_data_created ON source_data(created_date);
CREATE INDEX idx_processed_data_source ON processed_data(source_id);
```

### Paso 2: Insertar datos de prueba

```sql
INSERT INTO source_data (name, age, email, status) VALUES
('Juan Pérez', 25, 'JUAN.PEREZ@EMAIL.COM', 'ACTIVE'),
('María García', 30, 'MARIA.GARCIA@EMAIL.COM', 'ACTIVE'),
('Carlos López', 45, 'CARLOS.LOPEZ@EMAIL.COM', 'ACTIVE'),
('Ana Martínez', 17, 'ANA.MARTINEZ@EMAIL.COM', 'ACTIVE'),
('Pedro Sánchez', 65, 'PEDRO.SANCHEZ@EMAIL.COM', 'INACTIVE');

-- Insertar datos masivos para pruebas de performance
INSERT INTO source_data (name, age, email, status)
SELECT 
    'User ' || generate_series,
    (RANDOM() * 70 + 10)::INTEGER,
    'user' || generate_series || '@email.com',
    CASE WHEN RANDOM() > 0.5 THEN 'ACTIVE' ELSE 'INACTIVE' END
FROM generate_series(1, 10000);
```

### Paso 3: Configurar el Step

```java
@Bean
public Step databaseProcessingStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        JpaPagingItemReader<SourceData> reader,
        ItemProcessor<SourceData, ProcessedData> processor,
        ItemWriter<ProcessedData> writer) {
    
    return new StepBuilder("databaseProcessingStep", jobRepository)
            .<SourceData, ProcessedData>chunk(1000, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skipLimit(50)
            .skip(Exception.class)
            .listener(new StepExecutionListener() {
                @Override
                public void beforeStep(StepExecution stepExecution) {
                    log.info("Starting database processing step");
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
public Job databaseProcessingJob(
        JobRepository jobRepository,
        Step databaseProcessingStep) {
    
    return new JobBuilder("databaseProcessingJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(databaseProcessingStep)
            .build();
}
```

### Paso 5: Crear el Controller

```java
@RestController
@RequestMapping("/batch")
@Slf4j
public class DatabaseBatchController {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    @Qualifier("databaseProcessingJob")
    private Job databaseProcessingJob;
    
    @PostMapping("/process-database")
    public ResponseEntity<Status> processDatabase() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();
            
            JobExecution execution = jobLauncher.run(databaseProcessingJob, params);
            
            Status status = Status.builder()
                    .status(execution.getStatus().toString())
                    .message("Database processing completed")
                    .recordsProcessed(execution.getStepExecutions()
                        .stream()
                        .mapToLong(StepExecution::getWriteCount)
                        .sum())
                    .build();
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error processing database", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Status.builder()
                        .status("FAILED")
                        .message(e.getMessage())
                        .build());
        }
    }
    
    @GetMapping("/database-status")
    public ResponseEntity<Map<String, Long>> getDatabaseStatus() {
        // Implementar consultas para obtener estadísticas
        return ResponseEntity.ok(Map.of(
            "total", sourceDataRepository.count(),
            "processed", sourceDataRepository.countByProcessed(true),
            "pending", sourceDataRepository.countByProcessed(false)
        ));
    }
}
```

## 🧪 Pruebas

### 1. Probar con cURL

```bash
# Ejecutar el job
curl -X POST "http://localhost:8080/example-batch/batch/process-database"

# Verificar el estado
curl -X GET "http://localhost:8080/example-batch/batch/database-status"
```

### 2. Verificar resultados en la base de datos

```sql
-- Ver registros procesados
SELECT * FROM processed_data ORDER BY processed_date DESC LIMIT 10;

-- Verificar que se actualizó el flag
SELECT 
    COUNT(*) FILTER (WHERE processed = true) as procesados,
    COUNT(*) FILTER (WHERE processed = false) as pendientes,
    COUNT(*) as total
FROM source_data;

-- Ver registros por categoría
SELECT category, COUNT(*) as cantidad
FROM processed_data
GROUP BY category
ORDER BY cantidad DESC;
```

## 💡 Recomendaciones

### Performance

1. **Page Size Óptimo**: Ajustar según el tamaño de los registros
   ```java
   .pageSize(1000) // Comenzar con 1000 y ajustar
   ```

2. **Índices en la Base de Datos**:
   - Índice en la columna de filtro (`processed`)
   - Índice en la columna de ordenamiento (`id`)

3. **Fetch Size**: Configurar en Hibernate
   ```properties
   spring.jpa.properties.hibernate.jdbc.fetch_size=1000
   ```

4. **Read-Only Transactions**: Para el reader
   ```java
   @Transactional(readOnly = true)
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
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

### Queries de Monitoreo

```sql
-- Ver progreso del procesamiento
SELECT 
    COUNT(*) FILTER (WHERE processed = true) * 100.0 / COUNT(*) as porcentaje_completado,
    COUNT(*) FILTER (WHERE processed = false) as registros_pendientes
FROM source_data;

-- Ver performance por categoría
SELECT 
    category,
    COUNT(*) as cantidad,
    AVG(EXTRACT(EPOCH FROM (processed_date - 
        (SELECT created_date FROM source_data WHERE id = processed_data.source_id)))) as avg_tiempo_segundos
FROM processed_data
GROUP BY category;

-- Identificar registros problemáticos (si hay skip)
SELECT * FROM source_data 
WHERE processed = false 
AND created_date < NOW() - INTERVAL '1 hour'
LIMIT 100;
```

### Métricas Importantes

- **Throughput**: Registros procesados por segundo
- **Read/Write Count**: Desde StepExecution
- **Skip Count**: Registros saltados
- **Duration**: Tiempo total de ejecución
- **Memory Usage**: Monitorear heap usage

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

