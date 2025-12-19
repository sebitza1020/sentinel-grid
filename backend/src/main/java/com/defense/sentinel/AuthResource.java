package com.defense.sentinel;

import io.smallrye.jwt.build.Jwt;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashSet;

@Path("/api/auth")
public class AuthResource {

    // Model simplu pentru datele de intrare
    public static class LoginRequest {
        public String username;
        public String password;
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(LoginRequest request) {
        // ⚠️ Într-un proiect real, aici verificăm în Baza de Date (Neon) cu parola hash-uită.
        // Pentru DEMO, folosim o verificare hardcodată pentru "Commander".
        
        if ("commander".equals(request.username) && "sentinel2025".equals(request.password)) {
            
            // Generăm permisul (Token-ul)
            String token = Jwt.issuer("https://sentinel-grid.com")
                    .upn("commander") // User Principal Name
                    .groups(new HashSet<>(Arrays.asList("COMMANDER", "USER"))) // Rolurile
                    .expiresIn(3600) // Expiră într-o oră
                    .sign(); // Semnează cu privateKey.pem automat

            return Response.ok("{\"token\": \"" + token + "\"}").build();
        } 
        
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }
}