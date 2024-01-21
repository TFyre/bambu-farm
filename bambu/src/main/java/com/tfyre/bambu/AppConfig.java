package com.tfyre.bambu;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.Theme;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Theme("starter-theme")
@Push
public class AppConfig implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.setPageTitle("Bambu Web Interface");
        //FIXME: secure /q/metrics
    }

}
