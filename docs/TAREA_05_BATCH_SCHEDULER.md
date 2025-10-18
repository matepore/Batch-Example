# Tarea 5: Batch Programado con Scheduler

## 📋 Descripción

Esta tarea implementa la ejecución automática de jobs batch mediante Spring Scheduler. Es ideal para procesos que deben ejecutarse periódicamente sin intervención manual, como generación de reportes nocturnos, sincronización de datos, o limpieza de registros antiguos.

## 🎯 Objetivos

- ✅ Configurar Spring Scheduler en la aplicación
- ✅ Crear jobs programados con expresiones cron
- ✅ Implementar ejecuciones periódicas (fixed rate, fixed delay)
- ✅ Gestionar ejecuciones concurrentes
- ✅ Implementar locks distribuidos para múltiples instancias
- ✅ Monitorear y gestionar schedules dinámicamente
- ✅ Implementar notificaciones de ejecución

## 🏗️ Arquitectura

```
Spring Scheduler
    ↓
Cron Expression → Scheduled Method
    ↓
Distributed Lock Check
    ↓
Batch Job Execution
    ↓
Success/Failure Notification
```

## 📝 Componentes a Implementar

### 1. Configuración del Scheduler

**Ubicación**: `src/main/java/com/batch/example/demo/config/SchedulerConfig.java`

```java
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulerConfig {
    
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        scheduler.initialize();
        return scheduler;
    }
    
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("batch-async-");
        executor.initialize();
        return executor;
    }
}
```

### 2. Entidad de Schedule Log

**Ubicación**: `src/main/java/com/batch/example/demo/entity/ScheduleLog.java`

```java
@Entity
@Table(name = "schedule_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "job_name")
    private String jobName;
    
    @Column(name = "schedule_type")
    private String scheduleType; // CRON, FIXED_RATE, FIXED_DELAY, MANUAL
    
    @Column(name = "execution_time")
    private LocalDateTime executionTime;
    
    @Column(name = "completion_time")
    private LocalDateTime completionTime;
    
    @Column(name = "duration_ms")
    private Long durationMs;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ScheduleStatus status;
    
    @Column(name = "records_processed")
    private Long recordsProcessed;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "server_instance")
    private String serverInstance;
    
    @Column(name = "job_execution_id")
    private Long jobExecutionId;
}

enum ScheduleStatus {
    STARTED, COMPLETED, FAILED, SKIPPED
}
```

### 3. Servicio de Batch Programado

**Ubicación**: `src/main/java/com/batch/example/demo/service/ScheduledBatchService.java`

