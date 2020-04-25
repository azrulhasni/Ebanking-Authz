
package com.azrul.ebanking.gateway.security.authz;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import java.time.Duration;
import java.time.Instant;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.representation.TokenIntrospectionResponse;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class AuthzControlFilter extends ZuulFilter {
    
    private final Logger log = LoggerFactory.getLogger(AuthzControlFilter.class);

    private final OAuth2AuthorizedClientService clientService;
    private GenericObjectPool<AuthzClient> authzClientPool;
    private final RouteLocator routeLocator;


    public AuthzControlFilter(RouteLocator routeLocator, OAuth2AuthorizedClientService clientService, GenericObjectPool<AuthzClient> authzClientPool) {
        this.routeLocator=routeLocator;
        this.clientService = clientService;
        this.authzClientPool = authzClientPool;
    }

    @Override
    public String filterType() {
        return PRE_TYPE;
    }

    @Override
    public int filterOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        String requestUri = RequestContext.getCurrentContext().getRequest().getRequestURI();
        AuthzClient authzClient = null;
        try {
            authzClient = authzClientPool.borrowObject();
            String accessTokenValue = null;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof OAuth2AuthenticationToken) {
                OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
                String name = oauthToken.getName();
                String registrationId = oauthToken.getAuthorizedClientRegistrationId();
                OAuth2AuthorizedClient client = clientService.loadAuthorizedClient(
                        registrationId,
                        name);

                if (null == client) {
                    throw new OAuth2AuthorizationException(new OAuth2Error("access_denied", "The token is expired", null));
                }
                OAuth2AccessToken accessToken = client.getAccessToken();

                if (accessToken != null) {
                    String tokenType = accessToken.getTokenType().getValue();
                    accessTokenValue = accessToken.getTokenValue();

                }

            } else if (authentication instanceof JwtAuthenticationToken) {
                JwtAuthenticationToken accessToken = (JwtAuthenticationToken) authentication;
                accessTokenValue = accessToken.getToken().getTokenValue();

            }
            
            AuthzScope scope = mapHttpMethodToScope(ctx.getRequest().getMethod());
          
            // create an authorization request
            AuthorizationRequest request = new AuthorizationRequest();
            
            //figure out resource
            String resource=null;
            for (Route route : routeLocator.getRoutes()) {
                if (requestUri.contains(route.getPrefix())){
                    String servicePath = requestUri.replace(route.getPrefix(),"");
                    String[] breakdown = servicePath.split("(?=\\/)");
                    resource = breakdown[0]+breakdown[1];
                    break;
                }
            }
            if (null == resource) {
                throw new OAuth2AuthorizationException(new OAuth2Error("access_denied", "The token is expired", null));
            }

            // add permissions to the request based on the resources and scopes you want to check access
            request.addPermission(resource, scope.name());

            
            AuthorizationResponse response = authzClient.authorization(accessTokenValue).authorize(request);
            String rpt = response.getToken();

            // introspect the token. If not authorized, introspectRequestingPartyToken will throw an exception
            TokenIntrospectionResponse requestingPartyToken = authzClient.protection().introspectRequestingPartyToken(rpt);

            log.debug("Access Control: filtered authorized access on endpoint {}", ctx.getRequest().getRequestURI());
        } catch (Exception ex) {
            ctx.setResponseStatusCode(HttpStatus.FORBIDDEN.value());
            ctx.setSendZuulResponse(false);
            log.debug("Access Control: filtered unauthorized access on endpoint {}", ctx.getRequest().getRequestURI());
        } finally{
            authzClientPool.returnObject(authzClient);
        }
        return null;
    }

    private boolean isExpired(OAuth2AccessToken accessToken) {
        Instant now = Instant.now();
        Instant expiresAt = accessToken.getExpiresAt();
        return now.isAfter(expiresAt.minus(Duration.ofMinutes(1L)));
    }
    
    AuthzScope mapHttpMethodToScope(String httpMethod){
        switch(httpMethod.toUpperCase()) 
        { 
            case "POST": 
                return AuthzScope.CREATE; 
            case "PUT": 
                return AuthzScope.UPDATE;
            case "GET": 
               return AuthzScope.VIEW;
            case "DELETE": 
               return AuthzScope.DELETE;
            default: 
                return null;
        } 
    }
}



