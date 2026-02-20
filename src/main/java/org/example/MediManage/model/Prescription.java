package org.example.MediManage.model;

/**
 * Represents a prescription in the system.
 */
public class Prescription {
    private int prescriptionId;
    private Integer customerId;
    private String customerName;
    private String doctorName;
    private String status; // PENDING, VERIFIED, DISPENSED
    private String prescribedDate;
    private String notes;
    private String medicinesText; // Comma-separated list of medicines
    private String aiValidation;

    public Prescription() {
    }

    // Getters and Setters

    public int getPrescriptionId() {
        return prescriptionId;
    }

    public void setPrescriptionId(int prescriptionId) {
        this.prescriptionId = prescriptionId;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPrescribedDate() {
        return prescribedDate;
    }

    public void setPrescribedDate(String prescribedDate) {
        this.prescribedDate = prescribedDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getMedicinesText() {
        return medicinesText;
    }

    public void setMedicinesText(String medicinesText) {
        this.medicinesText = medicinesText;
    }

    public String getAiValidation() {
        return aiValidation;
    }

    public void setAiValidation(String aiValidation) {
        this.aiValidation = aiValidation;
    }
}