```java
@Service
@Slf4j
public class ScheduledBatchService {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    @Qualifier("fileProcessingJob")
    private Job fileProcessingJob;
    
    @Autowired
    @Qualifier("databaseProcessingJob")
    private Job databaseProcessingJob;
    
    @Autowired
    private ScheduleLogRepository scheduleLogRepository;
    
    @Autowired
    private DistributedLockService lockService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Value("${server.instance.id:unknown}")
    private String serverInstance;
    
    /**
     * Ejecutar procesamiento nocturno a las 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "America/Argentina/Buenos_Aires")
    public void runNightlyBatch() {
        String lockKey = "nightly-batch-lock";
        
        if (!lockService.acquireLock(lockKey, Duration.ofMinutes(60))) {
            log.info("Nightly batch already running on another instance. Skipping.");
            logSkippedExecution("nightly-batch", "CRON");
            return;
        }
        
        try {
            log.info("Starting nightly batch processing");
            executeJob(fileProcessingJob, "nightly-batch", "CRON");
        } finally {
            lockService.releaseLock(lockKey);
        }
    }
    
    /**
     * Ejecutar sincronización cada 15 minutos
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void runPeriodicSync() {
        String lockKey = "periodic-sync-lock";
        
        if (!lockService.acquireLock(lockKey, Duration.ofMinutes(10))) {
            log.debug("Periodic sync already running. Skipping.");
            return;
        }
        
        try {
            log.info("Starting periodic sync");
            executeJob(databaseProcessingJob, "periodic-sync", "CRON");
        } finally {
            lockService.releaseLock(lockKey);
        }
    }
    
    /**
     * Ejecutar limpieza de logs antiguos cada domingo a las 3 AM
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void runWeeklyCleanup() {
        log.info("Starting weekly cleanup");
        
        ScheduleLog scheduleLog = createScheduleLog("weekly-cleanup", "CRON");
        
        try {
            // Eliminar logs de más de 90 días
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
            long deletedCount = scheduleLogRepository.deleteByExecutionTimeBefore(cutoffDate);
            
            scheduleLog.setCompletionTime(LocalDateTime.now());
            scheduleLog.setDurationMs(calculateDuration(scheduleLog));
            scheduleLog.setStatus(ScheduleStatus.COMPLETED);
            scheduleLog.setRecordsProcessed(deletedCount);
            
            log.info("Weekly cleanup completed. Deleted {} old logs", deletedCount);
            
        } catch (Exception e) {
            handleScheduleError(scheduleLog, e);
        } finally {
            scheduleLogRepository.save(scheduleLog);
        }
    }
    
    /**
     * Ejecutar cada 5 minutos con fixed rate
     */
    @Scheduled(fixedRate = 300000) // 5 minutos
    public void runFixedRateTask() {
        log.debug("Fixed rate task executing");
        // Implementar tarea que debe ejecutarse cada 5 minutos exactos
    }
    
    /**
     * Ejecutar 10 segundos después de que termine la ejecución anterior
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    public void runFixedDelayTask() {
        log.debug("Fixed delay task executing");
        // Implementar tarea que debe esperar a que termine la anterior
    }
    
    /**
     * Ejecutar de forma asíncrona
     */
    @Async
    @Scheduled(cron = "0 */30 * * * *")
    public void runAsyncScheduledTask() {
        log.info("Async scheduled task executing on thread: {}", 
            Thread.currentThread().getName());
        // Implementar tarea asíncrona
    }
    
    // ========== Métodos auxiliares ==========
    
    private void executeJob(Job job, String jobName, String scheduleType) {
        ScheduleLog scheduleLog = createScheduleLog(jobName, scheduleType);
        
        try {
            JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("scheduledBy", "scheduler")
                .addString("instance", serverInstance)
                .toJobParameters();
            
            JobExecution execution = jobLauncher.run(job, params);
            scheduleLog.setJobExecutionId(execution.getId());
            
            // Esperar a que termine el job
            while (execution.isRunning()) {
                Thread.sleep(1000);
            }
            
            scheduleLog.setCompletionTime(LocalDateTime.now());
            scheduleLog.setDurationMs(calculateDuration(scheduleLog));
            scheduleLog.setStatus(execution.getStatus() == BatchStatus.COMPLETED ? 
                ScheduleStatus.COMPLETED : ScheduleStatus.FAILED);
            scheduleLog.setRecordsProcessed(getProcessedRecords(execution));
            
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                notificationService.sendSuccessNotification(jobName, scheduleLog);
            } else {
                scheduleLog.setErrorMessage(getJobErrors(execution));
                notificationService.sendFailureNotification(jobName, scheduleLog);
            }
            
            log.info("Scheduled job {} completed with status: {}", 
                jobName, execution.getStatus());
            
        } catch (Exception e) {
            handleScheduleError(scheduleLog, e);
        } finally {
            scheduleLogRepository.save(scheduleLog);
        }
    }
    
    private ScheduleLog createScheduleLog(String jobName, String scheduleType) {
        return ScheduleLog.builder()
            .jobName(jobName)
            .scheduleType(scheduleType)
            .executionTime(LocalDateTime.now())
            .status(ScheduleStatus.STARTED)
            .serverInstance(serverInstance)
            .build();
    }
    
    private void handleScheduleError(ScheduleLog scheduleLog, Exception e) {
        log.error("Scheduled job {} failed: {}", scheduleLog.getJobName(), e.getMessage(), e);
        
        scheduleLog.setCompletionTime(LocalDateTime.now());
        scheduleLog.setDurationMs(calculateDuration(scheduleLog));
        scheduleLog.setStatus(ScheduleStatus.FAILED);
        scheduleLog.setErrorMessage(e.getMessage());
        
        notificationService.sendFailureNotification(scheduleLog.getJobName(), scheduleLog);
    }
    
    private void logSkippedExecution(String jobName, String scheduleType) {
        ScheduleLog log = ScheduleLog.builder()
            .jobName(jobName)
            .scheduleType(scheduleType)
            .executionTime(LocalDateTime.now())
            .completionTime(LocalDateTime.now())
            .status(ScheduleStatus.SKIPPED)
            .serverInstance(serverInstance)
            .build();
        
        scheduleLogRepository.save(log);
    }
    
    private Long calculateDuration(ScheduleLog log) {
        if (log.getExecutionTime() != null && log.getCompletionTime() != null) {
            return Duration.between(log.getExecutionTime(), log.getCompletionTime()).toMillis();
        }
        return null;
    }
    
    private Long getProcessedRecords(JobExecution execution) {
        return execution.getStepExecutions().stream()
            .mapToLong(StepExecution::getWriteCount)
            .sum();
    }
    
    private String getJobErrors(JobExecution execution) {
        return execution.getAllFailureExceptions().stream()
            .map(Throwable::getMessage)
            .collect(Collectors.joining("; "));
    }
}
```

