import $file.helpers
import $file.bindings

import akka.stream.Materializer
import akka.util.Timeout
import bindings._
import mesosphere.marathon.PrePostDriverCallback
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.state.{PathId, Timestamp}
import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}

class DSL(implicit val mat: Materializer, timeout: Timeout) {
  import helpers.Helpers._

  case class AppId(path: PathId) {
    override def toString(): String = s"AppId(${path})"
  }
  case class DeploymentId(id: String)
  type TaskId = Task.Id

  trait StringFormatter[T] extends (T => String) { def apply(v: T): String }
  object StringFormatter {
    def apply[T](fn: T => String): StringFormatter[T] = new StringFormatter[T] {
      override def apply(v: T): String = fn(v)
    }
  }
  implicit val TaskIdFormatter = StringFormatter[TaskId] { _.idString }
  implicit val AppIdFormatter = StringFormatter[AppId] { _.path.toString }
  implicit val DeploymentIdFormatter = StringFormatter[DeploymentId] { _.id }
  class QueryResult[T](val values: Seq[T])(implicit formatter: StringFormatter[T]) {
    def formattedValues: Seq[String] = values.map(formatter)
    override def toString(): String = {
      val b = new java.lang.StringBuilder
      b.append("Results:\n\n")
      formattedValues.foreach { v =>
        b.append("  ")
        b.append(v)
        b.append('\n')
      }
      b.toString
    }
  }

  object QueryResult {
    def apply[T](values: Seq[T])(implicit formatter: StringFormatter[T]) = new QueryResult[T](values)
  }

  implicit def stringToAppId(s: String): AppId = AppId(PathId(s))
  implicit def stringToPathId(s: String): PathId = PathId(s)

  implicit def appIdToPath(appId: AppId): PathId = appId.path

  def listApps(containing: String = null, limit: Int = Int.MaxValue)(
    implicit module: StorageToolModule, timeout: Timeout): QueryResult[AppId] = {
    val predicates = List(
      Option(containing).map { c => { pathId: PathId => pathId.toString.contains(c) } }
    ).flatten
    // TODO - purge related deployments?

    QueryResult {
      await(module.appRepository.allPathIds)
        .filter { app =>
          predicates.forall { p => p(app) }
        }
        .take(limit)
        .toList
        .sorted
        .map(AppId(_))
    }
  }

  def listTasks(
    forApp: AppId = null,
    containing: String = null,
    limit: Int = Int.MaxValue)(
    implicit module: StorageToolModule, timeout: Timeout): QueryResult[TaskId] = {
    val predicates: List[(String => Boolean)] = List(
      Option(containing).map { c =>
        { taskId: String => taskId.contains(c) }
      }
    ).flatten

    val input = (Option(forApp)) match {
      case Some(appId) =>
        module.taskRepository.tasksKeys(appId.path)
      case None =>
        module.taskRepository.allIds()
    }
    QueryResult {
      await(input)
        .filter { taskId =>
          predicates.forall { p => p(taskId) }
        }
        .take(limit)
        .toList
        .sorted
        .map(Task.Id(_))
    }
  }

  def listDeployments(
    limit: Int = Int.MaxValue)(
    implicit module: StorageToolModule,
      timeout: Timeout): QueryResult[DeploymentId] = {

    QueryResult {
      await(module.deploymentRepository.allIds())
        .take(limit)
        .toList
        .sorted
        .map(DeploymentId(_))
    }
  }

  trait PurgeStrategy[T] {
    val purgeDescription: String
    def `purge!`(values: Seq[T]): Unit
  }

