package com.landverification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cors cors = new Cors();
    private String uploadDir = "./uploads";
    private String frontendPath = resolveFrontendPath();
    private String baseUrl = "http://localhost:8080";

    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public String getUploadDir() { return normalizeUploadDir(uploadDir); }
    public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
    public String getFrontendPath() { return normalizeFrontendPath(frontendPath); }
    public void setFrontendPath(String frontendPath) { this.frontendPath = frontendPath; }

    private static String normalizeUploadDir(String configuredDir) {
        if (configuredDir == null || configuredDir.isBlank()) {
            configuredDir = "./uploads";
        }

        Path configured = Paths.get(configuredDir);
        if (configured.isAbsolute()) {
            return configured.toAbsolutePath().normalize().toString();
        }

        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate : getUploadCandidates(cwd, configuredDir)) {
            if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                return candidate.toString();
            }
        }

        return cwd.resolve(configuredDir).normalize().toString();
    }

    private static List<Path> getUploadCandidates(Path cwd, String configuredDir) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(cwd.resolve(configuredDir).normalize());
        candidates.add(cwd.resolve("backend").resolve(configuredDir).normalize());
        if (cwd.endsWith("backend") && cwd.getParent() != null) {
            candidates.add(cwd.getParent().resolve(configuredDir).normalize());
            candidates.add(cwd.getParent().resolve("land-verification-system").resolve(configuredDir).normalize());
        }
        if (cwd.getParent() != null) {
            candidates.add(cwd.getParent().resolve(configuredDir).normalize());
            candidates.add(cwd.getParent().resolve("land-verification-system").resolve(configuredDir).normalize());
        }
        if (cwd.getParent() != null && cwd.getParent().getParent() != null) {
            candidates.add(cwd.getParent().getParent().resolve(configuredDir).normalize());
        }
        return candidates;
    }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    private static String normalizeFrontendPath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return resolveFrontendPath();
        }

        Path configured = Paths.get(configuredPath);
        if (configured.isAbsolute()) {
            return configured.toAbsolutePath().normalize().toString();
        }

        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate : getFrontendCandidates(cwd, configuredPath)) {
            if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                return candidate.toString();
            }
        }

        return cwd.resolve(configuredPath).normalize().toString();
    }

    private static String resolveFrontendPath() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate : getFrontendCandidates(cwd, null)) {
            if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                return candidate.toString();
            }
        }

        return cwd.resolve("frontend").resolve("public").normalize().toString();
    }

    private static List<Path> getFrontendCandidates(Path cwd, String configuredPath) {
        List<Path> candidates = new ArrayList<>();

        if (configuredPath != null) {
            candidates.add(cwd.resolve(configuredPath).normalize());
            candidates.add(cwd.resolve("backend").resolve(configuredPath).normalize());
            if (cwd.endsWith("backend") && cwd.getParent() != null) {
                candidates.add(cwd.getParent().resolve(configuredPath).normalize());
            }
            if (cwd.getParent() != null) {
                candidates.add(cwd.getParent().resolve(configuredPath).normalize());
                candidates.add(cwd.getParent().resolve("land-verification-system").resolve(configuredPath).normalize());
            }
            candidates.add(cwd.resolve("land-verification-system").resolve(configuredPath).normalize());
            candidates.add(cwd.resolve("backend").resolve("..").resolve(configuredPath).normalize());
        }

        candidates.add(cwd.resolve("frontend").resolve("public").normalize());
        candidates.add(cwd.resolve("land-verification-system").resolve("frontend").resolve("public").normalize());
        if (cwd.endsWith("backend") && cwd.getParent() != null) {
            candidates.add(cwd.getParent().resolve("frontend").resolve("public").normalize());
            candidates.add(cwd.getParent().resolve("land-verification-system").resolve("frontend").resolve("public").normalize());
        }
        if (cwd.getParent() != null) {
            candidates.add(cwd.getParent().resolve("frontend").resolve("public").normalize());
            candidates.add(cwd.getParent().resolve("land-verification-system").resolve("frontend").resolve("public").normalize());
        }
        if (cwd.getParent() != null && cwd.getParent().getParent() != null) {
            candidates.add(cwd.getParent().getParent().resolve("frontend").resolve("public").normalize());
        }

        return candidates;
    }

    public static class Cors {
        private String allowedOrigins = "http://127.0.0.1:5500";
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }
}
