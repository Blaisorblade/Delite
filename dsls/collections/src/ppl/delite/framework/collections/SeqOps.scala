package ppl.delite.framework.collections



import ppl.delite.framework.{DeliteApplication, DSLType}
import scala.virtualization.lms.common._
import ppl.delite.framework.collections.datastruct.scala._
import ppl.delite.framework.datastruct.scala.DeliteCollection
import java.io.PrintWriter



trait SeqOps extends TraversableOps {
  
  /* ctors */
  // TODO: rename to Seq
  object Sequence {
  }
  
  /* lifting */
  implicit def seqrep2traversableops[T: Manifest](t: Rep[Seq[T]]) = new TraversableClsOps[T, Seq[T]](t)
  
  /* class interface defs */
  
  /* implicit rules */
  implicit def seqCanBuild[T: Manifest, S: Manifest, Target <: DeliteCollection[S]: Manifest]: CanBuild[Seq[T], S, Seq[S]]
  
}


trait SeqOpsExp extends TraversableOpsExp {
self: ArraySeqOpsExp =>
  
  /* nodes */
  
  /* class interface */
  
  /* implicit rules */
  implicit def seqCanBuild[T: Manifest, S: Manifest]: CanBuild[Seq[T], S, Seq[S]] = new CanBuild[Seq[T], S, Seq[S]] {
    def alloc(source: Exp[Seq[T]]) = ArraySeq.apply[S](seqrep2traversableops(source).size)
    def emitter(source: Exp[Seq[T]]): Emitter[Seq[S]] = null
  }
  
}


trait ScalaGenSeqOps extends ScalaGenTraversableOps {
  val IR: SeqOpsExp
  import IR._
  
  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
    // these are the ops that call through to the underlying real data structure
    case _ => super.emitNode(sym, rhs)
  }
  
}

