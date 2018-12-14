package com.gruelbox.orko.auth.ipwhitelisting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.gruelbox.orko.auth.AuthConfiguration;
import com.gruelbox.orko.auth.RequestUtils;
import com.warrenstrange.googleauth.IGoogleAuthenticator;

import io.dropwizard.hibernate.UnitOfWork;

/**
 * Only one IP can be whitelisted at a time and requires 2FA.
 */
@Singleton
class IpWhitelistingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(IpWhitelistingService.class);

  private final Provider<RequestUtils> requestUtils;
  private final IGoogleAuthenticator googleAuthenticator;
  private final AuthConfiguration configuration;
  private final Provider<IpWhitelistAccess> ipWhitelistAccess;

  @Inject
  IpWhitelistingService(Provider<RequestUtils> requestUtils,
                        IGoogleAuthenticator googleAuthenticator,
                        AuthConfiguration configuration,
                        Provider<IpWhitelistAccess> ipWhitelistAccess) {
    this.requestUtils = requestUtils;
    this.googleAuthenticator = googleAuthenticator;
    this.configuration = configuration;
    this.ipWhitelistAccess = ipWhitelistAccess;
  }


  /**
   * Checks if the current IP is an authorised IP.
   *
   * @return True if authorised.
   */
  public boolean authoriseIp() {
    if (isDisabled())
      return true;
    String sourceIp = requestUtils.get().sourceIp();
    if (!ipWhitelistAccess.get().exists(sourceIp)) {
      LOGGER.error("Access attempt from [{}] not whitelisted", sourceIp);
      return false;
    }
    return true;
  }

  /**
   * Marks the current request IP as authorised. Requires
   * an ongoing transaction or {@link UnitOfWork}.
   *
   * @param token The attempted 2FA token.
   * @return True if success.
   */
  public boolean whiteListRequestIp(int token) {
    if (isDisabled())
      return true;
    String ip = requestUtils.get().sourceIp();
    if (!googleAuthenticator.authorize(configuration.getIpWhitelisting().getSecretKey(), token)) {
      LOGGER.error("Whitelist attempt failed from: " + ip);
      return false;
    }
    ipWhitelistAccess.get().add(ip);
    LOGGER.info("Whitelisted ip: " + ip);
    return true;
  }

  /**
   * Remove authorisation for the current IP.
   *
   * @return true if anything actually happened.
   */
  public boolean deWhitelistIp() {
    if (isDisabled())
      return false;
    if (!authoriseIp())
      return false;

    ipWhitelistAccess.get().delete(requestUtils.get().sourceIp());
    return true;
  }

  private boolean isDisabled() {
    return configuration.getIpWhitelisting() == null || !configuration.getIpWhitelisting().isEnabled();
  }
}