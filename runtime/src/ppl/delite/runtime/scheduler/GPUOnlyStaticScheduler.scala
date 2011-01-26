package ppl.delite.runtime.scheduler

import ppl.delite.runtime.graph.DeliteTaskGraph
import java.util.ArrayDeque
import ppl.delite.runtime.Config
import ppl.delite.runtime.graph.targets.Targets
import ppl.delite.runtime.graph.ops._

/**
 * Author: Kevin J. Brown
 * Date: Dec 4, 2010
 * Time: 1:43:32 AM
 *
 * Pervasive Parallelism Laboratory (PPL)
 * Stanford University
 */

/**
 * A simply static scheduler that mimics the classic CUDA programming model
 * All OPs that can be executed on the GPU do so
 * The remaining ops execute in a single CPU thread
 */
final class GPUOnlyStaticScheduler extends StaticScheduler {

  private val cpu = 0
  private val gpu = 1

  def schedule(graph: DeliteTaskGraph) {
    assert(Config.numThreads == 1 && Config.numGPUs == 1)
    scheduleFlat(graph)
  }

  protected def scheduleFlat(graph: DeliteTaskGraph) {
    val opQueue = new ArrayDeque[DeliteOP]
    val schedule = PartialSchedule(2)
    enqueueRoots(graph, opQueue)
    while (!opQueue.isEmpty) {
      val op = opQueue.remove
      scheduleOne(op, graph, schedule)
      enqueueRoots(graph, opQueue)
    }
    ensureScheduled(graph)
    graph.schedule = schedule
  }

  protected def scheduleOne(op: DeliteOP, graph: DeliteTaskGraph, schedule: PartialSchedule) {
    op match {
      case c: OP_Control => addNested(c, graph, schedule, Seq(cpu,gpu))
      case _ => {
        if (op.supportsTarget(Targets.Cuda)) { //schedule on GPU resource
          if (op.isDataParallel) {
            splitGPU(op, schedule)
          }
          else {
            schedule(gpu).add(op)
            op.scheduledResource = gpu
          }
        }
        else if (op.variant != null) { //kernel could be partially GPUable
          addNested(op.variant, graph, schedule, Seq(cpu,gpu))
        }
        else { //schedule on CPU resource
          if (op.isDataParallel) {
            split(op, graph, schedule, Seq(cpu))
          }
          else {
            schedule(cpu).add(op)
            op.scheduledResource = cpu
          }
        }
        op.isScheduled = true
      }
    }
  }

  private def splitGPU(op: DeliteOP, schedule: PartialSchedule) {
    val chunk = OpHelper.splitGPU(op)
    schedule(gpu).add(chunk)
    chunk.scheduledResource = gpu
    chunk.isScheduled = true
  }

}
