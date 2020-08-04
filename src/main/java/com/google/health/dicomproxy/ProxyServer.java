// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import com.google.health.dicomproxy.Configuration.ConfigKey;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.TransferCapability.Role;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomServiceRegistry;

import static com.google.common.base.Preconditions.*;

/** Receiver of DIMSE DICOM requests. */
final class ProxyServer {
  // TODO: Allow configuration of AE Title.
  private static final String ALL_ALLOWED_AE_TITLES = "*";

  private static final String ALL_ALLOWED_SOP_CLASSES = "*";
  private static final String ALL_ALLOWED_TRANSFER_SYNTAXES = "*";

  private final int listenPort = ConfigKey.RECEIVE_PORT.getInt();

  private final Device device = new Device("dicomweb-proxy");
  private final ApplicationEntity applicationEntity = new ApplicationEntity(ALL_ALLOWED_AE_TITLES);
  private final Connection connection = new Connection();
  private final DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();

  // Don't assume that we always are running a specific proxy type.
  private DicomUploader dicomUploader = null;
  private WorklistClient worklistClient = null;

  ProxyServer() {
    this(Executors.newCachedThreadPool(), Executors.newSingleThreadScheduledExecutor());
  }

  ProxyServer(ExecutorService executorService, ScheduledExecutorService scheduledExecutorService) {
    checkNotNull(executorService);
    checkNotNull(scheduledExecutorService);

    connection.setPort(listenPort);

    device.setDimseRQHandler(serviceRegistry);
    device.addConnection(connection);
    device.addApplicationEntity(applicationEntity);
    device.setScheduledExecutor(scheduledExecutorService);
    device.setExecutor(executorService);

    applicationEntity.setAssociationAcceptor(true);
    applicationEntity.addConnection(connection);

    // TODO: Add configurability of allowed SOP classes / transfer syntaxes. This only needs be done
    // here; the BasicCStoreSCP can claim to accept more than we allow here, but it doesn't make any
    // difference.

    // TODO: Disallow Explicit transfer syntaxes for RT DICOM due to length overflow limitations.

    // We always add support for C-ECHO.
    serviceRegistry.addDicomService(new BasicCEchoSCP());

    // For now, allow all transfer SOP classes / transfer syntaxes.
    applicationEntity.addTransferCapability(
        new TransferCapability(
            null /* commonName */,
            ALL_ALLOWED_SOP_CLASSES,
            Role.SCP,
            ALL_ALLOWED_TRANSFER_SYNTAXES));
  }

  void setDicomUploader(DicomUploader dicomUploader) {
    checkNotNull(dicomUploader);
    checkState(this.dicomUploader == null, "Attempted to setDicomUploader twice.");
    this.dicomUploader = dicomUploader;

    serviceRegistry.addDicomService(
        new ProxyStoreSCPReceiver(new String[] {ALL_ALLOWED_SOP_CLASSES}, dicomUploader));
  }

  void setWorklistClient(WorklistClient worklistClient) {
    checkNotNull(worklistClient);
    checkState(this.worklistClient == null, "Attempted to setWorklistClient twice.");
    this.worklistClient = worklistClient;

    serviceRegistry.addDicomService(new ProxyWorklistService(worklistClient));
  }

  /**
   * Binds to the configured port and starts handling incoming connections.
   *
   * @throws IOException if it cannot bind to the specified port
   */
  void startListening() throws Exception {
    device.bindConnections();
  }
}
