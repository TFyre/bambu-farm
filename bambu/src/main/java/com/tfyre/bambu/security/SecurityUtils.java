package com.tfyre.bambu.security;

import com.tfyre.bambu.SystemRoles;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.server.VaadinServletRequest;
import io.quarkus.logging.Log;
import java.security.Principal;
import java.util.Optional;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpSession;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class SecurityUtils {


    public static Optional<Principal> getPrincipal() {
        return Optional.ofNullable(VaadinServletRequest.getCurrent()).map(VaadinServletRequest::getUserPrincipal);
    }

    public static boolean isLoggedIn() {
        return getPrincipal().isPresent();
    }

    public static boolean userHasAccess(final String roleName) {
        return Optional.ofNullable(VaadinServletRequest.getCurrent())
                .map(vsr -> vsr.isUserInRole(SystemRoles.ROLE_ADMIN) || vsr.isUserInRole(roleName))
                .orElse(false);
    }

    public static boolean userHasRole(final String roleName) {
        return Optional.ofNullable(VaadinServletRequest.getCurrent())
                .map(vsr -> vsr.isUserInRole(roleName))
                .orElse(false);
    }

    public static boolean login(final String username, final String password) {
        return Optional.ofNullable(VaadinServletRequest.getCurrent())
                .map(vsr -> Optional.ofNullable(vsr.getUserPrincipal()).map(p -> {
                    Notification.show(String.format("Already logged in: %s", p.getName()));
                    return true;
                }).orElseGet(() -> {
                    try {
                        vsr.login(username, password);
                        return true;
                    } catch (ServletException ex) {
                        Log.error(String.format("Login failed for [%s] - %s", username, ex.getMessage()));
                        return false;
                    }
                })).orElseGet(() -> {
            Notification.show("VaadinServletRequest is null");
            return false;
        });
    }

    public static void logout() {
        Optional.ofNullable(VaadinServletRequest.getCurrent()).map(VaadinServletRequest::getSession)
                .ifPresent(HttpSession::invalidate);
    }
}
