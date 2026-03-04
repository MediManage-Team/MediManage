package org.example.MediManage.service.ai;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Manages multiple Python Conda environments for the AI Engine.
 *
 * Each environment has its own:
 * - conda env directory: ai_engine/envs/{name}/
 * - requirements file: requirements_{name}.txt
 *
 * Locations:
 * - Dev mode (IDE): {project}/ai_engine/envs/{name}/
 * - Installed mode: %APPDATA%/MediManage/ai_engine/envs/{name}/
 */
public class PythonEnvironmentManager {

    /** Available environment names */
    public static final String ENV_CPU = "cpu";
    public static final String ENV_GPU = "gpu";

    public static final String[] ALL_ENVS = { ENV_CPU, ENV_GPU };

    public static final Map<String, String> ENV_LABELS = Map.of(
            ENV_CPU, "CPU (BitNet.cpp / llama.cpp)",
            ENV_GPU, "NVIDIA GPU (CUDA)");

    private final boolean devMode;
    private final Path aiEngineDir; // ai_engine/ directory
    private volatile boolean cancelled = false;
    private volatile Process currentProcess;

    private Consumer<String> logCallback;

    private String activeEnv = ENV_CPU; // Default to CPU

    public PythonEnvironmentManager() {
        this.devMode = isDevMode();
        if (devMode) {
            this.aiEngineDir = Paths.get("ai_engine").toAbsolutePath();
        } else {
            this.aiEngineDir = Paths.get(System.getProperty("user.dir"), "ai_engine").toAbsolutePath();
        }
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    private void log(String msg) {
        System.out.println(msg);
        if (logCallback != null)
            logCallback.accept(msg);
    }

    // ======================== ENVIRONMENT MANAGEMENT ========================

    /**
     * Ensure a specific environment is ready (Conda create + Pip install).
     */
    public String ensureEnvironment(String envName) throws Exception {
        cancelled = false;

        // 1. Ensure Conda is available
        String condaExe = findOrInstallConda();

        // 2. Create/Update Conda Environment
        Path envPath = getEnvPath(envName);
        if (!Files.exists(envPath.resolve("python.exe"))) {
            checkCancelled();
            log("🐍 Creating Conda environment '" + envName + "' at: " + envPath);
            createCondaEnv(condaExe, envName);
        }

        // 3. Install Dependencies (pip)
        if (shouldUpdateDependencies(envName)) {
            checkCancelled();
            log("📦 Installing dependencies for '" + envName + "'...");
            installDependencies(envName);
        }

        return getPythonExePath(envName);
    }

    /**
     * Ensure the default (active) environment.
     */
    public String ensureEnvironment() throws Exception {
        return ensureEnvironment(activeEnv);
    }

    /**
     * Get the Conda env path for a specific environment.
     */
    public Path getEnvPath(String envName) {
        // We use a local 'envs' folder inside ai_engine
        if (devMode) {
            return aiEngineDir.resolve("envs").resolve(envName);
        } else {
            // In installed mode, use AppData to avoid permission issues?
            // Or keep it local if portable. Let's use AppData for robust installed mode.
            String appData = System.getenv("APPDATA");
            if (appData == null)
                appData = System.getProperty("user.home");
            return Paths.get(appData, "MediManage", "envs", envName);
        }
    }

    public Path getRequirementsPath(String envName) {
        return aiEngineDir.resolve("requirements_" + envName + ".txt");
    }

    public String getPythonExePath(String envName) {
        return getEnvPath(envName).resolve("python.exe").toAbsolutePath().toString();
    }

    public String getPythonExePath() {
        return getPythonExePath(activeEnv);
    }

    // In Conda on Windows, pip is usually in Scripts/pip.exe
    public String getPipExePath(String envName) {
        return getEnvPath(envName).resolve("Scripts").resolve("pip.exe").toAbsolutePath().toString();
    }

    public boolean isEnvReady(String envName) {
        return Files.exists(Path.of(getPythonExePath(envName)));
    }

    public boolean isVenvReady() { // Keep compatible method name if used elsewhere, or refactor
        return isEnvReady(activeEnv);
    }

    // ======================== ACTIVE ENVIRONMENT ========================

    public String getActiveEnvironment() {
        return activeEnv;
    }

    public void setActiveEnvironment(String envName) {
        this.activeEnv = envName;
    }

    public String getActiveLabel() {
        return ENV_LABELS.getOrDefault(activeEnv, activeEnv);
    }

    /**
     * Map a hardware backend string to an environment name.
     * "cuda" → "gpu", "directml" → "gpu", "cpu" → "cpu"
     */
    public static String mapBackendToEnv(String backend) {
        if (backend == null)
            return ENV_CPU;
        switch (backend.toLowerCase()) {
            case "cuda":
            case "directml":
                return ENV_GPU;
            default:
                return ENV_CPU;
        }
    }

    /**
     * Auto-detect the best Python environment based on available hardware.
     * Checks for NVIDIA GPU (→ gpu), fallback to CPU.
     * Only returns envs that are already installed.
     */
    public String autoDetectBestEnv() {
        // 1. Check NVIDIA GPU via nvidia-smi
        if (isEnvReady(ENV_GPU) && hasNvidiaGpu()) {
            log("🎯 Auto-detected NVIDIA GPU — selecting '" + ENV_GPU + "' environment");
            return ENV_GPU;
        }
        // 2. Fallback to CPU
        log("ℹ️ No GPU env ready — using CPU environment");
        return ENV_CPU;
    }

    /** Check if an NVIDIA GPU is present via nvidia-smi. */
    private boolean hasNvidiaGpu() {
        try {
            Process p = new ProcessBuilder("nvidia-smi", "--query-gpu=name", "--format=csv,noheader")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            int exitCode = p.waitFor();
            if (exitCode == 0 && !output.isEmpty()) {
                log("🟢 NVIDIA GPU found: " + output.split("\\n")[0].trim());
                return true;
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    // ======================== ENVIRONMENT LISTING ========================

    public List<Map<String, Object>> listEnvironments() {
        List<Map<String, Object>> envList = new ArrayList<>();
        for (String env : ALL_ENVS) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", env);
            info.put("label", ENV_LABELS.get(env));
            info.put("installed", isEnvReady(env));
            info.put("path", getEnvPath(env).toString());
            info.put("active", env.equals(activeEnv));
            info.put("requirementsFile", getRequirementsPath(env).toString());
            envList.add(info);
        }
        return envList;
    }

    // ======================== CONDA OPERATIONS ========================

    private String findOrInstallConda() throws Exception {
        // 1. Check local Miniconda
        PythonDownloader downloader = new PythonDownloader();
        // Pass our log callback to downloader too
        downloader.setLogCallback(this.logCallback);

        if (downloader.isInstalled()) {
            return downloader.getCondaExe().toAbsolutePath().toString();
        }

        // 2. Check system PATH
        String systemConda = findSystemConda();
        if (systemConda != null) {
            return systemConda;
        }

        // 3. Download Miniconda
        log("⚠️ No Conda found. Downloading Miniconda...");
        try {
            downloader.downloadAndSetup();
            return downloader.getCondaExe().toAbsolutePath().toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download Miniconda: " + e.getMessage());
        }
    }

    private String findSystemConda() {
        try {
            ProcessBuilder pb = new ProcessBuilder("conda", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (p.waitFor() == 0)
                return "conda";
        } catch (Exception e) {
        }
        return null;
    }

    private void createCondaEnv(String condaExe, String envName) throws Exception {
        Path envPath = getEnvPath(envName);
        String pythonVersion = getPythonVersionForEnv(envName);

        // conda create -p {path} python={version} -y
        List<String> cmd = new ArrayList<>();
        cmd.add(condaExe);
        cmd.add("create");
        cmd.add("-p");
        cmd.add(envPath.toAbsolutePath().toString());
        cmd.add("python=" + pythonVersion);
        cmd.add("-y");

        runCommand(cmd, "conda create " + envName);
    }

    /**
     * Get the right Python version for the given environment.
     */
    private String getPythonVersionForEnv(String envName) {
        return "3.10"; // Default for all envs
    }

    private void installDependencies(String envName) throws Exception {
        Path reqPath = getRequirementsPath(envName);

        if (!Files.exists(reqPath)) {
            log("⚠️ Requirements file not found: " + reqPath);
            return;
        }

        String pipExe = getPipExePath(envName);
        List<String> cmd = new ArrayList<>();
        cmd.add(pipExe);
        cmd.add("install");
        cmd.add("-r");
        cmd.add(reqPath.toAbsolutePath().toString());

        runCommand(cmd, "pip install " + envName);
        writeDepTimestamp(envName);
    }

    private void runCommand(List<String> cmd, String taskName) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        currentProcess = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled) {
                    currentProcess.destroyForcibly();
                    break;
                }
                log("[" + taskName + "]: " + line);
            }
        }

        int exitCode = currentProcess.waitFor();
        currentProcess = null;
        checkCancelled();

        if (exitCode != 0) {
            throw new RuntimeException(taskName + " failed with exit code: " + exitCode);
        }
        log("✅ " + taskName + " completed successfully.");
    }

