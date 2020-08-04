// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableSet;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.io.InputStream;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class UpsWorklistClientTest {
  private static final String FAKE_WORKLIST_URL = "https://worklist.example.com/workitems";

  // Fakes for HTTP request-handling.
  private final MockLowLevelHttpResponse mockResponse = new MockLowLevelHttpResponse();
  private MockLowLevelHttpRequest mockRequest;
  private final MockHttpTransport mockTransport =
      new MockHttpTransport() {
        @Override
        public boolean supportsMethod(String method) throws IOException {
          return "GET".equals(method);
        }

        @Override
        public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
          mockRequest = new MockLowLevelHttpRequest(url).setUrl(url).setResponse(mockResponse);
          return mockRequest;
        }
      };
  private final HttpRequestFactory mockHttpRequestFactory = mockTransport.createRequestFactory();

  private final UpsWorklistClient client = new UpsWorklistClient(mockHttpRequestFactory);
  private final Attributes request = new Attributes();

  @BeforeClass
  public static void setupFlags() {
    System.setProperty("com.google.health.dicomproxy.worklist-uri", FAKE_WORKLIST_URL);
  }

  @Test
  public void setsRequestParameters() throws Exception {
    request.setString(Tag.PatientName, VR.PN, "*");

    mockResponse.setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
    mockResponse.setContentType(
        "multipart/related; type=\"application/dicom+xml\"; boundary=\"BoUndaRy\"");
    mockResponse.setContent(readTestFile("ups_xml_multipart_response_body.txt"));

    assertThat(client.listWorkitems(request)).hasSize(2);
    assertThat(mockRequest.getUrl()).isEqualTo(FAKE_WORKLIST_URL + "?00100010=*");
  }

  @Test
  public void noContentResponse_returnsEmptyList() throws Exception {
    mockResponse.setStatusCode(HttpStatusCodes.STATUS_CODE_NO_CONTENT);
    mockResponse.setContentType("text/plain");
    mockResponse.setContent("");

    assertThat(client.listWorkitems(request)).isEmpty();
    assertThat(mockRequest.getUrl()).isEqualTo(FAKE_WORKLIST_URL);
  }

  private InputStream readTestFile(String filename) throws Exception {
    return getClass().getResourceAsStream(filename);
  }
}
