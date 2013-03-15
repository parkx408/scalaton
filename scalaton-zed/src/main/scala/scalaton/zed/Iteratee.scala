package scalaton.zed

import scalaz._
import Scalaz._
import iteratee._
import Iteratee._
import Free.Trampoline
import effect._
import java.io._

trait IterateeModule{

  /** enumerate BufferedReader; close readeer when done/error **/
  def enumLines[F[_]](r: => BufferedReader)(implicit MO: MonadPartialOrder[F, IO]): EnumeratorT[IoExceptionOr[String], F] =
    new EnumeratorT[IoExceptionOr[String], F] {
      import MO._
      lazy val reader = r
      def apply[A] = (s: StepT[IoExceptionOr[String], F, A]) =>
      s.mapCont(
        k => {
          val i = IoExceptionOr(reader.readLine)
          if (i exists (_ != null)) k(elInput(i)) >>== apply[A]
          else {
            reader.close
            s.pointI
          }
        }
      )
    }

  /** perform some IO given elements
   *  example: ((writeTo((x: String) => IO(println(x))) %= map((_: IoExceptionOr[String]).toOption | "!") )&= enumBuffered(reader.file("./NOTES"))(_.readLine)).run.unsafePerformIO
   * **/
  def writeTo[E](write: E => IO[Unit]): IterateeT[E, IO, Unit] =
    foldM(())((_: Unit, e: E) => write(e))

  def writeToBuffered[E](w: => BufferedWriter)(write: (BufferedWriter, E) => Unit): IterateeT[E, IO, Unit] =
    writeTo((e: E) => IO(write(w, e)))

}

object iter
extends IterateeModule