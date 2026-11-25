package dev.herald.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

    private final HandlerInterceptor ingestionRateLimitInterceptor;

    WebConfig(HandlerInterceptor ingestionRateLimitInterceptor) {
        this.ingestionRateLimitInterceptor = ingestionRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ingestionRateLimitInterceptor).addPathPatterns("/v1/events");
    }
}
