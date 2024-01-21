package com.tfyre.bambu.security;

import com.tfyre.bambu.view.LoginView;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.auth.NavigationAccessControl;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class NavigationAccessCheckerInitializer implements VaadinServiceInitListener {

    private final NavigationAccessControl accessControl;

    public NavigationAccessCheckerInitializer() {
        accessControl = new NavigationAccessControl();
        accessControl.setLoginView(LoginView.class);
    }

    @Override
    public void serviceInit(final ServiceInitEvent serviceInitEvent) {
        serviceInitEvent.getSource().addUIInitListener(uiInitEvent -> {
            uiInitEvent.getUI().addBeforeEnterListener(accessControl);
        });
    }
}
