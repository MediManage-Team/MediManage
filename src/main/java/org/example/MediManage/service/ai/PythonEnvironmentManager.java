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
    public static final String ENV_NPU_AMD = "npu_amd";
    public static final String ENV_NPU_INTEL = "npu_intel";

    public static final String[] ALL_ENVS = { ENV_CPU, ENV_GPU, ENV_NPU_AMD, ENV_NPU_INTEL };

    public static final Map<String, String> ENV_LABELS = Map.of(
            ENV_CPU, "CPU (BitNet.cpp / llama.cpp)",
            ENV_GPU, "NVIDIA GPU (CUDA)",
            ENV_NPU_AMD, "AMD NPU (Ryzen AI / DirectML)",
            ENV_NPU_INTEL, "Intel NPU (OpenVINO / Intel AI)");

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
     * Map a hardware backend string (from hardware_detect.py) to an environment
     * name.
     * "cuda" → "gpu", "directml" → "npu_amd", "openvino" → "npu_intel", "cpu" →
     * "cpu"
     */
    public static String mapBackendToEnv(String backend) {
        if (backend == null)
            return ENV_CPU;
        switch (backend.toLowerCase()) {
            case "cuda":
                return ENV_GPU;
            case "directml":
                return ENV_NPU_AMD;
            case "openvino":
                return ENV_NPU_INTEL;
            default:
                return ENV_CPU;
        }
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
     * XDNA 2 (Ryzen AI 300) requires Python 3.12; XDNA 1 uses 3.10.
     */
    private String getPythonVersionForEnv(String envName) {
        if (ENV_NPU_AMD.equals(envName)) {
            String xdnaGen = detectXdnaGeneration();
            if ("xdna2".equals(xdnaGen)) {
                log("🔹 XDNA 2 detected — using Python 3.12");
                return "3.12";
            }
        }
        return "3.10"; // Default for all other envs
    }

    /**
     * Detect AMD XDNA NPU generation from CPU name.
     * Returns "xdna1", "xdna2", or null.
     */
    public String detectXdnaGeneration() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-Command",
                    "(Get-CimInstance Win32_Processor).Name");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String cpuName;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                cpuName = reader.readLine();
            }
            p.waitFor();

            if (cpuName == null)
                return null;
            cpuName = cpuName.trim().toLowerCase();

            // XDNA 2: Ryzen AI 300 series (Strix/Krackan Point)
            if (cpuName.contains("ai 9 hx") || cpuName.contains("ai 7 pro") ||
                    cpuName.contains("ai 5 pro") || cpuName.contains("ai 7 350") ||
                    cpuName.contains("ai 5 340") || cpuName.contains("ai 9 365") ||
                    cpuName.contains("ryzen ai 300") || cpuName.contains("strix") ||
                    cpuName.contains("krackan")) {
                return "xdna2";
            }
            // XDNA 1: Ryzen 7x40/8x40 series (Phoenix/Hawk Point)
            if (cpuName.contains("7840") || cpuName.contains("7640") ||
                    cpuName.contains("7940") || cpuName.contains("8840") ||
                    cpuName.contains("8640") || cpuName.contains("8845") ||
                    cpuName.contains("8940") || cpuName.contains(" z1")) {
                return "xdna1";
            }
        } catch (Exception e) {
            log("⚠️ Could not detect XDNA generation: " + e.getMessage());
        }
        return null;
    }

    private void installDependencies(String envName) throws Exception {
        // For npu_amd, check for XDNA-specific requirements first
        Path reqPath = getRequirementsPath(envName);
        if (ENV_NPU_AMD.equals(envName)) {
            String xdnaGen = detectXdnaGeneration();
            if (xdnaGen != null) {
                Path xdnaReq = aiEngineDir.resolve("requirements_npu_amd_" + xdnaGen + ".txt");
                if (Files.exists(xdnaReq)) {
                    log("📋 Using XDNA-specific requirements: " + xdnaReq.getFileName());
                    reqPath = xdnaReq;
                }
            }
        }

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

        // For AMD NPU env, add AMD's pip index
        if (ENV_NPU_AMD.equals(envName)) {
            cmd.add("--extra-index-url");
            cmd.add("https://pypi.amd.com/simple");
        }

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
