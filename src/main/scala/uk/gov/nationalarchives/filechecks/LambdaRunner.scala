package uk.gov.nationalarchives.filechecks

import org.apache.commons.io.output.ByteArrayOutputStream

import java.io.ByteArrayInputStream

object LambdaRunner extends App {
  private val body = """
    |{
    |  "userId": "d4104dff-19a2-412e-924a-e59469102fb6",
    |  "consignmentId": "b8caaf81-5ee4-4f0c-a61d-414a20228c16",
    |  "fileId": "44e78491-c85c-44be-888d-fc54acfbc79e",
    |  "originalPath": "object-key.csv",
    |  "s3SourceBucket": "testbucket",
    |  "s3SourceBucketKey": "Test.docx",
    |  "s3QuarantineBucket": "quarantineBucket",
    |  "s3QuarantineBucketKey": "Test.docx"
    |}
    |""".stripMargin

  val inputStream = new ByteArrayInputStream(body.getBytes())
  private val output = new ByteArrayOutputStream()
  new Lambda().process(inputStream, output)
  println(output.toByteArray.map(_.toChar).mkString)
}
