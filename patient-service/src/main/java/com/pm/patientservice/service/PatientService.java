package com.pm.patientservice.service;

import com.pm.patientservice.dto.PatientRequestDTO;
import com.pm.patientservice.dto.PatientResponseDTO;
import com.pm.patientservice.exception.EmailAlreadyExistsException;
import com.pm.patientservice.exception.PatientNotFoundException;
import com.pm.patientservice.grpc.BillingServiceGrpcClient;
import com.pm.patientservice.kafka.KafkaProducer;
import com.pm.patientservice.mapper.PatientMapper;
import com.pm.patientservice.model.Patient;
import com.pm.patientservice.repository.PatientRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final BillingServiceGrpcClient billingServiceGrpcClient;
    private final KafkaProducer kafkaProducer;

    public PatientService(PatientRepository patientRepository,
                          BillingServiceGrpcClient billingServiceGrpcClient,
                          KafkaProducer kafkaProducer) {
        this.patientRepository = patientRepository;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
        this.kafkaProducer = kafkaProducer;
    }

    @Transactional(readOnly = true)
    public List<PatientResponseDTO> getPatients() {
        log.info("Retrieving all patients");
        try {
            List<Patient> patients = patientRepository.findAll();
            log.info("Successfully retrieved {} patients", patients.size());
            return patients.stream()
                    .map(PatientMapper::toDTO)
                    .toList();
        } catch (Exception e) {
            log.error("Error retrieving patients: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve patients", e);
        }
    }

    @Transactional
    public PatientResponseDTO createPatient(PatientRequestDTO patientRequestDTO) {
        log.info("Creating new patient with email: {}", patientRequestDTO.getEmail());
        try {
            if (patientRepository.existsByEmail(patientRequestDTO.getEmail())) {
                log.warn("Attempt to create patient with existing email: {}", patientRequestDTO.getEmail());
                throw new EmailAlreadyExistsException("A patient with this Email already exists: " + patientRequestDTO.getEmail());
            }

            Patient newPatient = patientRepository.save(PatientMapper.toModel(patientRequestDTO));
            log.info("Successfully created patient with ID: {}", newPatient.getId());

            try {
                billingServiceGrpcClient.createBillingAccount(
                        newPatient.getId().toString(),
                        newPatient.getName(),
                        newPatient.getEmail()
                );
                log.info("Successfully created billing account for patient ID: {}", newPatient.getId());
            } catch (Exception e) {
                log.error("Failed to create billing account for patient ID {}: {}",
                        newPatient.getId(), e.getMessage(), e);
                throw new RuntimeException("Failed to create billing account", e);
            }

            try {
                kafkaProducer.sendEvent(newPatient);
                log.info("Successfully sent Kafka event for patient ID: {}", newPatient.getId());
            } catch (Exception e) {
                log.error("Failed to send Kafka event for patient ID {}: {}",
                        newPatient.getId(), e.getMessage(), e);
                throw new RuntimeException("Failed to send Kafka event", e);
            }

            return PatientMapper.toDTO(newPatient);
        } catch (Exception e) {
            log.error("Error creating patient: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public PatientResponseDTO updatePatient(UUID id, PatientRequestDTO patientRequestDTO) {
        log.info("Updating patient with ID: {}", id);
        try {
            Patient patient = patientRepository.findById(id)
                    .orElseThrow(() -> {
                        log.warn("Patient not found with ID: {}", id);
                        return new PatientNotFoundException("Patient not found with ID: " + id);
                    });

            if (patientRepository.existsByEmailAndIdNot(patientRequestDTO.getEmail(), id)) {
                log.warn("Attempt to update patient with existing email: {}", patientRequestDTO.getEmail());
                throw new EmailAlreadyExistsException("A patient with this email already exists: " + patientRequestDTO.getEmail());
            }

            updatePatientFields(patient, patientRequestDTO);
            Patient updatedPatient = patientRepository.save(patient);
            log.info("Successfully updated patient with ID: {}", id);
            return PatientMapper.toDTO(updatedPatient);
        } catch (Exception e) {
            log.error("Error updating patient with ID {}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void deletePatient(UUID id) {
        log.info("Deleting patient with ID: {}", id);
        try {
            Patient patient = patientRepository.findById(id)
                    .orElseThrow(() -> {
                        log.warn("Patient not found with ID: {}", id);
                        return new PatientNotFoundException("Patient not found with ID: " + id);
                    });
            patientRepository.delete(patient);
            log.info("Successfully deleted patient with ID: {}", id);
        } catch (Exception e) {
            log.error("Error deleting patient with ID {}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        log.debug("Checking if patient exists with ID: {}", id);
        try {
            boolean exists = patientRepository.existsById(id);
            log.debug("Patient exists check result for ID {}: {}", id, exists);
            return exists;
        } catch (Exception e) {
            log.error("Error checking patient existence for ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to check patient existence", e);
        }
    }

    private void updatePatientFields(Patient patient, PatientRequestDTO dto) {
        patient.setName(dto.getName());
        patient.setEmail(dto.getEmail());
        patient.setAddress(dto.getAddress());
        patient.setDateOfBirth(LocalDate.parse(dto.getDateOfBirth()));
    }
}
