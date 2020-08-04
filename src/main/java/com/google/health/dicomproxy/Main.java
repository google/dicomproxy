// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import java.lang.Thread.UncaughtExceptionHandler;

/** Main entry point for DicomProxy. */
public class Main {
  public static void main(String[] args) throws Exception {
    // Prevent uncaught exceptions from simply evaporating.
    Thread.setDefaultUncaughtExceptionHandler(
        new UncaughtExceptionHandler() {
          @Override
          public void uncaughtException(Thread t, Throwable e) {
            Logging.error(e, "Uncaught exception in thread %s", t.getName());
          }
        });

    // TODO: check for old temp folders and clean them up and/or upload them.

    ProxyServer proxyServer = new ProxyServer();
    if (Configuration.ConfigKey.UPLOAD_URI.getString() != null) {
      proxyServer.setDicomUploader(new StowRsUploader());
    }
    if (Configuration.ConfigKey.WORKLIST_URI.getString() != null) {
        proxyServer.setWorklistClient(new UpsWorklistClient());
    }
    proxyServer.startListening();
  }
}
