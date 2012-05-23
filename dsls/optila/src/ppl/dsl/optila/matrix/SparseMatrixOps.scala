package ppl.dsl.optila.matrix

import java.io.{PrintWriter}

import scala.virtualization.lms.common.DSLOpsExp
import scala.virtualization.lms.common.{VariablesExp, Variables}
import scala.virtualization.lms.common.{CudaGenBase, ScalaGenBase, OpenCLGenBase, CGenBase}
import scala.virtualization.lms.internal.{GenerationFailedException}
import scala.reflect.SourceContext

import ppl.delite.framework.DeliteApplication
import ppl.delite.framework.datastruct.scala.DeliteCollection
import ppl.delite.framework.datastructures.DeliteArray
import ppl.delite.framework.ops.{DeliteOpsExp, DeliteCollectionOpsExp}
import ppl.delite.framework.Config
import ppl.delite.framework.extern.lib._
import ppl.delite.framework.Util._

import ppl.dsl.optila._

trait SparseMatrixOps extends Variables {
  this: OptiLA =>
  
  implicit def repToSparseMatOps[A:Manifest](x: Rep[SparseMatrix[A]]) = new SparseMatOpsCls(x)
  implicit def varToSparseMatOps[A:Manifest](x: Var[SparseMatrix[A]]) = new SparseMatOpsCls(readVar(x))  
  implicit def sparseMatToInterface[A:Manifest](lhs: Rep[SparseMatrix[A]]) = new MInterface[A](new SparseMatOpsCls[A](lhs))
  implicit def sparseMatVarToInterface[A:Manifest](lhs: Var[SparseMatrix[A]]) = new MInterface[A](new SparseMatOpsCls[A](readVar(lhs)))
  
  class SparseMatOpsCls[A:Manifest](val elem: Rep[SparseMatrix[A]]) extends MatOpsCls[A] {
    type M[X] = SparseMatrix[X]
    type V[X] = SparseVector[X]
    type I[X] = SparseMatrixBuildable[X]
    type Self = SparseMatrix[A]

    def mA: Manifest[A] = manifest[A]
    def mM[B:Manifest]: Manifest[M[B]] = manifest[SparseMatrix[B]]    
    def mI[B:Manifest]: Manifest[I[B]] = manifest[SparseMatrixBuildable[B]]
    def wrap(x: Rep[SparseMatrix[A]]): Interface[Matrix[A]] = sparseMatToInterface(x)
    def toOps[B:Manifest](x: Rep[M[B]]): MatOpsCls[B] = repToSparseMatOps[B](x)
    def toIntf[B:Manifest](x: Rep[M[B]]): Interface[Matrix[B]] = sparseMatToInterface[B](x)        
    def builder[B:Manifest](implicit ctx: SourceContext): MatrixBuilder[B,I[B],M[B]] = sparseMatrixBuilder[B]            
    def mV[B:Manifest]: Manifest[V[B]] = manifest[SparseVector[B]]
    def vecToIntf[B:Manifest](x: Rep[V[B]]): Interface[Vector[B]] = sparseVecToInterface[B](x)        
    def vecBuilder[B:Manifest](implicit ctx: SourceContext): VectorBuilder[B,V[B]] = sparseVectorBuilder[B]
    
    // delite collection
    def dcSize(implicit ctx: SourceContext): Rep[Int] = x.size
    def dcApply(n: Rep[Int])(implicit ctx: SourceContext): Rep[A] = sparsematrix_apply(x,n/x.numCols,n%x.numCols)
    def dcUpdate(n: Rep[Int], y: Rep[A])(implicit ctx: SourceContext): Rep[Unit] = sparsematrix_update(x,n/x.numCols,n%x.numCols,y)
    
    // accessors
    def apply(i: Rep[Int], j: Rep[Int])(implicit ctx: SourceContext) = sparsematrix_apply(x,i,j)
    def numRows(implicit ctx: SourceContext) = sparsematrix_numrows(x)
    def numCols(implicit ctx: SourceContext) = sparsematrix_numcols(x)
    def nnz(implicit ctx: SourceContext) = sparsematrix_nnz(elem)
    def vview(start: Rep[Int], stride: Rep[Int], length: Rep[Int], isRow: Rep[Boolean])(implicit ctx: SourceContext) = sparsematrix_vview(x,start,stride,length,isRow)
    
