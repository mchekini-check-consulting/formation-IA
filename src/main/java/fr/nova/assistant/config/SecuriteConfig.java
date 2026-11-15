package fr.nova.assistant.config;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Étape 14 : l'API exige un jeton JWT du realm Keycloak « nova ». L'identité
 * n'est plus déclarative — elle est portée par un jeton signé, vérifié ici,
 * puis propagé tel quel à chaque appel d'outil MCP.
 */
@Configuration
public class SecuriteConfig {

    @Bean
    SecurityFilterChain filtreSecurite(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // L'interface web reste servie sans jeton ; toute l'API est protégée
                .requestMatchers("/", "/index.html", "/favicon.ico").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                jwt.jwtAuthenticationConverter(convertisseurRoles())));
        return http.build();
    }

    /** Extrait les rôles realm Keycloak (claim realm_access.roles) en ROLE_*. */
    private JwtAuthenticationConverter convertisseurRoles() {
        JwtAuthenticationConverter convertisseur = new JwtAuthenticationConverter();
        convertisseur.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
                return List.of();
            }
            return roles.stream()
                    .map(String::valueOf)
                    .filter(r -> r.equals(r.toUpperCase()))   // ignore default-roles-*, offline_access…
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();
        });
        return convertisseur;
    }
}
