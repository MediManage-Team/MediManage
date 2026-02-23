package org.example.MediManage.service.subscription;

import org.example.MediManage.dao.SubscriptionAIDecisionLogDAO;
import org.example.MediManage.model.SubscriptionAIDecisionLog;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionAIDecisionLogServiceTest {

    @Test
    void logDecisionNormalizesCodesAndPersistsPayload() {
        FakeDecisionLogDAO dao = new FakeDecisionLogDAO(false);
        SubscriptionAIDecisionLogService service = new SubscriptionAIDecisionLogService(dao);

        service.logDecision(
                "plan recommendation",
                "customer",
                "  customer:42  ",
                "plan reco top positive benefit",
                "Top recommendation has positive benefit.",
                "{\"score\":91}",
                "SubscriptionPlanRecommendationEngine",
                "v1",
                "Plan_Prompt_Key",
                3,
                99);

        assertEquals(1, dao.rows.size());
        SubscriptionAIDecisionLog row = dao.rows.get(0);
        assertEquals("PLAN_RECOMMENDATION", row.decisionType());
        assertEquals("CUSTOMER", row.subjectType());
        assertEquals("customer:42", row.subjectRef());
        assertEquals("PLAN_RECO_TOP_POSITIVE_BENEFIT", row.reasonCode());
        assertEquals("{\"score\":91}", row.decisionPayloadJson());
        assertEquals("plan_prompt_key", row.promptKey());
    }

    @Test
    void logDecisionSwallowsDaoFailure() {
        FakeDecisionLogDAO dao = new FakeDecisionLogDAO(true);
        SubscriptionAIDecisionLogService service = new SubscriptionAIDecisionLogService(dao);

        assertDoesNotThrow(() -> service.logDecision(
                "renewal_churn_risk",
                "enrollment",
                "9001",
                "renewal_risk_high",
                "High churn risk.",
                "{\"risk\":88}",
                "SubscriptionRenewalPropensityEngine",
                "v1",
                null,
                null,
                7));
        assertTrue(dao.rows.isEmpty());
    }

    private static class FakeDecisionLogDAO extends SubscriptionAIDecisionLogDAO {
        private final boolean shouldThrow;
        private final List<SubscriptionAIDecisionLog> rows = new ArrayList<>();

        private FakeDecisionLogDAO(boolean shouldThrow) {
            this.shouldThrow = shouldThrow;
        }

        @Override
        public long appendDecisionLog(SubscriptionAIDecisionLog decisionLog) throws SQLException {
            if (shouldThrow) {
                throw new SQLException("forced failure");
            }
            rows.add(decisionLog);
            return rows.size();
        }
    }
}
