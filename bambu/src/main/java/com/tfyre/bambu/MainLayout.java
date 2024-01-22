package com.tfyre.bambu;

import com.tfyre.bambu.security.SecurityUtils;
import com.tfyre.bambu.view.LogsView;
import com.tfyre.bambu.view.MaintenanceView;
import com.tfyre.bambu.view.SdCardView;
import com.tfyre.bambu.view.dashboard.Dashboard;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.HighlightConditions;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.theme.lumo.Lumo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

/**
 * The main view contains a button and a click listener.
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class MainLayout extends AppLayout {

    private static final Map<Class<? extends Component>, AccessRoute> MAP = makeEntries(Stream.of(
            SdCardView.class,
            LogsView.class,
            MaintenanceView.class
    ));

    private final HorizontalLayout header = new HorizontalLayout();
    private final List<VerticalLayout> drawerItems = new ArrayList<>();
    private final Checkbox darkMode = new Checkbox("Dark Theme");

    @Inject
    BambuConfig config;

    public MainLayout() {
    }

    public static void setTheme(final Element element, final boolean darkMode) {
        final String js = "document.documentElement.setAttribute('theme', $0)";
        element.executeJs(js, darkMode ? Lumo.DARK : Lumo.LIGHT);
    }

    private void setTheme() {
        //FIXME: use security context
        SecurityUtils.getPrincipal()
                .flatMap(p -> Optional.ofNullable(config.users().get(p.getName().toLowerCase())))
                .ifPresent(u -> {
                    if (u.darkMode().orElseGet(config::darkMode)) {
                        darkMode.setValue(true);
                    }
                });
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        darkMode.addValueChangeListener(l -> setTheme(getElement(), l.getValue()));
        setDrawerOpened(false);
        createHeader();
        createDrawer();
        addToNavbar(header);
        setTheme();

    }

    private String getUsername() {
        return SecurityUtils.getPrincipal()
                .map(p -> p.getName())
                .orElse("Unknown");
    }

    private void createHeader() {
        header.removeAll();
        final H1 logo = new H1("Bambu Web Interface: %s".formatted(getUsername()));
        logo.addClassNames("text-l", "m-m");
        logo.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0");

        header.add(new DrawerToggle(), logo, darkMode);

        if (SecurityUtils.isLoggedIn()) {
            header.add(new Button("Logout", e -> SecurityUtils.logout()));
        }

        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        header.setWidth("100%");
        header.addClassNames("py-0", "px-m");
    }

    private void clearDrawerItems() {
        drawerItems.forEach(vl -> {
            vl.getChildren().forEach(c -> c.setVisible(false));
            vl.removeAll();
            vl.setVisible(false);
        });
        drawerItems.clear();
    }

    private void addToDrawerVL(final VerticalLayout layout) {
        drawerItems.add(layout);
        addToDrawer(layout);
    }

    private void createDrawer() {
        clearDrawerItems();
        final RouterLink listLink = new RouterLink("Dashboard", Dashboard.class);

        listLink.setHighlightCondition(HighlightConditions.sameLocation());

        addToDrawerVL(new VerticalLayout(listLink));

        final Predicate<String> roleChecker = VaadinRequest.getCurrent()::isUserInRole;
        getVerticalLayout(roleChecker, Stream.of(
                SdCardView.class,
                LogsView.class,
                MaintenanceView.class))
                .ifPresent(this::addToDrawerVL);
    }

    private Optional<VerticalLayout> getVerticalLayout(final Predicate<String> roleChecker, Stream<Class<? extends Component>> stream) {
        final List<RouterLink> list = stream
                .filter(clazz -> MAP.get(clazz).roles.stream().anyMatch(roleChecker))
                .map(clazz -> new RouterLink(MAP.get(clazz).name(), clazz))
                .collect(Collectors.toList());

        if (list.isEmpty()) {
            return Optional.empty();
        }

        final VerticalLayout result = new VerticalLayout();
        list.forEach(result::add);
        return Optional.of(result);
    }

    private static Map<Class<? extends Component>, AccessRoute> makeEntries(Stream<Class<? extends Component>> stream) {
        return stream
                .collect(Collectors.toMap(Function.identity(), clazz -> {
                    final String name = clazz.getAnnotation(PageTitle.class).value();
                    final Set<String> roles = Arrays.stream(clazz.getAnnotation(RolesAllowed.class).value())
                            .collect(Collectors.toSet());
                    return new AccessRoute(clazz, name, roles);
                }));
    }

    private record AccessRoute(Class<? extends Component> component, String name, Set<String> roles) {

    }

}
