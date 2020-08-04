// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.collect.ImmutableList;
import com.google.health.dicomproxy.Configuration.ConfigKey;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.dcm4che3.util.StreamUtils;

/**
 * Uploader of DICOM data via STOW-RS.
 *
 * <p>TODO: Add better error handling, retry with backoff, cleanups, multiple simultaneous uploads,
 * etc.
 */
final class StowRsUploader implements DicomUploader {

  private static final String REQUEST_CONTENT_TYPE = "multipart/related";
  private static final String REQUEST_PART_CONTENT_TYPE = "application/dicom";
  private static final String RESPONSE_CONTENT_TYPE = "application/dicom+xml";

  private final ExecutorService uploadExecutor;
  private final HttpRequestFactory requestFactory;

  StowRsUploader() {
    this(
        Executors.newFixedThreadPool(/* nThreads=*/ ConfigKey.UPLOAD_PARALLELISM.getInt()),
        new NetHttpTransport().createRequestFactory(Credentials.getServiceAccountCredentials()));
  }

  StowRsUploader(ExecutorService uploadExecutor, HttpRequestFactory requestFactory) {
    this.uploadExecutor = checkNotNull(uploadExecutor);
    this.requestFactory = checkNotNull(requestFactory);
  }

  @Override
  public void uploadDirectory(File toUpload) {
    // TODO: rename folder (or otherwise flag) before beginning uploads, so we can track what was
    // completely stored vs. what was aborted.
    Logging.info("Preparing to upload %s", toUpload);

    // ErrorProne requires Futures to be assigned to a variable; we don't care about the result
    // here.
    @SuppressWarnings("unused")
    Future<?> unused = uploadExecutor.submit(() -> performUpload(toUpload));
  }

  private void performUpload(File toUpload) {
    try {
      GenericUrl uploadUrl = new GenericUrl(ConfigKey.UPLOAD_URI.getString());
      HttpRequest postRequest =
          requestFactory.buildPostRequest(uploadUrl, new StowRsContent(toUpload));
      postRequest.getHeaders().setAccept(RESPONSE_CONTENT_TYPE);
      // Increase client-side read-timeout to 10 minutes (default is 20s).
      postRequest.setReadTimeout(600_000);

      HttpResponse response = postRequest.execute();
      processResponse(toUpload, response);
    } catch (Throwable e) {
      // TODO: add more robust error handling, e.g. retries.
      Logging.error(e, "Error performing upload of %s", toUpload);
    }
  }

  private void processResponse(File parentFolder, HttpResponse response) {
    String responseText = null;
    try {
      responseText = response.parseAsString();
    } catch (IOException e) {
      Logging.error(e, "Error extracting response body");
    }

    if (response.getStatusCode() != HttpStatusCodes.STATUS_CODE_OK) {
      Logging.error(
          "Error uploading %s - received status %s: %s",
          parentFolder, response.getStatusCode(), responseText);
    } else {
      Logging.info("Successfully uploaded %s:\n%s", parentFolder, responseText);
      // TODO: Parse result as XML-DICOM and verify no upload issues occurred.
      // TODO: Clean up temp folder after successful upload.
    }
  }

  /** Content provider for uploads. */
  private static final class StowRsContent implements HttpContent {

    private final List<File> files;

    /** Multipart boundary. This intentionally is different per-request. */
    private final String boundary = UUID.randomUUID().toString();

    StowRsContent(File parentFolder) {
      File[] fileArr =
          checkNotNull(
              parentFolder.listFiles(),
              "Parent folder for upload does not exist: %s",
              parentFolder);
      // Filter out any subdirectories which might have gotten in there through mysterious means.
      files = Arrays.stream(fileArr).filter(File::isFile).collect(ImmutableList.toImmutableList());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Writes the files as an HTTP multipart stream.
     *
     * <p>See <a href="https://www.w3.org/Protocols/rfc1341/7_2_Multipart.html">RFC-1341, Section
     * 7.2</a> for details on the multipart format.
     */
    @Override
    public void writeTo(OutputStream out) throws IOException {
      // DataOutput provides convenience methods for writing entire byte arrays.
      DataOutputStream dataOut = new DataOutputStream(out);

      for (int i = 0; i < files.size(); i++) {
        File f = files.get(i);

        Logging.info("Uploading file %s of %s: %s", i + 1, files.size(), f);
        // Boundary before a new part is just '--boundary'.
        dataOut.writeBytes("\r\n--" + boundary + "\r\n");
        dataOut.writeBytes("Content-Type: " + REQUEST_PART_CONTENT_TYPE + "\r\n");
        dataOut.writeBytes("\r\n");

        try (FileInputStream fis = new FileInputStream(f)) {
          StreamUtils.copy(fis, dataOut);
        }
      }

      // Final boundary is '--boundary--'.
      dataOut.writeBytes("\r\n--" + boundary + "--\r\n");
    }

    @Override
    public boolean retrySupported() {
      // Retry will be handled through different means.
      return false;
    }

    /** Provides the Content-Type. */
    @Override
    public String getType() {
      return String.format(
          "%s; type=\"%s\"; boundary=%s",
          REQUEST_CONTENT_TYPE, REQUEST_PART_CONTENT_TYPE, boundary);
    }

    @Override
    public long getLength() throws IOException {
      // We don't precompute the Content-Length, as it would be a pain due to the multipart headers.
      return -1;
    }
  }
}
