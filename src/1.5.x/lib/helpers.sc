import akka.stream.scaladsl.{Source, Sink}
import akka.util.Timeout
import scala.concurrent.{Future, Await}
import akka.stream.Materializer
import scala.collection.immutable.Seq

object Helpers {
  def await[T](f: Future[T])(implicit timeout: Timeout): T = {
    Await.result(f, timeout.duration)
  }

  def await[T](s: Source[T, Any])(implicit timeout: Timeout, mat: Materializer): Seq[T] = {
    val f: Future[Seq[T]] = s.completionTimeout(timeout.duration).runWith(Sink.seq[T])
    await(f)
  }

}
