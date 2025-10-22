package com.batch.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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