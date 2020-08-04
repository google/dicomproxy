// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.health.dicomproxy.Configuration.ConfigKey;
import java.io.IOException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;

/** Performs UPS-RS SearchForWorkitems queries. */
class UpsWorklistClient implements WorklistClient {

  private static final String XML_RESPONSE_CONTENT_TYPE =
      "multipart/related; type=\"application/dicom+xml\"";
  // Expected content-type for a JSON response; not currently used.
  // private static final String JSON_RESPONSE_CONTENT_TYPE = "application/dicom+json";

  private final HttpRequestFactory requestFactory;

  UpsWorklistClient() {
    this(new NetHttpTransport().createRequestFactory(Credentials.getServiceAccountCredentials()));
  }

  UpsWorklistClient(HttpRequestFactory requestFactory) {
    this.requestFactory = checkNotNull(requestFactory);
  }

  @Override
  public ImmutableList<Attributes> listWorkitems(Attributes queryParams)
      throws DicomServiceException {
    ImmutableSetMultimap<String, String> translatedParameters;
    try {
      translatedParameters = new WorklistQueryTranslator().buildQueryParameters(queryParams);
    } catch (Exception e) {
      Logging.error(
          e,
          "Error building query parameters:\n%s",
          queryParams.toString(/* limit */ 1000, /* maxWidth=*/ 1000));
      return ImmutableList.of();
    }

    HttpResponse response;
    try {
      GenericUrl queryUrl = new GenericUrl(ConfigKey.WORKLIST_URI.getString());
      translatedParameters.asMap().forEach((k, v) -> queryUrl.set(k, Joiner.on(',').join(v)));
      Logging.info("Performing query with URL <%s>", queryUrl);

      HttpRequest getRequest = requestFactory.buildGetRequest(queryUrl);
      // Tell the server we want to get back XML results and not JSON.
      getRequest.getHeaders().setAccept(XML_RESPONSE_CONTENT_TYPE);

      response = getRequest.execute();

      if (response.getStatusCode() == HttpStatusCodes.STATUS_CODE_NO_CONTENT) {
        // 204 means we don't need to know or care about the actual response body, since there are
        // no (more?) results.
        return ImmutableList.of();
      }

      if (response.getStatusCode() != HttpStatusCodes.STATUS_CODE_OK) {
        Logging.error(
            "Error performing worklist query - received status %s:\n%s",
            response.getStatusCode(), response.parseAsString());
      }

      return UpsWorklistResponseParser.parseUpsResponseToMwl(
          response.getContentType(), response.getContent());
    } catch (IOException e) {
      Logging.error(e, "Error performing UPS-RS query");
      throw new DicomServiceException(Status.ProcessingFailure, "Error performing UPS-RS query");
    }
  }
}