    // data operations
    // delite ops that use the fallback read from CSR matrices and write to COO matrices
    // def update(i: Rep[Int], j: Rep[Int], y: Rep[A])(implicit ctx: SourceContext) = err("SparseMatrix is immutable")
    // def insertRow(pos: Rep[Int], y: Rep[SparseVector[A]])(implicit ctx: SourceContext) = err("SparseMatrix is immutable")
    // def insertAllRows(pos: Rep[Int], y: Rep[SparseMatrix[A]])(implicit ctx: SourceContext) = err("SparseMatrix is immutable")
    // def insertCol(pos: Rep[Int], y: Rep[SparseVector[A]])(implicit ctx: SourceContext) = err("SparseMatrix is immutable")
    // def insertAllCols(pos: Rep[Int], y: Rep[SparseMatrix[A]])(implicit ctx: SourceContext) = err("SparseMatrix is immutable")
    // def removeRows(pos: Rep[Int], len: Rep[Int])(implicit ctx: SourceContext) = err("SparseMatrix is immutable")
    // def removeCols(pos: Rep[Int], len: Rep[Int])(implicit ctx: SourceContext) = err("SparseMatrix is immutable")
    
    // not supported by interface right now
    def *(y: Rep[MA])(implicit a: Arith[A], ctx: SourceContext): Rep[MA] = sparsematrix_multiply(x,y)
    def inv(implicit conv: Rep[A] => Rep[Double], ctx: SourceContext) = sparsematrix_inverse(x)    
    def mapRows[B:Manifest](f: Rep[VectorView[A]] => Rep[SparseVector[B]])(implicit ctx: SourceContext) = sparsematrix_maprows(x,f)
    def reduceRows(f: (Rep[SparseVector[A]],Rep[VectorView[A]]) => Rep[SparseVector[A]])(implicit ctx: SourceContext): Rep[SparseVector[A]] = sparsematrix_reducerows(x,f)
    
    // overrides
    def *(y: Rep[SparseVector[A]])(implicit a: Arith[A], o: Overloaded1, ctx: SourceContext): Rep[SparseVector[A]] = sparsematrix_times_vector(x,y)
  }
  
  // class defs
  def sparsematrix_apply[A:Manifest](x: Rep[SparseMatrix[A]], i: Rep[Int], j: Rep[Int])(implicit ctx: SourceContext): Rep[A]
  def sparsematrix_update[A:Manifest](x: Rep[SparseMatrix[A]], i: Rep[Int], j: Rep[Int], y: Rep[A])(implicit ctx: SourceContext): Rep[Unit]
  def sparsematrix_numrows[A:Manifest](x: Rep[SparseMatrix[A]])(implicit ctx: SourceContext): Rep[Int]
  def sparsematrix_numcols[A:Manifest](x: Rep[SparseMatrix[A]])(implicit ctx: SourceContext): Rep[Int]
  def sparsematrix_nnz[A:Manifest](x: Rep[SparseMatrix[A]])(implicit ctx: SourceContext): Rep[Int]
  def sparsematrix_vview[A:Manifest](x: Rep[SparseMatrix[A]], start: Rep[Int], stride: Rep[Int], length: Rep[Int], isRow: Rep[Boolean])(implicit ctx: SourceContext): Rep[VectorView[A]] 

  def sparsematrix_multiply[A:Manifest:Arith](x: Rep[SparseMatrix[A]], y: Rep[SparseMatrix[A]])(implicit ctx: SourceContext): Rep[SparseMatrix[A]]
  def sparsematrix_times_vector[A:Manifest:Arith](x: Rep[SparseMatrix[A]], y: Rep[SparseVector[A]])(implicit ctx: SourceContext): Rep[SparseVector[A]]
  def sparsematrix_inverse[A:Manifest](x: Rep[SparseMatrix[A]])(implicit conv: Rep[A] => Rep[Double], ctx: SourceContext): Rep[SparseMatrix[Double]]  
  def sparsematrix_maprows[A:Manifest,B:Manifest](x: Rep[SparseMatrix[A]], f: Rep[VectorView[A]] => Rep[SparseVector[B]])(implicit ctx: SourceContext): Rep[SparseMatrix[B]] 
  def sparsematrix_reducerows[A:Manifest](x: Rep[SparseMatrix[A]], f: (Rep[SparseVector[A]],Rep[VectorView[A]]) => Rep[SparseVector[A]])(implicit ctx: SourceContext): Rep[SparseVector[A]]   
  
