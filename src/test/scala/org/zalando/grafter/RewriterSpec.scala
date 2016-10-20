package org.zalando.grafter

import org.specs2._
import cats.Eval
import ExampleGraph._
import syntax.rewriter._

class RewriterSpec extends Specification { def is = s2"""

 An object graph can be rewritten with a node becoming a singleton object
   All identical nodes (having the same type) are replaced by just the first found instance $makeSingleton
   An instance can be passed and be replaced everywhere                                     $replace
   An instance of a subtype can be passed and be replaced everywhere                        $replaceWithSubtype
   An instance of a subtype with an interface can be passed and be replaced everywhere      $replaceWithInterfaceSubtype
   A replacement only works if we pass an instance of the same type                         $replaceWithDifferentType
   Startable components can be started in order from the bottom up                          $startInOrder
   If a component fails to start the sequence is interrupted right away                     $stopFailedStart
   If a component throws an exception on start the sequence is interrupted right away       $stopErrorStart

"""

  val graph =
    A(
      B(D("d1"), E("e1"), F1("f1")),
      C(D("d2"), E("e2"), F1("f2")))

  def makeSingleton = {

    val rewritten = graph.singleton[D]

    (rewritten.b.d must be(rewritten.c.d)) and
      (rewritten.b.d must_== D("d1"))

  }

  def replace = {

    val d = D("d3")
    val rewritten = graph.replace(d)

    (rewritten.b.d must be(rewritten.c.d)) and
      (rewritten.b.d must be(d))

  }

  def replaceWithSubtype = {

    val e: E = ESub("sub")
    val rewritten = graph.replace(e)

    (rewritten.b.e must be(rewritten.c.e)) and
      (rewritten.b.e must be(e))

  }

  def replaceWithInterfaceSubtype = {

    val f: F = F2("f2")
    val rewritten = graph.replace(f)

    (rewritten.b.f must be(rewritten.c.f)) and
      (rewritten.b.f must be(f))

  }

  def  replaceWithDifferentType = {

    // here we keep the type as F2, not F!
    val f = F2("f2")
    val rewritten = graph.replace(f)

    (rewritten.b.f must not be rewritten.c.f) and
      (rewritten.b.f must not be f)

  }

  def startInOrder =
    Rewriter.start(graph).value ==== List(
      StartOk("d1"), StartOk("e1"), StartOk("f1"), StartOk("B"),
      StartOk("d2"), StartOk("e2"), StartOk("f2"), StartOk("C"),
      StartOk("A")
    )

  def stopFailedStart = {
    val failedGraph = A(
      B(D("d1"), ESub("e1"), F1("f1")),
      C(D("d2"), ESub("e2"), F1("f2")))

    Rewriter.start(failedGraph).value ==== List(
      StartOk("d1"), StartFailure("e1")
    )
  }

  def stopErrorStart = {
    val exception = new Exception("boom")
    val errorStart = StartResult.eval("e1")(throw exception)

    val errorGraph = A(
      B(D("d1"), new ESub("e1") { override def start = errorStart }, F1("f1")),
      C(D("d2"), ESub("e2"), F1("f2")))

    Rewriter.start(errorGraph).value ==== List(
      StartOk("d1"), StartError("e1", exception)
    )
  }
}

object ExampleGraph {

  case class D(name: String) extends Start {
    override def toString = name

    def start: Eval[StartResult] =
      StartResult.eval(name)(())
  }

  class E(name: String) extends Start {
    override def toString = name

    def start: Eval[StartResult] =
      StartResult.eval(name)(())
  }

  object E {
    def apply(name: String) = new E(name)
  }
  case class ESub(name: String) extends E(name) {
    override def start: Eval[StartResult] =
      Eval.now(StartFailure(name))
  }

  trait F
  case class F1(name: String) extends F with Start {
    override def toString = name

    def start: Eval[StartResult] =
      StartResult.eval(name)(())
  }

  case class F2(name: String) extends F with Start {
    override def toString = name

    def start: Eval[StartResult] =
      StartResult.eval(name)(())
  }

  case class B(d: D, e: E, f: F) extends Start {
    def start: Eval[StartResult] =
      StartResult.eval("B")(())
  }

  case class C(d: D, e: E, f: F) extends Start {
    def start: Eval[StartResult] =
      StartResult.eval("C")(())
  }

  case class A(b: B, c: C) extends Start {
    def start: Eval[StartResult] =
      StartResult.eval("A")(())
  }

}
