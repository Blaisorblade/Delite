package ppl.delite.runtime.codegen.kernels.scala

import ppl.delite.runtime.graph.ops.OP_MultiLoop
import ppl.delite.runtime.graph.DeliteTaskGraph
import ppl.delite.runtime.codegen.{Profiler, ExecutableGenerator, ScalaCompile}

/**
 * Author: Kevin J. Brown
 * Date: Nov 17, 2010
 * Time: 9:00:34 PM
 *
 * Pervasive Parallelism Laboratory (PPL)
 * Stanford University
 */

/**
 * Creates a chunk for OP_MultiLoop and generates an executable kernel for that chunk
 * The generated kernels are designed to run in parallel on multiple threads in an SMP system
 * This implementation of MultiLoop is optimized for a DSL collection that is backed by an Array
 */

object MultiLoop_SMP_Array_Generator {

  def makeChunk(op: OP_MultiLoop, chunkIdx: Int, numChunks: Int, kernelPath: String): OP_MultiLoop = {
    val chunk = if (chunkIdx == 0) op else op.chunk(chunkIdx)
    val src = makeKernel(chunk, op, chunkIdx, numChunks, kernelPath)
    ScalaCompile.addSource(src)
    chunk
  }

  private def makeKernel(op: OP_MultiLoop, master: OP_MultiLoop, chunkIdx: Int, numChunks: Int, kernelPath: String) = {
    val out = new StringBuilder

    //update the op with this kernel
    updateOP(op, master, chunkIdx)

    //the header
    writeHeader(out, master, chunkIdx, kernelPath)

    //the kernel
    writeKernel(out, op, master, chunkIdx, numChunks)

    //the footer
    out.append("}\n")

    out.toString
  }

  private def updateOP(op: OP_MultiLoop, master: OP_MultiLoop, idx: Int) {
    op.setKernelName(kernelName(master, idx))
  }

  private def writeHeader(out: StringBuilder, master: OP_MultiLoop, idx: Int, kernelPath: String) {
    ExecutableGenerator.writePath(kernelPath, out)
    out.append("object ")
    out.append(kernelName(master, idx))
    out.append(" {\n")
  }

  private def writeKernel(out: StringBuilder, op: OP_MultiLoop, master: OP_MultiLoop, chunkIdx: Int, numChunks: Int) {
    out.append("def apply(head: ")
    out.append(op.getInputs.head._1.outputType)
    out.append("): ")
    out.append(op.outputType)
    out.append(" = {\n")

    //tree reduction
    //first every chunk performs its primary (map-)reduction
    out.append("val size = head.closure.size\n")
    out.append("val out = head.out\n")
    out.append("var idx = size*")
    out.append(chunkIdx)
    out.append('/')
    out.append(numChunks)
    out.append('\n')
    out.append("val end = size*")
    out.append(chunkIdx+1)
    out.append('/')
    out.append(numChunks)
    out.append('\n')

    Profiler.emitParallelOPTimerHeader(out, kernelName(master, chunkIdx))
    
    if (chunkIdx == 0)
      out.append("val acc = out\n")
    else
      out.append("val acc = head.closure.split(out)\n") // copy of out per chunk
    out.append("while (idx < end) {\n")
    out.append("head.closure.process(acc, idx)\n")
    out.append("idx += 1\n")
    out.append("}\n")

    Profiler.emitParallelTimerTailer(out, kernelName(master, chunkIdx))

    if (!op.needsCombine) {
      if (chunkIdx == 0) out.append("acc\n")
    } else {
      var half = chunkIdx
      var step = 1
      while ((half % 2 == 0) && (chunkIdx + step < numChunks)) { //half the chunks quit each iteration
        half = half / 2
        val neighbor = chunkIdx + step //the index of the chunk to reduce with
        step *= 2

        out.append("head.closure.combine(acc, head.get")
        out.append(neighbor)
        out.append(")\n")
      }
      if (chunkIdx == 0) { //chunk 0 returns result
        out.append("acc\n")
      }
      else { //other chunks store result
        out.append("head.set")
        out.append(chunkIdx)
        out.append("(acc)\n")
      }
    }

    out.append("}\n")
  }

