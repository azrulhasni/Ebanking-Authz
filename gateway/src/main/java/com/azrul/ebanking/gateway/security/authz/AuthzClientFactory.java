/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.azrul.ebanking.gateway.security.authz;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;

public class AuthzClientFactory extends BasePooledObjectFactory<AuthzClient> {
    private final Configuration conf;
    
    public AuthzClientFactory(){
        this.conf = null;
    }
    
    public AuthzClientFactory(Configuration conf){
        this.conf=conf;
    }

    @Override
    public AuthzClient create() {
        AuthzClient authzClient = AuthzClient.create(conf);
        return authzClient;
    }

 
    @Override
    public PooledObject<AuthzClient> wrap(AuthzClient buffer) {
        return new DefaultPooledObject<AuthzClient>(buffer);
    }

    
    @Override
    public void passivateObject(PooledObject<AuthzClient> pooledObject) {
    }
}
