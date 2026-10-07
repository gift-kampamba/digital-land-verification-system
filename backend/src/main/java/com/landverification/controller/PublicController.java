package com.landverification.controller;

import com.landverification.config.AppProperties;
import com.landverification.dto.ApiResponse;
import com.landverification.dto.ParcelResponse;
import com.landverification.dto.VerifyResponse;
import com.landverification.service.LandParcelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


@RestController
public class PublicController {

    private static final Logger log = LoggerFactory.getLogger(PublicController.class);

    private final LandParcelService parcelService;
    private final AppProperties appProperties;
    private final ResourceLoader resourceLoader;

    public PublicController(LandParcelService parcelService, AppProperties appProperties, ResourceLoader resourceLoader) {
        this.parcelService = parcelService;
        this.appProperties = appProperties;
        this.resourceLoader = resourceLoader;
    }

    @Value("${frontend.path:classpath:static}")
    private String frontendPathConfig;

    @PostConstruct
    public void init() {
        log.info("========== Frontend Configuration ==========");
        log.info("Working directory: {}", System.getProperty("user.dir"));

        Path frontendPath = Paths.get(appProperties.getFrontendPath()).toAbsolutePath().normalize();
        log.info("Resolved frontend path: {}", frontendPath);
        log.info("Frontend path exists: {}", Files.exists(frontendPath));

        Path cssPath = frontendPath.getParent().resolve("css/main.css");
        log.info("CSS file exists: {} - {}", Files.exists(cssPath), cssPath);

        if (Files.exists(frontendPath)) {
            try {
                log.info("Files in frontend public directory: {}", Files.list(frontendPath).map(Path::getFileName).toList());
            } catch (IOException e) {
                log.error("Failed to list directory", e);
            }
        }
        log.info("===========================================");
    }

    @GetMapping("/")
    public ResponseEntity<Void> welcome() {
        log.info("Redirecting root path to login page");
        return ResponseEntity.status(302)
                .header("Location", "/login.html")
                .build();
    }

    @GetMapping("/api/public/verify/{parcelNumber}")
    public ResponseEntity<ApiResponse<VerifyResponse>> verify(
            @PathVariable String parcelNumber) {
        return ResponseEntity.ok(ApiResponse.ok("Verification complete",
                parcelService.verifyParcel(parcelNumber)));
    }

        @GetMapping("/api/public/parcel/{parcelId}")
        public ResponseEntity<ApiResponse<com.landverification.dto.ParcelResponse>> getParcelById(
            @PathVariable Integer parcelId) {
        return ResponseEntity.ok(ApiResponse.ok("Parcel details",
            withoutOwnerPhoto(parcelService.getParcelById(parcelId))));
        }

    @GetMapping("/api/public/search")
    public ResponseEntity<ApiResponse<List<ParcelResponse>>> search(
            @RequestParam String q) {
        List<ParcelResponse> results = parcelService.searchParcels(q, null, null, null);
        results.forEach(this::withoutOwnerPhoto);
        return ResponseEntity.ok(ApiResponse.ok("Search results",
            results));
    }

    private ParcelResponse withoutOwnerPhoto(ParcelResponse response) {
        response.setOwnerPhotoPath(null);
        return response;
    }

    // ==================== HTML PAGE MAPPINGS ====================

