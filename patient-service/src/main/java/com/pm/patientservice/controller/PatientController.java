package com.pm.patientservice.controller;

import com.pm.patientservice.dto.PatientRequestDTO;
import com.pm.patientservice.dto.PatientResponseDTO;
import com.pm.patientservice.dto.validators.CreatePatientValidationGroup;
import com.pm.patientservice.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/patients")
@Tag(name = "Patient Management", description = "API endpoints for managing patients")
@Validated
public class PatientController {
    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    @Operation(summary = "Retrieve all patients")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved patients"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<PatientResponseDTO>> getPatients() {
        log.info("Retrieving all patients");
        try {
            List<PatientResponseDTO> patients = patientService.getPatients();
            log.info("Successfully retrieved {} patients", patients.size());
            return ResponseEntity.ok().body(patients);
        } catch (Exception e) {
            log.error("Error retrieving patients: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve patients", e);
        }
    }

    @PostMapping
    @Operation(summary = "Create a new patient")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Patient created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<PatientResponseDTO> createPatient(
            @Validated({Default.class, CreatePatientValidationGroup.class})
            @RequestBody PatientRequestDTO patientRequestDTO) {
        log.info("Creating new patient: {}", patientRequestDTO);
        try {
            PatientResponseDTO response = patientService.createPatient(patientRequestDTO);
            log.info("Successfully created patient with ID: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating patient: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create patient", e);
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a patient")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Patient updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Patient not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PatientResponseDTO> updatePatient(
            @PathVariable @Valid UUID id,
            @Validated({Default.class})
            @RequestBody PatientRequestDTO patientRequestDTO) {
        log.info("Updating patient with ID: {}", id);
        try {
            if (!patientService.existsById(id)) {
                log.warn("Patient not found with ID: {}", id);
                return ResponseEntity.notFound().build();
            }

            PatientResponseDTO response = patientService.updatePatient(id, patientRequestDTO);
            log.info("Successfully updated patient with ID: {}", id);
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            log.error("Error updating patient with ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to update patient", e);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a patient")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Patient deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Patient not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePatient(@PathVariable @Valid UUID id) {
        log.info("Attempting to delete patient with ID: {}", id);
        try {
            if (!patientService.existsById(id)) {
                log.warn("Delete failed - Patient not found with ID: {}", id);
                return ResponseEntity.notFound().build();
            }

            patientService.deletePatient(id);
            log.info("Successfully deleted patient with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting patient with ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete patient", e);
        }
    }
}
