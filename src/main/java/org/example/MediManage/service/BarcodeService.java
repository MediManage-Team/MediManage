package org.example.MediManage.service;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating and decoding barcodes using ZXing.
 * Primarily uses Code128 format — ideal for pharmacy product codes.
 */
public class BarcodeService {

    private static final int DEFAULT_WIDTH = 250;
    private static final int DEFAULT_HEIGHT = 80;

    /**
     * Generate a Code128 barcode as a BufferedImage.
     */
    public BufferedImage generateCode128(String text) throws WriterException {
        return generateCode128(text, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public BufferedImage generateCode128(String text, int width, int height) throws WriterException {
        Code128Writer writer = new Code128Writer();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 2);
        BitMatrix matrix = writer.encode(text, BarcodeFormat.CODE_128, width, height, hints);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    /**
     * Generate barcode and return it as PNG bytes (for embedding in receipts/PDFs).
     */
    public byte[] generateCode128Bytes(String text) throws WriterException, IOException {
        BufferedImage img = generateCode128(text);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Save barcode image to a file.
     */
    public void saveBarcodeImage(BufferedImage img, File output) throws IOException {
        ImageIO.write(img, "PNG", output);
    }

    /**
     * Decode a barcode from an image file. Returns the decoded text.
     */
    public String decodeBarcodeFromFile(File imageFile) throws IOException, NotFoundException {
        BufferedImage img = ImageIO.read(imageFile);
        return decodeBarcodeFromImage(img);
    }

    /**
     * Decode a barcode from a BufferedImage.
     */
    public String decodeBarcodeFromImage(BufferedImage img) throws NotFoundException {
        BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(img);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        MultiFormatReader reader = new MultiFormatReader();
        Result result = reader.decode(bitmap);
        return result.getText();
    }

    /**
     * Generate an auto-barcode string for a medicine based on its ID.
     * Format: MED-{id padded to 8 digits}
     */
    public String generateMedicineBarcode(int medicineId) {
        return String.format("MED-%08d", medicineId);
    }
}