    @GetMapping(value = {"/login.html", "/public/login.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getLoginPage() {
        try {
            String content = loadHtmlFile("login.html");
            if (content != null) {
                return ResponseEntity.ok(content);
            }
            log.error("login.html not found in any location");
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Failed to load login.html", e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = {"/index.html", "/public/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getIndexPage() {
        try {
            String content = loadHtmlFile("index.html");
            if (content != null) {
                return ResponseEntity.ok(content);
            }
            log.error("index.html not found in any location");
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Failed to load index.html", e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = {"/reset.html", "/public/reset.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getResetPage() {
        try {
            String content = loadHtmlFile("reset.html");
            if (content != null) {
                return ResponseEntity.ok(content);
            }
            log.error("reset.html not found in any location");
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Failed to load reset.html", e);
            return ResponseEntity.notFound().build();
        }
    }

  
    @GetMapping(value = {"/track-application.html", "/public/track-application.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getTrackApplicationPage() {
        return getPage("track-application.html");
    }

    // Admin Pages
    @GetMapping(value = {"/pages/admin/dashboard.html", "/public/pages/admin/dashboard.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getAdminDashboard() {
        return getPage("pages/admin/dashboard.html");
    }
    @GetMapping(value = {"/admin/user-management.html", "/public/admin/user-management.html", "/pages/admin/user-management.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getAdminUserManagement() {
        return getPage("pages/admin/all-users.html");
    }
    @GetMapping(value ={"/admin/system-statistics.html", "/public/admin/system-statistics.html", "/pages/admin/system-statistics.html"}, produces = MediaType.TEXT_HTML_VALUE)
   public ResponseEntity<String> getAdminSystemStatistics() {
       return getPage("pages/admin/system-statistics.html");
   }
   @GetMapping(value ={"/admin/system-monitoring.html", "/public/admin/system-monitoring.html", "/pages/admin/system-monitoring.html"}, produces = MediaType.TEXT_HTML_VALUE)
   public ResponseEntity<String> getAdminSystemMonitoring() {
       return getPage("pages/admin/system-monitoring.html");
   }
   @GetMapping(value ={"/admin/system-alerts.html", "/public/admin/system-alerts.html", "/pages/admin/system-alerts.html"}, produces = MediaType.TEXT_HTML_VALUE)
   public ResponseEntity<String> getAdminSystemAlerts() {
       return getPage("pages/admin/system-alerts.html");
   }
   @GetMapping(value ={"/admin/blockchain-status.html", "/public/admin/blockchain-status.html", "/pages/admin/blockchain-status.html"}, produces = MediaType.TEXT_HTML_VALUE)
   public ResponseEntity<String> getAdminBlockchainStatus() {
       return getPage("pages/admin/blockchain-status.html");
   }
   @GetMapping(value ={"/admin/all-users.html", "/public/admin/all-users.html", "/pages/admin/all-users.html"}, produces = MediaType.TEXT_HTML_VALUE)
   public ResponseEntity<String> getAdminAllUsers() {
       return getPage("pages/admin/all-users.html");
   }
   @GetMapping(value ={"/admin/user-details.html", "/public/admin/user-details.html", "/pages/admin/user-details.html"}, produces = MediaType.TEXT_HTML_VALUE)
   public ResponseEntity<String> getAdminUserDetails() {
       return getPage("pages/admin/user-details.html");
   }
   @GetMapping(value ={"/admin/create-user.html", "/public/admin/create-user.html", "/pages/admin/create-user.html"}, produces = MediaType.TEXT_HTML_VALUE)
   public ResponseEntity<String> getAdminCreateUser() {
       return getPage("pages/admin/create-user.html");
   }
   @GetMapping(value ={"/admin/transaction-details.html", "/public/admin/transaction-details.html", "/pages/admin/transaction-details.html"}, produces = MediaType.TEXT_HTML_VALUE)
   public ResponseEntity<String> getAdminTransactionDetails() {
       return getPage("pages/admin/transaction-details.html");
   }
   @GetMapping(value = {"/admin/verify-parcel.html", "/public/admin/verify-parcel.html", "/pages/admin/verify-parcel.html"}, produces = MediaType.TEXT_HTML_VALUE)
   public ResponseEntity<String> getAdminVerifyParcel() {
       return getPage("search-parcel.html");
   }

    // LAND OFFICER PAGES
    @GetMapping(value = {"/pages/officer/dashboard.html", "/public/pages/officer/dashboard.html", "/public/officer/dashboard.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getOfficerDashboard() {
        return getPage("pages/officer/dashboard.html");
    }

    @GetMapping(value = {"/pages/officer/register-parcel.html", "/public/pages/officer/register-parcel.html", "/public/officer/register-parcel.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getOfficerRegisterParcel() {
        return getPage("pages/officer/register-parcel.html");
    }

    @GetMapping(value = {"/pages/officer/parcel-registry.html", "/public/officer/parcel-registry.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getOfficerParcelRegistry() {
        return getPage("pages/officer/parcel-registry.html");
    }

    @GetMapping(value = {"/pages/officer/manage-profile.html", "/public/officer/manage-profile.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getOfficerManageProfile() {
        return getPage("pages/officer/manage-profile.html");
    }

    @GetMapping(value = {"/pages/officer/transfer-detail.html", "/public/officer/transfer-detail.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getOfficerTransferDetail() {
        return getPage("pages/officer/transfer-detail.html");
    }

    @GetMapping(value = {"/pages/officer/notifications.html", "/public/officer/notifications.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getOfficerNotifications() {
        return getPage("pages/officer/notifications.html");
    }

    @GetMapping(value = {"/pages/officer/parcel-status.html", "/public/officer/parcel-status.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getOfficerParcelStatus() {
        return getPage("pages/officer/parcel-status.html");
    }

    @GetMapping(value = {"/pages/officer/transfer-requests.html", "/public/officer/transfer-requests.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getOfficerTransferRequests() {
        return getPage("pages/officer/transfer-requests.html");
    }

    @GetMapping(value = {"/pages/officer/parcel-detail.html", "/public/officer/parcel-detail.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getOfficerParcelDetail() {
        return getPage("pages/officer/parcel-detail.html");
    }
    @GetMapping(value = {"/pages/officer/monthly-report.html", "/public/officer/monthly-report.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getOfficerMonthlyReport() {
        return getPage("pages/officer/monthly-report.html");
    }
    @GetMapping(value = {"/pages/officer/settings.html", "/public/officer/settings.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getOfficerSettings() {
        return getPage("pages/officer/settings.html");
    }   
   // LAND OWNER PAGES 
    @GetMapping(value = {"/pages/landowner/dashboard.html", "/public/pages/landowner/dashboard.html", "/public/landowner/dashboard.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getLandownerDashboard() {
        return getPage("pages/landowner/dashboard.html");
    }

    @GetMapping(value = {"/pages/landowner/profile.html", "/public/landowner/profile.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getLandownerProfile() {
        return getPage("pages/landowner/profile.html");
    }

    @GetMapping(value = {"/pages/landowner/transfer.html", "/public/pages/landowner/transfer.html", "/public/landowner/transfer.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getLandownerTransfer() {
        return getPage("pages/landowner/transfer.html");
    }

    @GetMapping(value = {"/pages/landowner/transfer-requests.html", "/public/pages/landowner/transfer-requests.html", "/public/landowner/transfer-requests.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getLandownerTransferRequests() {
        return getPage("pages/landowner/transfer-requests.html");
    }

    @GetMapping(value = {"/pages/landowner/parcels-list.html", "/public/pages/landowner/parcels-list.html", "/public/landowner/parcels-list.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getLandownerParcelsList() {
        return getPage("pages/landowner/parcels-list.html");
    }

    @GetMapping(value = {"/pages/landowner/my-parcels.html", "/public/pages/landowner/my-parcels.html", "/public/landowner/my-parcels.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getLandownerMyParcels() {
        return getPage("pages/landowner/my-parcels.html");
    }

    @GetMapping(value = {"/pages/landowner/parcel-detail.html", "/public/pages/landowner/parcel-detail.html", "/public/landowner/parcel-detail.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getLandownerParcelDetail() {
        return getPage("pages/landowner/parcel-detail.html");
    }

    @GetMapping(value = {"/pages/landowner/title-deeds.html", "/public/pages/landowner/title-deeds.html", "/public/landowner/title-deeds.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getLandownerTitleDeeds() {
        return getPage("pages/landowner/title-deeds.html");
    }

    @GetMapping(value = {"/pages/landowner/notifications.html", "/public/pages/landowner/notifications.html", "/public/landowner/notifications.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getLandownerNotifications() {
        return getPage("pages/landowner/notifications.html");
    }

    // SENIOR OFFICER &
    
    @GetMapping(value = {"/pages/senior-officer/audit-log.html", "/public/pages/senior-officer/audit-log.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getMinistryAuditLog() {
        return getPage("pages/senior-officer/audit-log.html");
    }
    @GetMapping(value = {"/pages/senior-officer/manage-profile.html", "/public/pages/senior-officer/manage-profile.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getManageProfile(){
        return getPage("pages/senior-officer/manage-profile.html");
    }   
    @GetMapping(value={"/pages/senior-officer/district-oversight.html","/public/pages/senior-officer/district-oversight.html"}, produces = MediaType.TEXT_HTML_VALUE)
       public ResponseEntity<String> getDistrictOversight() {
        return getPage("pages/senior-officer/district-oversight.html");
       }
    @GetMapping(value = {"/pages/senior-officer/dashboard.html", "/public/pages/senior-officer/dashboard.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSeniorOfficerDashboard() {
        return getPage("pages/senior-officer/dashboard.html");
    }

    @GetMapping(value = {"/pages/senior-officer/transfer-detail.html", "/public/pages/senior-officer/transfer-detail.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSeniorOfficerTransferDetail() {
        return getPage("pages/senior-officer/transfer-detail.html");
    }

    @GetMapping(value = {"/senior-officer/block-detail.html", "/public/senior-officer/block-detail.html", "/pages/senior-officer/block-detail.html", "/public/pages/senior-officer/block-detail.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSeniorOfficerBlockDetail() {
        return getPage("pages/senior-officer/block-detail.html");
    }

    @GetMapping(value={"/pages/senior-officer/flagged.html","/public/pages/senior-officer/flagged.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSeniorOfficerFlagged() {
        return getPage("pages/senior-officer/flagged.html");
    }
    @GetMapping(value={"/pages/senior-officer/notifications.html","/public/pages/senior-officer/notifications.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSeniorOfficerNotifications() {
        return getPage("pages/senior-officer/notifications.html");
    }
    @GetMapping(value={"/pages/senior-officer/monthly-reports.html","/public/pages/senior-officer/monthly-reports.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSeniorOfficerMonthlyReports() {
        return getPage("pages/senior-officer/monthly-reports.html");
    }
    @GetMapping(value={"/pages/senior-officer/parcel-detail.html","/public/pages/senior-officer/parcel-detail.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSeniorOfficerParcelDetail() {
        return getPage("pages/senior-officer/parcel-detail.html");
    }

    @GetMapping(value = {"/pages/contact.html", "/public/pages/contact.html", "/public/contact.html", "/contact.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getContactPage() {
        return getPage("pages/contact.html");
    }

    // ==================== STATIC RESOURCES ====================
    
    @GetMapping(value = "/css/main.css", produces = "text/css")
    public ResponseEntity<byte[]> getMainCss() {
        try {
            Path cssPath = getFrontendPublicRoot().resolve("css/main.css");
            
            log.info("Serving main.css from: {}", cssPath);
            
            if (Files.exists(cssPath)) {
                byte[] content = Files.readAllBytes(cssPath);
                return ResponseEntity.ok()
                        .header("Content-Type", "text/css; charset=UTF-8")
                        .header("Cache-Control", "no-cache")
                        .body(content);
            }
            log.error("main.css not found at: {}", cssPath);
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Failed to load main.css", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(value = "/js/api.js", produces = "application/javascript")
    public ResponseEntity<byte[]> getApiJs() {
        return getJsFile("api");
    }
    
    @GetMapping(value = "/js/auth.js", produces = "application/javascript")
    public ResponseEntity<byte[]> getAuthJs() {
        return getJsFile("auth");
    }

    @GetMapping(value = "/js/web3.js", produces = "application/javascript")
    public ResponseEntity<byte[]> getWeb3Js() {
        return getJsFile("web3");
    }

    // ==================== PUBLIC RESOURCE HANDLER ====================

    @GetMapping("/public/**")
    public ResponseEntity<byte[]> servePublicResource(HttpServletRequest request) {
        try {
            String requestPath = request.getRequestURI();
            String relativePath = requestPath.substring("/public/".length());
            Path frontendRoot = Paths.get(appProperties.getFrontendPath()).toAbsolutePath().normalize();
            Path resourcePath = frontendRoot.resolve(relativePath).normalize();

            log.info("Resolving public resource {} to {}", requestPath, resourcePath);
            
            if (!resourcePath.startsWith(frontendRoot)) {
                log.warn("Path traversal attempt detected: {}", resourcePath);
                return ResponseEntity.badRequest().build();
            }
            
            if (!Files.exists(resourcePath)) {
                Path pagesFallback = getFrontendPublicRoot().resolve("pages").resolve(relativePath).normalize();
                log.info("Resource {} not found, trying fallback {}", resourcePath, pagesFallback);
                if (Files.exists(pagesFallback) && Files.isReadable(pagesFallback)) {
                    resourcePath = pagesFallback;
                } else {
                    log.warn("Public resource not found: {} (requested as: {})", resourcePath, requestPath);
                    return ResponseEntity.notFound().build();
                }
            }
            
            if (!Files.isReadable(resourcePath)) {
                log.warn("Public resource not readable: {}", resourcePath);
                return ResponseEntity.notFound().build();
            }

            byte[] content = Files.readAllBytes(resourcePath);
            String lowerName = resourcePath.getFileName().toString().toLowerCase();
            MediaType contentType;
            if (lowerName.endsWith(".html")) {
                contentType = MediaType.TEXT_HTML;
            } else if (lowerName.endsWith(".css")) {
                contentType = MediaType.valueOf("text/css");
            } else if (lowerName.endsWith(".js")) {
                contentType = MediaType.valueOf("application/javascript");
            } else if (lowerName.endsWith(".png")) {
                contentType = MediaType.IMAGE_PNG;
            } else if (lowerName.endsWith(".svg")) {
                contentType = MediaType.valueOf("image/svg+xml");
            } else if (lowerName.endsWith(".ico")) {
                contentType = MediaType.valueOf("image/x-icon");
            } else {
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .body(content);
        } catch (IOException e) {
            log.error("Failed to load public resource", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private ResponseEntity<String> getPage(String pagePath) {
        try {
            Path targetPath = getFrontendPublicRoot().resolve(pagePath);
            
            log.info("Loading page from: {}", targetPath);
            
            if (Files.exists(targetPath) && Files.isReadable(targetPath)) {
                String content = new String(Files.readAllBytes(targetPath), StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(content);
            }
            
            log.error("Page not found at: {}", targetPath);
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Failed to load page: {}", pagePath, e);
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<byte[]> getJsFile(String filename) {
        try {
            Path jsPath = getFrontendPublicRoot().resolve("js").resolve(filename + ".js");
            
            log.info("Serving JS file: {}", jsPath);
            
            if (Files.exists(jsPath)) {
                byte[] content = Files.readAllBytes(jsPath);
                return ResponseEntity.ok()
                        .header("Content-Type", "application/javascript; charset=UTF-8")
                        .body(content);
            }
            log.error("JS file not found: {}", filename);
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Failed to load JS file: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Path getFrontendPublicRoot() {
        return Paths.get(appProperties.getFrontendPath()).toAbsolutePath().normalize();
    }

    private String loadHtmlFile(String filename) throws IOException {
        String userDir = System.getProperty("user.dir");
        
        List<Path> possiblePaths = List.of(
            Paths.get(userDir, "land-verification-system", "frontend", "public", filename),
            Paths.get(userDir, "frontend", "public", filename),
            Paths.get(userDir, "src", "main", "resources", "static", filename),
            Paths.get(userDir, "src", "main", "resources", "public", filename),
            Paths.get(appProperties.getFrontendPath(), filename)
        );
        
        for (Path path : possiblePaths) {
            Path absolutePath = path.toAbsolutePath().normalize();
            if (Files.exists(absolutePath) && Files.isReadable(absolutePath)) {
                log.info("Loading {} from: {}", filename, absolutePath);
                return new String(Files.readAllBytes(absolutePath), StandardCharsets.UTF_8);
            }
        }
        
        try {
            Resource resource = resourceLoader.getResource("classpath:static/" + filename);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    log.info("Loading {} from classpath", filename);
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.debug("Could not load from classpath: {}", e.getMessage());
        }
        
        return null;
    }
  
}