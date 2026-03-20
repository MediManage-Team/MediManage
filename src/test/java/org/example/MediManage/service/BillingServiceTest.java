package org.example.MediManage.service;

import net.sf.jasperreports.engine.JRException;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.CustomerDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.PaymentSplit;
import org.example.MediManage.service.ai.AIOrchestrator;
import org.example.MediManage.service.ai.PythonAIClient;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillingServiceTest {

    @Test
    void addMedicineToBillKeepsGstWhenSameMedicineIsAddedTwice() {
        BillingService service = new BillingService(
                new MedicineDAO(),
                new CustomerDAO(),
                new BillDAO(),
                new ReportService(),
                new AIOrchestrator(new PythonAIClient(false)));

        List<BillItem> billItems = new ArrayList<>();
        Medicine medicine = new Medicine(11, "Paracetamol", "Paracetamol", "Acme", "2030-12-31", 20, 100.0);

        service.addMedicineToBill(billItems, medicine, 1);
        service.addMedicineToBill(billItems, medicine, 1);

        assertEquals(1, billItems.size());
        assertEquals(2, billItems.get(0).getQty());
        assertEquals(236.0, billItems.get(0).getTotal(), 0.0001);
    }

    @Test
    void addMedicineToBillRejectsQuantityThatExceedsRemainingStock() {
        BillingService service = new BillingService(
                new MedicineDAO(),
                new CustomerDAO(),
                new BillDAO(),
                new ReportService(),
                new AIOrchestrator(new PythonAIClient(false)));

        List<BillItem> billItems = new ArrayList<>();
        Medicine medicine = new Medicine(12, "Ibuprofen", "Ibuprofen", "Acme", "2030-12-31", 2, 50.0);

        BillingService.AddItemResult first = service.addMedicineToBill(billItems, medicine, 2);
        BillingService.AddItemResult second = service.addMedicineToBill(billItems, medicine, 1);

        assertSame(BillingService.AddItemStatus.ADDED, first.status());
        assertSame(BillingService.AddItemStatus.OUT_OF_STOCK, second.status());
        assertEquals(0, second.availableStock());
        assertEquals(1, billItems.size());
        assertEquals(2, billItems.get(0).getQty());
    }

    @Test
    void completeCheckoutReturnsPartialSuccessWhenPdfGenerationFails() throws Exception {
        StubBillDAO billDAO = new StubBillDAO();
        BillingService service = new BillingService(
                new MedicineDAO(),
                new CustomerDAO(),
                billDAO,
                new FailingReportService(),
                new AIOrchestrator(new PythonAIClient(false)));

        List<BillItem> items = List.of(new BillItem(1, "Amoxicillin", "2030-12-31", 1, 100.0, 18.0));
        List<PaymentSplit> paymentSplits = List.of(new PaymentSplit("Cash", 118.0));

        BillingService.CheckoutResult result = service.completeCheckout(
                items,
                null,
                9,
                paymentSplits,
                "CASH",
                "",
                "",
                false);

        assertEquals(77, result.billId());
        assertFalse(result.pdfAvailable());
        assertTrue(result.pdfErrorMessage().contains("Simulated PDF failure"));
        assertEquals(118.0, result.total(), 0.0001);
        assertTrue(billDAO.called);
    }

    private static class StubBillDAO extends BillDAO {
        private boolean called;

        @Override
        public int generateInvoice(
                double totalAmount,
                List<BillItem> items,
                Integer customerId,
                Integer userId,
                List<PaymentSplit> paymentSplits,
                String paymentMode,
                int loyaltyPointsToRedeem,
                int loyaltyPointsToAward,
                String prescriptionHighlights) {
            called = true;
            return 77;
        }

        @Override
        public void saveAICareProtocol(int billId, String careProtocol) {
            // No-op for unit test
        }
    }

    private static class FailingReportService extends ReportService {
        @Override
        public void generateInvoicePDF(
                List<BillItem> items,
                double totalAmount,
                String customerName,
                String filePath,
                String careProtocol) throws JRException {
            throw new JRException("Simulated PDF failure");
        }
    }
}
