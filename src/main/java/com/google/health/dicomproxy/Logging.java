// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import com.google.common.base.Throwables;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** Placeholder logging interface until we decide on a logging solution. */
final class Logging {

  @FormatMethod
  static final void info(@FormatString String format, Object... args) {
    System.out.printf("INFO:  %s\n", String.format(format, args));
  }

  @FormatMethod
  static final void error(Throwable t, @FormatString String format, Object... args) {
    System.err.printf(
        "ERROR: %s\n%s\n", String.format(format, args), Throwables.getStackTraceAsString(t));
  }

  @FormatMethod
  static final void error(@FormatString String format, Object... args) {
    System.err.printf("ERROR: %s\n", String.format(format, args));
  }

  /** Prevent instantiation. */
  private Logging() {}
}
