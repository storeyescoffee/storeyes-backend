package io.storeyes.storeyes_coffee.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.CodeSource;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseConfig {

    private static final String SERVICE_ACCOUNT_FILE_NAME = "firebase-service-account.json";

    @Value("${firebase.service-account-path}")
    private Resource serviceAccountResource;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        File externalFile = resolveServiceAccountNextToJar();
        if (externalFile != null && externalFile.isFile()) {
            try (InputStream serviceAccount = new FileInputStream(externalFile)) {
                FirebaseApp app = initialize(serviceAccount);
                log.info("Firebase app initialized from {}", externalFile.getAbsolutePath());
                return app;
            }
        }

        try (InputStream serviceAccount = serviceAccountResource.getInputStream()) {
            FirebaseApp app = initialize(serviceAccount);
            log.info("Firebase app initialized from {}", serviceAccountResource.getDescription());
            return app;
        }
    }

    private FirebaseApp initialize(InputStream serviceAccount) throws IOException {
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();
        return FirebaseApp.initializeApp(options);
    }

    /**
     * Looks for {@value #SERVICE_ACCOUNT_FILE_NAME} in the directory containing the running jar,
     * so the credentials can sit on the same level as the deployed jar without being packaged.
     *
     * @return the resolved file, or {@code null} if the jar directory could not be determined.
     */
    private File resolveServiceAccountNextToJar() {
        try {
            CodeSource codeSource = FirebaseConfig.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return null;
            }
            File codeLocation = new File(codeSource.getLocation().toURI());
            File jarDir = codeLocation.isFile() ? codeLocation.getParentFile() : codeLocation;
            if (jarDir == null) {
                return null;
            }
            return new File(jarDir, SERVICE_ACCOUNT_FILE_NAME);
        } catch (URISyntaxException e) {
            log.warn("Could not resolve jar location to look up {}", SERVICE_ACCOUNT_FILE_NAME, e);
            return null;
        }
    }
}
