package com.tfyre.servlet;

import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.servlet.ServletContext;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
public class TFyreServletExtension implements ServletExtension {

    @Override
    public void handleDeployment(final DeploymentInfo deploymentInfo, final ServletContext servletContext) {
        deploymentInfo.setIdentityManager(CDI.current().select(TFyreIdentityManager.class).get());
    }

}
