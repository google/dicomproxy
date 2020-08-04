// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.UIDUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * DICOM C-STORE receiver which stores to a temp folder and passes off to a {@link DicomUploader}.
 */
final class ProxyStoreSCPReceiver extends BasicCStoreSCP {
  private static final String ASSOCIATION_UUID_PROPERTY = "association-uuid";

  private final DicomUploader dicomUploader;
  private final File tempFolder;

  ProxyStoreSCPReceiver(String[] sopClasses, DicomUploader uploader) {
    super(sopClasses);
    this.dicomUploader = uploader;

    String tempFolderPath = Configuration.ConfigKey.TEMP_FOLDER.getString();
    tempFolder = new File(tempFolderPath);
    checkArgument(
        !tempFolder.exists() || tempFolder.isDirectory(),
        "Temp Folder %s already exists, but is not a directory.",
        tempFolderPath);

    if (!tempFolder.isDirectory()) {
      checkArgument(tempFolder.mkdirs(), "Unable to create temp folder %s.", tempFolderPath);
    }
  }

  @Override
  protected void store(
      Association association,
      PresentationContext presentationContext,
      Attributes request,
      PDVInputStream dataStream,
      Attributes response)
      throws IOException {
    String sopClass = request.getString(Tag.AffectedSOPClassUID);
    String sopInstance = request.getString(Tag.AffectedSOPInstanceUID);
    if (!UIDUtils.isValid(sopInstance)) {
      throw new DicomServiceException(
          Status.ProcessingFailure,
          String.format("Invalid Affected SOP Instance UID: %s", sopInstance));
    }

    String transferSyntax = presentationContext.getTransferSyntax();
    String remoteAeTitle = association.getCallingAET();

    File parentFile = getAssociationTempPath(association);

    File tmpFile = new File(parentFile, sopInstance + ".dcm.tmp");
    try (DicomOutputStream out = new DicomOutputStream(tmpFile)) {
      out.writeFileMetaInformation(
          association.createFileMetaInformation(
              /* iuid=*/ sopInstance, /* cuid=*/ sopClass, /* tsuid=*/ transferSyntax));

      dataStream.copyTo(out);
    } catch (IOException e) {
      throw new DicomServiceException(Status.ProcessingFailure, e);
    }

    File permanentFile = new File(parentFile, sopInstance + ".dcm");
    // TODO: make sure this works on Windows.
    if (!tmpFile.renameTo(permanentFile)) {
      throw new DicomServiceException(
          Status.ProcessingFailure,
          String.format("Unable to rename %s to %s.", tmpFile, permanentFile));
    }

    response.setInt(Tag.Status, VR.US, Status.Success);
    Logging.info(
        "Received %s from %s with SOP %s in TS %s; stored as %s",
        sopInstance,
        remoteAeTitle,
        UID.nameOf(sopClass),
        UID.nameOf(transferSyntax),
        permanentFile);
  }

  /**
   * If not already set, generates a UUID for the specified association and creates a temp folder
   * based on that UUID. Associations have a generic property map that may be used to store
   * arbitrary data, for purposes such as this.
   *
   * @throws DicomServiceException if the temporary folder cannot be created.
   */
  private File getAssociationTempPath(Association association) throws DicomServiceException {
    String associationUuid = (String) association.getProperty(ASSOCIATION_UUID_PROPERTY);
    if (associationUuid == null) {
      associationUuid = UUID.randomUUID().toString();
      association.setProperty(ASSOCIATION_UUID_PROPERTY, associationUuid);
      if (!new File(tempFolder, associationUuid).mkdirs()) {
        throw new DicomServiceException(
            Status.ProcessingFailure,
            String.format("Unable to create temp folder %s/%s", tempFolder, associationUuid));
      }
    }

    return new File(tempFolder, associationUuid);
  }

  @Override
  public void onClose(Association association) {
    // If an exception occurs during processing of the association (such as a client- or server-
    // generated A-ABORT, an exception during store(), etc.) the exception is stored in the
    // Association instance, and may be detected during onClose (which is always called regardless
    // of success or failure).
    if (association.getException() != null) {
      Logging.error(association.getException(), "Association failed with exception.");
    } else {
      Logging.info("Association finished cleanly.");
      if (association.containsProperty(ASSOCIATION_UUID_PROPERTY)) {
        try {
          File tempPath = getAssociationTempPath(association);
          dicomUploader.uploadDirectory(tempPath);
        } catch (DicomServiceException e) {
          Logging.error(e, "Error getting temp folder for association.");
        }
      }
    }
  }
}
