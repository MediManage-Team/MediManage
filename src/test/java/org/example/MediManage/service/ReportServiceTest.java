package org.example.MediManage.service;

import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.PrescriptionDirection;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {

    @Test
    void generateInvoicePdfSupportsPrescriptionScheduleSection() throws Exception {
        ReportService service = new ReportService();
        BillItem item = new BillItem(1, "Amoxicillin 500", "2030-12-31", 1, 100.0, 18.0);
        item.setPrescriptionDirection(new PrescriptionDirection(
                "1 tab",
                "",
                "1 tab",
                "",
                "8 PM",
                "After meal",
                "5 days",
                "Swallow with water"));

        Path tempDir = Files.createTempDirectory("medimanage-report-test-");
        Path pdfPath = tempDir.resolve("invoice.pdf");

        service.generateInvoicePDF(
                List.of(item),
                118.0,
                "Asha Patel",
                pdfPath.toString(),
                "Take medicines as advised.",
                1042,
                "After food\nComplete full antibiotic course");

        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 0);
    }
}
