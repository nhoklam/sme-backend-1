package sme.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Business business = new Business();
    private Storage storage = new Storage();
    private Ai ai = new Ai();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs;
        private long refreshExpirationMs;
    }

    @Data
    public static class Business {
        private int lowStockThreshold = 10;
        private int deadStockDays = 90;
        private int loyaltyPointsPerVnd = 1000;
        private int silverThreshold = 500;
        private int goldThreshold = 2000;
    }

    @Data
    public static class Storage {
        private String type = "local";
        private String localPath = "./uploads";
        private String baseUrl;
    }

    @Data
    public static class Ai {
        private int chunkSize = 800;
        private int chunkOverlap = 100;
        private int topKResults = 5;
    }
}