### 4. Servicio de Lock Distribuido

**Ubicación**: `src/main/java/com/batch/example/demo/service/DistributedLockService.java`

```java
@Service
@Slf4j
public class DistributedLockService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Value("${server.instance.id:unknown}")
    private String serverInstance;
    
    private static final String LOCK_TABLE = "distributed_lock";
    
    public boolean acquireLock(String lockKey, Duration timeout) {
        try {
            LocalDateTime expiresAt = LocalDateTime.now().plus(timeout);
            
            // Intentar insertar el lock
            String sql = "INSERT INTO " + LOCK_TABLE + 
                " (lock_key, owner_instance, acquired_at, expires_at) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (lock_key) DO NOTHING";
            
            int rows = jdbcTemplate.update(sql, 
                lockKey, serverInstance, LocalDateTime.now(), expiresAt);
            
            if (rows > 0) {
                log.info("Lock acquired: {} by {}", lockKey, serverInstance);
                return true;
            }
            
            // Si no se pudo insertar, verificar si expiró
            cleanExpiredLocks();
            
            return false;
            
        } catch (Exception e) {
            log.error("Error acquiring lock: {}", lockKey, e);
            return false;
        }
    }
    
    public void releaseLock(String lockKey) {
        try {
            String sql = "DELETE FROM " + LOCK_TABLE + 
                " WHERE lock_key = ? AND owner_instance = ?";
            
            int rows = jdbcTemplate.update(sql, lockKey, serverInstance);
            
            if (rows > 0) {
                log.info("Lock released: {} by {}", lockKey, serverInstance);
            }
            
        } catch (Exception e) {
            log.error("Error releasing lock: {}", lockKey, e);
        }
    }
    
    public void cleanExpiredLocks() {
        try {
            String sql = "DELETE FROM " + LOCK_TABLE + 
                " WHERE expires_at < ?";
            
            int rows = jdbcTemplate.update(sql, LocalDateTime.now());
            
            if (rows > 0) {
                log.info("Cleaned {} expired locks", rows);
            }
            
        } catch (Exception e) {
            log.error("Error cleaning expired locks", e);
        }
    }
    
    @Scheduled(fixedRate = 60000) // Limpiar cada minuto
    public void scheduledCleanup() {
        cleanExpiredLocks();
    }
}
```

### 5. Servicio de Notificaciones

**Ubicación**: `src/main/java/com/batch/example/demo/service/NotificationService.java`

```java
@Service
@Slf4j
public class NotificationService {
    
    @Value("${batch.notification.email.enabled:false}")
    private boolean emailEnabled;
    
    @Value("${batch.notification.slack.enabled:false}")
    private boolean slackEnabled;
    
    @Value("${batch.notification.slack.webhook:}")
    private String slackWebhook;
    
    public void sendSuccessNotification(String jobName, ScheduleLog scheduleLog) {
        String message = String.format(
            "✅ Batch Job Completed Successfully\n" +
            "Job: %s\n" +
            "Duration: %d ms\n" +
            "Records Processed: %d\n" +
            "Time: %s",
            jobName,
            scheduleLog.getDurationMs(),
            scheduleLog.getRecordsProcessed(),
            scheduleLog.getCompletionTime()
        );
        
        log.info(message);
        
        if (slackEnabled) {
            sendSlackNotification(message);
        }
        
        if (emailEnabled) {
            sendEmailNotification("Batch Success: " + jobName, message);
        }
    }
    
    public void sendFailureNotification(String jobName, ScheduleLog scheduleLog) {
        String message = String.format(
            "❌ Batch Job Failed\n" +
            "Job: %s\n" +
            "Error: %s\n" +
            "Time: %s",
            jobName,
            scheduleLog.getErrorMessage(),
            scheduleLog.getExecutionTime()
        );
        
        log.error(message);
        
        if (slackEnabled) {
            sendSlackNotification(message);
        }
        
        if (emailEnabled) {
            sendEmailNotification("Batch FAILURE: " + jobName, message);
        }
    }
    
    private void sendSlackNotification(String message) {
        // Implementar integración con Slack
        log.info("Sending Slack notification: {}", message);
    }
    
    private void sendEmailNotification(String subject, String body) {
        // Implementar envío de email
        log.info("Sending email notification: {}", subject);
    }
}
```

