package ppl.delite.framework.embedded.scala

import java.io.PrintWriter
import ppl.delite.framework.{DSLType, DeliteApplication}
import ppl.delite.framework.codegen.scala.{TargetScala, CodeGeneratorScalaBase}
import scala.virtualization.lms.util.OverloadHack

trait Variables extends OverloadHack { this: DeliteApplication =>
  type Var[+T]

  implicit def readVar[T](v: Var[T]) : Rep[T]
  //implicit def chainReadVar[T,U](x: Var[T])(implicit f: Rep[T] => U): U = f(readVar(x))

  def __newVar[T](init: Rep[T])(implicit o: Overloaded1): Var[T]
  def __assign[T](lhs: Var[T], rhs: Rep[T]) : Rep[Unit]

}

trait VariablesExp extends Variables { this: DeliteApplication =>
  type Var[+T] = Variable[T]
  // TODO: make a design decision here.
  // defining Var[T] as Sym[T] is dangerous. If someone forgets to define a more-specific implicit conversion from
  // Var[T] to Ops, e.g. implicit def varToRepStrOps(s: Var[String]) = new RepStrOpsCls(varToRep(s))
  // then the existing implicit from Rep to Ops will be used, and the ReadVar operation will be lost.
  // Defining Vars as separate from Exps will always cause a compile-time error if the implicit is missing.
  //type Var[T] = Sym[T]

  // read operation
  implicit def readVar[T](v: Var[T]) : Exp[T] = reflectEffect(ReadVar(v))

  case class ReadVar[T](v: Var[T]) extends Def[T]
  case class NewVar[T](init: Exp[T]) extends Def[T]
  case class Assign[T](lhs: Var[T], rhs: Exp[T]) extends Def[Unit]

  def __newVar[T](init: Exp[T])(implicit o: Overloaded1): Var[T] = {
    //reflectEffect(NewVar(init)).asInstanceOf[Var[T]]
    Variable(reflectEffect(NewVar(init)))
  }

  def __assign[T](lhs: Var[T], rhs: Exp[T]): Exp[Unit] = {
    reflectEffect(Assign(lhs, rhs))
    Const()
  }

  targets.get("Scala").getOrElse(
    throw new RuntimeException("Couldn't find Scala code generator")
  ) .generators += new CodeGeneratorScalaVariables {
    val intermediate: VariablesExp.this.type = VariablesExp.this
  }
}


trait CodeGeneratorScalaVariables extends CodeGeneratorScalaBase {

  val intermediate: DeliteApplication with VariablesExp
  import intermediate._

  def emitNode(sym: Sym[_], rhs: Def[_])(implicit stream: PrintWriter): Boolean = {
    rhs match {
      case ReadVar(Variable(a)) => emitValDef(sym, quote(a))
      case NewVar(init) => emitVarDef(sym, quote(init))
      case Assign(Variable(a), b) => emitAssignment(quote(a), quote(b))
      //case Assign(a, b) => emitAssignment(quote(a), quote(b))
      case _ => return false
    }
    true
  }
}