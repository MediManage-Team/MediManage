package org.example.MediManage.service;

import org.example.MediManage.DatabaseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.concurrent.Task;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for managing database initialization and background tasks.
 * Provides asynchronous database initialization with proper error handling.
 */
public class DatabaseService {

        private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
        private static final ExecutorService executor = Executors.newSingleThreadExecutor();

        /**
         * Initializes the database asynchronously.
         * 
         * @param onSuccess callback to run on successful initialization
         * @param onFailure callback to run on failure
         */
        public static void initializeAsync(Runnable onSuccess, Runnable onFailure) {
                Task<Void> initTask = new Task<>() {
                        @Override
                        protected Void call() throws Exception {
                                try {
                                        DatabaseUtil.initDB();
                                        logger.info("Database initialized successfully");
                                        return null;
                                } catch (SQLException e) {
                                        logger.error("Database initialization failed", e);
                                        throw e;
                                }
                        }
                };

                initTask.setOnSucceeded(e -> {
                        logger.info("Database initialization task completed successfully");
                        if (onSuccess != null) {
                                onSuccess.run();
                        }
                });

                initTask.setOnFailed(e -> {
                        Throwable ex = initTask.getException();
                        logger.error("Database initialization task failed: {}", ex.getMessage(), ex);
                        if (onFailure != null) {
                                onFailure.run();
                        }
                });

                executor.submit(initTask);
        }

        /**
         * Shuts down the executor service.
         * Should be called on application shutdown.
         */
        public static void shutdown() {
                executor.shutdown();
                logger.info("DatabaseService executor shut down");
        }
}
