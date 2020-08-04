// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSetMultimap;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.TagUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts from a MWL C-FIND query-dataset to UPS-RS query-parameters.
 *
 * <p>This necessarily does some massaging of the data, as there's not a 1:1 mapping between MWL and
 * UPS. Mostly this consists of fields which are parented differently (top-level vs inside a
 * sequence), or converting from Date + Time to DateTime.
 *
 * <p>For details on the query-parameter format, see <a
 * href="http://dicom.nema.org/medical/dicom/2019a/output/chtml/part18/sect_6.7.html#sect_6.7.1.2">the
 * DICOM Standard</a>. TL;DR is that fields to match on are specified as "tagnumber=parameter". For
 * fields requested to be returned, they are listed as "includefield=tagnumber". (This is similar to
 * the concept of a protobuf FieldMask.)
 */
final class WorklistQueryTranslator {

  public static final String INCLUDEFIELD = "includefield";

  private static final String SCHEDULED_PROCEDURE_START_DATE = "00400100.00400002";
  private static final String SCHEDULED_PROCEDURE_START_TIME = "00400100.00400003";
  private static final String SCHEDULED_PROCEDURE_START_DATETIME = "00400100.00404005";

  /**
   * If set, rather than translating individual return-keys to "includefield=" parameters, we will
   * just set "includefield=all" in all cases. This will instruct the UPS-RS server to return all
   * relevant DICOM fields in the response.
   */
  private final boolean includefieldAll;

  public WorklistQueryTranslator() {
    includefieldAll = Configuration.ConfigKey.WORKLIST_INCLUDEFIELD_ALL.getBoolean();
  }

  public WorklistQueryTranslator(boolean includefieldAll) {
    this.includefieldAll = includefieldAll;
  }

  public ImmutableSetMultimap<String, String> buildQueryParameters(Attributes attributes)
      throws Exception {
    // First-pass: walk the entire Attributes and convert into keys-value pairs.
    Map<String, String> rawParamsAsStrings = new HashMap<>();
    attributes.accept(
        (Attributes attrs, int tag, VR vr, Object value) -> {
          String tagStr = tagPathString(attrs, tag);
          if (vr == VR.SQ) {
            Sequence sq = attrs.getSequence(tag);
            if (sq.isEmpty()) {
              // Empty sequences are set as includefield.
              rawParamsAsStrings.put(tagStr, "");
            }
            return true;
          }

          if (value == null || String.valueOf(value).isBlank()) {
            // Null vs blank are basically equivalent for C-FIND queries; both will get translated
            // into "includefield=tagnumber" in our third pass.
            rawParamsAsStrings.put(tagStr, "");
          } else {
            rawParamsAsStrings.put(tagStr, attrs.getString(tag));
          }

          // Returning true means to keep walking this data structure.
          return true;
        },
        /* visitNestedDatasets=*/ true);

    // Second pass: rewrite from MWL key-value pairs to UPS-RS parameters.
    rewriteScheduledProcedureDateTime(rawParamsAsStrings);
    rewriteTagIntoSequence(rawParamsAsStrings, Tag.StudyInstanceUID, Tag.ReferencedRequestSequence);
    rewriteTagIntoSequence(
        rawParamsAsStrings, Tag.RequestingPhysician, Tag.ReferencedRequestSequence);
    rewriteTagIntoSequence(
        rawParamsAsStrings, Tag.ReferringPhysicianName, Tag.ReferencedRequestSequence);
    rewriteTagIntoSequence(
        rawParamsAsStrings, Tag.RequestedProcedureDescription, Tag.ReferencedRequestSequence);
    rewriteTagIntoSequence(
        rawParamsAsStrings, Tag.RequestedProcedureID, Tag.ReferencedRequestSequence);
    rewriteTagIntoSequence(rawParamsAsStrings, Tag.AccessionNumber, Tag.ReferencedRequestSequence);

    // Third pass: convert "x=<blank>" to "includefield=x".
    ImmutableSetMultimap.Builder<String, String> result = ImmutableSetMultimap.builder();
    for (Map.Entry<String, String> entry : rawParamsAsStrings.entrySet()) {
      if (entry.getValue().isBlank()) {
        if (!includefieldAll) {
          result.put(INCLUDEFIELD, entry.getKey());
        }
      } else {
        result.put(entry);
      }
    }
    if (includefieldAll) {
      result.put("includefield", "all");
    }

    return result.build();
  }

  private static void rewriteTagIntoSequence(
      Map<String, String> rawParamsAsStrings, int tagNum, int newSequenceParentTag) {
    String origTagStr = TagUtils.toHexString(tagNum);
    String matchVal = rawParamsAsStrings.remove(origTagStr);

    if (matchVal != null) {
      rawParamsAsStrings.put(
          TagUtils.toHexString(newSequenceParentTag) + "." + origTagStr, matchVal);
    }
  }

  private static final Splitter DASH_SPLITTER = Splitter.on('-').limit(2);

  private static void rewriteScheduledProcedureDateTime(Map<String, String> rawParamsAsStrings) {
    if (!rawParamsAsStrings.containsKey(SCHEDULED_PROCEDURE_START_DATE)) {
      return;
    }

    String dateParam = rawParamsAsStrings.remove(SCHEDULED_PROCEDURE_START_DATE);
    String timeParam = rawParamsAsStrings.remove(SCHEDULED_PROCEDURE_START_TIME);

    if (dateParam.isBlank()) {
      // If no date is specified, querying by time makes no sense, so we just ask for the
      // datetime.
      rawParamsAsStrings.put(SCHEDULED_PROCEDURE_START_DATETIME, "");
    } else {
      if (timeParam != null) {
        if (dateParam.contains("-") && timeParam.contains("-")) {
          // Both date- and time-ranges can be converted to a datetime range.
          List<String> dateRange = DASH_SPLITTER.splitToList(dateParam);
          List<String> timeRange = DASH_SPLITTER.splitToList(timeParam);
          dateParam =
              dateRange.get(0) + timeRange.get(0) + '-' + dateRange.get(1) + timeRange.get(1);
        } else if (dateParam.contains("-")) {
          // A date-range with a rangeless time is OK (but weird).
          List<String> dateRange = DASH_SPLITTER.splitToList(dateParam);
          dateParam = dateRange.get(0) + timeParam + '-' + dateRange.get(1) + timeParam;
        } else if (timeParam.contains("-")) {
          // Static date with time range.
          List<String> timeRange = DASH_SPLITTER.splitToList(timeParam);
          dateParam = dateParam + timeRange.get(0) + '-' + dateParam + timeRange.get(1);
        } else {
          // Neither are ranged, so we can just concatenate.
          dateParam += timeParam;
        }
      }
      rawParamsAsStrings.put(SCHEDULED_PROCEDURE_START_DATETIME, dateParam);
    }
  }

  /** Returns the tag-path string for the given tag and level. */
  private static String tagPathString(Attributes attrs, int tag) {
    if (attrs.isRoot()) {
      return TagUtils.toHexString(tag);
    }
    return tagPathString(attrs.getParent(), attrs.getParentSequenceTag())
        + "."
        + TagUtils.toHexString(tag);
  }
}
