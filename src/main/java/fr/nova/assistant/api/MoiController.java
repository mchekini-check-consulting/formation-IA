package fr.nova.assistant.api;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Qui suis-je ? — identité et rôles lus dans le jeton Keycloak. */
@RestController
@RequestMapping("/api/moi")
public class MoiController {

    @GetMapping
    public Map<String, Object> moi(Authentication authentification) {
        Jwt jwt = (Jwt) authentification.getPrincipal();
        return Map.of(
                "username", jwt.getClaimAsString("preferred_username"),
                "email", String.valueOf(jwt.getClaimAsString("email")),
                "roles", authentification.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(a -> a.startsWith("ROLE_"))
                        .map(a -> a.substring(5))
                        .toList());
    }
}
