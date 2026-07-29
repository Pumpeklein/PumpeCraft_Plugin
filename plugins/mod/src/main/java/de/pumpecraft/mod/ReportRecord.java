package de.pumpecraft.mod;

record ReportRecord(int id, String reporterName, String targetName, String reason, long createdAt, boolean open) {
}
