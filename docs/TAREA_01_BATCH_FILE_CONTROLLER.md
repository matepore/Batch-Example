# Tarea 1: Batch con Lectura de Archivo por Controller

## 📋 Descripción

Esta tarea implementa un endpoint REST que permite cargar archivos CSV a través de un controlador y procesarlos mediante Spring Batch. Es ideal para escenarios donde los usuarios necesitan procesar datos bajo demanda.

## 🎯 Objetivos

- ✅ Crear un endpoint REST para recibir archivos CSV
- ✅ Validar el formato y tamaño del archivo
- ✅ Procesar el archivo usando Spring Batch
- ✅ Persistir los datos en PostgreSQL
- ✅ Retornar el estado de la ejecución del job

## 🏗️ Arquitectura

```
Cliente → Controller → Service → Batch Job
                                    ↓
                          Reader → Processor → Writer
                                                  ↓
                                            PostgreSQL
```

## 📝 Componentes Implementados

### 1. FileUploadController

**Ubicación**: `src/main/java/com/batch/example/demo/controller/FileUploadController.java`

**Responsabilidad**: Recibir archivos CSV y delegar el procesamiento al servicio batch.

```java
@PostMapping("/upload")
public ResponseEntity<Status> uploadFile(@RequestParam("file") MultipartFile file)
```

**Validaciones**:
- Archivo no vacío
- Tamaño máximo: 100MB
- Formato: CSV

### 2. BatchService

**Ubicación**: `src/main/java/com/batch/example/demo/service/BatchService.java`

**Responsabilidad**: Orquestar la ejecución de jobs batch.

**Métodos principales**:
- `runBatchJob(MultipartFile file)`: Ejecuta el job con el archivo proporcionado
- Gestiona parámetros únicos para cada ejecución

### 3. CSVReader

**Ubicación**: `src/main/java/com/batch/example/demo/batch/reader/`

**Responsabilidad**: Leer y parsear archivos CSV.

**Configuración**:
- Delimitador: coma (`,`)
- Mapeo automático a `PersonDto`
- Manejo de headers

### 4. PersonProcessor

**Ubicación**: `src/main/java/com/batch/example/demo/batch/processor/PersonProcessor.java`

**Responsabilidad**: Transformar datos de entrada a entidades de salida.

**Transformaciones**:
- Conversión de `PersonDto` a `RawData`
- Validación de datos
- Enriquecimiento de información

### 5. RawDataWriter

**Ubicación**: `src/main/java/com/batch/example/demo/batch/writer/`

**Responsabilidad**: Persistir datos procesados en la base de datos.

**Configuración**:
- Batch inserts optimizados
- Chunk size: 5000 registros

## 🚀 Instrucciones de Implementación

### Paso 1: Configurar el Reader

```java
@Bean
public FlatFileItemReader<PersonDto> csvReader(
    @Value("#{jobParameters['inputFile']}") String inputFile) {
    
    FlatFileItemReader<PersonDto> reader = new FlatFileItemReader<>();
    reader.setResource(new FileSystemResource(inputFile));
    reader.setLinesToSkip(1); // Skip header
    reader.setLineMapper(lineMapper());
    
    return reader;
}
```

### Paso 2: Configurar el Processor

```java
@Component
public class PersonProcessor implements ItemProcessor<PersonDto, RawData> {
    
    @Override
    public RawData process(PersonDto person) throws Exception {
        RawData rawData = new RawData();
        rawData.setName(person.getName());
        rawData.setAge(person.getAge());
        rawData.setEmail(person.getEmail());
        rawData.setProcessedDate(LocalDateTime.now());
        
        return rawData;
    }
}
```

### Paso 3: Configurar el Writer

```java
@Bean
public JpaItemWriter<RawData> databaseWriter(EntityManagerFactory emf) {
    JpaItemWriter<RawData> writer = new JpaItemWriter<>();
    writer.setEntityManagerFactory(emf);
    
    return writer;
}
```

### Paso 4: Configurar el Step

```java
@Bean
public Step processFileStep(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           ItemReader<PersonDto> reader,
                           ItemProcessor<PersonDto, RawData> processor,
                           ItemWriter<RawData> writer) {
    
    return new StepBuilder("processFileStep", jobRepository)
            .<PersonDto, RawData>chunk(5000, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
}
```

### Paso 5: Configurar el Job

```java
@Bean
public Job fileProcessingJob(JobRepository jobRepository, Step processFileStep) {
    return new JobBuilder("fileProcessingJob", jobRepository)
            .start(processFileStep)
            .build();
}
```

## 🧪 Pruebas

### 1. Preparar archivo CSV de prueba

Crear `persons.csv`:

```csv
name,age,email
Juan Pérez,30,juan.perez@email.com
María García,25,maria.garcia@email.com
Carlos López,35,carlos.lopez@email.com
```

### 2. Probar con cURL

```bash
curl -X POST "http://localhost:8080/example-batch/upload" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@persons.csv"
```

### 3. Probar con Swagger UI

1. Ir a: `http://localhost:8080/example-batch/swagger-ui.html`
2. Expandir el endpoint `/upload`
3. Click en "Try it out"
4. Seleccionar archivo
5. Ejecutar

### 4. Verificar en la base de datos

