package com.tfyre.bambu;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestResponse;

/**
 *
 * @author Francois Steyn - (fsteyn@tfyre.co.za)
 */
@RegisterRestClient(configKey = "cloud")
public interface CloudApi {

    @Consumes(MediaType.APPLICATION_JSON)
    @POST
    @Path("api/sign-in/form")
    Uni<RestResponse<String>> login(final Login login);

    record Login(String account, String password) {

    }

}
