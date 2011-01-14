package ppl.delite.framework.codegen.delite.overrides

import scala.virtualization.lms.common._
import ppl.delite.framework.ops.DeliteOpsExp
import java.io.PrintWriter
import scala.virtualization.lms.internal.{ScalaGenEffect, CudaGenEffect, CGenEffect, GenericNestedCodegen}

trait DeliteIfThenElseExp extends IfThenElseExp {

  this: DeliteOpsExp =>

  case class DeliteIfThenElse[T:Manifest](c: Exp[Boolean], t: Exp[T], e: Exp[T]) extends DeliteOpCondition[T](c, t, e)

  override def __ifThenElse[T:Manifest](cond: Rep[Boolean], thenp: => Rep[T], elsep: => Rep[T]) = {
    val a = reifyEffects(thenp)
    val b = reifyEffects(elsep)
    (a,b) match {
      case (Def(Reify(_,_)), _) | (_, Def(Reify(_,_))) => reflectEffect(DeliteIfThenElse(cond,a,b))
      case _ => DeliteIfThenElse(cond, thenp, elsep)
    }
  }

}

trait DeliteBaseGenIfThenElse extends GenericNestedCodegen {
  val IR: DeliteIfThenElseExp
  import IR._

  override def syms(e: Any): List[Sym[Any]] = e match {
    case DeliteIfThenElse(c, t, e) if shallow => syms(c) // in shallow mode, don't count deps from nested blocks
    case _ => super.syms(e)
  }

 override def getFreeVarNode(rhs: Def[_]): List[Sym[_]] = rhs match {
    case DeliteIfThenElse(c, t, e) => getFreeVarBlock(c,Nil) ::: getFreeVarBlock(t,Nil) ::: getFreeVarBlock(e,Nil)
    case _ => super.getFreeVarNode(rhs)
  }
}

trait DeliteScalaGenIfThenElse extends ScalaGenEffect with DeliteBaseGenIfThenElse {
  import IR._

  override def emitNode(sym: Sym[_], rhs: Def[_])(implicit stream: PrintWriter) = rhs match {
    /**
     * IfThenElse generates methods for each branch due to empirically discovered performance issues in the JVM
     * when generating long blocks of straight-line code in each branch.
     */
    case DeliteIfThenElse(c,a,b) =>
      stream.println("val " + quote(sym) + " = {")
      stream.println("def " + quote(sym) + "thenb(): " + remap(getBlockResult(a).Type) + " = {")
      emitBlock(a)
      stream.println(quote(getBlockResult(a)))
      stream.println("}")

      stream.println("def " + quote(sym) + "elseb(): " + remap(getBlockResult(b).Type) + " = {")
      emitBlock(b)
      stream.println(quote(getBlockResult(b)))
      stream.println("}")

      stream.println("if (" + quote(c) + ") {")
      stream.println(quote(sym) + "thenb()")
      stream.println("} else {")
      stream.println(quote(sym) + "elseb()")
      stream.println("}")
      stream.println("}")

    case _ => super.emitNode(sym, rhs)
  }
}


trait DeliteCudaGenIfThenElse extends CudaGenEffect with DeliteBaseGenIfThenElse {
  import IR._

  override def emitNode(sym: Sym[_], rhs: Def[_])(implicit stream: PrintWriter) = {
      rhs match {
        case DeliteIfThenElse(c,a,b) =>
          // TODO: Not GPUable if the result is not primitive types.
          // TODO: Changing the reference of the output is dangerous in general.
          // TODO: In the future, consider passing the object references to the GPU kernels rather than copying by value.
          // Below is a safety check related to changing the output reference of the kernel.
          // This is going to be changed when above TODOs are done.
          //if( (sym==kernelSymbol) && (isObjectType(sym.Type)) ) throw new RuntimeException("CudaGen: Changing the reference of output is not allowed within GPU kernel.")

          val hasPrimitiveRet = (remap(sym.Type)!="void") && (!isObjectType(sym.Type))
          hasPrimitiveRet match {
            case true =>
              stream.println("%s %s;".format(remap(sym.Type),quote(sym)))
              stream.println(addTab() + "if (" + quote(c) + ") {")
              tabWidth += 1
              emitBlock(a)
              stream.println(addTab() + "%s = %s;".format(quote(sym),quote(getBlockResult(a))))
              tabWidth -= 1
              stream.println(addTab() + "} else {")
              tabWidth += 1
              emitBlock(b)
              stream.println(addTab() + "%s = %s;".format(quote(sym),quote(getBlockResult(b))))
              tabWidth -= 1
              stream.println(addTab()+"}")
            case false =>
              stream.println(addTab() + "if (" + quote(c) + ") {")
              tabWidth += 1
              addVarLink(getBlockResult(a),sym)
              emitBlock(a)
              removeVarLink(getBlockResult(a),sym)
              tabWidth -= 1
              stream.println(addTab() + "} else {")
              tabWidth += 1
              addVarLink(getBlockResult(b),sym)
              emitBlock(b)
              removeVarLink(getBlockResult(b),sym)
              tabWidth -= 1
              stream.println(addTab()+"}")
              isObjectType(sym.Type) match {
                case true => allocReference(sym,getBlockResult(a).asInstanceOf[Sym[_]])
                case _ =>
              }
          }
        
        case _ => super.emitNode(sym, rhs)
      }
    }
}

trait DeliteCGenIfThenElse extends CGenEffect with DeliteBaseGenIfThenElse {
  import IR._

  override def emitNode(sym: Sym[_], rhs: Def[_])(implicit stream: PrintWriter) = {
      rhs match {
        case DeliteIfThenElse(c,a,b) =>
          //TODO: using if-else does not work
          remap(sym.Type) match {
            case "void" =>
              stream.println("if (" + quote(c) + ") {")
              emitBlock(a)
              stream.println("} else {")
              emitBlock(b)
              stream.println("}")
            case _ =>
              stream.println("%s %s;".format(remap(sym.Type),quote(sym)))
              stream.println("if (" + quote(c) + ") {")
              emitBlock(a)
              stream.println("%s = %s;".format(quote(sym),quote(getBlockResult(a))))
              stream.println("} else {")
              emitBlock(b)
              stream.println("%s = %s;".format(quote(sym),quote(getBlockResult(b))))
              stream.println("}")
          }
          /*
          val booll = remap(sym.Type).equals("void")
          if(booll) {
            stream.println("%s %s;".format(remap(sym.Type),quote(sym)))
            stream.println("if (" + quote(c) + ") {")
            emitBlock(a)
            stream.println("%s = %s;".format(quote(sym),quote(getBlockResult(a))))
            stream.println("} else {")
            emitBlock(b)
            stream.println("%s = %s;".format(quote(sym),quote(getBlockResult(b))))
            stream.println("}")
          }
          else {
            stream.println("if (" + quote(c) + ") {")
            emitBlock(a)
            stream.println("} else {")
            emitBlock(b)
            stream.println("}")
          }
          */
        case _ => super.emitNode(sym, rhs)
      }
    }
}
