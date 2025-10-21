package com.batch.example.demo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Controlador para la gestión de carga de archivos CSV.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileUploadController {

    /**
     * Procesa un archivo CSV y registra su contenido.
     *
     * @param file el archivo CSV a procesar
     * @return ResponseEntity con mensaje de éxito
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            log.error("El archivo está vacío");
            return ResponseEntity.badRequest().body("El archivo está vacío");
        }

        log.info("Iniciando procesamiento del archivo: {}", file.getOriginalFilename());

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return processCsvReader(reader);
        } catch (Exception e) {
            log.error("Error al procesar el archivo CSV: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Error al procesar el archivo: " + e.getMessage());
        }
    }

    /**
     * Método común para procesar el contenido CSV a través de un BufferedReader
     */
    private ResponseEntity<String> processCsvReader(BufferedReader reader) throws Exception {
        String line;
        int lineCount = 0;

        // Leer la primera línea (encabezados)
        String headers = reader.readLine();
        if (headers != null) {
            log.info("Encabezados del CSV: {}", headers);
        }

        // Procesar el resto de las líneas
        while ((line = reader.readLine()) != null) {
            lineCount++;
            log.info("Línea {}: {}", lineCount, line);
        }

        log.info("Procesamiento completado. Total de registros procesados: {}", lineCount);
        return ResponseEntity.ok("CSV procesado exitosamente. Registros procesados: " + lineCount);
    }
}
