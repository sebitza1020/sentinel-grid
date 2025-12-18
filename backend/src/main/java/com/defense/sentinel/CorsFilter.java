package com.defense.sentinel;

import java.io.IOException;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Global CORS filter for all JAX-RS endpoints.
 *
 * We add the CORS headers manually because the built-in Quarkus CORS
 * configuration does not appear to be applied on the Render deployment.
 *
 * This filter is intentionally conservative and only allows the production
 * dashboard origin. If you deploy the frontend under a different domain,
 * update the ORIGIN value below accordingly.
 */
@Provider
public class CorsFilter implements ContainerResponseFilter {

  private static final String ALLOWED_ORIGIN = "https://sentinel-dashboard-d0fw.onrender.com";

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {

    // Only bother if this is a browser CORS request
    String requestOrigin = requestContext.getHeaderString("Origin");
    if (requestOrigin == null) {
      return;
    }

    // If you later add more allowed origins, you can turn this into a whitelist
    // check.
    responseContext.getHeaders().putSingle("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
    // Make sure caches vary on Origin so responses are not mixed between sites
    responseContext.getHeaders().putSingle("Vary", "Origin");

    responseContext.getHeaders().putSingle("Access-Control-Allow-Credentials", "true");
    responseContext.getHeaders().putSingle("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    responseContext.getHeaders().putSingle("Access-Control-Allow-Headers",
        "Origin, X-Requested-With, Content-Type, Accept, Authorization");

    // For preflight requests, just return 200 with the headers above.
    if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
      responseContext.setStatus(200);
    }
  }
}