  def sparsematrix_size[A:Manifest](x: Rep[SparseMatrix[A]])(implicit ctx: SourceContext): Rep[Int]
}

trait SparseMatrixCompilerOps extends SparseMatrixOps {
  this: OptiLA =>
  
  def sparsematrix_set_numrows[A:Manifest](x: Rep[SparseMatrix[A]], newVal: Rep[Int])(implicit ctx: SourceContext): Rep[Unit]
  def sparsematrix_set_numcols[A:Manifest](x: Rep[SparseMatrix[A]], newVal: Rep[Int])(implicit ctx: SourceContext): Rep[Unit]
  def sparsematrix_set_nnz[A:Manifest](x: Rep[SparseMatrix[A]], newVal: Rep[Int])(implicit ctx: SourceContext): Rep[Unit]
}

trait SparseMatrixOpsExp extends SparseMatrixCompilerOps with DeliteCollectionOpsExp with VariablesExp {
  this: SparseMatrixImplOps with OptiLAExp  =>
  
  //////////////////////////////////////////////////
  // implemented via method on real data structure
  
  case class SparseMatrixNumRows[A:Manifest](x: Exp[SparseMatrix[A]]) extends DefWithManifest[A,Int] 
  case class SparseMatrixNumCols[A:Manifest](x: Exp[SparseMatrix[A]]) extends DefWithManifest[A,Int]
  case class SparseMatrixNNZ[A:Manifest](x: Exp[SparseMatrix[A]]) extends DefWithManifest[A,Int]
  case class SparseMatrixSetNumRows[A:Manifest](x: Exp[SparseMatrix[A]], newVal: Exp[Int]) extends DefWithManifest[A,Unit]
  case class SparseMatrixSetNumCols[A:Manifest](x: Exp[SparseMatrix[A]], newVal: Exp[Int]) extends DefWithManifest[A,Unit]
  case class SparseMatrixSetNNZ[A:Manifest](x: Exp[SparseMatrix[A]], newVal: Exp[Int]) extends DefWithManifest[A,Unit]
    
  /////////////////////////////////////
  // implemented via kernel embedding

  case class SparseMatrixVView[A:Manifest](x: Exp[SparseMatrix[A]], start: Exp[Int], stride: Exp[Int], length: Exp[Int], isRow: Exp[Boolean])
    extends DeliteOpSingleWithManifest[A,VectorView[A]](reifyEffectsHere(sparsematrix_vview_impl(x, start, stride, length, isRow)))

  case class SparseMatrixApply[A:Manifest](x: Exp[SparseMatrix[A]], i: Exp[Int], j: Exp[Int])
    extends DeliteOpSingleWithManifest[A,A](reifyEffectsHere(sparsematrix_apply_impl(x, i, j)))
    
  case class SparseMatrixUpdate[A:Manifest](x: Exp[SparseMatrix[A]], i: Exp[Int], j: Exp[Int], y: Exp[A])
    extends DeliteOpSingleWithManifest[A,Unit](reifyEffectsHere(sparsematrix_update_impl(x, i, j, y)))

  // this is a single task right now because of the likely early exit. should we have a delite op for this?
  // case class SparseMatrixEquals[A:Manifest](x: Exp[SparseMatrix[A]], y: Exp[SparseMatrix[A]])
  //   extends DeliteOpSingleTask(reifyEffectsHere(sparsematrix_equals_impl[A](x,y)))
    
