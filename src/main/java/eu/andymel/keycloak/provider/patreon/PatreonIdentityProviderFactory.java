package eu.andymel.keycloak.provider.patreon;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.logging.Logger;
import org.keycloak.broker.provider.AbstractIdentityProviderFactory;
import org.keycloak.broker.social.SocialIdentityProviderFactory;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ServerInfoAwareProviderFactory;

public class PatreonIdentityProviderFactory
        extends AbstractIdentityProviderFactory<PatreonIdentityProvider>
        implements SocialIdentityProviderFactory<PatreonIdentityProvider>, ServerInfoAwareProviderFactory  {

    private static final Logger logger = Logger.getLogger(PatreonIdentityProviderFactory.class);
   
    public static final String PROVIDER_ID = "patreon";
    public static final String PROVIDER_NAME = "Patreon";

    public String getId() {
        return PROVIDER_ID;
    }
    public String getName() {
        return PROVIDER_NAME;
    }

    public PatreonIdentityProvider create(KeycloakSession keycloakSession,
            IdentityProviderModel identityProviderModel) {
        logger.infof("create, session: %s, ipm: %s", keycloakSession, identityProviderModel);

        /** 
         * Das wurde aufgerufen als ich meinen "connect with patreon" button geklickt habe, dann kam die patreon seite mit allow or deny
         * 
         * nach dem allow wurde das hier nochmal aufgerufen, mit einer anderen keycloakSession instanz aber der gleichen identityProviderModel instanz
         */
        
        return new PatreonIdentityProvider(keycloakSession, new PatreonIdentityProviderConfig(identityProviderModel));
    }

    @Override
    public PatreonIdentityProviderConfig createConfig() {
        return new PatreonIdentityProviderConfig();
    }


    @Override
    public Map<String, String> getOperationalInfo() {
        Map<String, String> ret = new LinkedHashMap<>();
        ret.put("test123", "operational info in PatreonIdentityProviderFactory");
        return ret;
    }

}
