package org.example.MediManage.model;

/**
 * Represents receipt/ticket printer settings for the pharmacy.
 * Matches the receipt_settings table schema.
 */
public class ReceiptSettings {
    private int settingId;
    private String pharmacyName = "MediManage Pharmacy";
    private String addressLine1;
    private String addressLine2;
    private String phone;
    private String email;
    private String gstNumber;
    private String logoPath;
    private String footerText = "Thank you for your purchase!";
    private String invoiceTemplatePath;
    private String receiptTemplatePath;
    private boolean showBarcodeOnReceipt = true;

    public ReceiptSettings() {
    }

    // settingId
    public int getSettingId() {
        return settingId;
    }

    public void setSettingId(int v) {
        settingId = v;
    }

    // pharmacyName
    public String getPharmacyName() {
        return pharmacyName;
    }

    public void setPharmacyName(String v) {
        pharmacyName = v;
    }

    // addressLine1
    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String v) {
        addressLine1 = v;
    }

    // addressLine2
    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String v) {
        addressLine2 = v;
    }

    // phone
    public String getPhone() {
        return phone;
    }

    public void setPhone(String v) {
        phone = v;
    }

    // email
    public String getEmail() {
        return email;
    }

    public void setEmail(String v) {
        email = v;
    }

    // gstNumber
    public String getGstNumber() {
        return gstNumber;
    }

    public void setGstNumber(String v) {
        gstNumber = v;
    }

    // logoPath
    public String getLogoPath() {
        return logoPath;
    }

    public void setLogoPath(String v) {
        logoPath = v;
    }

    // footerText
    public String getFooterText() {
        return footerText;
    }

    public void setFooterText(String v) {
        footerText = v;
    }

    // invoiceTemplatePath
    public String getInvoiceTemplatePath() {
        return invoiceTemplatePath;
    }

    public void setInvoiceTemplatePath(String v) {
        invoiceTemplatePath = v;
    }

    // receiptTemplatePath
    public String getReceiptTemplatePath() {
        return receiptTemplatePath;
    }

    public void setReceiptTemplatePath(String v) {
        receiptTemplatePath = v;
    }

    // showBarcodeOnReceipt
    public boolean isShowBarcodeOnReceipt() {
        return showBarcodeOnReceipt;
    }

    public void setShowBarcodeOnReceipt(boolean v) {
        showBarcodeOnReceipt = v;
    }
}
