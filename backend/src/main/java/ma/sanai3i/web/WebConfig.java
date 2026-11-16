package ma.sanai3i.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/** /app -> the web app, /downloads/** -> files (APK) dropped in the downloads folder on the server. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.downloads-dir:./downloads}")
    private String downloadsDir;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/app", "/app/");
        registry.addViewController("/app/").setViewName("forward:/app/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(downloadsDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/downloads/**").addResourceLocations(location);
    }
}
