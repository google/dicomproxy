// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.util.IOUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import com.google.common.net.MediaType;
import com.google.protobuf.ByteString;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.ContentHandlerAdapter;
import org.dcm4che3.mime.MultipartParser;

/**
 * Parses a UPS-RS SearchForWorkItems response into DICOM datasets suitable for consumption by a
 * MWL-speaking device.
 */
final class UpsWorklistResponseParser {

  /**
   * Extracts and parses a worklist-query response, returning each work-item as an {@link
   * Attributes}.
   *
   * @param contentType specifies the Content-Type of the HTTP response to be parsed
   * @param input input stream to consume data from
   */
  static final ImmutableList<Attributes> parseUpsResponseToMwl(
      String contentType, InputStream input) throws IOException {
    MediaType mediaType = MediaType.parse(contentType);
    checkArgument(
        "multipart/related".equals(mediaType.type() + "/" + mediaType.subtype()),
        "media-type %s is not supported",
        contentType);

    String boundary = Iterables.getOnlyElement(mediaType.parameters().get("boundary"));
    checkArgument(!Strings.isNullOrEmpty(boundary));

    // Wrap in a buffered stream so we can peek ahead to see if the response is empty.
    BufferedInputStream bufferedWrapper = new BufferedInputStream(input);
    bufferedWrapper.mark(1);
    if (bufferedWrapper.read() == -1) {
      return ImmutableList.of();
    }
    bufferedWrapper.reset();


    ImmutableList.Builder<Attributes> resultBuilder = ImmutableList.builder();
    new MultipartParser(boundary)
        .parse(
            bufferedWrapper,
            (unusedPartNumber, partStream) -> {
              try {
                Attributes parsed = parseXmlPart(partStream);
                convertUpsResponseToMwl(parsed);
                resultBuilder.add(parsed);
              } catch (Exception e) {
                Logging.error(e, "Error parsing response");
                throw new RuntimeException("Failed to parse", e);
              }
            });

    return resultBuilder.build();
  }

  private static Attributes parseXmlPart(InputStream inputStream) throws Exception {
    String fullStr =
        CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    List<String> lines = Splitter.on("\r\n").splitToList(fullStr);

    String contentType = null;
    int idx = 0;
    for (; idx < lines.size(); idx++) {
      String curLine = lines.get(idx);
      if (curLine.isBlank()) {
        // Blank line
        break;
      }

      // Must be a header line; we only care about Content-Type.
      List<String> headerPieces = Splitter.on(":").splitToList(curLine);
      if (headerPieces.size() != 2) {
        throw new IOException(
            "Expected either blank-line or header; found '" + curLine + "' at line idx");
      }

      if (headerPieces.get(0).strip().equalsIgnoreCase("content-type")) {
        contentType = headerPieces.get(1).strip();
      }
    }

    if (contentType == null) {
      throw new IOException("Expected to find a Content-Type header");
    }
    if (!contentType.equals("application/dicom+xml")) {
      throw new IOException(
          "Cannot handle Content-Type '"
              + contentType
              + "'; only application/dicom+xml is supported");
    }

    Attributes attributes = new Attributes();
    SAXParserFactory parserFactory = SAXParserFactory.newDefaultInstance();
    SAXParser parser = parserFactory.newSAXParser();
    String payload = Joiner.on("\r\n").join(lines.subList(idx + 1, lines.size()));
    parser.parse(
        new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)),
        new ContentHandlerAdapter(attributes));

    return attributes;
  }

  /**
   * Converts a worklist-entry from the UPS-RS schema to the MWL schema. This will modify the
   * contents of the specified Attributes.
   */
  @VisibleForTesting
  static void convertUpsResponseToMwl(Attributes attributes) {
    // Move the contents of the ReferencedRequestSequence to top-level.
    Sequence rrSeq = attributes.getSequence(Tag.ReferencedRequestSequence);
    if (rrSeq != null && rrSeq.size() == 1) {
      Attributes rrSeqItem = rrSeq.get(0);
      attributes.addAll(rrSeqItem);
      attributes.remove(Tag.ReferencedRequestSequence);
    }

    // If populated, convert the ScheduledProcedureStepStartDateTime into separate Date and/or Time.
    Sequence spsSequence = attributes.getSequence(Tag.ScheduledProcedureStepSequence);
    if (spsSequence != null && spsSequence.size() == 1) {
      Attributes spsSeqItem = spsSequence.get(0);
      String dateTime = spsSeqItem.getString(Tag.ScheduledProcedureStepStartDateTime);

      if (dateTime != null && !dateTime.isBlank()) {
        spsSeqItem.remove(Tag.ScheduledProcedureStepStartDateTime);
        // DICOM DateTimes are in form YYYYMMDDHHMMSS.FFFFFF&ZZXX; we convert from DateTime to
        // Date + Time, but only include the Time component if it's included. Only the year
        // component is required for a DateTime; everything else is "optional".

        if (dateTime.length() >= 4 && dateTime.length() < 8) {
          // We have a partial date, which we'll treat as the earliest possible valid day.
          if (dateTime.length() == 4) {
            // YYYY -> YYYY0101.
            spsSeqItem.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, dateTime + "0101");
          } else if (dateTime.length() == 6) {
            // YYYYMM -> YYYYMM01.
            spsSeqItem.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, dateTime + "01");
          } else {
            // YYYYM or YYYYMMD isn't a valid format.
          }
        } else if (dateTime.length() == 8) {
          // We have just a date, which can simply be copied through.
          spsSeqItem.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, dateTime);
        } else if (dateTime.length() > 8) {
          // We have a date + time, so both pieces can be converted.
          spsSeqItem.setString(
              Tag.ScheduledProcedureStepStartDate, VR.DA, dateTime.substring(0, 8));
          String timePart = dateTime.substring(8);

          // Strip off timezone piece, which isn't supported by DA.
          int ampIdx = timePart.indexOf('&');
          if (ampIdx > -1) {
            timePart = timePart.substring(0, ampIdx);
          }
          if (timePart.length() >= 6 && !timePart.matches("[0.]+")) {
            // If time-part isn't blank or midnight, we set the time.
            spsSeqItem.setString(Tag.ScheduledProcedureStepStartTime, VR.TM, timePart);
          }
        } else {
          // If length is < 4, just ignore it as this isn't a valid year anyway.
        }
      }
    }
  }
}