  // case class SparseMatrixInverse[A:Manifest](x: Exp[SparseMatrix[A]])(implicit val conv: Exp[A] => Exp[Double])
  //   extends DeliteOpSingleWithManifest[A,SparseMatrix[Double]](reifyEffectsHere(sparsematrix_inverse_impl(x))) 
  //   
  // case class SparseMatrixReduceRows[A:Manifest](x: Exp[SparseMatrix[A]], func: (Exp[SparseVector[A]], Exp[VectorView[A]]) => Exp[SparseVector[A]])
  //   extends DeliteOpSingleWithManifest[A,SparseVector[A]](reifyEffectsHere(sparsematrix_reducerows_impl(x,func)))
  // 
  // case class SparseMatrixMultiply[A:Manifest:Arith](x: Exp[SparseMatrix[A]], y: Exp[SparseMatrix[A]])
  //   extends DeliteOpSingleWithManifest[A,SparseMatrix[A]](reifyEffectsHere(sparsematrix_multiply_impl(x,y))) {
  //   
  //   val a = implicitly[Arith[A]]
  // }

  ////////////////////////////////
  // implemented via delite ops
  
  // case class SparseMatrixMapRows[A:Manifest,B:Manifest](x: Exp[SparseMatrix[A]], block: Exp[VectorView[A]] => Exp[SparseVector[B]], out: Exp[SparseMatrix[B]])
  //   extends DeliteOpIndexedLoop {
  // 
  //   val size = copyTransformedOrElse(_.size)(x.numRows)
  //   def func = i => { out(i) = block(x(i)) } // updateRow should be fused with function application
  //   
  //   val mA = manifest[A]
  //   val mB = manifest[B]
  // }

  // More efficient (though slightly uglier) to express this as a loop directly. 
  // TODO: nicer DeliteOpLoop templates? e.g. DeliteOpReductionLoop, ...
  // case class SparseMatrixReduceRows[A:Manifest](x: Exp[SparseMatrix[A]], func: (Exp[VectorView[A]], Exp[SparseVector[A]]) => Exp[SparseVector[A]])
  //   extends DeliteOpReduceLike[VectorView[A],SparseVector[A]] {
  // 
  //   val size = x.numRows
  //   val zero = EmptyVector[A]
  //   
  //   lazy val body: Def[SparseVector[A]] = copyBodyOrElse(DeliteReduceElem[SparseVector[A]](
  //     func = reifyEffects(x(v)),
  //     Nil,
  //     zero = this.zero,
  //     rV = this.rV,
  //     rFunc = reifyEffects(this.func(rV._1, rV._2)),
  //     true
  //   ))
  // }
  
  ///////////////////
  // class interface

  def sparsematrix_apply[A:Manifest](x: Exp[SparseMatrix[A]], i: Exp[Int], j: Exp[Int])(implicit ctx: SourceContext) = reflectPure(SparseMatrixApply[A](x,i,j))
  def sparsematrix_update[A:Manifest](x: Exp[SparseMatrix[A]], i: Exp[Int], j: Exp[Int], y: Exp[A])(implicit ctx: SourceContext) = reflectWrite(x)(SparseMatrixUpdate[A](x,i,j,y))
  def sparsematrix_numrows[A:Manifest](x: Exp[SparseMatrix[A]])(implicit ctx: SourceContext) = reflectPure(SparseMatrixNumRows(x))
  def sparsematrix_numcols[A:Manifest](x: Exp[SparseMatrix[A]])(implicit ctx: SourceContext) = reflectPure(SparseMatrixNumCols(x))
  def sparsematrix_nnz[A:Manifest](x: Exp[SparseMatrix[A]])(implicit ctx: SourceContext) = reflectPure(SparseMatrixNNZ(x))
  def sparsematrix_vview[A:Manifest](x: Exp[SparseMatrix[A]], start: Exp[Int], stride: Exp[Int], length: Exp[Int], isRow: Exp[Boolean])(implicit ctx: SourceContext) = reflectPure(SparseMatrixVView(x,start,stride,length,isRow))

