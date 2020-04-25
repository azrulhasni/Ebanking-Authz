
package com.azrul.ebanking.gateway.config.authz;

import com.azrul.ebanking.gateway.security.authz.AuthzClientFactory;
import com.azrul.ebanking.gateway.security.authz.AuthzControlFilter;
import java.net.URI;
import java.util.Map;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.keycloak.authorization.client.AuthzClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

/**
 *
 * @author azrul
 */
@Configuration
public class AuthzConfiguration {
    @Value("${spring.security.oauth2.client.registration.oidc.client-id}")
    private String oidcClientId;
    
    @Value("${spring.security.oauth2.client.registration.oidc.client-secret}")
    private String oidcClientSecret;
    
    @Value("${spring.security.oauth2.client.provider.oidc.issuer-uri}")
    private String issuerUri;
    
    @Value("${ebanking.authz.client.pool-size}")
    private Integer authzClientPoolSize;
    
     @Bean
    public AuthzControlFilter authzControlFilter(
            RouteLocator routeLocator,
            OAuth2AuthorizedClientService clientService,
            GenericObjectPool<AuthzClient> authzClientPool) {
        return new AuthzControlFilter(routeLocator,clientService,authzClientPool);
    }
    
    @Bean
    @Scope("singleton")
    public GenericObjectPool<AuthzClient> authzClientPool() throws Exception{
        URI iss = new URI(issuerUri);
        String realm = getLastToken(iss.getPath(),"/");
        String serverURL = iss.getScheme()
                +"://"+iss.getAuthority()
                +"/"
                +getSecondToken(iss.getPath(),"/");
        org.keycloak.authorization.client.Configuration keycloakConf = new org.keycloak.authorization.client.Configuration();
        keycloakConf.setRealm(realm);
        keycloakConf.setAuthServerUrl(serverURL);
        keycloakConf.setSslRequired("external");
        keycloakConf.setResource(oidcClientId);
        keycloakConf.setVerifyTokenAudience(true);
        keycloakConf.setCredentials(Map.of("secret", oidcClientSecret));
        GenericObjectPool<AuthzClient> pool =  new GenericObjectPool<AuthzClient>(new AuthzClientFactory(keycloakConf));
    
        pool.setMinIdle(authzClientPoolSize);
        pool.setMaxTotal(2*authzClientPoolSize);
        pool.setBlockWhenExhausted(true);
        return pool;
    }
    
    private String getLastToken(String strValue, String splitter )  {        
       String[] strArray = strValue.split(splitter);  
       return strArray[strArray.length -1];            
    }   
    
    private String getSecondToken(String strValue, String splitter )  {        
       String[] strArray = strValue.split(splitter);  
       return strArray[1];            
    }   
}
