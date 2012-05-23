package ppl.dsl.optiml.matrix

import java.io.{PrintWriter}
import scala.reflect.SourceContext
import scala.virtualization.lms.common.DSLOpsExp
import scala.virtualization.lms.common.{VariablesExp, Variables}
import scala.virtualization.lms.common.{CudaGenBase, ScalaGenBase, CGenBase, OpenCLGenBase}
import scala.virtualization.lms.internal.{GenerationFailedException}
import ppl.delite.framework.DeliteApplication
import ppl.delite.framework.ops.DeliteOpsExp
import ppl.delite.framework.Config
import ppl.delite.framework.extern.lib._
import ppl.dsl.optiml._

trait OptiMLDenseMatrixOps extends ppl.dsl.optila.matrix.DenseMatrixOps {
  this: OptiML =>
  
  implicit def denseToMatOverrides[A:Manifest](x: Rep[DenseMatrix[A]]) = new OptiMLDenseMatOpsOverrides(x)  
}

trait ImageOpsExtension extends ImageOps {
  this: OptiML =>
  
  implicit def imageToMatOverrides[A:Manifest](x: Rep[Image[A]]) = new OptiMLImageOpsOverrides(x)  
}

trait MatrixOps extends ppl.dsl.optila.matrix.MatrixOps  {
  this: OptiML =>

  trait OptiMLMatOpsOverrides[A] extends MatOpsCls[A] {
    def apply(rowIndices: Interface[IndexVector])(implicit ctx: SourceContext) = matrix_apply_row_indices[A,IA,MA](x, rowIndices)
    def apply(rowIndices: Interface[IndexVector], colIndices: IndexWildcard)(implicit ctx: SourceContext) = matrix_apply_row_indices[A,IA,MA](x, rowIndices)
    def apply(rowIndices: IndexWildcard, colIndices: Interface[IndexVector])(implicit ctx: SourceContext) = matrix_apply_col_indices[A,IA,MA](x, colIndices)
    def apply(rowIndices: Interface[IndexVector], colIndices: Interface[IndexVector])(implicit ctx: SourceContext) = matrix_apply_block_indices[A,IA,MA](x, rowIndices, colIndices)    
  }
  
  class OptiMLDenseMatOpsOverrides[A:Manifest](x: Rep[DenseMatrix[A]]) extends DenseMatOpsCls(x) with OptiMLMatOpsOverrides[A] 
  class OptiMLImageOpsOverrides[A:Manifest](x: Rep[Image[A]]) extends ImageOpsCls(x) with OptiMLMatOpsOverrides[A] 

  // class defs
  def matrix_apply_row_indices[A:Manifest,I:Manifest,MA:Manifest](x: Interface[Matrix[A]], rowIndices: Interface[IndexVector])(implicit b: MatrixBuilder[A,I,MA], ctx: SourceContext): Rep[MA] 
  def matrix_apply_col_indices[A:Manifest,I:Manifest,MA:Manifest](x: Interface[Matrix[A]], colIndices: Interface[IndexVector])(implicit b: MatrixBuilder[A,I,MA], ctx: SourceContext): Rep[MA]   
  def matrix_apply_block_indices[A:Manifest,I:Manifest,MA:Manifest](x: Interface[Matrix[A]], rowIndices: Interface[IndexVector], colIndices: Interface[IndexVector])(implicit b: MatrixBuilder[A,I,MA], ctx: SourceContext): Rep[MA]  
}

trait MatrixOpsExp extends ppl.dsl.optila.matrix.MatrixOpsExp with MatrixOps with VariablesExp {
  this: OptiMLExp  =>
 
  ////////////////////////////////
  // implemented via delite ops

  case class MatrixApplyRowIndices[A:Manifest,I:Manifest,MA:Manifest](x: Interface[Matrix[A]], rowIndices: Interface[IndexVector])(implicit val b: MatrixBuilder[A,I,MA])
    extends DeliteOpSingleWithManifest2[A,I,MA](reifyEffectsHere(matrix_apply_row_indices_impl[A,I,MA](x,rowIndices)))       
  
  case class MatrixApplyColIndices[A:Manifest,I:Manifest,MA:Manifest](x: Interface[Matrix[A]], colIndices: Interface[IndexVector])(implicit val b: MatrixBuilder[A,I,MA])
    extends DeliteOpSingleWithManifest2[A,I,MA](reifyEffectsHere(matrix_apply_col_indices_impl[A,I,MA](x,colIndices)))       

  case class MatrixApplyBlockIndices[A:Manifest,I:Manifest,MA:Manifest](x: Interface[Matrix[A]], rowIndices: Interface[IndexVector], colIndices: Interface[IndexVector])(implicit val b: MatrixBuilder[A,I,MA])
    extends DeliteOpSingleWithManifest2[A,I,MA](reifyEffectsHere(matrix_apply_block_indices_impl[A,I,MA](x,rowIndices,colIndices)))       
  
