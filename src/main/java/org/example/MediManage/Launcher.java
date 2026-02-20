package org.example.MediManage;

public class Launcher {
    public static void main(String[] args) {
        setupLogging();
        // Launch the JavaFX application
        MediManageApplication.main(args);
    }

    private static void setupLogging() {
        try {
            String appData = System.getenv("APPDATA");
            if (appData == null) {
                appData = System.getProperty("user.home") + "/AppData/Roaming";
            }
            java.io.File logDir = new java.io.File(appData, "MediManage/logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
            java.util.logging.FileHandler fileHandler = new java.util.logging.FileHandler(
                    new java.io.File(logDir, "application.log").getAbsolutePath(), true);
            fileHandler.setFormatter(new java.util.logging.SimpleFormatter());
            rootLogger.addHandler(fileHandler);
            rootLogger.setLevel(java.util.logging.Level.INFO);

            System.out.println("✅ Logging initialized to: " + logDir.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("❌ Failed to setup logging: " + e.getMessage());
            e.printStackTrace();
        }
    }
}