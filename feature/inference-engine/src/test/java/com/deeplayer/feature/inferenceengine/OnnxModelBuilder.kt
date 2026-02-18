package com.deeplayer.feature.inferenceengine

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Builds minimal valid ONNX model files for testing. Creates raw protobuf bytes matching the ONNX
 * ModelProto schema without requiring the onnx Python package or protobuf compiler.
 *
 * The generated model is a simple Identity operation: input(float[1,N]) -> output(float[1,N]).
 */
object OnnxModelBuilder {

  /**
   * Create a minimal ONNX model file with an Identity op.
   *
   * @param outputFile the file to write the model to
   * @param inputSize the size of the second dimension (model maps float[1,inputSize] ->
   *   float[1,inputSize])
   */
  fun createIdentityModel(outputFile: File, inputSize: Int = 4) {
    outputFile.writeBytes(buildIdentityModelBytes(inputSize))
  }

  /**
   * Create a minimal ONNX model file with an Add op that adds input to itself.
   *
   * @param outputFile the file to write the model to
   * @param inputSize the size of the second dimension
   */
  fun createAddModel(outputFile: File, inputSize: Int = 4) {
    outputFile.writeBytes(buildAddModelBytes(inputSize))
  }

  private fun buildIdentityModelBytes(inputSize: Int): ByteArray {
    val graph =
      buildGraph(
        name = "test_identity",
        nodes = listOf(buildNode(opType = "Identity", inputs = listOf("X"), outputs = listOf("Y"))),
        inputs = listOf(buildValueInfo("X", floatTensorType(1, inputSize))),
        outputs = listOf(buildValueInfo("Y", floatTensorType(1, inputSize))),
      )
    return buildModel(graph)
  }

  private fun buildAddModelBytes(inputSize: Int): ByteArray {
    val graph =
      buildGraph(
        name = "test_add",
        nodes = listOf(buildNode(opType = "Add", inputs = listOf("X", "X"), outputs = listOf("Y"))),
        inputs = listOf(buildValueInfo("X", floatTensorType(1, inputSize))),
        outputs = listOf(buildValueInfo("Y", floatTensorType(1, inputSize))),
      )
    return buildModel(graph)
  }

  // --- Protobuf encoding helpers ---
  // ONNX uses standard protobuf encoding. We manually encode the required fields.

  private fun buildModel(graph: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    // ir_version = 8 (field 1, varint)
    out.writeField(1, WIRETYPE_VARINT)
    out.writeVarint(8)
    // opset_import (field 8, length-delimited)
    val opset = buildOpsetImport(version = 19)
    out.writeField(8, WIRETYPE_LENGTH_DELIMITED)
    out.writeBytes(opset)
    // graph (field 7, length-delimited)
    out.writeField(7, WIRETYPE_LENGTH_DELIMITED)
    out.writeBytes(graph)
    return out.toByteArray()
  }

  private fun buildOpsetImport(version: Int): ByteArray {
    val out = ByteArrayOutputStream()
    // version (field 2, varint)
    out.writeField(2, WIRETYPE_VARINT)
    out.writeVarint(version)
    return out.toByteArray()
  }

  private fun buildGraph(
    name: String,
    nodes: List<ByteArray>,
    inputs: List<ByteArray>,
    outputs: List<ByteArray>,
  ): ByteArray {
    val out = ByteArrayOutputStream()
    // node (field 1, length-delimited, repeated)
    for (node in nodes) {
      out.writeField(1, WIRETYPE_LENGTH_DELIMITED)
      out.writeBytes(node)
    }
    // name (field 2, length-delimited)
    out.writeField(2, WIRETYPE_LENGTH_DELIMITED)
    out.writeString(name)
    // input (field 11, length-delimited, repeated)
    for (input in inputs) {
      out.writeField(11, WIRETYPE_LENGTH_DELIMITED)
      out.writeBytes(input)
    }
    // output (field 12, length-delimited, repeated)
    for (output in outputs) {
      out.writeField(12, WIRETYPE_LENGTH_DELIMITED)
      out.writeBytes(output)
    }
    return out.toByteArray()
  }

  private fun buildNode(opType: String, inputs: List<String>, outputs: List<String>): ByteArray {
    val out = ByteArrayOutputStream()
    // input (field 1, length-delimited, repeated)
    for (input in inputs) {
      out.writeField(1, WIRETYPE_LENGTH_DELIMITED)
      out.writeString(input)
    }
    // output (field 2, length-delimited, repeated)
    for (output in outputs) {
      out.writeField(2, WIRETYPE_LENGTH_DELIMITED)
      out.writeString(output)
    }
    // op_type (field 4, length-delimited)
    out.writeField(4, WIRETYPE_LENGTH_DELIMITED)
    out.writeString(opType)
    return out.toByteArray()
  }

  private fun buildValueInfo(name: String, type: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    // name (field 1, length-delimited)
    out.writeField(1, WIRETYPE_LENGTH_DELIMITED)
    out.writeString(name)
    // type (field 2, length-delimited)
    out.writeField(2, WIRETYPE_LENGTH_DELIMITED)
    out.writeBytes(type)
    return out.toByteArray()
  }

  private fun floatTensorType(vararg dims: Int): ByteArray {
    val shape = buildTensorShape(dims.toList())
    val tensor = ByteArrayOutputStream()
    // elem_type (field 1, varint) - 1 = FLOAT
    tensor.writeField(1, WIRETYPE_VARINT)
    tensor.writeVarint(1)
    // shape (field 2, length-delimited)
    tensor.writeField(2, WIRETYPE_LENGTH_DELIMITED)
    tensor.writeBytes(shape)
    val tensorBytes = tensor.toByteArray()

    val typeProto = ByteArrayOutputStream()
    // tensor_type (field 1, length-delimited)
    typeProto.writeField(1, WIRETYPE_LENGTH_DELIMITED)
    typeProto.writeBytes(tensorBytes)
    return typeProto.toByteArray()
  }

  private fun buildTensorShape(dims: List<Int>): ByteArray {
    val out = ByteArrayOutputStream()
    for (dim in dims) {
      val dimProto = ByteArrayOutputStream()
      // dim_value (field 1, varint)
      dimProto.writeField(1, WIRETYPE_VARINT)
      dimProto.writeVarint(dim)
      out.writeField(1, WIRETYPE_LENGTH_DELIMITED)
      out.writeBytes(dimProto.toByteArray())
    }
    return out.toByteArray()
  }

  // --- Low-level protobuf wire format ---

  private const val WIRETYPE_VARINT = 0
  private const val WIRETYPE_LENGTH_DELIMITED = 2

  private fun ByteArrayOutputStream.writeField(fieldNumber: Int, wireType: Int) {
    writeVarint((fieldNumber shl 3) or wireType)
  }

  private fun ByteArrayOutputStream.writeVarint(value: Int) {
    var v = value
    while (v and 0x7F.inv() != 0) {
      write((v and 0x7F) or 0x80)
      v = v ushr 7
    }
    write(v)
  }

  private fun ByteArrayOutputStream.writeString(s: String) {
    val bytes = s.toByteArray(Charsets.UTF_8)
    writeVarint(bytes.size)
    write(bytes)
  }

  private fun ByteArrayOutputStream.writeBytes(bytes: ByteArray) {
    writeVarint(bytes.size)
    write(bytes)
  }
}