  /////////////////////
  // class interface

  def matrix_apply_row_indices[A:Manifest,I:Manifest,MA:Manifest](x: Interface[Matrix[A]], rowIndices: Interface[IndexVector])(implicit b: MatrixBuilder[A,I,MA], ctx: SourceContext)
    = reflectPure(MatrixApplyRowIndices[A,I,MA](x,rowIndices))
  def matrix_apply_col_indices[A:Manifest,I:Manifest,MA:Manifest](x: Interface[Matrix[A]], colIndices: Interface[IndexVector])(implicit b: MatrixBuilder[A,I,MA], ctx: SourceContext)
    = reflectPure(MatrixApplyColIndices[A,I,MA](x,colIndices))
  def matrix_apply_block_indices[A:Manifest,I:Manifest,MA:Manifest](x: Interface[Matrix[A]], rowIndices: Interface[IndexVector], colIndices: Interface[IndexVector])(implicit b: MatrixBuilder[A,I,MA], ctx: SourceContext)
    = reflectPure(MatrixApplyBlockIndices[A,I,MA](x,rowIndices,colIndices))

  //////////////
  // mirroring

  override def mirror[A:Manifest](e: Def[A], f: Transformer)(implicit ctx: SourceContext): Exp[A] = (e match {
    case e@MatrixApplyRowIndices(x,y) => reflectPure(new { override val original = Some(f,e) } with MatrixApplyRowIndices(f(x),f(y))(e.mA,e.mB,e.mR,e.b))(mtype(manifest[A]),implicitly[SourceContext])  
    case e@MatrixApplyColIndices(x,y) => reflectPure(new { override val original = Some(f,e) } with MatrixApplyColIndices(f(x),f(y))(e.mA,e.mB,e.mR,e.b))(mtype(manifest[A]),implicitly[SourceContext])      
    case e@MatrixApplyBlockIndices(x,r,c) => reflectPure(new { override val original = Some(f,e) } with MatrixApplyBlockIndices(f(x),f(r),f(c))(e.mA,e.mB,e.mR,e.b))(mtype(manifest[A]),implicitly[SourceContext])        
    case Reflect(e@MatrixApplyRowIndices(x,y), u, es) => reflectMirrored(Reflect(new { override val original = Some(f,e) } with MatrixApplyRowIndices(f(x),f(y))(e.mA,e.mB,e.mR,e.b), mapOver(f,u), f(es)))(mtype(manifest[A]))    
    case Reflect(e@MatrixApplyColIndices(x,y), u, es) => reflectMirrored(Reflect(new { override val original = Some(f,e) } with MatrixApplyColIndices(f(x),f(y))(e.mA,e.mB,e.mR,e.b), mapOver(f,u), f(es)))(mtype(manifest[A]))    
    case Reflect(e@MatrixApplyBlockIndices(x,r,c), u, es) => reflectMirrored(Reflect(new { override val original = Some(f,e) } with MatrixApplyBlockIndices(f(x),f(r),f(c))(e.mA,e.mB,e.mR,e.b), mapOver(f,u), f(es)))(mtype(manifest[A]))        
    case _ => super.mirror(e, f)
  }).asInstanceOf[Exp[A]] // why??

  
}

/**
 *  Optimizations for composite MatrixOps operations.
 */

trait MatrixOpsExpOpt extends ppl.dsl.optila.matrix.MatrixOpsExpOpt with MatrixOpsExp {
  this: OptiMLExp =>

  // override def matrix_numrows[A:Manifest](x: Exp[Matrix[A]])(implicit ctx: SourceContext) = x match {
  //   //case Def(TrainingSetObjectFromMat(x,y)) => matrix_numrows(x) // TODO: move to TrainingSetOpsExpOpt ?
  //   case _ => super.matrix_numrows(x)
  // }
  // 
  // override def matrix_numcols[A:Manifest](x: Exp[Matrix[A]])(implicit ctx: SourceContext) = x match {
  //   //case Def(TrainingSetObjectFromMat(x,y)) => matrix_numcols(x) // TODO: move to TrainingSetOpsExpOpt ?
  //   case _ => super.matrix_numcols(x)
  // }
}


trait ScalaGenMatrixOps extends ScalaGenBase {
  val IR: MatrixOpsExp
  import IR._

  // override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {  
  //   case _ => super.emitNode(sym, rhs)
  // }
}

trait CudaGenMatrixOps extends CudaGenBase with CudaGenDataStruct {
  val IR: MatrixOpsExp
  import IR._

  // override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
  //   case _ => super.emitNode(sym, rhs)
  // }
}

trait CGenMatrixOps extends CGenBase {
  val IR: MatrixOpsExp
  import IR._

  // override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
  //   case _ => super.emitNode(sym, rhs)
  // }
}
