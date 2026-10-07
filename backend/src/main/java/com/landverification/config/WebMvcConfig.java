package com.landverification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.CacheControl;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public WebMvcConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path publicRoot = Paths.get(appProperties.getFrontendPath()).toAbsolutePath().normalize();
        Path frontendRoot = publicRoot.getParent();

        String publicLocation = publicRoot.toUri().toString();
        String frontendLocation = frontendRoot.toUri().toString();
        String publicPagesLocation = publicRoot.resolve("pages").toUri().toString();

        // Include the 'pages' folder in the /public/** resource locations so
        // requests like /public/landowner/transfer.html map to frontend/public/pages/landowner/transfer.html
        registry.addResourceHandler("/public/**")
                .addResourceLocations(publicLocation, publicPagesLocation);

        // Serve CSS and JS from the frontend 'public' directory so files under /public/js are reachable at /js/**
        registry.addResourceHandler("/css/**", "/js/**")
                .addResourceLocations(publicLocation, frontendLocation);

        registry.addResourceHandler("/pages/**")
                .addResourceLocations(publicLocation)
                .setCacheControl(CacheControl.noStore());

        registry.addResourceHandler("/login.html", "/index.html", "/officer-register.html")
                .addResourceLocations(publicLocation)
                .setCacheControl(CacheControl.noStore());

        Path uploadRoot = Paths.get(appProperties.getUploadDir()).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadRoot.toUri().toString());
    }
}
