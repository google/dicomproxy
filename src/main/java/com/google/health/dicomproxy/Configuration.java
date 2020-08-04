// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Entry point for managing config data for DICOMweb Proxy.
 *
 * <p>Currently this just has hardcoded data which may be overridden by system properties.
 *
 * <p>TODO: decide on a configuration format and location.
 */
final class Configuration {
  enum ConfigKey {
    TEMP_FOLDER("temp-data", "com.google.health.dicomproxy.temp-folder"),
    RECEIVE_PORT("4008", "com.google.health.dicomproxy.receive-port"),
    UPLOAD_URI(
        null /* Required, unless operating in worklist-only mode. */,
        "com.google.health.dicomproxy.upload-uri",
        Configuration::validateUpstreamUri),
    UPLOAD_PARALLELISM(
      "10", "com.google.health.dicomproxy.upload-parallelism"
    ),
    WORKLIST_URI(
        null /* Required, unless operating in upload-only mode. */,
        "com.google.health.dicomproxy.worklist-uri",
        Configuration::validateUpstreamUri),
    /**
     * If true, UPS-RS queries should set "includefield=all", regardless of what return-keys the MWL
     * client has specified.
     */
    WORKLIST_INCLUDEFIELD_ALL("false", "com.google.health.dicomproxy.worklist-includefield-all"),
    SERVICE_ACCOUNT_CREDS_JSON(
        "service_account_creds.json", "com.google.health.dicomproxy.service-account-creds-json-file"),
    ;

    static {
      validateConfig();
    }

    private final String defaultValue;
    private final String systemProperty;

    /**
     * @param defaultValue hard-coded default value if System Property is not set. If null, this
     *     means that no default is provided; this must be either user-specified or the relevant
     *     feature will be disabled.
     * @param systemProperty the System Property key which may be used to customize this setting.
     * @param validator a Consumer which ensures the given configuration is valid; it should throw
     *     an exception if the configuration cannot be used.
     */
    ConfigKey(@Nullable String defaultValue, String systemProperty, Consumer<String> validator) {
      this.defaultValue = defaultValue;
      this.systemProperty = systemProperty;

      String value = System.getProperty(systemProperty, defaultValue);
      if (value != null) {
        validator.accept(value);
      }
    }

    /** Constructs a ConfigKey without a validator. */
    ConfigKey(@Nullable String defaultValue, String systemProperty) {
      this(defaultValue, systemProperty, (s) -> {} /* Default validator accepts anything. */);
    }

    String getString() {
      return System.getProperty(systemProperty, defaultValue);
    }

    int getInt() {
      return Integer.parseInt(getString());
    }

    boolean getBoolean() {
      return Boolean.getBoolean(systemProperty);
    }

    String getSystemProperty() {
      return systemProperty;
    }

    private static void validateConfig() {
      if (UPLOAD_URI.getString() == null && WORKLIST_URI.getString() == null) {
        throw new IllegalArgumentException(
            String.format(
                "Neither Upload URI not Worklist URI was configured. "
                    + "Please specify at least one of system properties '%s' and '%s'.",
                UPLOAD_URI.systemProperty, WORKLIST_URI.systemProperty));
      }
    }
  }

  /**
   * Ensures that a given upstream URI is well-formed.
   *
   * <p>Note: these checks are intentionally simplistic. Their primary purpose is to ensure that
   * secure HTTP is being used unless connecting explicitly to localhost. This will prevent OAuth2
   * Bearer tokens from accidental disclosure by sending over an insecure connection.
   */
  private static void validateUpstreamUri(String upstreamUri) {
    URI uri;
    try {
      uri = new URI(upstreamUri);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(
          String.format("The specified upload URI is not valid: '%s'.", upstreamUri));
    }
    if (uri.getScheme().equals("https")) {
      // Allow HTTPS for anything.
      return;
    }
    if (uri.getScheme().equals("http") && uri.getHost().equals("127.0.0.1")) {
      // Allow insecure HTTP only for localhost.
      return;
    }

    throw new IllegalArgumentException(
        String.format(
            "The specified upload URI is not valid: '%s'.\n"
                + "The protocol used must be https://, unless connecting to 127.0.0.1.",
            upstreamUri));
  }

  /** Prevent instantiation. */
  private Configuration() {}
}
