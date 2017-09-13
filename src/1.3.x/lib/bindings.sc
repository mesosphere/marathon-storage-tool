#!/usr/bin/env amm-2.11

import $file.helpers

import scala.annotation.tailrec
import akka.actor.{ ActorSystem, ActorRefFactory, Scheduler }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, Sink}
import akka.util.Timeout
import com.codahale.metrics.MetricRegistry
import mesosphere.marathon.Protos.StorageVersion
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.state._
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.leader.LeaderLatch
import org.rogach.scallop.ScallopOption
import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import mesosphere.marathon.{ZookeeperConf,MarathonConf}
import mesosphere.chaos.http.HttpConf
import mesosphere.util.state.FrameworkId

case class StorageToolModule(
  appRepository: AppRepository,
  taskRepository: TaskRepository,
  deploymentRepository: DeploymentRepository,
  taskFailureRepository: TaskFailureRepository,
  groupRepository: GroupRepository,
  frameworkIdStore: EntityStore[FrameworkId],
  migration: Migration
)

class MarathonStorage(args: List[String] = helpers.InternalHelpers.argsFromEnv) {
  import helpers.Helpers._
  implicit val actorSystem = ActorSystem()
  implicit val actorMaterializer = ActorMaterializer()
  implicit val scheduler: Scheduler = actorSystem.scheduler
  implicit val timeout = Timeout(5.seconds)
  implicit val executionContext = scala.concurrent.ExecutionContext.global

  object Internals {
    class ScallopStub[A](name: String, value: Option[A]) extends ScallopOption[A](name) {
      override def get = value
      override def apply() = value.get
    }

    object ScallopStub {
      def apply[A](value: Option[A]): ScallopStub[A] = new ScallopStub("", value)
      def apply[A](name: String, value: Option[A]): ScallopStub[A] = new ScallopStub(name, value)
    }

    class MyStorageConf(
      args: List[String] = Nil) extends
        org.rogach.scallop.ScallopConf(args) with ZookeeperConf { self =>
      import org.rogach.scallop.exceptions._
      override def onError(e: Throwable): Unit = e match {
        case Help("") =>
          builder.printHelp
          sys.exit(0)
        case e => println(e)
      }

      val marathonConf: MarathonConf = new MarathonConf {
        override lazy val internalStoreBackend = ScallopStub(Some("zk"))
        override lazy val storeCache = ScallopStub(Some(false))

        override lazy val zooKeeperCompressionEnabled = self.zooKeeperCompressionEnabled
        override lazy val zooKeeperCompressionThreshold = self.zooKeeperCompressionThreshold
        override lazy val zooKeeperMaxNodeSize = self.zooKeeperMaxNodeSize
        override lazy val zooKeeperMaxVersions = self.zooKeeperMaxVersions
        override lazy val zooKeeperSessionTimeout = self.zooKeeperSessionTimeout
        override lazy val zooKeeperTimeout = self.zooKeeperTimeout
        override lazy val zooKeeperUrl = self.zooKeeperUrl
      }
    }
    val config = new MyStorageConf(args); config.verify
    val marathonModule = new mesosphere.marathon.MarathonModule(
      config.marathonConf, new HttpConf {})

    implicit lazy val metrics: Metrics = new Metrics(new MetricRegistry)
    val store = marathonModule.provideStore()
    val taskStore = marathonModule.provideTaskStore(store, metrics)
    val taskFailureStore = marathonModule.provideTaskFailureStore(store, metrics)
    val appStore = marathonModule.provideAppStore(store, metrics)
    val groupStore = marathonModule.provideGroupStore(store, metrics)
    val frameworkIdStore = marathonModule.provideFrameworkIdStore(store, metrics)
    val deploymentPlanStore = marathonModule.provideDeploymentPlanStore(store, metrics)
    val deploymentRepository = marathonModule.provideDeploymentRepository(
      deploymentPlanStore, config.marathonConf, metrics)
    val taskRepository = marathonModule.provideTaskRepository(taskStore, metrics)
    val appRepository = marathonModule.provideAppRepository(appStore, metrics)
    val groupRepository = marathonModule.provideGroupRepository(groupStore, appRepository, metrics)
    val taskFailureRepository = marathonModule.provideTaskFailureRepository(taskFailureStore, metrics)
    val migration = new Migration(
      store, appRepository, groupRepository, taskRepository, deploymentRepository, config.marathonConf, metrics)
  }

  implicit val metrics = Internals.metrics
  implicit lazy val module = StorageToolModule(
    appRepository = Internals.appRepository,
    taskRepository = Internals.taskRepository,
    deploymentRepository = Internals.deploymentRepository,
    taskFailureRepository = Internals.taskFailureRepository,
    groupRepository = Internals.groupRepository,
    frameworkIdStore = Internals.frameworkIdStore,
    migration = Internals.migration
  )
  val store = Internals.store

  def assertStoreCompat: Unit = {
    def formattedVersion(v: StorageVersion): String = s"${v.getMajor}.${v.getMinor}.${v.getPatch}"
    val storageVersion = await(Internals.migration.currentStorageVersion)
    if ((storageVersion.getMajor == StorageVersions.current.getMajor) &&
      (storageVersion.getMinor == StorageVersions.current.getMinor) &&
      (storageVersion.getPatch == StorageVersions.current.getPatch)) {
      println(s"Storage version ${formattedVersion(storageVersion)} matches tool version.")
    } else {
      error(s"Storage version ${formattedVersion(storageVersion)} does not match tool version!")
    }
  }
}
