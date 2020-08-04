// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static com.google.health.dicomproxy.WorklistQueryTranslator.INCLUDEFIELD;

/** Unit tests for {@link WorklistQueryTranslator}. */
@RunWith(JUnit4.class)
public class WorklistQueryTranslatorTest {

  private final WorklistQueryTranslator translator =
      new WorklistQueryTranslator(/* includeFieldAll= */ false);

  private final WorklistQueryTranslator includeFieldAllTranslator =
      new WorklistQueryTranslator(/* includeFieldAll= */ true);

  /**
   * Queries with fields present but unpopulated should convert those fields to "includefield"
   * parameters.
   */
  @Test
  public void includeOnly_basicParams() throws Exception {
    Attributes mwlQuery = new Attributes();
    mwlQuery.setNull(Tag.PatientName, VR.PN);
    mwlQuery.setNull(Tag.PatientID, VR.LO);

    assertThat(translator.buildQueryParameters(mwlQuery))
        .containsExactly(INCLUDEFIELD, "00100010", INCLUDEFIELD, "00100020");
  }

  @Test
  public void includeOnly_includeFieldAllSet() throws Exception {
    Attributes mwlQuery = new Attributes();
    mwlQuery.setNull(Tag.PatientName, VR.PN);
    mwlQuery.setNull(Tag.PatientID, VR.LO);

    assertThat(includeFieldAllTranslator.buildQueryParameters(mwlQuery))
        .containsExactly(INCLUDEFIELD, "all");
  }

  @Test
  public void emptySequence_shouldBeIncluded() throws Exception {
    Attributes mwlQuery = new Attributes();
    mwlQuery.setNull(Tag.ReferencedStudySequence, VR.SQ);

    assertThat(translator.buildQueryParameters(mwlQuery)).containsExactly(INCLUDEFIELD, "00081110");
  }

  @Test
  public void matchOnly_basicParams() throws Exception {
    Attributes mwlQuery = new Attributes();
    mwlQuery.setString(Tag.PatientName, VR.PN, "DOE^JOHN");
    mwlQuery.setString(Tag.PatientID, VR.LO, "1234");

    assertThat(translator.buildQueryParameters(mwlQuery))
        .containsExactly("00100010", "DOE^JOHN", "00100020", "1234");
  }

  // MWL uses separate Date and Time fields; UPS uses a DateTime.
  @Test
  public void includeOnly_startDateAndTime_toDateTime() throws Exception {
    Attributes mwlQuery = new Attributes();

    Sequence seq =
        mwlQuery.newSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes seqAttrs = new Attributes();
    seq.add(seqAttrs);
    seqAttrs.setNull(Tag.ScheduledProcedureStepStartDate, VR.DA);
    seqAttrs.setNull(Tag.ScheduledProcedureStepStartTime, VR.TM);

    assertThat(translator.buildQueryParameters(mwlQuery))
        .containsExactly(INCLUDEFIELD, "00400100.00404005");
  }

  @Test
  public void match_startDateOnly_toDateTime() throws Exception {
    Attributes mwlQuery = new Attributes();

    Sequence seq =
        mwlQuery.newSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes seqAttrs = new Attributes();
    seq.add(seqAttrs);
    seqAttrs.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, "20190204");
    seqAttrs.setNull(Tag.ScheduledProcedureStepStartTime, VR.TM);

    assertThat(translator.buildQueryParameters(mwlQuery))
        .containsExactly("00400100.00404005", "20190204");
  }

  @Test
  public void match_startDateAndTime_toDateTime() throws Exception {
    Attributes mwlQuery = new Attributes();

    Sequence seq =
        mwlQuery.newSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes seqAttrs = new Attributes();
    seq.add(seqAttrs);
    seqAttrs.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, "20190204");
    seqAttrs.setString(Tag.ScheduledProcedureStepStartTime, VR.TM, "100400");

    assertThat(translator.buildQueryParameters(mwlQuery))
        .containsExactly("00400100.00404005", "20190204100400");
  }

  @Test
  public void match_startDateRange_toDateTimeRange() throws Exception {
    Attributes mwlQuery = new Attributes();

    Sequence seq =
        mwlQuery.newSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes seqAttrs = new Attributes();
    seq.add(seqAttrs);
    seqAttrs.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, "20190204-20190405");
    seqAttrs.setNull(Tag.ScheduledProcedureStepStartTime, VR.TM);

    assertThat(translator.buildQueryParameters(mwlQuery))
        .containsExactly("00400100.00404005", "20190204-20190405");
  }

  @Test
  public void match_startDateRangeWithStaticTime_toDateTimeRange() throws Exception {
    Attributes mwlQuery = new Attributes();

    Sequence seq =
        mwlQuery.newSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes seqAttrs = new Attributes();
    seq.add(seqAttrs);
    seqAttrs.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, "20190204-20190405");
    seqAttrs.setString(Tag.ScheduledProcedureStepStartTime, VR.TM, "090401");

    assertThat(translator.buildQueryParameters(mwlQuery))
        .containsExactly("00400100.00404005", "20190204090401-20190405090401");
  }

  @Test
  public void match_startDateWithTimeRange_toDateTimeRange() throws Exception {
    Attributes mwlQuery = new Attributes();

    Sequence seq =
        mwlQuery.newSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes seqAttrs = new Attributes();
    seq.add(seqAttrs);
    seqAttrs.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, "20190204");
    seqAttrs.setString(Tag.ScheduledProcedureStepStartTime, VR.TM, "100400-200303");

    assertThat(translator.buildQueryParameters(mwlQuery))
        .containsExactly("00400100.00404005", "20190204100400-20190204200303");
  }

  @Test
  public void match_startDateAndTimeRange_toDateTimeRange() throws Exception {
    Attributes mwlQuery = new Attributes();

    Sequence seq =
        mwlQuery.newSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes seqAttrs = new Attributes();
    seq.add(seqAttrs);
    seqAttrs.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, "20190204-20190405");
    seqAttrs.setString(Tag.ScheduledProcedureStepStartTime, VR.TM, "100400-200303");

    assertThat(translator.buildQueryParameters(mwlQuery))
        .containsExactly("00400100.00404005", "20190204100400-20190405200303");
  }

  @Test
  public void include_translatesToReferencedRequestSequence() throws Exception {
    Attributes mwlQuery = new Attributes();
    mwlQuery.setNull(Tag.StudyInstanceUID, VR.UI);
    mwlQuery.setNull(Tag.RequestingPhysician, VR.PN);
    mwlQuery.setNull(Tag.ReferringPhysicianName, VR.PN);
    mwlQuery.setNull(Tag.RequestedProcedureDescription, VR.LO);
    mwlQuery.setNull(Tag.RequestedProcedureID, VR.SH);
    mwlQuery.setNull(Tag.AccessionNumber, VR.LO);
    // TODO: handle this code-sequence.
    //  mwlQuery.setNull(Tag.RequestedProcedureCodeSequence, VR.SQ);

    assertThat(translator.buildQueryParameters(mwlQuery))
        .containsExactly(
            INCLUDEFIELD, "0040A370.0020000D" /* RRS.StudyInstanceUID */,
            INCLUDEFIELD, "0040A370.00321032" /* RRS.RequestingPhysician */,
            INCLUDEFIELD, "0040A370.00080090" /* RRS.ReferringPhysicianName */,
            INCLUDEFIELD, "0040A370.00321060" /* RRS.RequestedProcedureDescription */,
            INCLUDEFIELD, "0040A370.00401001" /* RRS.RequestedProcedureID */,
            INCLUDEFIELD, "0040A370.00080050" /* RRS.AccessionNumber */);
  }
}
