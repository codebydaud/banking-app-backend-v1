package com.codebydaud.training.banking_app.security;


import com.codebydaud.training.banking_app.exception.InvalidTokenException;
import com.codebydaud.training.banking_app.service.TokenService;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;


/**
 * JWT Authentication Filter
 * <p>
 * This filter intercepts incoming requests to authenticate users based on JWT
 * tokens. It extends OncePerRequestFilter to ensure it's executed once per
 * request.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final TokenService tokenService;

    /**
     * Performs the filtering for each request
     *
     * @param request     The HTTP request
     * @param response    The HTTP response
     * @param filterChain The filter chain
     * @throws ServletException If a servlet-specific error occurs
     * @throws IOException      If an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            logger.info("User is already authenticated");

            filterChain.doFilter(request, response);
            return;
        }

        val requestTokenHeader = request.getHeader("Authorization");

        if (requestTokenHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!requestTokenHeader.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Token must start with 'Bearer '");

            return;
        }

        val token = requestTokenHeader.substring(7);
        String username = null;

        try {
            tokenService.validateToken(token);
            username = tokenService.getUsernameFromToken(token);
            logger.info("User: " + username + " is authenticated");

        } catch (InvalidTokenException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    e.getMessage());
            return;
        }

        val userDetails = userDetailsService.loadUserByUsername(username);
        val authToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        logger.info("Role: " + userDetails.getAuthorities());


        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        filterChain.doFilter(request, response);
    }

}