## 🚀 Instrucciones de Implementación

### Paso 1: Crear tablas necesarias

```sql
-- Tabla de logs de schedule
CREATE TABLE schedule_log (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    schedule_type VARCHAR(50) NOT NULL,
    execution_time TIMESTAMP NOT NULL,
    completion_time TIMESTAMP,
    duration_ms BIGINT,
    status VARCHAR(50) NOT NULL,
    records_processed BIGINT,
    error_message TEXT,
    server_instance VARCHAR(100),
    job_execution_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_schedule_log_job ON schedule_log(job_name);
CREATE INDEX idx_schedule_log_execution ON schedule_log(execution_time);
CREATE INDEX idx_schedule_log_status ON schedule_log(status);

-- Tabla de locks distribuidos
CREATE TABLE distributed_lock (
    lock_key VARCHAR(100) PRIMARY KEY,
    owner_instance VARCHAR(100) NOT NULL,
    acquired_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_lock_expires ON distributed_lock(expires_at);
```

### Paso 2: Configurar properties

```yaml
# application.yml
server:
  instance:
    id: ${HOSTNAME:local-instance}

batch:
  scheduler:
    enabled: true
    pool-size: 10
  notification:
    email:
      enabled: false
      recipients: admin@example.com
    slack:
      enabled: false
      webhook: https://hooks.slack.com/services/YOUR/WEBHOOK/URL

spring:
  task:
    scheduling:
      pool:
        size: 10
    execution:
      pool:
        core-size: 5
        max-size: 10
        queue-capacity: 25
```

### Paso 3: Habilitar Scheduling en Application

```java
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class BatchExampleApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(BatchExampleApplication.class, args);
    }
}
```

### Paso 4: Crear Controller para Gestión Manual

```java
@RestController
@RequestMapping("/batch/schedule")
@Slf4j
public class ScheduleManagementController {
    
    @Autowired
    private ScheduleLogRepository scheduleLogRepository;
    
    @GetMapping("/history")
    public ResponseEntity<List<ScheduleLog>> getScheduleHistory(
            @RequestParam(required = false) String jobName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, 
            Sort.by("executionTime").descending());
        
        Page<ScheduleLog> logs = jobName != null ? 
            scheduleLogRepository.findByJobName(jobName, pageable) :
            scheduleLogRepository.findAll(pageable);
        
        return ResponseEntity.ok(logs.getContent());
    }
    
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalExecutions", scheduleLogRepository.count());
        stats.put("successfulExecutions", 
            scheduleLogRepository.countByStatus(ScheduleStatus.COMPLETED));
        stats.put("failedExecutions", 
            scheduleLogRepository.countByStatus(ScheduleStatus.FAILED));
        stats.put("skippedExecutions", 
            scheduleLogRepository.countByStatus(ScheduleStatus.SKIPPED));
        
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/next-execution")
    public ResponseEntity<Map<String, String>> getNextExecutionTimes() {
        Map<String, String> nextExecutions = new HashMap<>();
        
        // Calcular próximas ejecuciones basadas en expresiones cron
        nextExecutions.put("nightly-batch", "02:00 AM daily");
        nextExecutions.put("periodic-sync", "Every 15 minutes");
        nextExecutions.put("weekly-cleanup", "03:00 AM every Sunday");
        
        return ResponseEntity.ok(nextExecutions);
    }
}
```

## 🧪 Pruebas

### 1. Verificar que el Scheduler está activo

```bash
curl -X GET "http://localhost:8080/example-batch/batch/schedule/next-execution"
```

### 2. Ver historial de ejecuciones

```bash
curl -X GET "http://localhost:8080/example-batch/batch/schedule/history?jobName=nightly-batch"
```

### 3. Verificar logs en la base de datos

