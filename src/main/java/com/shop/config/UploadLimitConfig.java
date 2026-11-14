package com.shop.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat limits how many parts (files + form fields) one multipart request may have
 * (default 10), which makes uploads of 9+ photos fail. Raise it so any number of photos
 * can go into one slide.
 */
@Configuration
public class UploadLimitConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> multipartPartLimit() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            try {
                connector.getClass().getMethod("setMaxPartCount", int.class).invoke(connector, 1000);
            } catch (Exception ignored) { /* older Tomcat: no limit to raise */ }
        });
    }
}
