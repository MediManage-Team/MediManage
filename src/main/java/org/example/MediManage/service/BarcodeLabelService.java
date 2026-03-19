package org.example.MediManage.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfWriter;
import org.example.MediManage.model.Medicine;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BarcodeLabelService {
    private final BarcodeService barcodeService = new BarcodeService();

    public void exportMedicineLabelPdf(Medicine medicine, String filePath) throws IOException {
        if (medicine == null) {
            throw new IOException("Select a medicine before printing a barcode label.");
        }
        String barcode = medicine.getBarcode();
        if (barcode == null || barcode.isBlank()) {
            throw new IOException("Assign a barcode before printing a label.");
        }

        Path outputPath = Path.of(filePath).toAbsolutePath();
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Rectangle labelSize = new Rectangle(250, 160);
        Document document = new Document(labelSize, 16, 16, 12, 12);
        try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph name = new Paragraph(medicine.getName(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
            name.setAlignment(Element.ALIGN_CENTER);
            document.add(name);

            String subline = (medicine.getCompany() == null ? "" : medicine.getCompany())
                    + (medicine.getExpiry() == null || medicine.getExpiry().isBlank() ? "" : " | Exp " + medicine.getExpiry());
            if (!subline.isBlank()) {
                Paragraph company = new Paragraph(subline, FontFactory.getFont(FontFactory.HELVETICA, 8));
                company.setAlignment(Element.ALIGN_CENTER);
                document.add(company);
            }

            Paragraph price = new Paragraph("MRP ₹" + String.format(java.util.Locale.ROOT, "%.2f", medicine.getPrice()),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
            price.setSpacingBefore(4f);
            price.setAlignment(Element.ALIGN_CENTER);
            document.add(price);

            document.add(new Paragraph(" "));
            Image barcodeImage = Image.getInstance(toPngBytes(barcode));
            barcodeImage.scaleToFit(210, 60);
            barcodeImage.setAlignment(Element.ALIGN_CENTER);
            document.add(barcodeImage);

            Paragraph code = new Paragraph(barcode, FontFactory.getFont(FontFactory.HELVETICA, 8));
            code.setAlignment(Element.ALIGN_CENTER);
            document.add(code);
        } catch (Exception e) {
            throw new IOException("Failed to generate barcode label PDF", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    private byte[] toPngBytes(String barcode) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(barcodeService.generateCode128(barcode, 220, 64), "PNG", out);
        return out.toByteArray();
    }
}
