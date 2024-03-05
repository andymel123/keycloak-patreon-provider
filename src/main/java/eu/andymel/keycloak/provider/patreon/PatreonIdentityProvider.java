package eu.andymel.keycloak.provider.patreon;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RoleModel;
import org.keycloak.models.utils.KeycloakModelUtils;

import com.fasterxml.jackson.databind.JsonNode;



/**
 * Thread in patreondev forum
 * https://www.patreondevelopers.com/t/keycloak-patreon/8629
 * 
 * Thread in keycloak discourse
 * https://keycloak.discourse.group/t/link-with-patreon/24639
 * 
 * Thread in keycloak-dev slack channel
 * https://app.slack.com/client/T08PSQ7BQ/C056XU905S6
 * 
 * ---------------------------------------------------
 * 
 * SO - adding a mapper
 * https://stackoverflow.com/a/48525719/7869582
 * 
 * 
 */



public class PatreonIdentityProvider
        extends AbstractOAuth2IdentityProvider<PatreonIdentityProviderConfig>
        implements SocialIdentityProvider<PatreonIdentityProviderConfig> {

    private static final boolean FETCH_EMAIL = false;

    private static final String AUTH_URL = "https://www.patreon.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://www.patreon.com/api/oauth2/token";
    private static final String PROFILE_URL = "https://www.patreon.com/api/oauth2/v2/identity";
    private static final String DEFAULT_SCOPE = FETCH_EMAIL ? "identity[email] identity": "identity";

    private static final String PATREON_QUERY_STRING = buildPatreonQueryString();

    

    public PatreonIdentityProvider(KeycloakSession session, PatreonIdentityProviderConfig config) {
        super(session, config);
        config.setAuthorizationUrl(AUTH_URL);
        config.setTokenUrl(TOKEN_URL);
        config.setUserInfoUrl(PROFILE_URL);
    }

    @Override
    protected BrokeredIdentityContext extractIdentityFromProfile(EventBuilder event, JsonNode patreonJson) {

        try {

            logger.infof("extractIdentityFromProfile. event: %s, data: %s", event, patreonJson);

            final JsonNode  patreonJson_data            = patreonJson.get("data");
            final String    patreonJson_data_id         = getJsonProperty(patreonJson_data, "id");
            final JsonNode  patreonJson_data_attributes = patreonJson_data.get("attributes");

            if (patreonJson_data_id == null || patreonJson_data_id.length() == 0) {
                throw new Exception("No patreonJson_data_id");
            }

            if (patreonJson_data_attributes == null) {
                throw new Exception("No patreonJson_data_attributes");
            }

            // final JsonNode patreonJson_data_relationships = patreonJson_data.get("relationships");
            // if (patreonJson_data_relationships == null) {
            //     throw new Exception("No relationships");
            // }

            // final JsonNode patreonJson_data_relationships_memberships = patreonJson_data_relationships.get("memberships");
            // if (patreonJson_data_relationships_memberships == null) {
            //     throw new Exception("No memberships");
            // }

            // final JsonNode patreonJson_data_relationships_memberships_data = patreonJson_data_relationships_memberships.get("data");
            // if (patreonJson_data_relationships_memberships_data == null) {
            //     throw new Exception("No memberships data");
            // }
            // if (!patreonJson_data_relationships_memberships_data.isArray()) {
            //     throw new Exception("memberships data is not an array");
            // }
            // if (patreonJson_data_relationships_memberships_data.size() != 1) {
            //     throw new Exception("memberships data array size is " + patreonJson_data_relationships_memberships_data.size() + "! Expected 1");
            // }

            // final JsonNode membership = patreonJson_data_relationships_memberships_data.get(0);
            // final String membership_id = getJsonProperty(membership, "id");
            

            // final JsonNode patreonJson_included = patreonJson.get("included");
            // if (!patreonJson_included.isArray()) {
            //     throw new Exception("patreonJson_included is not an array");
            // }
            
            // List<String> listOfPatreonParamsToMap = new ArrayList<>();
            // // iterate json array https://stackoverflow.com/a/16793133/7869582
            // boolean foundMember = false;
            // for (final JsonNode objNode : patreonJson_data_includes) {
            //     String objType = getJsonProperty(objNode, "type");
            //     if (objType != "member") {
            //         continue;
            //     } else if(foundMember) {
            //         throw new Error("Found multiple 'member' objects in includes!");
            //     }
            //     foundMember = true;

            //     listOfPatreonParamsToMap.add(membership_id);
            // }


            final BrokeredIdentityContext user = new BrokeredIdentityContext(patreonJson_data_id);
            user.setIdpConfig(getConfig());
            user.setIdp(this);

            user.setUsername(getUserNameFromPatreonAttributes(patreonJson_data_attributes));

            
            // RoleModel role = KeycloakModelUtils.getRoleFromString(realm, roleName);

            if (FETCH_EMAIL) {
    
                String email = getJsonProperty(patreonJson_data_attributes, "email");
                Optional<Boolean> isEmailVerfied = getJsonBooleanProperty(patreonJson_data_attributes, "is_email_verified");

                if (email == null || email.trim().isEmpty()) {
                    logger.warnf("FETCH_EMAIL is true but patreon user "+user.getUsername()+" has no email?!");
                } else {
                    if (!isEmailVerfied.isPresent()) {
                        logger.warnf("FETCH_EMAIL is true but user "+user.getUsername()+" has no flag 'is_email_verified' set?!");
                    } else if (!isEmailVerfied.get()) {
                        // do something like verifying if I really use it!!
                        logger.warnf("Saving >>> UNVERIFIED EMAIL <<< '"+email+"' of patreon user "+user.getUsername());
                    }
                    user.setEmail(email);    
                }
                
            }

            // saves the whole patreon json to the context to be read in Mappers later on
            AbstractJsonUserAttributeMapper.storeUserProfileForMapper(user, patreonJson, getConfig().getAlias());

            // user.setUserAttribute("patreon", listOfPatreonParamsToMap);
    
            // commented out: dont need this
            // user.setFirstName(getJsonProperty(attribtues, "first_name"));
            // user.setLastName(getJsonProperty(attribtues, "last_name"));
            
    
            return user;

        } catch (Exception e) {
            throw new RuntimeException("Can't get user data from given patreonJson! "+patreonJson.asText(), e);
        }
        
    }

    private String getUserNameFromPatreonAttributes(JsonNode attributesJson) {
        String fullName = getJsonProperty(attributesJson, "full_name");
        String vanity = getJsonProperty(attributesJson, "vanity");

        if (fullName == null && vanity == null) {
            logger.warnf("Patreon user has neither 'full_name' nor 'vanity' set. attributes: " + attributesJson.asText());
            return "no-patreon-name";
        } else if (fullName != null && vanity != null) {
            logger.warnf("Patreon user has both 'full_name' and 'vanity' set, using fullName as userName! attributes: " + attributesJson.asText());
            return fullName;
        } else if (fullName != null) {
            return fullName;
        } else {
            return vanity;
        }
    }

    @Override
    protected BrokeredIdentityContext doGetFederatedIdentity(String accessToken) {


        String url = PROFILE_URL + "?" + PATREON_QUERY_STRING;
        logger.infof("Get profile from: %s", url);

        try (
            SimpleHttp.Response response = SimpleHttp.doGet(url, session)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .asResponse()
        ) {

            // if (Response.Status.fromStatusCode(response.getStatus()).getFamily() != Response.Status.Family.SUCCESSFUL) {
            if (response.getStatus() < 200 || response.getStatus() > 299) {
                logger.warnf("Profile endpoint returned an error (%d): %s", response.getStatus(), response.asString());
                throw new IdentityBrokerException("Status is not 2xx => " + PROFILE_URL + "' returned "+response.asJson()+".");
            }

            JsonNode profile = response.asJson();
            // TODO was trace
            logger.infof("profile retrieved from patreon: %s", profile);
            BrokeredIdentityContext user = extractIdentityFromProfile(null, profile);

            return user;
        } catch (Exception e) {
            throw new IdentityBrokerException("Profile could not be retrieved from the patreon endpoint", e);
        }
    }

    protected String getDefaultScopes() {
        return DEFAULT_SCOPE;
    }

    /**
     * @return Optional.empty if the parameter did not exists or an Optional of true or false if the the boolean valuesexisted in the json
     */
    public static Optional<Boolean> getJsonBooleanProperty(JsonNode jsonNode, String name) {
        if (jsonNode.has(name) && !jsonNode.get(name).isNull()) {
            return Optional.of(jsonNode.get(name).asBoolean());
        }
        return Optional.empty();
    }


    private static final String buildPatreonQueryString() {
        
        Map<String, String> queryParams = Map.of(

            // Without any includes the /identity request only returns { "data": { "attributes": {}, "id": "114651575", "type": "user" }, "links": { "self": "https://www.patreon.com/api/oauth2/v2/user/114651575"}}
            // memberships.currently_entitled_tiers => https://www.patreondevelopers.com/t/api-v2-how-to-access-tiers-of-user-memberships-with-only-identity-scope/3690/2
            "include",          "memberships.currently_entitled_tiers",
    
            "fields[member]",   "patron_status,campaign_lifetime_support_cents",
                "fields[tier]", "amount_cents,title,patron_count"
            
            // I wanted to omit those to get the least possible amount of data for privacy reasons but keycloak throws a NullPointerException without providing a username in the BrokeredIdentityContext
            , "fields[user]", "full_name,vanity"                                // full_name as there seem to be no 'username' in patreon (maybe 'vanity', but its deprecated and can be null as well)
                                + (FETCH_EMAIL ? ",email,is_email_verified" : "")  // to get the email scope 'identity[email]' has to be sent in the auth request!
            
        );

        try {
            Set<String> entries = new HashSet<>();
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                try {
                    entries.add(URLEncoder.encode(e.getKey(), "UTF-8") + "=" + e.getValue());
                } catch (UnsupportedEncodingException e1) {
                    throw new RuntimeException("Can't url encode the query key '"+e.getKey()+"'", e1);
                }
            }
    
            return String.join("&", entries);
        } catch(Exception e) {
            throw new RuntimeException("Can't build patreon query string from the queryParams map", e);
        }

    }
}
