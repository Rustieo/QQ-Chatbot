package rustie.qqchat.config;


import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
public class OkHttpClientConfig {

    @Bean
    @Primary
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(5_000))
                .readTimeout(Duration.ofMillis(10_000))
                .writeTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(60))
                .build();
    }
}
