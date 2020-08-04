// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link UpsWorklistResponseParser}.
 */
@RunWith(JUnit4.class)
public final class UpsWorklistResponseParserTest {

  @Test
  public void parsesXmlWorklist() throws Exception {
    ImmutableList<Attributes> result =
        UpsWorklistResponseParser.parseUpsResponseToMwl(
            "multipart/related; boundary=BoUndaRy",
            readTestFile("ups_xml_multipart_response_body.txt"));

    Attributes firstResult = new Attributes();
    firstResult.setString(Tag.Modality, VR.CS, "OP");
    firstResult.setString(Tag.PatientName, VR.PN, "PATIENT^SAMPLE");
    firstResult.setString(Tag.PatientID, VR.LO, "PATIENT1234");
    firstResult.setString(Tag.PatientBirthDate, VR.DA, "20000201");
    firstResult.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5");
    firstResult.setString(Tag.PatientSex, VR.CS, "M");
    firstResult.setString(Tag.RequestingPhysician, VR.PN, "DOCTOR^REQUESTING");
    firstResult.setString(Tag.ReferringPhysicianName, VR.PN, "DOCTOR^REFERRING");
    firstResult.setString(Tag.RequestedProcedureDescription, VR.LO, "Take a picture of both eyes");
    firstResult.setString(Tag.RequestedProcedureID, VR.SH, "PROC1234");
    firstResult.setString(Tag.AccessionNumber, VR.SH, "ACC4321");

    Sequence firstSPSSeq =
        firstResult.ensureSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes firstSPSSeqAttr = new Attributes();
    firstSPSSeqAttr.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, "20200204");
    firstSPSSeqAttr.setString(Tag.ScheduledProcedureStepStartTime, VR.TM, "101010");
    firstSPSSeq.add(firstSPSSeqAttr);

    Attributes secondResult = new Attributes();
    secondResult.setString(Tag.Modality, VR.CS, "OPT");
    secondResult.setString(Tag.PatientName, VR.PN, "DOE^JANE");
    secondResult.setString(Tag.PatientID, VR.LO, "OTHERPATIENT123");
    secondResult.setString(Tag.PatientBirthDate, VR.DA, "19810408");
    secondResult.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.6");
    secondResult.setString(Tag.PatientSex, VR.CS, "F");
    Sequence secondSPSSeq =
        secondResult.ensureSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes secondSPSSeqAttr = new Attributes();
    secondSPSSeqAttr.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, "20200201");
    secondSPSSeq.add(secondSPSSeqAttr);

    assertThat(result).containsExactly(firstResult, secondResult);
  }

  @Test
  public void parsesEmptyXmlWorklist() throws Exception {
    assertThat(UpsWorklistResponseParser.parseUpsResponseToMwl(
        /* contentType=*/ "multipart/related; boundary=BoUndaRy",
        new ByteArrayInputStream(new byte[0])
    )).isEmpty();
  }

  @Test
  public void convertUpsResponseToMwl_yearOnlyDateTime() {
    Attributes attrs = new Attributes();
    Sequence seq = attrs
        .ensureSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes nestedAttrs = new Attributes();
    seq.add(nestedAttrs);
    nestedAttrs.setString(Tag.ScheduledProcedureStepStartDateTime, VR.DT, "2020");

    UpsWorklistResponseParser.convertUpsResponseToMwl(attrs);

    Attributes expectedNested = new Attributes();
    expectedNested.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, "20200101");
    assertThat(nestedAttrs).isEqualTo(expectedNested);
  }

  @Test
  public void convertUpsResponseToMwl_yearMonthOnlyDateTime() {
    Attributes attrs = new Attributes();
    Sequence seq = attrs
        .ensureSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes nestedAttrs = new Attributes();
    seq.add(nestedAttrs);
    nestedAttrs.setString(Tag.ScheduledProcedureStepStartDateTime, VR.DT, "202002");

    UpsWorklistResponseParser.convertUpsResponseToMwl(attrs);

    Attributes expectedNested = new Attributes();
    expectedNested.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, "20200201");
    assertThat(nestedAttrs).isEqualTo(expectedNested);
  }

  @Test
  public void convertUpsResponseToMwl_dateWithMidnightTime() {
    Attributes attrs = new Attributes();
    Sequence seq = attrs
      .ensureSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes nestedAttrs = new Attributes();
    seq.add(nestedAttrs);
    nestedAttrs.setString(Tag.ScheduledProcedureStepStartDateTime, VR.DT, "20200101000000.00");

    UpsWorklistResponseParser.convertUpsResponseToMwl(attrs);

    Attributes expectedNested = new Attributes();
    expectedNested.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, "20200101");
    assertThat(nestedAttrs).isEqualTo(expectedNested);
  }

  @Test
  public void convertUpsResponseToMwl_invalidDateDoesNotConvert() {
    Attributes attrs = new Attributes();
    Sequence seq = attrs
        .ensureSequence(Tag.ScheduledProcedureStepSequence, /* initialCapacity=*/ 1);
    Attributes nestedAttrs = new Attributes();
    seq.add(nestedAttrs);
    nestedAttrs.setString(Tag.ScheduledProcedureStepStartDateTime, VR.DT, "2020020");

    UpsWorklistResponseParser.convertUpsResponseToMwl(attrs);

    assertThat(nestedAttrs).isEqualTo(new Attributes());
  }

  private InputStream readTestFile(String filename) throws Exception {
    return getClass().getResourceAsStream(filename);
  }
}
