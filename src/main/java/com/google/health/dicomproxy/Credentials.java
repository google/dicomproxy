// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

import com.google.health.dicomproxy.Configuration.ConfigKey;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

/** Utilities for working with OAuth credentials. */
final class Credentials {

  /** Provides auto-refreshing Google service account credentials. */
  static Credential getServiceAccountCredentials() {
    String credsJsonPath = ConfigKey.SERVICE_ACCOUNT_CREDS_JSON.getString();
    GoogleCredential creds = null;

    try (FileInputStream input = new FileInputStream(new File(credsJsonPath))) {
      creds =
          GoogleCredential.fromStream(input, new NetHttpTransport(), new JacksonFactory())
              .createScoped(Arrays.asList("https://www.googleapis.com/auth/lifescience.dicomweb"));
    } catch (IOException e) {
      Logging.error(e, "Error loading service account credentials.");
      System.exit(-1);
    }
    return creds;
  }

  /** Prevent instantiation. */
  private Credentials() {}
}
