import akka.stream.scaladsl.{Source, Sink}
import akka.util.Timeout
import scala.concurrent.{Future, Await}
import akka.stream.Materializer
import scala.collection.immutable.Seq

object InternalHelpers {
  /**
    * get arguments from environment variable; we need to do this because
    * Ammonite is launched in repl mode, and as of Ammonite 1.0.1, repl mode
    * does not take additional program arguments.
    */
  def argsFromEnv: List[String] = {
    "(?<!\\\\)( +)".r.split(sys.env.getOrElse("MARATHON_ARGS", "").trim).toList.filterNot(_ == "")
  }
}

object Helpers {
  def await[T](f: Future[T])(implicit timeout: Timeout): T = {
    Await.result(f, timeout.duration)
  }

  def await[T](s: Source[T, Any])(implicit timeout: Timeout, mat: Materializer): Seq[T] = {
    val f: Future[Seq[T]] = s.completionTimeout(timeout.duration).runWith(Sink.seq[T])
    await(f)
  }

}
