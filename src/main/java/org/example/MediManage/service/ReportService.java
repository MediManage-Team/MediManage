package org.example.MediManage.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.MediManage.dao.ReceiptSettingsDAO;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.ReceiptSettings;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReportService {
    private static final DateTimeFormatter REPORT_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int EXCEL_SHEET_NAME_MAX = 31;
    private final ReceiptSettingsDAO receiptSettingsDAO = new ReceiptSettingsDAO();
    private final BarcodeService barcodeService = new BarcodeService();

    public enum AnalyticsExportFormat {
        PDF("pdf"),
        EXCEL("xlsx"),
        CSV("csv");

        private final String fileExtension;

        AnalyticsExportFormat(String fileExtension) {
            this.fileExtension = fileExtension;
        }

        public String fileExtension() {
            return fileExtension;
        }

        public static AnalyticsExportFormat fromValue(String value) {
            if (value == null || value.isBlank()) {
                return PDF;
            }
            String normalized = value.trim().toUpperCase();
            if ("XLSX".equals(normalized)) {
                return EXCEL;
            }
            try {
                return AnalyticsExportFormat.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return PDF;
            }
        }
    }

    public record AnalyticsReportSection(
            String title,
            List<String> headers,
            List<List<String>> rows) {
    }

    public record AnalyticsExportPayload(
            String title,
            String generatedAt,
            String filterScope,
            List<String> summaryLines,
            List<AnalyticsReportSection> sections) {
    }

    public void generateInvoicePDF(List<BillItem> items, double totalAmount, String customerName, String filePath,
            String careProtocol, Integer billId)
            throws JRException {
        generateInvoicePDF(items, totalAmount, customerName, filePath, careProtocol, billId, "");
    }

    public void generateInvoicePDF(List<BillItem> items, double totalAmount, String customerName, String filePath,
            String careProtocol, Integer billId, String prescriptionHighlights)
            throws JRException {
        if (hasLegacyInvoiceOverride()) {
            generateInvoicePDF(items, totalAmount, customerName, filePath, careProtocol);
            return;
        }
        generateInvoicePdfInternal(items, totalAmount, customerName, filePath, careProtocol, billId,
                prescriptionHighlights);
    }

    private void generateInvoicePdfInternal(
            List<BillItem> items,
            double totalAmount,
            String customerName,
            String filePath,
            String careProtocol,
            Integer billId,
            String prescriptionHighlights) throws JRException {
        try {
            ensureParentDirectory(filePath);
        } catch (IOException e) {
            throw new JRException("Failed to prepare output path: " + filePath, e);
        }

        ReceiptSettings receiptSettings = loadReceiptSettings();
        JasperReport jasperReport;
        try (InputStream reportStream = openReportTemplate(
                "/reports/invoice.jrxml",
                receiptSettings.getInvoiceTemplatePath(),
                "invoice")) {
            jasperReport = JasperCompileManager.compileReport(reportStream);
        } catch (IOException e) {
            throw new JRException("Failed to load invoice template.", e);
        }

        List<BillItem> scheduleItems = buildPrescriptionScheduleItems(items);
        JasperReport prescriptionScheduleReport = null;
        if (!scheduleItems.isEmpty() || (prescriptionHighlights != null && !prescriptionHighlights.isBlank())) {
            try (InputStream subreportStream = getClass()
                    .getResourceAsStream("/reports/invoice-prescription-schedule.jrxml")) {
                if (subreportStream == null) {
                    throw new JRException("Bundled prescription schedule subreport not found.");
                }
                prescriptionScheduleReport = JasperCompileManager.compileReport(subreportStream);
            } catch (IOException e) {
                throw new JRException("Failed to load prescription schedule subreport.", e);
            }
        }

        // Parameters
        Map<String, Object> parameters = buildDocumentParameters(
                items,
                customerName,
                totalAmount,
                careProtocol,
                prescriptionHighlights,
                receiptSettings,
                buildDocumentLabel("Invoice", billId),
                null,
                prescriptionScheduleReport,
                new JRBeanCollectionDataSource(scheduleItems));

        // Data Source
        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(items);

        // Fill Report
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

        // Export to PDF
        JasperExportManager.exportReportToPdfFile(jasperPrint, filePath);
    }

    public void generateInvoicePDF(List<BillItem> items, double totalAmount, String customerName, String filePath)
            throws JRException {
        generateInvoicePDF(items, totalAmount, customerName, filePath, "", null); // Overload for backward compatibility
    }

    public void generateInvoicePDF(List<BillItem> items, double totalAmount, String customerName, String filePath,
            String careProtocol)
            throws JRException {
        generateInvoicePdfInternal(items, totalAmount, customerName, filePath, careProtocol, null, "");
    }

    public void generateReceiptPDF(List<BillItem> items, double totalAmount, String customerName, String filePath,
            Integer billId)
            throws JRException {
        if (hasLegacyReceiptOverride()) {
            generateReceiptPDF(items, totalAmount, customerName, filePath);
            return;
        }
        generateReceiptPdfInternal(items, totalAmount, customerName, filePath, billId);
    }

    private void generateReceiptPdfInternal(
            List<BillItem> items,
            double totalAmount,
            String customerName,
            String filePath,
            Integer billId) throws JRException {
        try {
            ensureParentDirectory(filePath);
        } catch (IOException e) {
            throw new JRException("Failed to prepare output path: " + filePath, e);
        }

        ReceiptSettings receiptSettings = loadReceiptSettings();
        JasperReport jasperReport;
        try (InputStream reportStream = openReportTemplate(
                "/reports/receipt.jrxml",
                receiptSettings.getReceiptTemplatePath(),
                "receipt")) {
            jasperReport = JasperCompileManager.compileReport(reportStream);
        } catch (IOException e) {
            throw new JRException("Failed to load receipt template.", e);
        }

        // Parameters
        Image barcodeImage = receiptSettings.isShowBarcodeOnReceipt()
                ? buildBarcodeImage("RCT-" + (billId == null ? "UNSAVED" : billId))
                : null;
        Map<String, Object> parameters = buildDocumentParameters(
                items,
                customerName,
                totalAmount,
                "",
                "",
                receiptSettings,
                buildDocumentLabel("Receipt", billId),
                barcodeImage,
                null,
                new JRBeanCollectionDataSource(List.of()));

        // Data Source
        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(items);

        // Fill Report
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

        // Export to PDF
        JasperExportManager.exportReportToPdfFile(jasperPrint, filePath);
    }

    public void generateReceiptPDF(List<BillItem> items, double totalAmount, String customerName, String filePath)
            throws JRException {
        generateReceiptPdfInternal(items, totalAmount, customerName, filePath, null);
    }

    private boolean hasLegacyInvoiceOverride() {
        return isOverridePresent("generateInvoicePDF", List.class, double.class, String.class, String.class, String.class);
    }

    private boolean hasLegacyReceiptOverride() {
        return isOverridePresent("generateReceiptPDF", List.class, double.class, String.class, String.class);
    }

    private boolean isOverridePresent(String methodName, Class<?>... parameterTypes) {
        try {
            Method method = getClass().getMethod(methodName, parameterTypes);
            return method.getDeclaringClass() != ReportService.class;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    public void exportInventoryToExcel(List<Medicine> medicines, String filePath) throws IOException {
        ensureParentDirectory(filePath);
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

    public void exportAnalyticsReport(
            AnalyticsExportPayload payload,
            AnalyticsExportFormat format,
            String filePath) throws IOException {
        AnalyticsExportPayload safePayload = sanitizePayload(payload);
        AnalyticsExportFormat safeFormat = format == null ? AnalyticsExportFormat.PDF : format;
        Path exportPath = Path.of(filePath);
        Files.createDirectories(exportPath.toAbsolutePath().getParent());

        switch (safeFormat) {
            case CSV -> exportAnalyticsToCsv(safePayload, exportPath);
            case EXCEL -> exportAnalyticsToExcel(safePayload, exportPath);
            case PDF -> exportAnalyticsToPdf(safePayload, exportPath);
        }
    }

    private AnalyticsExportPayload sanitizePayload(AnalyticsExportPayload payload) {
        String defaultTitle = "MediManage Weekly Analytics Report";
        String title = payload == null || payload.title() == null || payload.title().isBlank()
                ? defaultTitle
                : payload.title().trim();
        String generatedAt = payload == null || payload.generatedAt() == null || payload.generatedAt().isBlank()
                ? LocalDateTime.now().format(REPORT_TS)
                : payload.generatedAt().trim();
        String filterScope = payload == null || payload.filterScope() == null || payload.filterScope().isBlank()
                ? "Scope: All"
                : payload.filterScope().trim();
        List<String> summaryLines = payload == null || payload.summaryLines() == null
                ? List.of()
                : payload.summaryLines().stream()
                        .filter(line -> line != null && !line.isBlank())
                        .map(String::trim)
                        .toList();

        List<AnalyticsReportSection> rawSections = payload == null || payload.sections() == null
                ? List.of()
                : payload.sections();
        List<AnalyticsReportSection> sections = new ArrayList<>();
        for (AnalyticsReportSection raw : rawSections) {
            String sectionTitle = raw == null || raw.title() == null || raw.title().isBlank()
                    ? "Section"
                    : raw.title().trim();
            List<String> headers = raw == null || raw.headers() == null
                    ? List.of()
                    : raw.headers().stream()
                            .map(header -> header == null ? "" : header.trim())
                            .toList();
            List<List<String>> rows = raw == null || raw.rows() == null
                    ? List.of()
                    : raw.rows().stream()
                            .map(row -> row == null ? List.<String>of()
                                    : row.stream()
                                            .map(cell -> cell == null ? "" : cell.trim())
                                            .toList())
                            .toList();
            sections.add(new AnalyticsReportSection(sectionTitle, headers, rows));
        }

        return new AnalyticsExportPayload(title, generatedAt, filterScope, summaryLines, sections);
    }

    private void ensureParentDirectory(String filePath) throws IOException {
        Path outputPath = Path.of(filePath).toAbsolutePath();
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private InputStream openReportTemplate(String bundledResource, String customTemplatePath, String templateLabel)
            throws IOException, JRException {
        Path customTemplate = optionalFilePath(customTemplatePath);
        if (customTemplate != null) {
            return Files.newInputStream(customTemplate);
        }

        InputStream bundledTemplate = getClass().getResourceAsStream(bundledResource);
        if (bundledTemplate == null) {
            throw new JRException("Bundled " + templateLabel + " template not found: " + bundledResource);
        }
        return bundledTemplate;
    }

    private Path optionalFilePath(String rawPath) throws IOException {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        Path path = Path.of(rawPath.trim()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IOException("Custom template file not found: " + path);
        }
        return path;
    }

    private Map<String, Object> buildDocumentParameters(
            List<BillItem> items,
            String customerName,
            double totalAmount,
            String careProtocol,
            String prescriptionHighlights,
            ReceiptSettings receiptSettings,
            String documentLabel,
            Image barcodeImage,
            JasperReport prescriptionScheduleReport,
            JRDataSource prescriptionScheduleDataSource) {
        ReceiptSettings safeSettings = receiptSettings == null ? new ReceiptSettings() : receiptSettings;
        List<BillItem> scheduleItems = buildPrescriptionScheduleItems(items);
        boolean hasPrescriptionHighlights = prescriptionHighlights != null && !prescriptionHighlights.isBlank();
        boolean hasPrescriptionSchedule = !scheduleItems.isEmpty();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("CustomerName", customerName);
        parameters.put("TotalAmount", totalAmount);
        parameters.put("CareProtocol", careProtocol == null ? "" : careProtocol);
        parameters.put("PrescriptionHighlights", prescriptionHighlights == null ? "" : prescriptionHighlights);
        parameters.put("PrescriptionHighlightsHtml", buildPrescriptionHighlightsHtml(prescriptionHighlights));
        parameters.put("PrescriptionScheduleText", buildPrescriptionScheduleText(scheduleItems));
        parameters.put("HasPrescriptionHighlights", hasPrescriptionHighlights);
        parameters.put("HasPrescriptionSchedule", hasPrescriptionSchedule);
        parameters.put("PrescriptionScheduleReport", prescriptionScheduleReport);
        parameters.put("PrescriptionScheduleDataSource", prescriptionScheduleDataSource);
        parameters.put("PharmacyName", defaultIfBlank(safeSettings.getPharmacyName(), "MediManage Pharmacy"));
        parameters.put("AddressBlock", buildAddressBlock(safeSettings));
        parameters.put("ContactLine", buildContactLine(safeSettings));
        parameters.put("FooterText", defaultIfBlank(safeSettings.getFooterText(), "Thank you for your purchase!"));
        parameters.put("DocumentNumber", documentLabel == null ? "" : documentLabel);
        parameters.put("LogoImage", loadLogoImage(safeSettings.getLogoPath()));
        parameters.put("BarcodeImage", barcodeImage);
        return parameters;
    }

    private List<BillItem> buildPrescriptionScheduleItems(List<BillItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(BillItem::hasPrescriptionDirection)
                .toList();
    }

    private String buildPrescriptionHighlightsHtml(String prescriptionHighlights) {
        if (prescriptionHighlights == null || prescriptionHighlights.isBlank()) {
            return "";
        }
        List<String> lines = prescriptionHighlights.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                html.append("<br/>");
            }
            html.append("&#8226; ").append(escapeHtml(lines.get(i)));
        }
        return html.toString();
    }

    private String buildPrescriptionScheduleText(List<BillItem> scheduleItems) {
        if (scheduleItems == null || scheduleItems.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (BillItem item : scheduleItems) {
            lines.add(item.getName() + ": " + item.getPrescriptionSummary());
        }
        return String.join("\n", lines);
    }

    private ReceiptSettings loadReceiptSettings() {
        try {
            return receiptSettingsDAO.getSettings();
        } catch (Exception ignored) {
            return new ReceiptSettings();
        }
    }

    private String buildDocumentLabel(String type, Integer billId) {
        if (billId == null || billId <= 0) {
            return type;
        }
        return type + " #" + billId;
    }

    private String buildAddressBlock(ReceiptSettings settings) {
        List<String> lines = new ArrayList<>();
        if (settings.getAddressLine1() != null && !settings.getAddressLine1().isBlank()) {
            lines.add(settings.getAddressLine1().trim());
        }
        if (settings.getAddressLine2() != null && !settings.getAddressLine2().isBlank()) {
            lines.add(settings.getAddressLine2().trim());
        }
        return String.join("\n", lines);
    }

    private String buildContactLine(ReceiptSettings settings) {
        List<String> parts = new ArrayList<>();
        if (settings.getPhone() != null && !settings.getPhone().isBlank()) {
            parts.add("Phone: " + settings.getPhone().trim());
        }
        if (settings.getEmail() != null && !settings.getEmail().isBlank()) {
            parts.add("Email: " + settings.getEmail().trim());
        }
        if (settings.getGstNumber() != null && !settings.getGstNumber().isBlank()) {
            parts.add("GST: " + settings.getGstNumber().trim());
        }
        return String.join(" | ", parts);
    }

    private Image loadLogoImage(String logoPath) {
        if (logoPath == null || logoPath.isBlank()) {
            return null;
        }
        try {
            File logoFile = new File(logoPath.trim());
            if (!logoFile.exists() || !logoFile.isFile()) {
                return null;
            }
            BufferedImage bufferedImage = ImageIO.read(logoFile);
            return bufferedImage;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Image buildBarcodeImage(String barcodeValue) {
        if (barcodeValue == null || barcodeValue.isBlank()) {
            return null;
        }
        try {
            return barcodeService.generateCode128(barcodeValue, 260, 64);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void exportAnalyticsToCsv(AnalyticsExportPayload payload, Path filePath) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append(toCsvValue(payload.title())).append('\n');
        csv.append(toCsvValue("Generated At")).append(',').append(toCsvValue(payload.generatedAt())).append('\n');
        csv.append(toCsvValue("Filter Scope")).append(',').append(toCsvValue(payload.filterScope())).append('\n');

        if (!payload.summaryLines().isEmpty()) {
            csv.append('\n').append(toCsvValue("Summary")).append('\n');
            for (String line : payload.summaryLines()) {
                csv.append(toCsvValue(line)).append('\n');
            }
        }

        for (AnalyticsReportSection section : payload.sections()) {
            csv.append('\n').append(toCsvValue(section.title())).append('\n');
            if (!section.headers().isEmpty()) {
                csv.append(String.join(",", section.headers().stream().map(this::toCsvValue).toList())).append('\n');
            }
            for (List<String> row : section.rows()) {
                csv.append(String.join(",", row.stream().map(this::toCsvValue).toList())).append('\n');
            }
        }

        Files.writeString(filePath, csv.toString(), StandardCharsets.UTF_8);
    }

    private void exportAnalyticsToExcel(AnalyticsExportPayload payload, Path filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Sheet summarySheet = workbook.createSheet("Summary");
            int rowIndex = 0;
            Row titleRow = summarySheet.createRow(rowIndex++);
            titleRow.createCell(0).setCellValue(payload.title());

            Row generatedRow = summarySheet.createRow(rowIndex++);
            generatedRow.createCell(0).setCellValue("Generated At");
            generatedRow.createCell(1).setCellValue(payload.generatedAt());

            Row scopeRow = summarySheet.createRow(rowIndex++);
            scopeRow.createCell(0).setCellValue("Filter Scope");
            scopeRow.createCell(1).setCellValue(payload.filterScope());

            if (!payload.summaryLines().isEmpty()) {
                rowIndex++;
                Row summaryHeader = summarySheet.createRow(rowIndex++);
                Cell summaryCell = summaryHeader.createCell(0);
                summaryCell.setCellValue("Summary");
                summaryCell.setCellStyle(headerStyle);
                for (String line : payload.summaryLines()) {
                    Row lineRow = summarySheet.createRow(rowIndex++);
                    lineRow.createCell(0).setCellValue(line);
                }
            }
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);

            Set<String> usedSheetNames = new LinkedHashSet<>();
            usedSheetNames.add("Summary");

            for (AnalyticsReportSection section : payload.sections()) {
                String sheetName = nextSheetName(usedSheetNames, section.title());
                usedSheetNames.add(sheetName);
                Sheet sheet = workbook.createSheet(sheetName);
                int sectionRowIndex = 0;

                if (!section.headers().isEmpty()) {
                    Row headerRow = sheet.createRow(sectionRowIndex++);
                    for (int i = 0; i < section.headers().size(); i++) {
                        Cell cell = headerRow.createCell(i);
                        cell.setCellValue(section.headers().get(i));
                        cell.setCellStyle(headerStyle);
                    }
                }

                for (List<String> rowData : section.rows()) {
                    Row row = sheet.createRow(sectionRowIndex++);
                    for (int i = 0; i < rowData.size(); i++) {
                        row.createCell(i).setCellValue(rowData.get(i));
                    }
                }

                int maxColumns = section.headers().isEmpty()
                        ? section.rows().stream().mapToInt(List::size).max().orElse(1)
                        : section.headers().size();
                for (int i = 0; i < maxColumns; i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            try (FileOutputStream out = new FileOutputStream(filePath.toFile())) {
                workbook.write(out);
            }
        }
    }

    private void exportAnalyticsToPdf(AnalyticsExportPayload payload, Path filePath) throws IOException {
        Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
        try (FileOutputStream out = new FileOutputStream(filePath.toFile())) {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph(payload.title(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            document.add(new Paragraph("Generated At: " + payload.generatedAt(),
                    FontFactory.getFont(FontFactory.HELVETICA, 10)));
            document.add(new Paragraph(payload.filterScope(), FontFactory.getFont(FontFactory.HELVETICA, 10)));

            if (!payload.summaryLines().isEmpty()) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Summary", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
                for (String line : payload.summaryLines()) {
                    document.add(new Paragraph("- " + line, FontFactory.getFont(FontFactory.HELVETICA, 10)));
                }
            }

            for (AnalyticsReportSection section : payload.sections()) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph(section.title(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));

                int columns = !section.headers().isEmpty()
                        ? section.headers().size()
                        : section.rows().stream().mapToInt(List::size).max().orElse(1);
                PdfPTable table = new PdfPTable(Math.max(1, columns));
                table.setWidthPercentage(100f);

                if (!section.headers().isEmpty()) {
                    for (String header : section.headers()) {
                        PdfPCell cell = new PdfPCell(
                                new Phrase(header, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
                        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                        table.addCell(cell);
                    }
                }

                if (section.rows().isEmpty()) {
                    PdfPCell emptyCell = new PdfPCell(
                            new Phrase("No data", FontFactory.getFont(FontFactory.HELVETICA, 9)));
                    emptyCell.setColspan(Math.max(1, columns));
                    table.addCell(emptyCell);
                } else {
                    for (List<String> row : section.rows()) {
                        for (int i = 0; i < columns; i++) {
                            String value = i < row.size() ? row.get(i) : "";
                            table.addCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA, 9)));
                        }
                    }
                }

                document.add(table);
            }
        } catch (DocumentException e) {
            throw new IOException("Failed to generate analytics PDF report", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    private String toCsvValue(String value) {
        String safe = value == null ? "" : value;
        String escaped = safe.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String nextSheetName(Set<String> usedNames, String rawTitle) {
        String base = sanitizeSheetName(rawTitle);
        if (base.isBlank()) {
            base = "Section";
        }
        String candidate = base;
        int suffix = 2;
        while (usedNames.contains(candidate)) {
            String suffixText = " " + suffix++;
            int maxBaseLength = Math.max(1, EXCEL_SHEET_NAME_MAX - suffixText.length());
            String trimmedBase = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
            candidate = trimmedBase + suffixText;
        }
        return candidate;
    }

    private String sanitizeSheetName(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isEmpty()) {
            return "";
        }
        safe = safe.replaceAll("[\\\\/:*?\\[\\]]", " ");
        safe = safe.replaceAll("\\s+", " ").trim();
        if (safe.length() > EXCEL_SHEET_NAME_MAX) {
            safe = safe.substring(0, EXCEL_SHEET_NAME_MAX);
        }
        return safe;
    }
}
