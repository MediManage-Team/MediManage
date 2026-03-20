package org.example.MediManage;

import org.example.MediManage.util.LogContext;
import org.example.MediManage.util.StructuredLogFormatter;
import org.example.MediManage.util.AppPaths;

import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Launcher {
    private static final Logger LOGGER = Logger.getLogger(Launcher.class.getName());

    public static void main(String[] args) {
        LogContext.ensureCorrelationId();
        setupLogging();
        LOGGER.info("Launching MediManage application");
        // Launch the JavaFX application
        MediManageApplication.main(args);
    }

    private static void setupLogging() {
        try {
            java.io.File logDir = AppPaths.appDataPath("logs").toFile();
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");

            for (Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }

            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new StructuredLogFormatter());
            consoleHandler.setLevel(Level.INFO);

            FileHandler fileHandler = new FileHandler(
                    new java.io.File(logDir, "application.log").getAbsolutePath(), true);
            fileHandler.setFormatter(new StructuredLogFormatter());
            fileHandler.setLevel(Level.INFO);

            rootLogger.addHandler(consoleHandler);
            rootLogger.addHandler(fileHandler);
            rootLogger.setUseParentHandlers(false);
            rootLogger.setLevel(Level.INFO);

            LOGGER.info("Logging initialized to: " + logDir.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to setup logging", e);
        }
    }
}
