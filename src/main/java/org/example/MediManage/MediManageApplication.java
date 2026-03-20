package org.example.MediManage;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.MediManage.config.WhatsAppBridgeConfig;
import org.example.MediManage.security.LocalAdminTokenManager;
import org.example.MediManage.service.AdminBootstrapService;
import org.example.MediManage.service.sidecar.SidecarHttpProbe;
import org.example.MediManage.service.sidecar.SidecarOwnershipMetadata;
import org.example.MediManage.service.sidecar.SidecarProbeResult;
import org.example.MediManage.service.sidecar.SidecarStartupAdvisor;
import org.example.MediManage.util.AppPaths;
import org.example.MediManage.util.AppExecutors;
import org.example.MediManage.controller.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MediManageApplication extends Application {
        private static final Logger LOGGER = Logger.getLogger(MediManageApplication.class.getName());
        private static final String AI_SERVICE_NAME = "medimanage-ai-engine";
        private static final int AI_PORT = 5000;
        private static final String AI_ENGINE_PATH_OVERRIDE_PROPERTY = "medimanage.ai.engine.path";

        private static MediManageApplication instance;
        private volatile Process pythonProcess;
        private volatile Process mcpProcess;
        private volatile Process whatsappBridgeProcess;
        private volatile boolean shuttingDown;
        private final AtomicBoolean startingWhatsAppBridge = new AtomicBoolean();

        public static MediManageApplication getInstance() {
                return instance;
        }

        @Override
        public void start(Stage stage) throws Exception {
                instance = this;

                // Load Cascadia Code font family (bundled TTFs)
                Font.loadFont(getClass().getResourceAsStream("/org/example/MediManage/fonts/CascadiaCode.ttf"), 13);
                Font.loadFont(getClass().getResourceAsStream("/org/example/MediManage/fonts/CascadiaMono.ttf"), 13);

                // Apply AtlantaFX Theme immediately
                Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());

                if (!initializeDatabase()) {
                        return;
                }

                if (!ensureInitialAdmin(stage)) {
                        return;
                }

                // Show Login immediately — server starts in background
                showLoginScreen(stage);

                // Start Python Server in background (non-blocking)
                startPythonServer();

                // Start or reuse the WhatsApp bridge automatically so invoice sending
                // is ready without requiring a manual click in Settings.
                startWhatsAppBridge();
        }

        private boolean initializeDatabase() {
                try {
                        org.example.MediManage.util.DatabaseUtil.initDB();
                        return true;
                } catch (Exception e) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Database Error");
                        alert.setHeaderText("Connection Failed");
                        alert.setContentText("Could not initialize the database. " + e.getMessage());
                        alert.showAndWait();
                        Platform.exit();
                        return false;
                }
        }

        private boolean ensureInitialAdmin(Stage owner) {
                try {
                        AdminBootstrapService bootstrapService = new AdminBootstrapService();
                        if (bootstrapService.requiresBootstrap()) {
                                bootstrapService.createDefaultAdminIfMissing();
                        }
                        return true;
                } catch (Exception e) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Admin Bootstrap Error");
                        alert.setHeaderText("Could not create the initial admin account");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                        Platform.exit();
                        return false;
                }
        }

        private void startPythonServer() {
                AppExecutors.runBackground(() -> {
                        try {
                                SidecarStartupAdvisor.Decision decision = evaluateAiEngineStartup();
                                if (decision.clearStaleMetadata()) {
                                        SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                                }
                                if (decision.action() == SidecarStartupAdvisor.Action.REUSE_EXISTING) {
                                        LOGGER.info(decision.message());
                                        return;
                                }
                                if (decision.action() == SidecarStartupAdvisor.Action.REJECT_CONFLICT) {
                                        LOGGER.warning(decision.message());
                                        org.example.MediManage.util.ToastNotification
                                                        .warning("AI Engine port 5000 is occupied by another process.");
                                        return;
                                }
                                java.util.prefs.Preferences prefs = java.util.prefs.Preferences
                                                .userNodeForPackage(org.example.MediManage.MediManageApplication.class);
                                Path aiEngineRoot = resolveAiEngineRoot();
                                if (aiEngineRoot == null) {
                                        throw new IllegalStateException("ai_engine directory not found.");
                                }
                                Path rawServerScript = aiEngineRoot.resolve("server").resolve("server.py");
                                Path protectedScript = aiEngineRoot.resolve("dist").resolve("server").resolve("server.py");
                                boolean useProtectedBundle = !Files.exists(rawServerScript)
                                                && Files.exists(protectedScript);

                                String pythonExe = resolvePythonExecutable(aiEngineRoot, useProtectedBundle);
                                String serverScript = useProtectedBundle
                                                ? protectedScript.toString()
                                                : rawServerScript.toString();
                                java.io.File pythonWorkingDir = aiEngineRoot.toFile();
                                java.io.File protectedDistRoot = useProtectedBundle
                                                ? protectedScript.getParent().getParent().toFile()
                                                : null;
                                final String pythonExeFinal = pythonExe;
                                LOGGER.info("🚀 Starting AI Engine (" + serverScript + ")...");
                                ProcessBuilder pb = createPythonProcessBuilder(pythonExe, serverScript, protectedDistRoot);
                                pb.redirectErrorStream(true);
                                configurePythonProcess(pb, pythonWorkingDir, protectedDistRoot);
                                pb.environment().put(LocalAdminTokenManager.ENV_NAME,
                                                LocalAdminTokenManager.getOrCreateToken());

                                pb.environment().put("MEDIMANAGE_DB_BACKEND", "sqlite");
                                pb.environment().put("MEDIMANAGE_DB_PATH",
                                                prefs.get(org.example.MediManage.config.DatabaseConfig.PREF_DB_PATH,
                                                                org.example.MediManage.config.DatabaseConfig
                                                                                .getResolvedDatabaseFile()
                                                                                .getAbsolutePath()));

                                pythonProcess = pb.start();
                                SidecarOwnershipMetadata.write(AI_SERVICE_NAME, AI_PORT, pythonProcess.pid());

                                // Add Shutdown Hook
                                Runtime.getRuntime().addShutdownHook(AppExecutors.newThread("jvm-shutdown-hook", () -> {
                                        if (pythonProcess != null && pythonProcess.isAlive()) {
                                                LOGGER.info("🛑 JVM Shutdown Hook: Killing AI Engine...");
                                                pythonProcess.destroyForcibly();
                                                SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                                        }
                                        if (mcpProcess != null && mcpProcess.isAlive()) {
                                                LOGGER.info("🛑 JVM Shutdown Hook: Killing MCP Server...");
                                                mcpProcess.destroyForcibly();
                                        }
                                }, false));

                                java.io.BufferedReader reader = new java.io.BufferedReader(
                                                new java.io.InputStreamReader(pythonProcess.getInputStream()));
                                String line;
                                boolean serverReady = false;
                                while ((line = reader.readLine()) != null) {
                                        LOGGER.info("[AI Engine]: " + line);
                                        if (!serverReady && line.contains("Running on")) {
                                                org.example.MediManage.util.ToastNotification
                                                                .success("AI Engine Ready");
                                                serverReady = true;
                                                startMcpServer(pythonExeFinal);
                                        }
                                }

                                int exitCode = pythonProcess.waitFor();
                                SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                                if (!serverReady) {
                                        String failureMessage = "AI Engine exited before becoming ready (code " + exitCode + ").";
                                        LOGGER.warning("❌ " + failureMessage);
                                        org.example.MediManage.util.ToastNotification.error("AI Engine failed to start");
                                }
                        } catch (InterruptedException e) {
                                SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                                Thread.currentThread().interrupt();
                                if (shuttingDown) {
                                        LOGGER.info("AI Engine watcher interrupted during application shutdown.");
                                        return;
                                }
                                LOGGER.log(Level.WARNING, "AI Engine watcher interrupted unexpectedly.", e);
                        } catch (Exception e) {
                                SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                                if (shuttingDown) {
                                        LOGGER.info("AI Engine startup watcher stopped during shutdown.");
                                        return;
                                }
                                LOGGER.log(Level.SEVERE, "❌ Failed to start AI Engine: " + e.getMessage(), e);
                                org.example.MediManage.util.ToastNotification.error("AI Engine failed to start");
                        }
                });

        }

        private SidecarStartupAdvisor.Decision evaluateAiEngineStartup() {
                SidecarProbeResult probe = SidecarHttpProbe.probe(
                                URI.create("http://127.0.0.1:5000/health"),
                                AI_SERVICE_NAME,
                                LocalAdminTokenManager::applyHeader);
                return SidecarStartupAdvisor.decide("AI Engine",
                                probe,
                                SidecarOwnershipMetadata.read(AI_SERVICE_NAME));
        }

        private SidecarStartupAdvisor.Decision evaluateBridgeStartup() {
                SidecarProbeResult probe = SidecarHttpProbe.probe(
                                URI.create(WhatsAppBridgeConfig.statusUrl()),
                                WhatsAppBridgeConfig.SERVICE_NAME,
                                WhatsAppBridgeConfig::applyAdminHeader);
                return SidecarStartupAdvisor.decide("WhatsApp Bridge",
                                probe,
                                SidecarOwnershipMetadata.read(WhatsAppBridgeConfig.SERVICE_NAME));
        }

        /**
         * Start WhatsApp Bridge (Node.js server) on port 3000.
         * Mirrors the Python server lifecycle: auto-start, log forwarding.
         */
        public void startWhatsAppBridge() {
                AppExecutors.runBackground(() -> {
                        if (!startingWhatsAppBridge.compareAndSet(false, true)) {
                                LOGGER.fine("WhatsApp Bridge startup already in progress.");
                                return;
                        }
                        try {
                                SidecarStartupAdvisor.Decision decision = evaluateBridgeStartup();
                                if (decision.clearStaleMetadata()) {
                                        SidecarOwnershipMetadata.delete(WhatsAppBridgeConfig.SERVICE_NAME);
                                }
                                if (decision.action() == SidecarStartupAdvisor.Action.REUSE_EXISTING) {
                                        LOGGER.info(decision.message());
                                        return;
                                }
                                if (decision.action() == SidecarStartupAdvisor.Action.REJECT_CONFLICT) {
                                        LOGGER.warning(decision.message());
                                        org.example.MediManage.util.ToastNotification.warning(
                                                        "WhatsApp Bridge port " + WhatsAppBridgeConfig.getPort()
                                                                        + " is occupied by another process.");
                                        return;
                                }

                                // Resolve Node.js executable (bundled or system)
                                String nodeExe = WhatsAppBridgeConfig.resolveNodeExe();

                                // Check if Node.js is available on this system
                                if (!WhatsAppBridgeConfig.isNodeAvailable()) {
                                        LOGGER.warning("⚠️ Node.js is not installed. WhatsApp Bridge requires Node.js. Skipping.");
                                        return;
                                }

                                java.io.File serverDir = WhatsAppBridgeConfig.resolveServerDir();
                                if (serverDir == null) {
                                        LOGGER.warning("⚠️ whatsapp-server/ directory not found. WhatsApp Bridge not started.");
                                        return;
                                }

                                // Check if node_modules exists, run npm install if missing
                                java.io.File nodeModules = new java.io.File(serverDir, "node_modules");
                                if (!nodeModules.isDirectory()) {
                                        if (!WhatsAppBridgeConfig.isNpmAvailable()) {
                                                LOGGER.warning(
                                                                "⚠️ npm is not installed. Cannot provision WhatsApp Bridge dependencies.");
                                                return;
                                        }
                                        LOGGER.info("📦 Installing WhatsApp Bridge dependencies (npm install)...");
                                        runLoggedProcess(
                                                        WhatsAppBridgeConfig
                                                                        .createDependencyInstallProcessBuilder(serverDir),
                                                        "[npm install]");
                                }

                                if (!WhatsAppBridgeConfig.isBrowserAvailable(serverDir)) {
                                        if (!WhatsAppBridgeConfig.isNpmAvailable()) {
                                                LOGGER.warning(
                                                                "⚠️ Chromium/Chrome is missing and npm is unavailable. WhatsApp Bridge cannot start.");
                                                return;
                                        }
                                        LOGGER.info(
                                                        "🌐 No Chrome/Chromium detected for WhatsApp Bridge. Installing Puppeteer-managed browser...");
                                        runLoggedProcess(
                                                        WhatsAppBridgeConfig
                                                                        .createBrowserInstallProcessBuilder(serverDir),
                                                        "[puppeteer install]");
                                }

                                LOGGER.info("🟢 Starting WhatsApp Bridge (" + nodeExe + ", port "
                                                + WhatsAppBridgeConfig.getPort() + ")...");
                                ProcessBuilder pb = WhatsAppBridgeConfig.createStartProcessBuilder();
                                instance.whatsappBridgeProcess = pb.start();
                                SidecarOwnershipMetadata.write(WhatsAppBridgeConfig.SERVICE_NAME,
                                                WhatsAppBridgeConfig.getPort(), instance.whatsappBridgeProcess.pid());

                                // Consume output in background thread
                                java.io.BufferedReader reader = new java.io.BufferedReader(
                                                new java.io.InputStreamReader(instance.whatsappBridgeProcess.getInputStream()));
                                String line;
                                while ((line = reader.readLine()) != null) {
                                        LOGGER.info("[WhatsApp Bridge]: " + line);
                                }
                                whatsappBridgeProcess = null;
                                SidecarOwnershipMetadata.delete(WhatsAppBridgeConfig.SERVICE_NAME);
                        } catch (Exception e) {
                                whatsappBridgeProcess = null;
                                SidecarOwnershipMetadata.delete(WhatsAppBridgeConfig.SERVICE_NAME);
                                LOGGER.log(Level.WARNING, "⚠️ WhatsApp Bridge failed to start: " + e.getMessage(), e);
                        } finally {
                                startingWhatsAppBridge.set(false);
                        }
                });
        }


        /**
         * Restart the AI Engine with the currently active environment.
         * Called from SettingsController when user switches environment.
         */
        public void restartServer() {
                AppExecutors.runBackground(() -> {
                        LOGGER.info("🔄 Restarting AI Engine...");
                        if (pythonProcess != null && pythonProcess.isAlive()) {
                                pythonProcess.destroyForcibly();
                                try {
                                        pythonProcess.waitFor();
                                } catch (InterruptedException ignored) {
                                        Thread.currentThread().interrupt();
                                }
                                LOGGER.info("🛑 Old AI Engine stopped.");
                        }
                        if (mcpProcess != null && mcpProcess.isAlive()) {
                                mcpProcess.destroyForcibly();
                                try {
                                        mcpProcess.waitFor();
                                } catch (InterruptedException ignored) {
                                        Thread.currentThread().interrupt();
                                }
                                LOGGER.info("🛑 Old MCP Server stopped.");
                        }
                        startPythonServer();
                });
        }

        /**
         * Start MCP Server on port 5001 as a sidecar process.
         * Uses the same Python environment as the AI Engine.
         */
        private void startMcpServer(String pythonExe) {
                AppExecutors.runBackground(() -> {
                        try {
                                // Check if port 5001 already in use
                                try (java.net.Socket ignored = new java.net.Socket("127.0.0.1", 5001)) {
                                        LOGGER.info("ℹ️ MCP Server already running on port 5001.");
                                        return;
                                } catch (Exception e) {
                                        // Port 5001 is free
                                }

                                LOGGER.info("🔌 Starting MCP Server (port 5001)...");
                                
                                Path aiEngineRoot = resolveAiEngineRoot();
                                if (aiEngineRoot == null) {
                                        throw new IllegalStateException("ai_engine directory not found.");
                                }
                                Path rawMcpScript = aiEngineRoot.resolve("server").resolve("mcp_server.py");
                                Path protectedMcpScript = aiEngineRoot.resolve("dist").resolve("server")
                                                .resolve("mcp_server.py");
                                boolean useProtectedBundle = !Files.exists(rawMcpScript) && Files.exists(protectedMcpScript);

                                String mcpScript = useProtectedBundle
                                                ? protectedMcpScript.toString()
                                                : rawMcpScript.toString();
                                java.io.File pythonWorkingDir = aiEngineRoot.toFile();
                                java.io.File protectedDistRoot = useProtectedBundle
                                                ? protectedMcpScript.getParent().getParent().toFile()
                                                : null;
                                ProcessBuilder mcpPb = createPythonProcessBuilder(pythonExe, mcpScript, protectedDistRoot);
                                mcpPb.redirectErrorStream(true);
                                configurePythonProcess(mcpPb, pythonWorkingDir, protectedDistRoot);
                                java.util.prefs.Preferences prefs = java.util.prefs.Preferences
                                                .userNodeForPackage(org.example.MediManage.MediManageApplication.class);
                                mcpPb.environment().put(LocalAdminTokenManager.ENV_NAME,
                                                LocalAdminTokenManager.getOrCreateToken());
                                mcpPb.environment().put("MEDIMANAGE_DB_BACKEND", "sqlite");
                                mcpPb.environment().put("MEDIMANAGE_DB_PATH",
                                                prefs.get(org.example.MediManage.config.DatabaseConfig.PREF_DB_PATH,
                                                                org.example.MediManage.config.DatabaseConfig
                                                                                .getResolvedDatabaseFile()
                                                                                .getAbsolutePath()));
                                mcpProcess = mcpPb.start();

                                // Consume MCP output in background
                                java.io.BufferedReader mcpReader = new java.io.BufferedReader(
                                                new java.io.InputStreamReader(mcpProcess.getInputStream()));
                                String mcpLine;
                                while ((mcpLine = mcpReader.readLine()) != null) {
                                        LOGGER.info("[MCP Server]: " + mcpLine);
                                }
                        } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "⚠️ MCP Server failed to start: " + e.getMessage(), e);
                        }
                });
        }

        private void configurePythonProcess(ProcessBuilder processBuilder, java.io.File workingDir,
                        java.io.File distRoot) {
                if (processBuilder == null) {
                        return;
                }

                if (distRoot != null && distRoot.isDirectory()) {
                        processBuilder.directory(distRoot);
                } else if (workingDir != null && workingDir.isDirectory()) {
                        processBuilder.directory(workingDir);
                }

                Map<String, String> env = processBuilder.environment();
                env.putIfAbsent("PYTHONIOENCODING", "utf-8");
        }

        private ProcessBuilder createPythonProcessBuilder(String pythonExe, String scriptPath, java.io.File distRoot) {
                if (distRoot == null || !distRoot.isDirectory()) {
                        return new ProcessBuilder(pythonExe, scriptPath);
                }

                String normalizedDistRoot = toPythonLiteralPath(distRoot.getAbsolutePath());
                String normalizedScriptPath = toPythonLiteralPath(scriptPath);
                String bootstrap = "import runpy, sys; sys.path.insert(0, '" + normalizedDistRoot
                                + "'); runpy.run_path('" + normalizedScriptPath + "', run_name='__main__')";

                return new ProcessBuilder(List.of(pythonExe, "-c", bootstrap));
        }

        private String toPythonLiteralPath(String path) {
                return path.replace("\\", "/").replace("'", "\\'");
        }

        private Path resolveAiEngineRoot() {
                List<Path> candidates = new ArrayList<>();

                String override = System.getProperty(AI_ENGINE_PATH_OVERRIDE_PROPERTY, "").trim();
                if (!override.isBlank()) {
                        candidates.add(Path.of(override));
                }

                Path installRoot = AppPaths.resolveInstallRoot();
                candidates.add(installRoot.resolve("ai_engine"));
                candidates.add(installRoot.resolve("app").resolve("ai_engine"));
                candidates.add(installRoot.resolve("lib").resolve("app").resolve("ai_engine"));
                candidates.add(Path.of(System.getProperty("user.dir")).resolve("ai_engine"));

                for (Path candidate : candidates) {
                        if (candidate != null && Files.isDirectory(candidate)) {
                                return candidate.toAbsolutePath().normalize();
                        }
                }
                return null;
        }

        private String resolvePythonExecutable(Path aiEngineRoot, boolean useProtectedBundle) throws Exception {
                Path bundledPython = findPythonExecutable(aiEngineRoot.resolve("python"));
                if (bundledPython != null) {
                        return bundledPython.toString();
                }

                Path venvDir = AppPaths.appDataPath("ai_engine", ".venv");
                Path venvPython = findPythonExecutable(venvDir);
                if (venvPython != null) {
                        return venvPython.toString();
                }

                if (!useProtectedBundle) {
                        String systemPython = findAvailableCommand(
                                        AppPaths.isWindows() ? new String[] { "python", "py" }
                                                        : new String[] { "python3", "python" });
                        if (systemPython == null) {
                                throw new IllegalStateException("Python 3 runtime not found on PATH.");
                        }

                        Path requirements = aiEngineRoot.resolve("requirements").resolve("requirements.txt");
                        if (Files.isRegularFile(requirements)) {
                                try {
                                        bootstrapPythonVenv(systemPython, aiEngineRoot, requirements, venvDir);
                                        Path bootstrappedPython = findPythonExecutable(venvDir);
                                        if (bootstrappedPython != null) {
                                                return bootstrappedPython.toString();
                                        }
                                } catch (Exception e) {
                                        LOGGER.log(Level.WARNING,
                                                        "Failed to provision AI Engine virtual environment. Falling back to system Python.",
                                                        e);
                                }
                        }

                        return systemPython;
                }

                throw new IllegalStateException("Bundled Python runtime not found for the protected AI Engine.");
        }

        static List<Path> pythonExecutableCandidates(Path root) {
                if (root == null) {
                        return List.of();
                }

                if (AppPaths.isWindows()) {
                        return List.of(
                                        root.resolve("python.exe"),
                                        root.resolve("Scripts").resolve("python.exe"),
                                        root.resolve("bin").resolve("python.exe"),
                                        root.resolve("bin").resolve("python"));
                }

                return List.of(
                                root.resolve("bin").resolve("python3"),
                                root.resolve("bin").resolve("python"),
                                root.resolve("python3"),
                                root.resolve("python"));
        }

        private Path findPythonExecutable(Path root) {
                if (root == null) {
                        return null;
                }

                for (Path candidate : pythonExecutableCandidates(root)) {
                        if (Files.isRegularFile(candidate)) {
                                return candidate.toAbsolutePath().normalize();
                        }
                }
                return null;
        }

        private String findAvailableCommand(String[] commands) {
                for (String command : commands) {
                        if (isCommandAvailable(command)) {
                                return command;
                        }
                }
                return null;
        }

        private boolean isCommandAvailable(String command) {
                try {
                        Process process = new ProcessBuilder(command, "--version")
                                        .redirectErrorStream(true)
                                        .start();
                        int exitCode = process.waitFor();
                        return exitCode == 0;
                } catch (Exception e) {
                        return false;
                }
        }

        private void bootstrapPythonVenv(String basePython, Path aiEngineRoot, Path requirements,
                        Path venvDir) throws Exception {
                if (findPythonExecutable(venvDir) != null) {
                        return;
                }

                if (venvDir.getParent() != null) {
                        Files.createDirectories(venvDir.getParent());
                }

                ProcessBuilder venvBuilder = new ProcessBuilder(basePython, "-m", "venv", venvDir.toString());
                venvBuilder.directory(aiEngineRoot.toFile());
                runLoggedProcess(venvBuilder, "[AI Engine setup]");

                Path venvPython = findPythonExecutable(venvDir);
                if (venvPython == null) {
                        throw new IllegalStateException("Python virtual environment was created without a runnable interpreter.");
                }

                ProcessBuilder pipBuilder = new ProcessBuilder(
                                venvPython.toString(),
                                "-m",
                                "pip",
                                "install",
                                "--disable-pip-version-check",
                                "-r",
                                requirements.toString());
                pipBuilder.directory(aiEngineRoot.toFile());
                runLoggedProcess(pipBuilder, "[AI Engine setup]");
        }

        private void runLoggedProcess(ProcessBuilder processBuilder, String logPrefix) throws Exception {
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                                LOGGER.info(logPrefix + ": " + line);
                        }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                        throw new IllegalStateException(
                                        logPrefix + " exited with code " + exitCode + ".");
                }
        }

        private void showLoginScreen(Stage stage) {
                try {
                        FXMLLoader loader = new FXMLLoader(
                                        getClass().getResource("/org/example/MediManage/login-view.fxml"));
                        javafx.scene.Parent root = loader.load();

                        // Add drop shadow to the login card for popup effect
                        DropShadow shadow = new DropShadow();
                        shadow.setRadius(30);
                        shadow.setOffsetX(0);
                        shadow.setOffsetY(8);
                        shadow.setColor(Color.color(0, 0, 0, 0.6));
                        root.setEffect(shadow);

                        Scene scene = new Scene(root, 520, 500);
                        scene.setFill(Color.TRANSPARENT);
                        scene.getStylesheets().add(getClass().getResource("/org/example/MediManage/css/common.css")
                                        .toExternalForm());

                        // Create an undecorated popup stage
                        Stage loginStage = new Stage();
                        loginStage.initStyle(StageStyle.TRANSPARENT);
                        loginStage.initModality(Modality.APPLICATION_MODAL);
                        loginStage.setTitle("MediManage - Login");
                        try {
                                loginStage.getIcons().add(new Image(getClass().getResourceAsStream("/app_icon.png")));
                        } catch (Exception e) {
                                /* ignore */ }
                        loginStage.setScene(scene);
                        loginStage.setResizable(false);
                        loginStage.centerOnScreen();

                        // Pass the primary stage reference to the controller
                        LoginController controller = loader.getController();
                        controller.setPrimaryStage(stage);

                        loginStage.showAndWait();
                } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Failed to show login screen.", e);
                }
        }

        @Override
        public void stop() throws Exception {
                super.stop();
                shuttingDown = true;
                if (pythonProcess != null) {
                        LOGGER.info("🛑 Stopping AI Engine...");
                        pythonProcess.destroyForcibly();
                        SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                } else {
                        // Try to kill via HTTP if process handle is missing
                        try {
                                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                                                .uri(URI.create("http://127.0.0.1:5000/shutdown"))
                                                .POST(HttpRequest.BodyPublishers.noBody());
                                LocalAdminTokenManager.applyHeader(requestBuilder);
                                HttpClient.newHttpClient().send(requestBuilder.build(),
                                                HttpResponse.BodyHandlers.discarding());
                                LOGGER.info("🛑 Sent shutdown signal to existing AI Engine.");
                                SidecarOwnershipMetadata.delete(AI_SERVICE_NAME);
                        } catch (Exception ignored) {}
                }

                if (mcpProcess != null) {
                        LOGGER.info("🛑 Stopping MCP Server...");
                        mcpProcess.destroyForcibly();
                }

                if (whatsappBridgeProcess != null) {
                        LOGGER.info("🛑 Stopping WhatsApp Bridge...");
                        whatsappBridgeProcess.destroyForcibly();
                        SidecarOwnershipMetadata.delete(WhatsAppBridgeConfig.SERVICE_NAME);
                } else {
                        // Force shutdown of bridge via configured local port if process handle lost
                        try {
                                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                                                .uri(URI.create(WhatsAppBridgeConfig.shutdownUrl()))
                                                .POST(HttpRequest.BodyPublishers.noBody());
                                WhatsAppBridgeConfig.applyAdminHeader(requestBuilder);
                                HttpClient.newHttpClient().send(requestBuilder.build(),
                                                HttpResponse.BodyHandlers.discarding());
                                SidecarOwnershipMetadata.delete(WhatsAppBridgeConfig.SERVICE_NAME);
                        } catch (Exception ignored) {}
                }

                org.example.MediManage.service.DatabaseService.shutdown();
                AppExecutors.shutdown();
                Platform.exit();
                System.exit(0);
        }

        public static void main(String[] args) {
                launch();
        }
}