  private def kernelName(master: OP_MultiLoop, idx: Int) = {
    "MultiLoop_SMP_Array_" + master.id + "_Chunk_" + idx
  }

}

object MultiLoop_SMP_Array_Header_Generator {

  def makeHeader(op: OP_MultiLoop, numChunks: Int, graph: DeliteTaskGraph) = {
    val out = new StringBuilder

    //the header
    writeObject(out, op, graph.kernelPath)

    //the kernel
    writeClass(out, op)

    if (op.needsCombine) {
      //the sync state
      for (i <- 1 until numChunks) //sync for all chunks except 0
        writeSync(out, i, op.outputType)
    }
    
    //the footer
    out.append("}\n")

    //add header for compilation
    val src = out.toString
    ScalaCompile.addSource(src)

    //return header OP
    op.header(kernelName(op), graph)
  }

  private def writeObject(out: StringBuilder, op: OP_MultiLoop, kernelPath: String) {
    ExecutableGenerator.writePath(kernelPath, out)
    out.append("object ")
    out.append(kernelName(op))
    out.append(" {\n")
    writeObjectApply(out, op)
    out.append("}\n")
  }

  private def writeObjectApply(out: StringBuilder, op: OP_MultiLoop) {
    out.append("def apply(")
    var inIdx = 0
    var first = true
    for ((input, name) <- op.getInputs) {
      if (!first) out.append(", ")
      first = false
      out.append("in")
      out.append(inIdx)
      inIdx += 1
      out.append(": ")
      out.append(input.outputType(name))
    }
    out.append(") = new ")
    out.append(kernelName(op))
    out.append("(")
    for (i <- 0 until inIdx) {
      if (i > 0) out.append(", ")
      out.append("in")
      out.append(i)
    }
    out.append(")\n")
  }

  private def writeClass(out: StringBuilder, op: OP_MultiLoop) {
    out.append("final class ")
    out.append(kernelName(op))
    out.append("(")
    var inIdx = 0
    var first = true
    for ((input, name) <- op.getInputs) {
      if (!first) out.append(", ")
      first = false
      out.append("in")
      out.append(inIdx)
      inIdx += 1
      out.append(": ")
      out.append(input.outputType(name))
    }
    out.append(") {\n")

    out.append("val closure = ")
    out.append(op.function)
    out.append("(")
    for (i <- 0 until inIdx) {
      if (i > 0) out.append(", ")
      out.append("in")
      out.append(i)
    }
    out.append(")\n")

    out.append("val out: ")
    out.append(op.outputType)
    out.append(" = closure.alloc\n")
  }

  private def writeSync(out: StringBuilder, chunkIdx: Int, outputType: String) {
    out.append("@volatile private var notReady")
    out.append(chunkIdx)
    out.append(": Boolean = true\n")

    out.append("private var _result")
    out.append(chunkIdx)
    out.append(" : ")
    out.append(outputType)
    out.append(" = _\n")

    out.append("def get")
    out.append(chunkIdx)
    out.append(": ")
    out.append(outputType)
    out.append(" = { while (notReady")
    out.append(chunkIdx)
    out.append(") { }; _result")
    out.append(chunkIdx)
    out.append(" }\n")

    out.append("def set")
    out.append(chunkIdx)
    out.append("(result: ")
    out.append(outputType)
    out.append(") { _result")
    out.append(chunkIdx)
    out.append(" = result; notReady")
    out.append(chunkIdx)
    out.append(" = false }\n")
  }
  
  private def kernelName(op: OP_MultiLoop) = {
    "MultiLoop_SMP_Array_Header" + op.id
  }
}
