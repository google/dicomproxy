// Copyright 2020 Google LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, you can obtain one at http://mozilla.org/MPL/2.0/.
package com.google.health.dicomproxy;

import java.io.File;

/** Interface to decouple DICOM receiver from DICOMweb uploader. */
interface DicomUploader {
  /** Upload the files directly contained in the specified directory. */
  void uploadDirectory(File directoryToUpload);
}
