package com.induztek.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BootstrapAdmin {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    @Bean
    ApplicationRunner bootstrapAdminRunner(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${identity.bootstrap-admin.email:}") String email,
            @Value("${identity.bootstrap-admin.password:}") String password,
            @Value("${identity.demo-users.admin.password:}") String demoAdminPassword,
            @Value("${identity.demo-users.technician.password:}") String demoTechnicianPassword,
            @Value("${identity.demo-users.lampa.password:}") String demoLampaPassword,
            @Value("${identity.demo-users.escondida.password:}") String demoEscondidaPassword,
            @Value("${identity.demo-users.ventanas.password:}") String demoVentanasPassword) {
        return args -> {
            if (!email.isBlank() && !password.isBlank() && password.length() >= 10) {
                String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
                UserEntity admin = users.findByEmailIgnoreCase(normalizedEmail).orElse(null);
                if (admin == null) {
                    users.save(new UserEntity(
                            java.util.UUID.randomUUID().toString(),
                            normalizedEmail,
                            passwordEncoder.encode(password),
                            "Administrador Induztek",
                            "ADMIN",
                            null,
                            java.time.Instant.now(),
                            true));
                } else {
                    admin.setPasswordHash(passwordEncoder.encode(password));
                    admin.setActive(true);
                    users.save(admin);
                }
            }

            seedUser(users, passwordEncoder, "admin@induztek.cl", demoAdminPassword, "Administrador Induztek", "ADMIN", null);
            seedUser(users, passwordEncoder, "tecnico@induztek.cl", demoTechnicianPassword, "Carlos Técnico Terreno", "FIELD_TECHNICIAN", null);
            seedUser(users, passwordEncoder, "cliente.lampa@induztek.cl", demoLampaPassword, "Encargado Planta Lampa", "CLIENT", "Planta Lampa");
            seedUser(users, passwordEncoder, "cliente.escondida@induztek.cl", demoEscondidaPassword, "Supervisor Minera Escondida", "CLIENT", "Minera Escondida");
            seedUser(users, passwordEncoder, "cliente.ventanas@induztek.cl", demoVentanasPassword, "Gerente Codelco Ventanas", "CLIENT", "Codelco Ventanas");
        };
    }

    private void seedUser(UserRepository users, PasswordEncoder encoder, String email, String password, String name, String roleCode, String company) {
        if (password == null || password.isBlank()) {
            log.info("Skipping demo account {} because no password is configured", email);
            return;
        }
        if (password.length() < 10) {
            log.warn("Skipping demo account {} because its configured password is shorter than 10 characters", email);
            return;
        }

        String normEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        UserEntity existing = users.findByEmailIgnoreCase(normEmail).orElse(null);
        if (existing == null) {
            users.save(new UserEntity(
                    java.util.UUID.randomUUID().toString(),
                    normEmail,
                    encoder.encode(password),
                    name,
                    roleCode,
                    company,
                    java.time.Instant.now(),
                    true));
        } else {
            existing.setPasswordHash(encoder.encode(password));
            existing.setActive(true);
            users.save(existing);
        }
    }
}
