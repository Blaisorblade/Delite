package ppl.delite.framework.embedded.scala

import java.io.PrintWriter
import ppl.delite.framework.{DSLType, DeliteApplication}
import ppl.delite.framework.codegen.scala.{TargetScala, CodeGeneratorScalaBase}
import scala.virtualization.lms.util.OverloadHack
import scala.virtualization.lms.common.{EffectExp, BaseExp, Base}

trait Equal extends OverloadHack { this: DeliteApplication with Variables =>
  // TODO: we need a better way of handling this, too many combinations
  def __equal[A,B](a: Rep[A], b: Rep[B]) : Rep[Boolean] = equals(a,b)
  def __equal[A,B](a: Rep[A], b: Var[B])(implicit o: Overloaded1) : Rep[Boolean] = equals(a, readVar(b))
  def __equal[A,B](a: Var[A], b: Rep[B])(implicit o: Overloaded2) : Rep[Boolean] = equals(readVar(a), b)
  def __equal[A,B](a: Rep[A], b: B)(implicit o: Overloaded3): Rep[Boolean] = equals(a, unit(b))
  def __equal[A,B](a: A, b: Rep[B])(implicit o: Overloaded4): Rep[Boolean] = equals(unit(a), b)
  //def __equal[A,B](a: Var[A], b: B)(implicit o: Overloaded3): Rep[Boolean] = __equal(a, readVar(b))
  //def __equal[A,B](a: A, b: Var[B])(implicit o: Overloaded4): Rep[Boolean] = __equal(readVar(a), b)

  def __ext__!=[A,B](a: Rep[A], b: Rep[B]) : Rep[Boolean] = notequals(a,b)
  def __ext__!=[A,B](a: Rep[A], b: Var[B])(implicit o: Overloaded1) : Rep[Boolean] = notequals(a,readVar(b))
  def __ext__!=[A,B](a: Var[A], b: Rep[B])(implicit o: Overloaded2) : Rep[Boolean] = notequals(readVar(a), b)
  def __ext__!=[A,B](a: Rep[A], b: B)(implicit o: Overloaded3) : Rep[Boolean] = __ext__!=(a, unit(b))
  def __ext__!=[A,B](a: A, b: Rep[B])(implicit o: Overloaded4) : Rep[Boolean] = __ext__!=(unit(a), b)
  //def __ext__!=[A,B](a: Var[A], b: B)(implicit o: Overloaded3) : Rep[Boolean] = __ext__!=(a, readVar(b))
  //def __ext__!=[A,B](a: A, b: Var[B])(implicit o: Overloaded4) : Rep[Boolean] = __ext__!=(readVar(a), b)

  def equals[A,B](a: Rep[A], b: Rep[B]) : Rep[Boolean]
  def notequals[A,B](a: Rep[A], b: Rep[B]) : Rep[Boolean]
}

trait EqualExp extends Equal { this: DeliteApplication with VariablesExp =>
  case class Equal[A,B](a: Exp[A], b: Exp[B]) extends Def[Boolean]
  case class NotEqual[A,B](a: Exp[A], b: Exp[B]) extends Def[Boolean]

  def equals[A,B](a: Rep[A], b: Rep[B]): Rep[Boolean] = Equal(a,b)
  def notequals[A,B](a: Rep[A], b: Rep[B]): Rep[Boolean] = NotEqual(a,b)

  targets.get("Scala").getOrElse(
    throw new RuntimeException("Couldn't find Scala code generator")
  ) .generators += new CodeGeneratorScalaEqual {
    val intermediate: EqualExp.this.type = EqualExp.this
  }
}

trait CodeGeneratorScalaEqual extends CodeGeneratorScalaBase {

  val intermediate: DeliteApplication with EqualExp with EffectExp
  import intermediate._
  
  override def emitNode(sym: Sym[_], rhs: Def[_])(implicit stream: PrintWriter) = rhs match {
    case Equal(a,b) =>  emitValDef(sym, quote(a) + " == " + quote(b))
    case NotEqual(a,b) =>  emitValDef(sym, quote(a) + " != " + quote(b))
    case _ => super.emitNode(sym, rhs)
  }
}
