package com.tfyre.bambu;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.Theme;
import io.quarkus.logging.Log;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@Theme(value = "bambu-theme")
@Push
public class AppConfig implements AppShellConfigurator {

    private static final String STYLES = "styles.css";

    @Override
    public void configurePage(final AppShellSettings settings) {
        settings.setPageTitle("Bambu Web Interface");
        final Path styleSheet = Paths.get(STYLES);
        if (Files.isRegularFile(styleSheet)) {
            try {
                final String contents = new String(Files.readAllBytes(styleSheet));
                settings.addInlineWithContents(contents, Inline.Wrapping.STYLESHEET);
            } catch (IOException ex) {
                Log.errorf(ex, "Error loading %s - %s", STYLES, ex.getMessage());
            }
        }
        //FIXME: secure /q/metrics

    }

}
