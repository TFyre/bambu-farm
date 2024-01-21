package com.tfyre.bambu.view;

import com.tfyre.bambu.security.SecurityUtils;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.login.AbstractLogin;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import static com.vaadin.flow.server.auth.NavigationAccessControl.SESSION_STORED_REDIRECT;
import java.util.Optional;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Route(LoginView.LOGIN)
@PageTitle("Login")
public class LoginView extends VerticalLayout implements BeforeEnterObserver, ShowInterface {

    protected static final String LOGIN = "login";

    public static final String LOGIN_SUCCESS_URL = "/";

    private final LoginForm login = new LoginForm();

    @Override
    protected void onAttach(final AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        addClassName("login-view");
        setSizeFull();

        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        setAlignItems(FlexComponent.Alignment.CENTER);

        login.addLoginListener(this::onLogin);
        login.addForgotPasswordListener(this::onForgotPassword);

        add(new H1("Bambu Web Farm"), login);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        if (beforeEnterEvent.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            login.setError(true);
        } else if (SecurityUtils.isLoggedIn()) {
            UI.getCurrent().getPage().setLocation(getLoggedInUrl());
        }
    }

    private void onLogin(AbstractLogin.LoginEvent event) {
        boolean authenticated = SecurityUtils.login(event.getUsername(), event.getPassword());
        if (authenticated) {
            UI.getCurrent().getPage().setLocation(getLoggedInUrl());
        } else {
            login.setError(true);
        }
    }

    private String getLoggedInUrl() {
        return Optional.ofNullable(VaadinSession.getCurrent())
                .map(vs -> String.class.cast(vs.getSession().getAttribute(SESSION_STORED_REDIRECT)))
                .map(s -> s.isBlank() || s.startsWith(LOGIN) ? LOGIN_SUCCESS_URL : s)
                .orElse(LOGIN_SUCCESS_URL);
    }

    private void onForgotPassword(AbstractLogin.ForgotPasswordEvent event) {
        showError("This has not been implemented");
    }
}
