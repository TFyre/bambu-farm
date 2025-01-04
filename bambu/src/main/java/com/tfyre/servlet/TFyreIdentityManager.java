package com.tfyre.servlet;

import io.quarkus.arc.Unremovable;
import io.quarkus.logging.Log;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.undertow.runtime.QuarkusUndertowAccount;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Singleton
@Unremovable
@Alternative
@Priority(100)
public class TFyreIdentityManager implements IdentityManager {

    @Inject
    IdentityProviderManager ipm;

    @Override
    public Account verify(final Account account) {
        Log.debugf("verify1: %s", account.getPrincipal().getName());
        return account;
    }

    @Transactional
    QuarkusUndertowAccount authenticateBlocking(final AuthenticationRequest authenticationRequest) {
        return new QuarkusUndertowAccount(ipm.authenticateBlocking(authenticationRequest));
    }

    @Override
    public Account verify(final String id, final Credential credential) {
        Log.debugf("verify2: %s - %s", id, credential.getClass());

        if (credential instanceof io.undertow.security.idm.PasswordCredential password) {
            return authenticateBlocking(new UsernamePasswordAuthenticationRequest(id, new PasswordCredential(password.getPassword())));
        }

        return null;
    }

    @Override
    public Account verify(final Credential credential) {
        Log.debugf("verify3: %s", credential.getClass());
        return null;
    }
}
