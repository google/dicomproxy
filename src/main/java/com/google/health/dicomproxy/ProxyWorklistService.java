// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCFindSCP;
import org.dcm4che3.net.service.BasicQueryTask;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryTask;

import java.util.ArrayDeque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;

/** DICOM Modality Worklist C-FIND listener which proxies to DICOMweb UPS-RS SearchForWorkitems. */
final class ProxyWorklistService extends BasicCFindSCP {

  private WorklistClient worklistClient;

  ProxyWorklistService(WorklistClient worklistClient) {
    super(new String[] {UID.ModalityWorklistInformationModelFIND});
    this.worklistClient = worklistClient;
  }

  @Override
  protected QueryTask calculateMatches(
      Association as, PresentationContext pc, Attributes requestMetadata, Attributes keys)
      throws DicomServiceException {
    Logging.info("Received query:\n%s", keys.toString(1000,1000));
    return new PatientQueryTask(as, pc, requestMetadata, keys, worklistClient.listWorkitems(keys));
  }

  private static class PatientQueryTask extends BasicQueryTask {
    private final Queue<Attributes> resultQueue;

    public PatientQueryTask(
        Association as, PresentationContext pc, Attributes requestMetadata, Attributes keys,
        List<Attributes> results) {
      super(as, pc, requestMetadata, keys);

      resultQueue = new ArrayDeque<>(results);
    }

    @Override
    protected Attributes nextMatch() throws DicomServiceException {
      if (!hasMoreMatches()) {
        throw new NoSuchElementException();
      }

      return resultQueue.poll();
    }

    @Override
    protected boolean hasMoreMatches() throws DicomServiceException {
      return !resultQueue.isEmpty();
    }
  }
}