```sql
SELECT * FROM raw_data ORDER BY processed_date DESC;
```

## 📊 Respuestas de la API

### Éxito (200 OK)

```json
{
  "status": "SUCCESS",
  "message": "Batch job completed successfully",
  "recordsProcessed": 3,
  "executionTime": "2.5s"
}
```

### Error de Validación (400 Bad Request)

```json
{
  "status": "ERROR",
  "message": "Invalid file format. Only CSV files are allowed",
  "recordsProcessed": 0
}
```

### Error de Procesamiento (500 Internal Server Error)

```json
{
  "status": "FAILED",
  "message": "Error processing batch job: ...",
  "recordsProcessed": 150
}
```

## 💡 Recomendaciones

### Performance

1. **Ajustar Chunk Size**: Para archivos grandes, experimenta con diferentes tamaños
   ```properties
   spring.batch.chunk-size=5000  # Valor por defecto
   ```

2. **Habilitar Batch Processing en Hibernate**:
   ```properties
   spring.jpa.properties.hibernate.jdbc.batch_size=5000
   spring.jpa.properties.hibernate.order_inserts=true
   spring.jpa.properties.hibernate.order_updates=true
   ```

3. **Pool de Conexiones**: Configurar adecuadamente HikariCP
   ```properties
   spring.datasource.hikari.maximum-pool-size=10
   spring.datasource.hikari.minimum-idle=5
   ```

### Validación

1. **Validar Headers del CSV**: Asegurar que los headers coincidan con los esperados
2. **Validar Tipos de Datos**: Verificar que los datos sean del tipo correcto
3. **Límite de Tamaño**: Establecer límites razonables según tu infraestructura

### Manejo de Errores

1. **Skip Policy**: Configurar para saltar registros inválidos
   ```java
   .faultTolerant()
   .skipLimit(100)
   .skip(ValidationException.class)
   ```

2. **Retry Logic**: Para errores transitorios
   ```java
   .retryLimit(3)
   .retry(DataAccessException.class)
   ```

3. **Listeners**: Para logging detallado
   ```java
   .listener(new CustomItemReadListener())
   .listener(new CustomItemProcessListener())
   ```

### Seguridad

1. **Validar Tipo MIME**: No confiar solo en la extensión del archivo
2. **Escanear Contenido**: Validar que no contenga código malicioso
3. **Autenticación**: Proteger el endpoint con Spring Security
4. **Rate Limiting**: Limitar la frecuencia de requests

### Escalabilidad

1. **Procesamiento Asíncrono**: Usar `@Async` para no bloquear el thread
2. **Partitioning**: Para archivos muy grandes, dividir en particiones
3. **Remote Chunking**: Distribuir procesamiento en múltiples instancias
4. **Almacenamiento Temporal**: Usar un directorio temporal para archivos grandes

## 🔍 Monitoreo

### Logs Importantes

```properties
logging.level.org.springframework.batch=DEBUG
logging.level.com.batch.example.demo=INFO
```

### Métricas a Monitorear

- Tiempo de ejecución del job
- Número de registros procesados
- Tasa de errores/skips
- Uso de memoria
- Conexiones a la base de datos

### Queries de Monitoreo

```sql
-- Ver ejecuciones del job
SELECT * FROM batch_job_execution ORDER BY start_time DESC LIMIT 10;

-- Ver steps ejecutados
SELECT * FROM batch_step_execution ORDER BY start_time DESC LIMIT 10;

-- Ver parámetros de ejecución
SELECT * FROM batch_job_execution_params;
```

## 🐛 Troubleshooting

### Problema: "File too large"

**Solución**: Aumentar el límite en `application.yml`:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

### Problema: "Job already exists"

**Solución**: Asegurar que cada ejecución tenga parámetros únicos:
```java
JobParameters params = new JobParametersBuilder()
    .addString("inputFile", filePath)
    .addLong("timestamp", System.currentTimeMillis())
    .toJobParameters();
```

### Problema: Performance lento

**Soluciones**:
1. Reducir chunk size si hay mucha memoria usada
2. Aumentar chunk size si el procesamiento es rápido
3. Verificar índices en la base de datos
4. Analizar queries con `show_sql=true`

## 📚 Referencias

- [Spring Batch Documentation](https://docs.spring.io/spring-batch/docs/current/reference/html/)
- [Spring Boot File Upload](https://spring.io/guides/gs/uploading-files/)
- [Hibernate Batch Processing](https://docs.jboss.org/hibernate/orm/5.6/userguide/html_single/Hibernate_User_Guide.html#batch)

## ✅ Checklist de Implementación

- [ ] Controller con endpoint `/upload` implementado
- [ ] Validaciones de archivo en el controller
- [ ] BatchService configurado
- [ ] CSVReader implementado y configurado
- [ ] PersonProcessor implementado
- [ ] RawDataWriter configurado con JPA
- [ ] Step configurado con chunk processing
- [ ] Job configurado en BatchConfig
- [ ] Pruebas unitarias creadas
- [ ] Pruebas de integración realizadas
- [ ] Documentación actualizada
- [ ] Logs configurados
- [ ] Manejo de errores implementado

---

**Estado**: ✅ Completado
**Última actualización**: 2025-10-18

