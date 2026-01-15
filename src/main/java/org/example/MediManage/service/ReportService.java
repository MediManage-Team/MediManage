package org.example.MediManage.service;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.Customer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportService {

    public void generateInvoicePDF(List<BillItem> items, double totalAmount, String customerName, String filePath)
            throws JRException {
        // Load Template
        InputStream reportStream = getClass().getResourceAsStream("/reports/invoice.jrxml");
        if (reportStream == null) {
            throw new JRException("Invoice template not found!");
        }

        // Compile Report
        JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);

        // Parameters
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("CustomerName", customerName);
        parameters.put("TotalAmount", totalAmount);

        // Data Source
        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(items);

        // Fill Report
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

        // Export to PDF
        JasperExportManager.exportReportToPdfFile(jasperPrint, filePath);
    }

    public void exportInventoryToExcel(List<Medicine> medicines, String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Inventory");

            // Header
            Row headerRow = sheet.createRow(0);
            String[] columns = { "ID", "Medicine Name", "Company", "Expiry", "Stock", "Price" };

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data
            int rowNum = 1;
            for (Medicine med : medicines) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(med.getId());
                row.createCell(1).setCellValue(med.getName());
                row.createCell(2).setCellValue(med.getCompany());
                row.createCell(3).setCellValue(med.getExpiry());
                row.createCell(4).setCellValue(med.getStock());
                row.createCell(5).setCellValue(med.getPrice());
            }

            // Auto-size columns
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }
}
