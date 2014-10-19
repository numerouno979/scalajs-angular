package com.greencatsoft.angularjs.core

import scala.annotation.implicitNotFound
import scala.concurrent.{ CanAwait, ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scala.scalajs.js
import scala.scalajs.js.Any.fromFunction1
import scala.scalajs.js.JavaScriptException
import scala.util.{ Failure, Success, Try }

import com.greencatsoft.angularjs.injectable

@injectable("$q")
trait Q extends js.Object {

  def defer(): Defer = ???

  def promise(): Promise = ???
}

trait Defer extends js.Object {

  def resolve(value: js.Any): Unit = ???

  def reject(reason: String): Unit = ???

  def notify(value: js.Any): Unit = ???

  val promise: Promise = ???
}

object Defer {

  implicit def defer2promise[T](defer: Defer): scala.concurrent.Promise[T] = new DeferredPromise[T](defer)

  class DeferredPromise[T](defer: Defer) extends scala.concurrent.Promise[T] {

    private var completed = false

    override def future: Future[T] = new Promise.DeferredFuture(defer.promise)

    override def isCompleted: Boolean = completed

    override def tryComplete(result: Try[T]): Boolean = if (isCompleted) false else {
      result match {
        case Success(r) =>
          defer.resolve(r.asInstanceOf[js.Any])
        case Failure(e) =>
          defer.reject(e.getMessage())
      }

      this.completed = true
      completed
    }
  }
}

trait Promise extends js.Object {

  def `then`(successCallback: js.Function1[js.Any, js.Any]): this.type = ???

  def `then`(successCallback: js.Function1[js.Any, js.Any], errorCallback: js.Function1[js.Any, Unit]): this.type = ???

  def `then`(successCallback: js.Function1[js.Any, js.Any], errorCallback: js.Function1[js.Any, Unit], notifyCallback: js.Function1[js.Any, Unit]): this.type = ???

  def `catch`(errorCallback: js.Function1[js.Any, Unit]): this.type = ???

  def `finally`(callback: js.Function1[js.Any, Unit]): Unit = ???
}

object Promise {

  implicit def promise2future[T](promise: Promise): Future[T] = new DeferredFuture[T](promise)

  class DeferredFuture[T](promise: Promise) extends Future[T] {

    type Listener[U] = Try[T] => U

    private var result: Option[Try[T]] = None

    private var listeners: Seq[Listener[_]] = Seq.empty

    private def notify(result: Try[T]): Option[Try[T]] = {
      listeners.foreach(_(result))
      Some(result)
    }

    promise `then` { (r: js.Any) =>
      this.result = notify(Success(r.asInstanceOf[T]))
      r
    } `catch` { (error: js.Any) =>
      this.result = notify(Failure(JavaScriptException(error)))
    }

    override def ready(atMost: Duration)(implicit permit: CanAwait): this.type =
      throw new UnsupportedOperationException

    override def result(atMost: Duration)(implicit permit: CanAwait): T =
      throw new UnsupportedOperationException

    override def isCompleted: Boolean = result.isDefined

    override def onComplete[U](f: Listener[U])(implicit executor: ExecutionContext): Unit =
      listeners +:= f

    override def value: Option[Try[T]] = result
  }
}