package com.tngtech.keycloakmock.api;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class TokenGenerator {
  private static final String KEY_ID = "keyId";
  private static final String KEY = "rsa";
  private static final String JWKS_TEMPLATE =
      "{\n"
          + "    \"keys\": [{\n"
          + "        \"kid\": \"%s\",\n"
          + "        \"kty\": \"RSA\",\n"
          + "        \"alg\": \"RS256\",\n"
          + "        \"use\": \"sig\",\n"
          + "        \"n\": \"%s\",\n"
          + "        \"e\": \"%s\"\n"
          + "    }]\n"
          + "}";
  @Nonnull private final Key privateKey;
  @Nonnull private final String jwksResponse;

  TokenGenerator() {
    try {
      KeyStore keyStore = KeyStore.getInstance("JKS");
      try (InputStream keystoreStream = this.getClass().getResourceAsStream("/keystore.jks")) {
        keyStore.load(keystoreStream, null);
      }
      privateKey = keyStore.getKey(KEY, new char[] {});
      RSAPublicKey publicKey = (RSAPublicKey) keyStore.getCertificate(KEY).getPublicKey();
      String base = Base64.getEncoder().encodeToString(publicKey.getModulus().toByteArray());
      String exponent =
          Base64.getEncoder().encodeToString(publicKey.getPublicExponent().toByteArray());
      jwksResponse = String.format(JWKS_TEMPLATE, KEY_ID, base, exponent);
    } catch (IOException
        | CertificateException
        | NoSuchAlgorithmException
        | UnrecoverableKeyException
        | KeyStoreException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  @Nonnull
  String getToken(@Nonnull final TokenConfig tokenConfig, @Nonnull final String issuer) {
    JwtBuilder builder =
        Jwts.builder()
            .setHeaderParam("kid", KEY_ID)
            .setIssuedAt(new Date(tokenConfig.getIssuedAt().toEpochMilli()))
            .claim("aud", tokenConfig.getAudience())
            .setExpiration(new Date(tokenConfig.getExpiration().toEpochMilli()))
            .setIssuer(Objects.requireNonNull(issuer))
            .setSubject(tokenConfig.getSubject())
            .claim("typ", "Bearer")
            .claim("azp", tokenConfig.getAuthorizedParty());
    setClaimIfPresent(builder, "name", tokenConfig.getName());
    setClaimIfPresent(builder, "given_name", tokenConfig.getGivenName());
    setClaimIfPresent(builder, "family_name", tokenConfig.getFamilyName());
    setClaimIfPresent(builder, "email", tokenConfig.getEmail());
    setClaimIfPresent(builder, "preferred_username", tokenConfig.getPreferredUsername());
    return builder
        .claim("realm_access", tokenConfig.getRealmAccess())
        .claim("resource_access", tokenConfig.getResourceAccess())
        .addClaims(tokenConfig.getClaims())
        .signWith(privateKey, SignatureAlgorithm.RS256)
        .compact();
  }

  @Nonnull
  String getJwksResponse() {
    return jwksResponse;
  }

  private void setClaimIfPresent(
      @Nonnull final JwtBuilder builder, @Nonnull final String claim, @Nullable String value) {
    if (value != null) {
      Objects.requireNonNull(builder).claim(claim, value);
    }
  }
}
