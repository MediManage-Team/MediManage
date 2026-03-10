package org.example.MediManage;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.example.MediManage.model.BillItem;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JasperTester {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Jasper Tester...");
        
        // 1. Generate 5000 character string
        StringBuilder longText = new StringBuilder();
        longText.append("START OF CARE PROTOCOL.\n\n");
        for (int i = 0; i < 100; i++) {
            longText.append(i).append(". This is a very long line of test text designed to force pagination. We need to make sure this string does not get completely deleted by the Jasper engine.\n");
        }
        longText.append("\n\nEND OF CARE PROTOCOL.");
        
        System.out.println("String length: " + longText.length());

        // 2. Mock Items
        List<BillItem> items = new ArrayList<>();
        items.add(new BillItem(1, "Test Med", "2025", 1, 10.0, 1.0));

        // 3. Compile Report
        InputStream reportStream = JasperTester.class.getResourceAsStream("/reports/invoice.jrxml");
        JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);

        // 4. Fill Report
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("CustomerName", "Jasper Test");
        parameters.put("TotalAmount", 10.0);
        parameters.put("CareProtocol", longText.toString());

        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(items);
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

        // 5. Export
        JasperExportManager.exportReportToPdfFile(jasperPrint, "JasperTestOutput.pdf");
        System.out.println("Successfully generated JasperTestOutput.pdf!");
        
        System.exit(0);
    }
}