  def sparsematrix_multiply[A:Manifest:Arith](x: Exp[SparseMatrix[A]], y: Exp[SparseMatrix[A]])(implicit ctx: SourceContext) = {
    throw new UnsupportedOperationException("tbd")
    //reflectPure(SparseMatrixMultiply(x,y))
  }
  def sparsematrix_times_vector[A:Manifest:Arith](x: Exp[SparseMatrix[A]], y: Exp[SparseVector[A]])(implicit ctx: SourceContext) = {
    throw new UnsupportedOperationException("tbd")
    //reflectPure(MatrixTimesVector[A,SparseVector[A]](x,y))
  }    
  def sparsematrix_inverse[A:Manifest](x: Exp[SparseMatrix[A]])(implicit conv: Exp[A] => Exp[Double], ctx: SourceContext) = {
    throw new UnsupportedOperationException("tbd")
    //reflectPure(SparseMatrixInverse(x))
  }
  def sparsematrix_maprows[A:Manifest,B:Manifest](x: Exp[SparseMatrix[A]], f: Exp[VectorView[A]] => Exp[SparseVector[B]])(implicit ctx: SourceContext) = {
    throw new UnsupportedOperationException("tbd")
    // val out = Matrix.sparse[B](x.numRows, x.numCols)
    // reflectWrite(out)(SparseMatrixMapRows(x,f,out))
    // out.unsafeImmutable 
  }  
  def sparsematrix_reducerows[A:Manifest](x: Exp[SparseMatrix[A]], f: (Exp[SparseVector[A]],Exp[VectorView[A]]) => Exp[SparseVector[A]])(implicit ctx: SourceContext) = {
    throw new UnsupportedOperationException("tbd")
    //reflectPure(SparseMatrixReduceRows(x, f))
  }
  

  //////////////////
  // internal

  def sparsematrix_size[A:Manifest](x: Exp[SparseMatrix[A]])(implicit ctx: SourceContext) = x.numRows * x.numCols
  def sparsematrix_set_numrows[A:Manifest](x: Exp[SparseMatrix[A]], newVal: Exp[Int])(implicit ctx: SourceContext) = reflectWrite(x)(SparseMatrixSetNumRows(x,newVal))
  def sparsematrix_set_numcols[A:Manifest](x: Exp[SparseMatrix[A]], newVal: Exp[Int])(implicit ctx: SourceContext) = reflectWrite(x)(SparseMatrixSetNumCols(x,newVal))  
  def sparsematrix_set_nnz[A:Manifest](x: Exp[SparseMatrix[A]], newVal: Exp[Int])(implicit ctx: SourceContext) = reflectWrite(x)(SparseMatrixSetNNZ(x,newVal))
  
    
  /////////////////////
  // delite collection
  
  def isSparseMat[A](x: Exp[DeliteCollection[A]])(implicit ctx: SourceContext) = isSubtype(x.Type.erasure,classOf[SparseMatrix[A]])  
  def asSparseMat[A](x: Exp[DeliteCollection[A]])(implicit ctx: SourceContext) = x.asInstanceOf[Exp[SparseMatrix[A]]]
  
  override def dc_size[A:Manifest](x: Exp[DeliteCollection[A]])(implicit ctx: SourceContext) = { 
    if (isSparseMat(x)) sparsematrix_size(asSparseMat(x))
    else super.dc_size(x)
  }
  
  override def dc_apply[A:Manifest](x: Exp[DeliteCollection[A]], n: Exp[Int])(implicit ctx: SourceContext) = {
    if (isSparseMat(x)) asSparseMat(x).dcApply(n)
    else super.dc_apply(x,n)    
  }  
  
  //////////////
  // mirroring

