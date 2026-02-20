package org.example.MediManage.service;

import org.example.MediManage.DatabaseUtil;
import javafx.concurrent.Task;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseService {

        private static final ExecutorService executor = Executors.newSingleThreadExecutor();

        public static void initializeAsync(Runnable onSuccess, Runnable onFailure) {
                Task<Void> initTask = new Task<>() {
                        @Override
                        protected Void call() throws Exception {
                                // Initialize DB structure and Seed Data
                                // This might involve copying the DB file (handled in Config)
                                // and ensuring tables exist (DatabaseUtil)
                                DatabaseUtil.initDB();
                                return null;
                        }
                };

                initTask.setOnSucceeded(e -> {
                        if (onSuccess != null)
                                onSuccess.run();
                });

                initTask.setOnFailed(e -> {
                        Throwable ex = initTask.getException();
                        System.err.println("Database Initialization Failed: " + ex.getMessage());
                        ex.printStackTrace();
                        if (onFailure != null)
                                onFailure.run();
                });

                executor.submit(initTask);
        }

        public static void shutdown() {
                executor.shutdown();
        }
}
