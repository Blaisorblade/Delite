package ppl.delite.framework.embedded.scala

import java.io.PrintWriter
import ppl.delite.framework.{DSLType, DeliteApplication}
import ppl.delite.framework.codegen.scala.{TargetScala, CodeGeneratorScalaBase}

trait Functions { this: DeliteApplication =>

  implicit def doLambda[A,B](fun: Rep[A] => Rep[B])(implicit mA: Manifest[A]): Rep[A => B]
  implicit def toLambdaOps[A,B](fun: Rep[A => B]) = new LambdaOps(fun)
  
  class LambdaOps[A,B](f: Rep[A => B]) {
    def apply(x: Rep[A]): Rep[B] = doApply(f,x)
  }
  def doApply[A,B](fun: Rep[A => B], arg: Rep[A]): Rep[B]

}

trait FunctionsExp extends Functions { this: DeliteApplication =>
  class ApplyExtractor[A,B](f: Exp[A => B]) {
    def apply(x: Exp[A]): Exp[B] = Apply(f,x)
    def unapply(e: Def[B]): Option[Exp[A]] = e match {
      case Apply(`f`, x: Exp[A]) => Some(x)
      case _ => None
    }
  }

  case class Lambda[A,B](f: Exp[A] => Exp[B], x: Sym[A], y: Exp[B])(implicit val mA: Manifest[A]) extends Def[A => B]
  case class Apply[A,B](f: Exp[A => B], arg: Exp[A]) extends Def[B]

  def doLambda[A,B](f: Exp[A] => Exp[B])(implicit mA: Manifest[A]) : Exp[A => B] = {
    val x = fresh[A]
    val y = reifyEffects(f(x)) // unfold completely at the definition site. 
                               // TODO: this will not work if f is recursive. 
                               // need to incorporate the other pieces at some point.
    Lambda(f, x, y)
  }
  
  def doApply[A,B](f: Exp[A => B], x: Exp[A]): Exp[B] = f match {
    case Def(Lambda(_,_,Def(Reify(_,_)))) => 
      // if function result is known to be effectful, so is application
      reflectEffect(Apply(f,x))
    case Def(Lambda(_,_,_)) => 
      // if function result is known to be pure, so is application
      Apply(f, x)
    case _ => // unknown function, assume it is effectful
      reflectEffect(Apply(f, x))
  }

  targets.get("Scala").getOrElse(
    throw new RuntimeException("Couldn't find Scala code generator")
  ) .generators += new CodeGeneratorScalaFunctions {
    val intermediate: FunctionsExp.this.type = FunctionsExp.this
  }
}

trait CodeGeneratorScalaFunctions extends CodeGeneratorScalaBase {

  val intermediate: DeliteApplication with FunctionsExp
  import intermediate._

  override def syms2(e: Any, shallow: Boolean): Option[List[Sym[Any]]] = e match {
    case Lambda(f, x, y) if shallow => Some(Nil) // in shallow mode, don't count deps from nested blocks
    case _ => None
  }
  override def emitNode(sym: Sym[_], rhs: Def[_])(implicit stream: PrintWriter) = rhs match {
    case e@Lambda(fun, x, y) =>
      stream.println("val " + quote(sym) + " = {" + quote(x) + ": (" + e.mA + ") => ")
      emitBlock(y, intermediate.targets.get("Scala").get)
      stream.println(quote(getBlockResult(y)))
      stream.println("}")

    case Apply(fun, arg) => 
      emitValDef(sym, quote(fun) + "(" + quote(arg) + ")")

    case _ => super.emitNode(sym, rhs)
  }
}