// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import com.google.common.collect.ImmutableList;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.service.DicomServiceException;

/** Interface to decouple MWL SCP from DICOMweb UPS-RS worklist client. */
public interface WorklistClient {
  /**
   * Performs a SearchForWorkitems query, returning a list of results.
   *
   * @param queryParams Specifies keys to match on and/or include in the search results.
   */
  ImmutableList<Attributes> listWorkitems(Attributes queryParams) throws DicomServiceException;
}
