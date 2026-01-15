package org.example.MediManage.model;

public class Customer {
    private int customerId;
    private String name;
    private String email;
    private String address;
    private String nomineeName;
    private String nomineeRelation;
    private String insuranceProvider;
    private String insurancePolicyNo;
    private String diseases;
    private String photoIdPath;
    private String phoneNumber;

    public Customer() {
    }

    public Customer(int customerId, String name, String phoneNumber) {
        this.customerId = customerId;
        this.name = name;
        this.phoneNumber = phoneNumber;
    }

    public String getPhone() {
        return phoneNumber;
    }

    public Customer(String name, String email, String phoneNumber, String address, String nomineeName,
            String nomineeRelation,
            String insuranceProvider, String insurancePolicyNo, String diseases, String photoIdPath) {
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.nomineeName = nomineeName;
        this.nomineeRelation = nomineeRelation;
        this.insuranceProvider = insuranceProvider;
        this.insurancePolicyNo = insurancePolicyNo;
        this.diseases = diseases;
        this.photoIdPath = photoIdPath;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNomineeName() {
        return nomineeName;
    }

    public void setNomineeName(String nomineeName) {
        this.nomineeName = nomineeName;
    }

    public String getNomineeRelation() {
        return nomineeRelation;
    }

    public void setNomineeRelation(String nomineeRelation) {
        this.nomineeRelation = nomineeRelation;
    }

    public String getInsuranceProvider() {
        return insuranceProvider;
    }

    public void setInsuranceProvider(String insuranceProvider) {
        this.insuranceProvider = insuranceProvider;
    }

    public String getInsurancePolicyNo() {
        return insurancePolicyNo;
    }

    public void setInsurancePolicyNo(String insurancePolicyNo) {
        this.insurancePolicyNo = insurancePolicyNo;
    }

    public String getDiseases() {
        return diseases;
    }

    public void setDiseases(String diseases) {
        this.diseases = diseases;
    }

    public String getPhotoIdPath() {
        return photoIdPath;
    }

    public void setPhotoIdPath(String photoIdPath) {
        this.photoIdPath = photoIdPath;
    }
}
