package org.example.MediManage.service;

import org.example.MediManage.model.BillItem;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BillingCareProtocolFallbackRulesEngine {

    public String build(List<BillItem> billItems, String fallbackReason) {
        StringBuilder protocol = new StringBuilder();
        protocol.append("Rule-based care protocol (AI unavailable).\n");
        if (fallbackReason != null && !fallbackReason.isBlank()) {
            protocol.append("Reason: ").append(fallbackReason.trim()).append("\n");
        }

        protocol.append("1. Follow the prescription label exactly for dose and schedule.\n")
                .append("2. Do not self-adjust dosage or combine with unknown OTC medicines without pharmacist advice.\n")
                .append("3. If unusual side effects appear, stop self-medication and consult a doctor.\n");

        List<BillItem> safeItems = billItems == null ? List.of() : billItems;
        if (!safeItems.isEmpty()) {
            protocol.append("4. Medicines in this bill:\n");
            int listed = 0;
            Set<String> seen = new LinkedHashSet<>();
            for (BillItem item : safeItems) {
                if (item == null || item.getName() == null || item.getName().isBlank()) {
                    continue;
                }
                String normalized = item.getName().trim().toLowerCase();
                if (!seen.add(normalized)) {
                    continue;
                }
                protocol.append("   - ")
                        .append(item.getName().trim())
                        .append(": qty ")
                        .append(Math.max(0, item.getQty()));
                if (item.getExpiry() != null && !item.getExpiry().isBlank()) {
                    protocol.append(", expiry ").append(item.getExpiry().trim());
                }
                protocol.append("\n");
                listed++;
                if (listed >= 6) {
                    break;
                }
            }
        } else {
            protocol.append("4. Bill is empty; verify medicines before checkout.\n");
        }

        protocol.append("5. Keep medicines in a cool, dry place and away from children.\n")
                .append("6. Return to the pharmacy if dose guidance is unclear.");

        return protocol.toString();
    }
}