  override def mirror[A:Manifest](e: Def[A], f: Transformer)(implicit ctx: SourceContext): Exp[A] = (e match {
    case e@SparseMatrixNumRows(x) => reflectPure(SparseMatrixNumRows(f(x))(e.mA))(mtype(manifest[A]),implicitly[SourceContext])
    case e@SparseMatrixNumCols(x) => reflectPure(SparseMatrixNumCols(f(x))(e.mA))(mtype(manifest[A]),implicitly[SourceContext])
    case e@SparseMatrixNNZ(x) => reflectPure(SparseMatrixNNZ(f(x))(e.mA))(mtype(manifest[A]),implicitly[SourceContext])

    // delite ops
    case e@SparseMatrixVView(x,s,str,l,r) => reflectPure(new { override val original = Some(f,e) } with SparseMatrixVView(f(x),f(s),f(str),f(l),f(r))(e.mA))(mtype(manifest[A]),implicitly[SourceContext])
    case e@SparseMatrixApply(x,i,j) => reflectPure(new { override val original = Some(f,e) } with SparseMatrixApply(f(x),f(i),f(j))(e.mA))(mtype(manifest[A]),implicitly[SourceContext])
    // case e@SparseMatrixInverse(x) => reflectPure(new {override val original = Some(f,e) } with SparseMatrixInverse(f(x))(e.mA,e.conv))(mtype(manifest[A]),implicitly[SourceContext])      
    // case e@SparseMatrixMultiply(x,y) => reflectPure(new {override val original = Some(f,e) } with SparseMatrixMultiply(f(x),f(y))(e.mA,e.a))(mtype(manifest[A]),implicitly[SourceContext])
    // case e@SparseMatrixMapRows(x,g,y) => reflectPure(new { override val original = Some(f,e) } with SparseMatrixMapRows(f(x),f(g),f(y))(e.mA,e.mB))(mtype(manifest[A]),implicitly[SourceContext])
    //case e@SparseMatrixTimesVector(x,y) => reflectPure(new {override val original = Some(f,e) } with SparseMatrixTimesVector(f(x),f(y))(e.m,e.a))(mtype(manifest[A]),implicitly[SourceContext])
    
    // reflected
    case Reflect(e@SparseMatrixNumRows(x), u, es) => reflectMirrored(Reflect(SparseMatrixNumRows(f(x))(e.mA), mapOver(f,u), f(es)))(mtype(manifest[A]))
    case Reflect(e@SparseMatrixNumCols(x), u, es) => reflectMirrored(Reflect(SparseMatrixNumCols(f(x))(e.mA), mapOver(f,u), f(es)))(mtype(manifest[A]))   
    case Reflect(e@SparseMatrixNNZ(x), u, es) => reflectMirrored(Reflect(SparseMatrixNNZ(f(x))(e.mA), mapOver(f,u), f(es)))(mtype(manifest[A]))   
    case Reflect(e@SparseMatrixSetNumRows(x,v), u, es) => reflectMirrored(Reflect(SparseMatrixSetNumRows(f(x),f(v))(e.mA), mapOver(f,u), f(es)))(mtype(manifest[A]))    
    case Reflect(e@SparseMatrixSetNumCols(x,v), u, es) => reflectMirrored(Reflect(SparseMatrixSetNumCols(f(x),f(v))(e.mA), mapOver(f,u), f(es)))(mtype(manifest[A]))  
    case Reflect(e@SparseMatrixSetNNZ(x,v), u, es) => reflectMirrored(Reflect(SparseMatrixSetNNZ(f(x),f(v))(e.mA), mapOver(f,u), f(es)))(mtype(manifest[A]))  
    case Reflect(e@SparseMatrixVView(x,s,str,l,r), u, es) => reflectMirrored(Reflect(new { override val original = Some(f,e) } with SparseMatrixVView(f(x),f(s),f(str),f(l),f(r))(e.mA), mapOver(f,u), f(es)))(mtype(manifest[A]))               
    case Reflect(e@SparseMatrixApply(x,i,j), u, es) => reflectMirrored(Reflect(new { override val original = Some(f,e) } with SparseMatrixApply(f(x),f(i),f(j))(e.mA), mapOver(f,u), f(es)))(mtype(manifest[A]))      
    // case Reflect(e@SparseMatrixInverse(x), u, es) => reflectMirrored(Reflect(new { override val original = Some(f,e) } with SparseMatrixInverse(f(x))(e.mA,e.conv), mapOver(f,u), f(es)))(mtype(manifest[A]))          
    // case Reflect(e@SparseMatrixMultiply(x,y), u, es) => reflectMirrored(Reflect(new { override val original = Some(f,e) } with SparseMatrixMultiply(f(x),f(y))(e.mA,e.a), mapOver(f,u), f(es)))(mtype(manifest[A]))         
    // case Reflect(e@SparseMatrixMapRows(x,g,y), u, es) => reflectMirrored(Reflect(new { override val original = Some(f,e) } with SparseMatrixMapRows(f(x),f(g),f(y))(e.mA,e.mB), mapOver(f,u), f(es)))(mtype(manifest[A]))              
    case _ => super.mirror(e, f)
  }).asInstanceOf[Exp[A]] // why??
  
  
  /////////////////////
  // aliases and sharing

  // TODO: precise sharing info for other IR types (default is conservative)

  override def aliasSyms(e: Any): List[Sym[Any]] = e match {
    // case SparseMatrixMultiply(a,b) => Nil
    //case SparseMatrixTimesVector(a,v) => Nil
    case _ => super.aliasSyms(e)
  }

