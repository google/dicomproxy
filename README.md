## Disclaimer

This is not an official Google product. DICOMweb is a trademark of the National
Electrical Manufacturers Association, Secretariat for the DICOM Standards
Committee.

# DICOMweb Proxy

A simple proxy from legacy DIMSE DICOM network protocols to DICOMweb requests.
Currently supported are C-STORE to STOW-RS (for data uploads) and C-FIND
Modality Worklist to UPS-RS (for worklist queries).

## Authentication

OAuth2 Bearer tokens are used to authenticate with the receiving service; we
currently use Google Cloud Service Account credentials to create OAuth tokens,
using the OAuth scope https://www.googleapis.com/auth/lifescience.dicomweb.

To use such a service account, follow the instructions for
[Creating a service account](https://developers.google.com/identity/protocols/OAuth2ServiceAccount#creatinganaccount),
and download the private key in JSON format. Save it to a file named
service\_account\_creds.json in this folder.

## Launching the Proxy

### Configuration Parameters

*   Receive Port: defaults to `4008`. May be overridden by specifying the
    `com.google.health.dicomproxy.receive-port` system property. This is used for both
    C-STORE and C-FIND (MWL) requests.
*   DICOMweb URIs. The proxy expects at least one URI to be specified (for
    Upload and/or Worklist mode).
    *   Upload URI: This is specified by the `com.google.health.dicomproxy.upload-uri`
        system property. Example URI would be
        https://medicalimaging.googleapis.com/v1/dicom/projects/<your-project>/programs/<your-program>/studies.
    *   Worklist URI: This is specified by the
        `com.google.health.dicomproxy.worklist-uri` system property. Example URI would
        be
        https://medicalimaging.googleapis.com/v1/dicom/projects/<your-project>/programs/<your-program>/workitems.
*   Temporary storage location: defaults to `temp-data` in the current
    directory. May be overridden by specifying the
    `com.google.health.dicomproxy.temp-folder` system property.
*   Service Account Credentials file: Defaults to `service_account_creds.json`
    in the current directory. May be overridden by specifying the
    `com.google.health.dicomproxy.service-account-creds-json-file` system property.

### Sample Command Line

If running on a Unix-type system (this includes Linux and Mac), this will run
the proxy with sample destination:

```bash
./gradlew run \
    -Dcom.google.health.dicomproxy.upload-uri=https://medicalimaging.googleapis.com/projects/sample-project/programs/sample-program/studies \
    -Dcom.google.health.dicomproxy.worklist-uri=https://medicalimaging.googleapis.com/projects/sample-project/programs/sample-program/worklist \
    -Dcom.google.health.dicomproxy.temp-folder=/tmp/dicomweb-data/ \
    -Dcom.google.health.dicomproxy.receive-port=12345 \
    -Dcom.google.health.dicomproxy.service-account-creds-json-file=my_service_account.json
```

## Known Issues / Future Improvements

This proxy should currently be thought of as a working prototype; it has not
been hardened for long-term production uses. Patches are welcome! This is a
short list of some items which should be considered before deploying this for
real-world use cases.

### Authentication and API Support

The authentication used by this proxy is currently targeted to the DICOMweb APIs
hosted at medicalimaging.googleapis.com. If you are working with a different
DICOMweb implementation, you may want to modify the code in this package to fit
your requirements.

### Data Management / Cleanup

This proxy doesn't yet handle cleanup / removal of temporary data that is no longer
needed. It also doesn't yet have a way, upon restart, to resume uploads that
were incomplete or queued (in the case of a crash).

### Configuration / Installation

There is not yet an installation procedure defined for this software, and all
configuration is done via command-line (Java System Property) parameters.

### Logging

Currently all log statements are simply printed to the console.

### AE Title Support

The DICOM proxy software does not currently care about DICOM AE titles; it is a
so-called "promiscuous receiver", and will accept any valid connections
regardless of called or calling AE titles. Future improvements (patches are
welcome!) may include mapping AE titles to upload or query destinations, to
support proxying to multiple DICOMweb instances.
