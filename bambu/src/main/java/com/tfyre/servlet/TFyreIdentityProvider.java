package com.tfyre.servlet;

import com.tfyre.bambu.BambuConfig;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.password.util.ModularCrypt;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Singleton
public class TFyreIdentityProvider implements IdentityProvider<UsernamePasswordAuthenticationRequest> {

    @Inject
    BambuConfig config;

    private final Map<String, User> map = new HashMap<>();

    @PostConstruct
    public void postConstruct() {
        map.clear();
        map.putAll(config.users().entrySet()
                .stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), e -> {
                    String password = e.getValue().password();
                    if (ModularCrypt.identifyAlgorithm(password.toCharArray()) == null) {
                        password = BcryptUtil.bcryptHash(password);
                    }
                    return new User(password, e.getValue().role());
                }))
        );
    }

    @Override
    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(final UsernamePasswordAuthenticationRequest request, final AuthenticationRequestContext context) {
        return context.runBlocking(() ->
                Optional.ofNullable(map.get(request.getUsername().toLowerCase()))
                        .filter(u -> passwordValid(u.password, request.getPassword().getPassword()))
                        .map(u -> QuarkusSecurityIdentity.builder()
                                .setPrincipal(new QuarkusPrincipal(request.getUsername()))
                                .addCredential(request.getPassword())
                                .addRole(u.role())
                                .build()
                        )
                        .orElseThrow(AuthenticationFailedException::new)
        );
    }

    private boolean passwordValid(final String password, final char[] requestPassword) {
        final PasswordGuessEvidence evidence = new PasswordGuessEvidence(requestPassword);
        try {
            return new PasswordCredential(ModularCrypt.decode(password)).verify(Security::getProviders, evidence);
        } catch (InvalidKeySpecException ex) {
            return false;
        }
    }

    private record User(String password, String role) {

    }

}