    private boolean shouldUpdateDependencies(String envName) {
        try {
            Path markerFile = getEnvPath(envName).resolve(".deps_installed");
            if (!Files.exists(markerFile))
                return true;

            Path reqPath = getRequirementsPath(envName);
            if (!Files.exists(reqPath))
                return false;

            return Files.getLastModifiedTime(reqPath).toMillis() > Files.getLastModifiedTime(markerFile).toMillis();
        } catch (Exception e) {
            return true;
        }
    }

    private void writeDepTimestamp(String envName) {
        try {
            Path markerFile = getEnvPath(envName).resolve(".deps_installed");
            Files.writeString(markerFile, String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
        }
    }

    // ======================== UTILITIES ========================

    public boolean deleteEnvironment(String envName) {
        Path envPath = getEnvPath(envName);
        if (!Files.exists(envPath))
            return false;

        try {
            // conda remove -p {path} --all -y (better than file deletion for cleanup)
            // But if conda exe is not easily available here without checking,
            // maybe manual delete is fine for local envs (since they are isolated -p).
            // Let's try recursive delete.
            Files.walk(envPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            log("🗑️ Environment '" + envName + "' deleted.");
            return true;
        } catch (Exception e) {
            log("❌ Failed to delete environment: " + e.getMessage());
            return false;
        }
    }

    private boolean isDevMode() {
        return Files.exists(Paths.get("ai_engine", "server.py"));
    }

    public void cancel() {
        cancelled = true;
        Process proc = currentProcess;
        if (proc != null && proc.isAlive()) {
            log("🛑 Cancelling operation...");
            proc.destroyForcibly();
        }
    }

    private void checkCancelled() throws InterruptedException {
        if (cancelled)
            throw new InterruptedException("Operation cancelled.");
    }
}