```sql
-- Ver últimas ejecuciones programadas
SELECT * FROM schedule_log 
ORDER BY execution_time DESC 
LIMIT 20;

-- Ver estadísticas por job
SELECT 
    job_name,
    COUNT(*) as total_execuciones,
    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as exitosas,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as fallidas,
    AVG(duration_ms) as duracion_promedio_ms
FROM schedule_log
GROUP BY job_name;

-- Ver locks activos
SELECT * FROM distributed_lock;
```

## 💡 Recomendaciones

### Expresiones Cron

```java
// Ejemplos de expresiones cron comunes:

// Cada día a las 2 AM
@Scheduled(cron = "0 0 2 * * *")

// Cada 15 minutos
@Scheduled(cron = "0 */15 * * * *")

// Primer día del mes a las 3 AM
@Scheduled(cron = "0 0 3 1 * *")

// Días laborables a las 9 AM
@Scheduled(cron = "0 0 9 * * MON-FRI")

// Cada domingo a las 23:30
@Scheduled(cron = "0 30 23 * * SUN")

// Cada hora en el minuto 0
@Scheduled(cron = "0 0 * * * *")
```

### Fixed Rate vs Fixed Delay

```java
// Fixed Rate: ejecuta cada X milisegundos, sin importar cuánto tarde
@Scheduled(fixedRate = 60000) // Cada 60 segundos

// Fixed Delay: espera X milisegundos DESPUÉS de que termine
@Scheduled(fixedDelay = 60000) // 60 segundos después de terminar

// Initial Delay: espera antes de la primera ejecución
@Scheduled(fixedDelay = 60000, initialDelay = 10000)
```

### Ejecución Asíncrona

```java
@Async
@Scheduled(cron = "0 */5 * * * *")
public void asyncTask() {
    // Se ejecuta en un thread separado
    // No bloquea el scheduler
}
```

### Locks Distribuidos

1. **Usar para ambientes con múltiples instancias**
2. **Configurar timeout apropiado**
3. **Limpiar locks expirados periódicamente**
4. **Considerar Redis para mejor performance**

### Manejo de Errores

```java
@Scheduled(cron = "0 0 2 * * *")
public void scheduledJob() {
    try {
        // Lógica del job
    } catch (Exception e) {
        log.error("Scheduled job failed", e);
        // El scheduler continuará ejecutando
    }
}
```

### Zonas Horarias

```java
@Scheduled(cron = "0 0 2 * * *", zone = "America/Argentina/Buenos_Aires")
public void jobWithTimezone() {
    // Se ejecuta en la zona horaria especificada
}
```

## 📊 Monitoreo

### Métricas Importantes

```java
@Component
public class SchedulerMetrics {
    
    @Autowired
    private MeterRegistry registry;
    
    public void recordScheduledExecution(String jobName, long duration, boolean success) {
        Timer.builder("batch.scheduled.execution")
            .tag("job", jobName)
            .tag("status", success ? "success" : "failure")
            .register(registry)
            .record(duration, TimeUnit.MILLISECONDS);
    }
}
```

### Health Check

```java
@Component
public class SchedulerHealthIndicator implements HealthIndicator {
    
    @Autowired
    private ScheduleLogRepository scheduleLogRepository;
    
    @Override
    public Health health() {
        // Verificar si hay ejecuciones recientes
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentExecutions = scheduleLogRepository
            .countByExecutionTimeAfter(oneHourAgo);
        
        if (recentExecutions > 0) {
            return Health.up()
                .withDetail("recentExecutions", recentExecutions)
                .build();
        }
        
        return Health.down()
            .withDetail("message", "No recent scheduled executions")
            .build();
    }
}
```

## ✅ Checklist de Implementación

- [ ] @EnableScheduling configurado
- [ ] SchedulerConfig con ThreadPoolTaskScheduler
- [ ] ScheduleLog entidad y tabla creadas
- [ ] DistributedLock tabla creada
- [ ] ScheduledBatchService implementado
- [ ] Expresiones cron configuradas correctamente
- [ ] DistributedLockService implementado
- [ ] NotificationService implementado
- [ ] Properties de configuración añadidas
- [ ] Controller de gestión creado
- [ ] Pruebas de ejecución programada
- [ ] Verificación de logs
- [ ] Verificación de locks distribuidos
- [ ] Health checks configurados
- [ ] Monitoreo configurado
- [ ] Documentación de schedules actualizada

---

**Estado**: 📝 Pendiente
**Última actualización**: 2025-10-18

