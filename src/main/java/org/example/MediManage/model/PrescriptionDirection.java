package org.example.MediManage.model;

import java.util.ArrayList;
import java.util.List;

public class PrescriptionDirection {
    private String morningDose = "";
    private String afternoonDose = "";
    private String eveningDose = "";
    private String nightDose = "";
    private String exactTime = "";
    private String mealRelation = "";
    private String duration = "";
    private String shortNote = "";

    public PrescriptionDirection() {
    }

    public PrescriptionDirection(
            String morningDose,
            String afternoonDose,
            String eveningDose,
            String nightDose,
            String exactTime,
            String mealRelation,
            String duration,
            String shortNote) {
        setMorningDose(morningDose);
        setAfternoonDose(afternoonDose);
        setEveningDose(eveningDose);
        setNightDose(nightDose);
        setExactTime(exactTime);
        setMealRelation(mealRelation);
        setDuration(duration);
        setShortNote(shortNote);
    }

    public PrescriptionDirection copy() {
        return new PrescriptionDirection(
                morningDose,
                afternoonDose,
                eveningDose,
                nightDose,
                exactTime,
                mealRelation,
                duration,
                shortNote);
    }

    public boolean isEmpty() {
        return morningDose.isBlank()
                && afternoonDose.isBlank()
                && eveningDose.isBlank()
                && nightDose.isBlank()
                && exactTime.isBlank()
                && mealRelation.isBlank()
                && duration.isBlank()
                && shortNote.isBlank();
    }

    public int configuredFieldCount() {
        int count = 0;
        if (!morningDose.isBlank()) count++;
        if (!afternoonDose.isBlank()) count++;
        if (!eveningDose.isBlank()) count++;
        if (!nightDose.isBlank()) count++;
        if (!exactTime.isBlank()) count++;
        if (!mealRelation.isBlank()) count++;
        if (!duration.isBlank()) count++;
        if (!shortNote.isBlank()) count++;
        return count;
    }

    public String buildSummary() {
        if (isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        String slotSummary = buildSlotSummary();
        if (!slotSummary.isBlank()) {
            parts.add(slotSummary);
        }
        if (!exactTime.isBlank()) {
            parts.add(exactTime);
        }
        if (!mealRelation.isBlank()) {
            parts.add(mealRelation);
        }
        if (!duration.isBlank()) {
            parts.add(duration);
        }
        if (!shortNote.isBlank()) {
            parts.add(shortNote);
        }
        return String.join(" | ", parts);
    }

    public String buildSlotSummary() {
        List<String> parts = new ArrayList<>();
        if (!morningDose.isBlank()) parts.add("M: " + morningDose);
        if (!afternoonDose.isBlank()) parts.add("A: " + afternoonDose);
        if (!eveningDose.isBlank()) parts.add("E: " + eveningDose);
        if (!nightDose.isBlank()) parts.add("N: " + nightDose);
        return String.join(" | ", parts);
    }

    public String getMorningDose() {
        return morningDose;
    }

    public void setMorningDose(String morningDose) {
        this.morningDose = normalize(morningDose);
    }

    public String getAfternoonDose() {
        return afternoonDose;
    }

    public void setAfternoonDose(String afternoonDose) {
        this.afternoonDose = normalize(afternoonDose);
    }

    public String getEveningDose() {
        return eveningDose;
    }

    public void setEveningDose(String eveningDose) {
        this.eveningDose = normalize(eveningDose);
    }

    public String getNightDose() {
        return nightDose;
    }

    public void setNightDose(String nightDose) {
        this.nightDose = normalize(nightDose);
    }

    public String getExactTime() {
        return exactTime;
    }

    public void setExactTime(String exactTime) {
        this.exactTime = normalize(exactTime);
    }

    public String getMealRelation() {
        return mealRelation;
    }

    public void setMealRelation(String mealRelation) {
        this.mealRelation = normalize(mealRelation);
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = normalize(duration);
    }

    public String getShortNote() {
        return shortNote;
    }

    public void setShortNote(String shortNote) {
        this.shortNote = normalize(shortNote);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