  override def containSyms(e: Any): List[Sym[Any]] = e match {
    // case SparseMatrixMultiply(a,b) => Nil
    //case SparseMatrixTimesVector(a,v) => Nil
    case _ => super.containSyms(e)
  }

  override def extractSyms(e: Any): List[Sym[Any]] = e match {
    // case SparseMatrixMultiply(a,b) => Nil
    //case SparseMatrixTimesVector(a,v) => Nil
    case _ => super.extractSyms(e)
  }

  override def copySyms(e: Any): List[Sym[Any]] = e match {
    // case SparseMatrixMultiply(a,b) => Nil
    //case SparseMatrixTimesVector(a,v) => Nil
    case _ => super.copySyms(e)
  } 
}

/**
 *  Optimizations for composite SparseMatrixOps operations.
 */

trait SparseMatrixOpsExpOpt extends SparseMatrixOpsExp {
  this: SparseMatrixImplOps with OptiLAExp =>

  // override def sparsematrix_equals[A:Manifest](x: Exp[SparseMatrix[A]], y: Exp[SparseMatrix[A]])(implicit ctx: SourceContext) = (x, y) match {
  //   case (a,b) if (a == b) => unit(true) // same symbol
  //   case _ => super.sparsematrix_equals(x,y)
  // }

  // Def(SparseMatrixFinalize(SparseMatrixObjectNew(....)))
  // override def sparsematrix_numrows[A:Manifest](x: Exp[SparseMatrix[A]])(implicit ctx: SourceContext) = x match {
  //   case Def(s@Reflect(SparseMatrixObjectNew(rows,cols), u, es)) if context.contains(s) => rows // only if not modified! // TODO: check writes
  //   case Def(SparseMatrixObjectNew(rows,cols)) => rows
  //   case _ => super.sparsematrix_numrows(x)
  // }
  // 
  // override def sparsematrix_numcols[A:Manifest](x: Exp[SparseMatrix[A]])(implicit ctx: SourceContext) = x match {
  //   case Def(s@Reflect(SparseMatrixObjectNew(rows,cols), u, es)) if context.contains(s) => cols // only if not modified! // TODO: check writes
  //   case Def(SparseMatrixObjectNew(rows,cols)) => cols
  //   case _ => super.sparsematrix_numcols(x)
  // }

  override def sparsematrix_size[A:Manifest](x: Exp[SparseMatrix[A]])(implicit ctx: SourceContext) = x match {
    case Def(e: DeliteOpMap[_,_,_]) => e.size
    case Def(e: DeliteOpZipWith[_,_,_,_]) => e.size
    case _ => super.sparsematrix_size(x)
  }
  
}


trait ScalaGenSparseMatrixOps extends ScalaGenBase {
  val IR: SparseMatrixOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
    // these are the ops that call through to the underlying real data structure
    case SparseMatrixNumRows(x)  => emitValDef(sym, quote(x) + "._numRows")
    case SparseMatrixNumCols(x)  => emitValDef(sym, quote(x) + "._numCols")
    case SparseMatrixNNZ(x)  => emitValDef(sym, quote(x) + "._nnz")
    case SparseMatrixSetNumRows(x,v) => emitValDef(sym, quote(x) + "._numRows = " + quote(v))
    case SparseMatrixSetNumCols(x,v) => emitValDef(sym, quote(x) + "._numCols = " + quote(v))
    case SparseMatrixSetNNZ(x,v) => emitValDef(sym, quote(x) + "._nnz = " + quote(v))
    case _ => super.emitNode(sym, rhs)
  }  
}

trait CudaGenSparseMatrixOps extends CudaGenBase with CudaGenDataStruct {
  val IR: SparseMatrixOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
    case _ => super.emitNode(sym, rhs)
  }
}

trait OpenCLGenSparseMatrixOps extends OpenCLGenBase with OpenCLGenDataStruct {
  val IR: SparseMatrixOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
    case _ => super.emitNode(sym, rhs)
  }
}

trait CGenSparseMatrixOps extends CGenBase {
  val IR: SparseMatrixOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
    case _ => super.emitNode(sym, rhs)
  }
}
