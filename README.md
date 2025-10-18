# Batch-Example

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen?style=flat-square&logo=spring-boot)
![Spring Batch](https://img.shields.io/badge/Spring%20Batch-3.2.4-brightgreen?style=flat-square&logo=spring)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15.2-blue?style=flat-square&logo=postgresql)
![Maven](https://img.shields.io/badge/Maven-3.x-red?style=flat-square&logo=apache-maven)
![Docker](https://img.shields.io/badge/Docker-Compose-blue?style=flat-square&logo=docker)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

## 📋 Descripción

**Batch-Example** es una aplicación de demostración que implementa procesamiento por lotes (batch processing) utilizando **Spring Boot** y **Spring Batch**. Este proyecto está diseñado para procesar grandes volúmenes de datos de manera eficiente, leyendo archivos CSV y persistiendo la información en una base de datos PostgreSQL.

### 🎯 Características Principales

- ✅ **Procesamiento por Lotes**: Implementación completa de Spring Batch para procesamiento eficiente de datos
- ✅ **Lectura de CSV**: Capacidad de leer y procesar archivos CSV con información de personas
- ✅ **Persistencia de Datos**: Integración con PostgreSQL mediante Spring Data JPA
- ✅ **API REST**: Endpoints para gestionar trabajos batch y subir archivos
- ✅ **Documentación OpenAPI**: Interfaz Swagger UI para explorar y probar los endpoints
- ✅ **Health Check**: Monitoreo del estado de la aplicación
- ✅ **Docker Support**: Configuración Docker Compose para despliegue rápido
- ✅ **Optimización de Rendimiento**: Configuración de batch inserts con Hibernate (chunk size: 5000)

## 📋 Tareas de Implementación

A continuación se presenta una lista de tareas y ejemplos de implementación para diferentes escenarios de procesamiento batch:

| # | Tarea | Descripción | Estado | README |
|---|-------|-------------|--------|--------|
| 1 | **Batch con Lectura de Archivo por Controller** | Implementar un endpoint que reciba un archivo CSV y lo procese mediante Spring Batch | ✅ Completado | [Ver Documentación](./docs/TAREA_01_BATCH_FILE_CONTROLLER.md) |
| 2 | **Batch con Lectura desde Base de Datos** | Crear un job que lea registros de una tabla PostgreSQL y los procese en lotes | 🚧 En Progreso | [Ver Documentación](./docs/TAREA_02_BATCH_DB_READER.md) |
| 3 | **Batch con Escritura en Múltiples Destinos** | Implementar un processor que escriba datos procesados a DB y archivo simultáneamente | 📝 Pendiente | [Ver Documentación](./docs/TAREA_03_BATCH_MULTI_WRITER.md) |
| 4 | **Batch con Validación y Manejo de Errores** | Crear un job con skip policies y listeners para manejar errores durante el procesamiento | 📝 Pendiente | [Ver Documentación](./docs/TAREA_04_BATCH_ERROR_HANDLING.md) |
| 5 | **Batch Programado con Scheduler** | Implementar un job batch que se ejecute automáticamente mediante Spring Scheduler | 📝 Pendiente | [Ver Documentación](./docs/TAREA_05_BATCH_SCHEDULER.md) |

### Leyenda de Estados
- ✅ **Completado**: Implementación finalizada y probada
- 🚧 **En Progreso**: En desarrollo activo
- 📝 **Pendiente**: Por implementar
- ❌ **Bloqueado**: Requiere resolución de dependencias

## 🏗️ Arquitectura

El proyecto sigue una arquitectura en capas bien definida:

```
┌─────────────────────────────────────┐
│         Controllers                 │
│  (REST API & File Upload)          │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│          Services                   │
│    (Business Logic)                 │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│        Batch Layer                  │
│  Reader → Processor → Writer        │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      Repository Layer               │
│    (Spring Data JPA)                │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│       PostgreSQL Database           │
└─────────────────────────────────────┘
```

## 🛠️ Tecnologías Utilizadas

- **Java 17**: Lenguaje de programación
- **Spring Boot 3.5.6**: Framework principal
- **Spring Batch 3.2.4**: Framework de procesamiento por lotes
- **Spring Data JPA**: Capa de persistencia
- **PostgreSQL 15.2**: Base de datos relacional
- **Hibernate**: ORM con optimizaciones de rendimiento
- **Lombok 1.18.30**: Reducción de código boilerplate
- **SpringDoc OpenAPI**: Documentación automática de API
- **Maven**: Gestión de dependencias y construcción
- **Docker Compose**: Containerización y orquestación

## 📦 Requisitos Previos

- Java 17 o superior
- Maven 3.x
- Docker y Docker Compose
- PostgreSQL 15.2 (si no se usa Docker)

## 🚀 Instalación y Configuración

### 1. Clonar el repositorio

```bash
git clone <repository-url>
cd Batch-Example
```

### 2. Levantar la base de datos con Docker

```bash
docker-compose up -d
```

Esto creará:
- Un contenedor PostgreSQL en el puerto 5432
- Base de datos: `batch_example`
- Usuario: `user`
- Contraseña: `password`
- Esquemas e inicialización automática mediante scripts SQL

### 3. Compilar el proyecto

```bash
mvnw clean install
```

### 4. Ejecutar la aplicación

```bash
mvnw spring-boot:run
```

La aplicación estará disponible en: `http://localhost:8080/example-batch`

## 📚 Endpoints Disponibles

### Health Check
- **GET** `/example-batch/health` - Verificar el estado de la aplicación

### Batch Operations
- **POST** `/example-batch/batch/run` - Ejecutar un trabajo batch
- **GET** `/example-batch/batch/status` - Consultar el estado de trabajos

### File Upload
- **POST** `/example-batch/upload` - Subir archivo CSV para procesamiento
  - Max file size: 100MB

### Documentación API
- **Swagger UI**: `http://localhost:8080/example-batch/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/example-batch/v3/api-docs`

## 🔧 Configuración

La configuración principal se encuentra en `application.yml`:

```yaml
server:
  port: 8080
  servlet:
    context-path: /example-batch

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/batch_example
    username: user
    password: password
  
  batch:
    job:
      chunk: 5000  # Tamaño de chunk para procesamiento batch
```

### Variables de Entorno

- `PORT`: Puerto del servidor (default: 8080)

## 📁 Estructura del Proyecto

```
src/main/java/com/batch/example/demo/
├── batch/
│   ├── BatchConfig.java          # Configuración de trabajos batch
│   ├── processor/                # Procesadores de items
│   ├── reader/                   # Lectores de datos
│   ├── step/                     # Definición de steps
│   └── writer/                   # Escritores de datos
├── config/
│   └── OpenApiConfig.java        # Configuración Swagger
├── controller/
│   ├── BatchRunnerController.java
│   ├── FileUploadController.java
│   └── HealthCheckController.java
├── entity/
│   └── RawData.java              # Entidades JPA
```

### Leyenda de Estados
- ✅ **Completado**: Implementación finalizada y probada
- 🚧 **En Progreso**: En desarrollo activo
- 📝 **Pendiente**: Por implementar
- ❌ **Bloqueado**: Requiere resolución de dependencias

## 📊 Optimizaciones de Rendimiento

- **Batch Processing**: Procesamiento en chunks de 5000 registros
- **Hibernate Batch Inserts**: Inserciones optimizadas en lote
- **Order Inserts/Updates**: Optimización del orden de operaciones SQL
- **Statistics**: Habilitadas para monitoreo de rendimiento

## 🤝 Contribución

Las contribuciones son bienvenidas. Por favor:

1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## 📝 Licencia

Este proyecto es un ejemplo de demostración para propósitos educativos.

## 👥 Autor
**Mateo Calcagno**
- 📧 Email: calcagno.mateo@gmail.com
- 📱 Teléfono: +54 9 11 3119-1742
- 🌎 Nacionalidad: Argentino
- 🎓 Educación: Técnico Universitario en Desarrollo de Software
- 🏛️ Institución: Universidad de Ezeiza
Batch Example Project

## 📞 Soporte

Para preguntas o soporte, por favor abre un issue en el repositorio.

---

⭐ Si este proyecto te fue útil, considera darle una estrella!
