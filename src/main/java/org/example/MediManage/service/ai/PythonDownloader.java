package org.example.MediManage.service.ai;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Handles downloading and installing Miniconda if no system Conda is found.
 */
public class PythonDownloader {

    private static final String MINICONDA_URL = "https://repo.anaconda.com/miniconda/Miniconda3-latest-Windows-x86_64.exe";

    // Install dir: %APPDATA%/MediManage/miniconda
    private final Path installDir;
    private final HttpClient httpClient;
    private Consumer<String> logCallback;

    public PythonDownloader() {
        String appData = System.getenv("APPDATA");
        if (appData == null)
            appData = System.getProperty("user.home");
        this.installDir = Paths.get(appData, "MediManage", "miniconda");
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    private void log(String msg) {
        System.out.println(msg);
        if (logCallback != null)
            logCallback.accept(msg);
    }

    /**
     * Checks if Miniconda is installed.
     */
    public boolean isInstalled() {
        return Files.exists(getCondaExe());
    }

    public Path getCondaExe() {
        // Miniconda puts conda.exe in Scripts/conda.exe or condabin/conda.bat
        // We prefer Scripts/conda.exe or check both
        Path scriptConda = installDir.resolve("Scripts").resolve("conda.exe");
        if (Files.exists(scriptConda))
            return scriptConda;

        return installDir.resolve("condabin").resolve("conda.bat");
    }

    /**
     * Downloads and installs Miniconda silently.
     */
    public void downloadAndSetup() throws Exception {
        if (isInstalled()) {
            log("✅ Miniconda already installed at: " + installDir);
            return;
        }

        log("⬇️ Downloading Miniconda installer from " + MINICONDA_URL + "...");
        Files.createDirectories(installDir.getParent());

        Path installerPath = installDir.getParent().resolve("miniconda_installer.exe");
        downloadFile(MINICONDA_URL, installerPath);

        log("📦 Installing Miniconda silently to " + installDir + "...");
        log("⏳ This may take a few minutes...");

        // Run installer silently
        // /InstallationType=JustMe /RegisterPython=0 /S /D={installDir}
        ProcessBuilder pb = new ProcessBuilder(
                installerPath.toAbsolutePath().toString(),
                "/InstallationType=JustMe",
                "/RegisterPython=0",
                "/S",
                "/D=" + installDir.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();

        // Capture output (installer might not have much stdout)
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log("[Installer]: " + line);
            }
        }

        int exitCode = p.waitFor();
        // Delete installer
        Files.deleteIfExists(installerPath);

        if (exitCode != 0) {
            throw new RuntimeException("Miniconda installation failed with code " + exitCode);
        }

        if (!isInstalled()) {
            throw new RuntimeException("Miniconda installation finished but conda executable not found.");
        }

        log("✅ Miniconda installed successfully at: " + getCondaExe());
    }

    private void downloadFile(String url, Path target) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download " + url + ": " + response.statusCode());
        }
    }
}
