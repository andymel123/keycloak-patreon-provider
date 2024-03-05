package eu.andymel.keycloak.provider.patreon;


import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.broker.provider.IdentityProviderMapperSyncModeDelegate;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderSyncMode;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * inspired by GitHubUserAttributeMapper 
 * See my post: https://keycloak.discourse.group/t/link-with-patreon/24639/7?u=andymel
 * 
 * - I start with this and see if I can add an attribute if users are active patrons.
 * - In a next step I can try to add a role, maybe I can get inspiration from one of the subclasses of AbstractAttributeToRoleMapper
 *      (https://www.keycloak.org/docs-api/23.0.7/javadocs/org/keycloak/broker/saml/mappers/AbstractAttributeToRoleMapper.html)
 * 
 * 
 */
// public class PatreonUserAttributeMapper extends AbstractIdentityProviderMapper {
public class PatreonUserAttributeMapper extends AbstractJsonUserAttributeMapper {

	private static final String PROPERTY_NAME_CENTS_PER_MONTH = "minCentsPerMonth";
	private static final Logger logger = Logger.getLogger(PatreonUserAttributeMapper.class);

	private static final String MAPPER_ID = "patreon-add-preventer-role-mapper";
	private static final String[] cp = new String[] { PatreonIdentityProviderFactory.PROVIDER_ID };

	private static final List<ProviderConfigProperty> configProperties = ProviderConfigurationBuilder.create()
			.property()
				.name(PROPERTY_NAME_CENTS_PER_MONTH)
				.label("Minimum amount of cents per month")
				.helpText("The minimum amount of cents the user has to pay per month as a patron to get the role.")
				.type(ProviderConfigProperty.STRING_TYPE)
				.add()
			.property()
				.name(ConfigConstants.ROLE) // its done with this name in the HardcodedRoleMapper
				.label("Role to add")
				.helpText(
						"The role to grant if the user is an active patreon and paying enough. Click 'Select Role' button to browse roles, or just type it in the textbox.  To reference a client role the syntax is clientname.clientrole, i.e. myclient.myrole") // help text inspired by HardcodedRoleMapper
				.type(ProviderConfigProperty.ROLE_TYPE)
				.add()
			.build();

	public PatreonUserAttributeMapper() {
		logger.infof(">>>>>>>>> PatreonUserAttributeMapper constructor <<<<<<<<<<<");
	}

	@Override
	public String[] getCompatibleProviders() {
		return cp;
	}

	@Override
	public List<ProviderConfigProperty> getConfigProperties() {
		return configProperties;
	}

	@Override
	public String getId() {
		return MAPPER_ID;
	}

	@Override
	public String getDisplayCategory() {
		return "Role Importer";
	}

	@Override
	public String getDisplayType() {
		return "Patreon Ad Preventer Role Mapper";
	}

	@Override
	public String getHelpText() {
		return "Adds the ad-preventer role to the user if he is an active patreon";
	}

	// commented out: I think this is not used as I am only linking and not creating new users
	// @Override
	// public void importNewUser(KeycloakSession session, RealmModel realm, UserModel user,
	// 		IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
	// 	logger.infof("importNewUser! %s", mapToString(context.getContextData()));
	// 	RoleModel roleToAdd = getRoleIfEnoughCents(realm, mapperModel);
	// 	if (roleToAdd != null) {
	// 		user.grantRole(roleToAdd);
	// 	}
	// }

	
	@Override
	public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user,
			IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
		logger.infof("updateBrokeredUser! %s", mapToString(context.getContextData()));
		String nameOfRoleToAdd = getRoleNameIfEnoughCents(realm, mapperModel, context);
		RoleModel roleToAdd = KeycloakModelUtils.getRoleFromString(realm, nameOfRoleToAdd);

		if (roleToAdd != null) {
			logger.info("adding role '" + nameOfRoleToAdd + "' in updateBrokeredUser");
			user.grantRole(roleToAdd);

			// die funktionieren, commented out...statdessen dynamischerer Test mit time...siehe drunter
			// user.setAttribute("testattr1a", List.of("123", "234"));
			// user.setSingleAttribute("testattr1b", "cba");
			String now = new Date().toString();
			user.setSingleAttribute("testtime", now);			// test wann der überschrieben wird (sollt eja regelmäßig checken ob der user noch patreon ist)
			user.setSingleAttribute("testtime_multi_"+now, now);	// sollte eine history ergeben wann updateBrokeredUser in der VErgangenheit aufgerufen wurde
		} else{
			logger.warn("NOT adding role in updateBrokeredUser!!");
		}
	}

	// @Override
	public void preprocessFederatedIdentity(KeycloakSession session, RealmModel realm,
			IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {

		IdentityProviderSyncMode effectiveSyncMode = IdentityProviderMapperSyncModeDelegate.combineIdpAndMapperSyncMode(context.getIdpConfig().getSyncMode(), mapperModel.getSyncMode());
		logger.infof("preprocessFederatedIdentity! syncMode: %s, %s", effectiveSyncMode, mapToString(context.getContextData()));

		// 	grantUserRoleIfEnoughCents(realm, user, mapperModel);
		String nameOfRoleToAdd = getRoleNameIfEnoughCents(realm, mapperModel, context);
		if (nameOfRoleToAdd != null) {
			logger.info("adding role '"+nameOfRoleToAdd+"' in preprocessFederatedIdentity");
			context.addMapperGrantedRole(nameOfRoleToAdd);
			context.setUserAttribute("testattr2a", List.of("321", "432"));
			context.setUserAttribute("testattr2b", "abc");
		} else {
			logger.warn("NOT adding role in preprocessFederatedIdentity!!");
		}

	}

	
	private String getRoleNameIfEnoughCents(RealmModel realm, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {

		try {

			// Read config properties
			String roleName = mapperModel.getConfig().get(ConfigConstants.ROLE);
			int minCentsPerMonth = Integer.MAX_VALUE;
			try {
				String minCentsPerMonthString = mapperModel.getConfig().get(PROPERTY_NAME_CENTS_PER_MONTH);
				minCentsPerMonth = Integer.parseInt(minCentsPerMonthString);
			} catch (Exception e) {
				throw new Exception("The given value for '" + PROPERTY_NAME_CENTS_PER_MONTH + "' is not an Integer!",
						e);
			}

			// Data from the patreon API
			// This has been written in AbstractJsonUserAttributeMapper.storeUserProfileForMapper
			/**
			 * Example Data
			 * {
				"data": {
					"attributes": {},
					"id": "123456789",
					"relationships": {
						"memberships": {
							"data": [
								{
									"id": "e4d8ae89-...",
									"type": "member"
								}
							]
						}
					},
					"type": "user"
				},
				"included": [
					{
						"attributes": {
							"campaign_lifetime_support_cents": 100,
							"patron_status": "active_patron"
						},
						"id": "e4d8ae89-...",
						"relationships": {
							"currently_entitled_tiers": {
								"data": [
									{
										"id": "1231231",
										"type": "tier"
									}
								]
							}
						},
						"type": "member"
					},
					{
						"attributes": {
							"amount_cents": 100,
							"title": "Test Supporter",
							"url": "/checkout/myurl?rid=1231231"
						},
						"id": "1231231",
						"type": "tier"
					}
				],
				"links": {
					"self": "https://www.patreon.com/api/oauth2/v2/user/123456789"
				}
			} */
			
			final JsonNode patreonJson = (JsonNode) context.getContextData()
					.get(AbstractJsonUserAttributeMapper.CONTEXT_JSON_NODE);
			
			final JsonNode patreonJson_included = patreonJson.get("included");
			if (!patreonJson_included.isArray()) {
			    throw new Exception("patreonJson_included is not an array");
			}
			JsonNode tierJson = null;
			for (int i = 0; i < patreonJson_included.size(); i++) {
				JsonNode objNode = patreonJson_included.get(i);
				if (objNode == null) {
					// use org.keycloak.social.user_profile_dump:DEBUG to see the content of the 'included' array
					logger.warnf("Null object in patreon 'included' json at index %s!", i);
					continue;
				}

				JsonNode jsonType = objNode.get("type");
				if (jsonType == null) {
					logger.warnf("No 'type' param in included patreon object at index %s!", i);
					continue;
				}

				if (!"tier".equals(jsonType.asText())) {
					continue;
				} else if (tierJson != null) {
					// tierJson is already set
					// use org.keycloak.social.user_profile_dump:DEBUG to see the content of the 'included' array
					// TODO (sum up cents?!)
					throw new Error("Found a second 'tier' object in 'includes' at index " + i);
				}
				tierJson = objNode;

			}

			if (tierJson == null) {
				// use org.keycloak.social.user_profile_dump:DEBUG to see the content of the 'included' array
				logger.warnf("Found no object of type 'tier' in included patreon data!");
				return null;
			}

			JsonNode tierAttributes = tierJson.get("attributes");
			if (tierAttributes == null) {
				// use org.keycloak.social.user_profile_dump:DEBUG to see the content of the 'included' array
				logger.warnf("Found no attributes in 'tier' object in included patreon data!");
				return null;
			}

			JsonNode amountCents = tierAttributes.get("amount_cents");
			if (amountCents == null || !amountCents.isIntegralNumber()) {
				// use org.keycloak.social.user_profile_dump:DEBUG to see the content of the 'included' array
				logger.warnf("Found no valid 'amount_cents' in tier attributes in included patreon data!");
				return null;
			}

			// check amount of money and return
			try {
				int centsTheUserPaysPerMonth = Integer.parseInt(amountCents.asText());
				if (centsTheUserPaysPerMonth >= minCentsPerMonth) {
					return roleName;
				} else {
					logger.warnf("Not enough patreon cents to add role '%s'", roleName);
					return null;
				}
			} catch(Exception e) {
				throw new Exception("Can't get cents from '"+amountCents.asText()+"'", e);
			}

		} catch (Exception e) {
			logger.warn("Can't get role after patreon link because of an Exception", e);
			return null;
		}
	}

	private static String mapToString(Map<String, Object> patreonData) {
		return patreonData.keySet().stream()
				// .map(key -> key + "=" + patreonData.get(key).toString())
				.map(key -> key + "=" + patreonData.get(key))
				.collect(Collectors.joining(", ", "{", "}"));
	}

}