  implicit def UnwrapQueryResult[T](qr: QueryResult[T]): Seq[T] = qr.values
  implicit def DeploymentPurgeStrategy(implicit module: StorageToolModule): PurgeStrategy[DeploymentId] = new PurgeStrategy[DeploymentId] {
    val purgeDescription = "deployments"
    override def `purge!`(values: Seq[DeploymentId]): Unit = {
      values.foreach { v =>
        module.deploymentRepository.expunge(v.id)
        println(s"Purged deployment: ${DeploymentIdFormatter(v)}")
      }
    }
  }
  implicit def TaskPurgeStrategy(implicit module: StorageToolModule): PurgeStrategy[TaskId] = new PurgeStrategy[TaskId] {
    val purgeDescription = "tasks"
    override def `purge!`(values: Seq[TaskId]): Unit = {
      values.foreach { v =>
        module.taskRepository.expunge(v.idString)
        println(s"Purged task: ${TaskIdFormatter(v)}")
      }
    }
  }

  implicit def AppPurgeStrategy(implicit module: StorageToolModule): PurgeStrategy[AppId] = new PurgeStrategy[AppId] {
    val purgeDescription = "apps and associated tasks"
    override def `purge!`(appIds: Seq[AppId]): Unit = {
      // Remove from rootGroup
      val rootGroup = await(module.groupRepository.rootGroup).get
      val now = Timestamp.now
      val newGroup = appIds.foldLeft(rootGroup) { (r, appId) =>
        r.update(appId.parent, { g => g.removeApplication(appId) }, now)
      }
      module.groupRepository.store(module.groupRepository.zkRootName, newGroup)
      println(s"Removed ${appIds.map(AppIdFormatter).toList} from root group")

      appIds.foreach { appId =>
        val tasks = listTasks(forApp = AppId(appId)).values
        TaskPurgeStrategy.`purge!`(tasks)
        module.appRepository.expunge(appId.path)
        println(s"Purged app ${appId}")
      }
    }
  }

  val NoPendingAction: Int => Unit = { _ => println(s"No pending action for confirmation") }
  var confirm: Int => Unit = NoPendingAction

  def setConfirmation(id: Int)(fn: => Unit): Unit = {
    confirm = { confirmId =>
      if (id!=confirmId)
        println(s"Confirmation ID did not match")
      else {
        confirm = NoPendingAction
        fn
      }
    }
    println(s"To confirm, type:\n\n  confirm(${id})")
  }

  def purge[T](values: Seq[T])(implicit purgeStrategy: PurgeStrategy[T], formatter: StringFormatter[T]): Unit = {
    println()
    println(s"Are you sure you wish to purge the following ${purgeStrategy.purgeDescription}?")
    println()
    val formattedValues = values.map(formatter)
    formattedValues.foreach { v => println(s"  ${v}") }
    println()
    setConfirmation(formattedValues.hashCode) {
      purgeStrategy.`purge!`(values)
      println()
      println("Done")
      println()
      println("Note: The leading Marathon will need to be restarted to see changes")
    }
  }
  def purge[T](queryResult: QueryResult[T])(implicit purgeStrategy: PurgeStrategy[T], formatter: StringFormatter[T]): Unit = {
    purge(queryResult.values)
  }

  def purge[T](value: T)(implicit purgeStrategy: PurgeStrategy[T], formatter: StringFormatter[T]): Unit = {
    purge(Seq(value))
  }

  def help: Unit = {
    println(s"""
Marathon State Surgey Tool
==========================

Commands:

  listApps(containing: String, limit: Int)

    description: Return (sorted) list apps in repository

    params:
      containing : List all apps with the specified string in the appId
      limit      : Limit number of apps returned

    example:

      listApps(containing = "store", limit = 5)

  listTasks(forApp: PathId, containing: String, limit: Int)

    description: List tasks
    params:
      containing : List all tasks containing the specified string in their id
      limit      : Limit number of tasks returned
      forApp     : List tasks pertaining to the specified appId

    example:

      listTasks(forApp = "/example", limit = 5)

  listDeployments(limit: Int)

    description: List deployments
    params:
      limit      : Limit number of tasks returned

    example:

      listDeployments(forApp = "/example", limit = 5)

  purge(items: T)

    description: Purge the specified items

    example:

      purge(listTasks(forApp = "/example"))
      purge(AppId("/example"))

  help

    description: Show this help
""")
  }

  def error(str: String): Nothing = {
    println(s"Error! ${str}")
    sys.exit(1)
    ???
  }
}
