# TDR File Checks

The TDR File Checks Lambda function runs as part of the backend checks step function in the Transfer Digital Records (TDR).
It performs comprehensive file validation checks on files uploaded to the TDR system, ensuring that they meet the required standards for format,
integrity, and security before being processed further:

1. **DROID File Format Identification** - Identifies file formats using the DROID signature library and validates file type information
2. **Container Signature Detection** - Detects container formats (such as ZIP, TAR, etc.) using container signature files
3. **Malware Scanning** - Performs malware scanning on files using AWS GuardDuty to ensure files are free from threats
4. **Checksum Calculation** - Generates SHA256 checksums for file integrity verification and audit trails
5. **File Routing** - Routes scanned files to appropriate destinations (clean or quarantine buckets) based on scan results and threat status

## Usage

### Lambda Function Input

The Lambda function expects a JSON payload with the following structure:

```json
{
  "consignmentId": "uuid",
  "fileId": "uuid",
  "originalPath": "/path/to/file",
  "userId": "uuid",
  "s3SourceBucket": "source-bucket",
  "s3SourceBucketKey": "s3/object/key",
  "s3QuarantineBucket": "quarantine-bucket",
  "s3QuarantineBucketKey": "s3/quarantine/object/key",
  "s3CleanDestinationBucket": "clean-bucket",
  "s3CleanDestinationBucketKey": "s3/clean/object/key"
}
```

**Notes:**
- `consignmentId`, `fileId`, `originalPath`, `userId`, `s3SourceBucket`, `s3SourceBucketKey` - **Required** parameters
- `s3QuarantineBucket`, `s3QuarantineBucketKey`, `s3CleanDestinationBucket`, `s3CleanDestinationBucketKey` - **Optional** parameters
  - If destination buckets are not provided, files are not copied after scanning
  - At least one destination bucket pair should be provided for file routing to work correctly

### Lambda Function Output

The Lambda function returns a JSON response with file checks results:

```json
{
  "checksum": {
    "fileId": "uuid",
    "sha256Checksum": "hash-value"
  },
  "fileFormat": {
    "fileFormat": "format-identification",
    "fileFormatVersion": "version"
  },
  "antivirus": {
    "software": "GuardDuty",
    "softwareVersion": "version",
    "databaseVersion": "$LATEST",
    "result": "NO_THREATS_FOUND|THREATS_FOUND|UNSUPPORTED",
    "datetime": 1234567890,
    "fileId": "uuid"
  }
}
```

## File Routing Logic

Based on the malware scan result:

- **NO_THREATS_FOUND** → File is copied to `s3CleanDestinationBucket`
- **Any other result** → File is copied to `s3QuarantineBucket`

## Running Manually

You can run the Lambda function manually for testing and development purposes using the [LambdaRunner](src/main/scala/uk/gov/nationalarchives/filechecks/LambdaRunner.scala).

### Prerequisites for Running Locally

When running locally, you'll need to ensure:

1. **AWS Credentials** are configured:
   - Set `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables
   - Or use AWS credentials file (`~/.aws/credentials`)

2. **S3 Buckets Exist** in the AWS environment you are testing against:
   - Source bucket with the test file
   - Destination/quarantine buckets for file routing

3. **Environment Variables** are set:
   ```bash
   export AWS_REGION=eu-west-2
   export AWS_ACCESS_KEY_ID=your-access-key
   export AWS_SECRET_ACCESS_KEY=your-secret-key
   ```

4. **DROID and Container Signature Files**

   Download the signature files from the National Archives CDN and save them to `src/main/resources/`. Use the version numbers from the configuration:

   ```bash
   # Download DROID Signature File V{latest version} (check application.conf for current version)
   curl -L "https://cdn.nationalarchives.gov.uk/documents/DROID_SignatureFile_V{latest version}.xml" -o "src/main/resources/DROID_SignatureFile_V{latest version}.xml"

   # Download Container Signature File (check application.conf for current version)
   curl -L "https://cdn.nationalarchives.gov.uk/documents/container-signature-{latest version}.xml" -o "src/main/resources/container-signature-{latest version}.xml"
   ```
   
   **Note:** Update the version numbers in the curl commands and filenames to match the versions specified in `application.conf`.

## Module: tdr-file-checks-utils

The `tdr-file-checks-utils` is a shared utility module that provides reusable components for file checking operations.
This module is included as a dependency in the main Lambda function and exposes the core file validation logic.
