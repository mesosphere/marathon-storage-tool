#!/usr/bin/env amm-2.11

import $file.helpers
import scala.annotation.tailrec

import akka.actor.{ ActorSystem, ActorRefFactory, Scheduler }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, Sink}
import akka.util.Timeout
import com.codahale.metrics.MetricRegistry
import mesosphere.marathon.Protos.StorageVersion
import mesosphere.marathon.core.instance.Instance
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.state.PathId
import mesosphere.marathon.storage._
import mesosphere.marathon.storage.migration.{StorageVersions, Migration}
import mesosphere.marathon.storage.repository._
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.leader.LeaderLatch
import org.rogach.scallop.ScallopOption
import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}

case class StorageToolModule(
  appRepository: AppRepository,
  podRepository: PodRepository,
  instanceRepository: InstanceRepository,
  deploymentRepository: DeploymentRepository,
  taskFailureRepository: TaskFailureRepository,
  groupRepository: GroupRepository,
  frameworkIdRepository: FrameworkIdRepository,
  migration: Migration
)

class MarathonStorage(args: List[String] = helpers.InternalHelpers.argsFromEnv) {
  import helpers.Helpers._
  implicit val actorSystem = ActorSystem()
  implicit val actorMaterializer = ActorMaterializer()
  implicit val scheduler: Scheduler = actorSystem.scheduler
  implicit val timeout = Timeout(5.seconds)
  implicit val executionContext = scala.concurrent.ExecutionContext.global

  private class ScallopStub[A](name: String, value: Option[A]) extends ScallopOption[A](name) {
    override def get = value
    override def apply() = value.get
  }

  private object ScallopStub {
    def apply[A](value: Option[A]): ScallopStub[A] = new ScallopStub("", value)
    def apply[A](name: String, value: Option[A]): ScallopStub[A] = new ScallopStub(name, value)
  }

  private class MyStorageConf(args: List[String] = Nil, override val availableFeatures: Set[String] = Set.empty) extends org.rogach.scallop.ScallopConf(args) with StorageConf {
    import org.rogach.scallop.exceptions._
    override def onError(e: Throwable): Unit = e match {
      case Help("") =>
        builder.printHelp
        sys.exit(0)
      case e => println(e)
    }

    override lazy val storeCache = ScallopStub(Some(false))
    override lazy val versionCacheEnabled = ScallopStub(Some(false))
  }

  implicit lazy val metrics: Metrics = new Metrics(new MetricRegistry)
  private val config = new MyStorageConf(args); config.verify
  implicit lazy val storage = StorageConfig(config) match {
    case zk: CuratorZk => zk
  }
  implicit lazy val client = storage.client
  implicit lazy val underlyingModule = StorageModule(storage, None)
  implicit lazy val module = StorageToolModule(
    appRepository = AppRepository.zkRepository(storage.store),
    podRepository = PodRepository.zkRepository(storage.store),
    instanceRepository = underlyingModule.instanceRepository,
    deploymentRepository = underlyingModule.deploymentRepository,
    taskFailureRepository = underlyingModule.taskFailureRepository,
    groupRepository = underlyingModule.groupRepository,
    frameworkIdRepository = underlyingModule.frameworkIdRepository,
    migration = underlyingModule.migration)


  def assertStoreCompat: Unit = {
    def formattedVersion(v: StorageVersion): String = s"${v.getMajor}.${v.getMinor}.${v.getPatch}"
    val storageVersion = await(storage.store.storageVersion()).getOrElse {
      error(s"Could not determine current storage version!")
    }
    if ((storageVersion.getMajor == StorageVersions.current.getMajor) &&
      (storageVersion.getMinor == StorageVersions.current.getMinor) &&
      (storageVersion.getPatch == StorageVersions.current.getPatch)) {
      println(s"Storage version ${formattedVersion(storageVersion)} matches tool version.")
    } else {
      error(s"Storage version ${formattedVersion(storageVersion)} does not match tool version!")
    }
  }
}
