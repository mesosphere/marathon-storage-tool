#!/usr/bin/env amm-2.11

import $file.helpers
import $file.version

import version.StorageToolVersion

import akka.actor.{ ActorSystem, ActorRefFactory, Scheduler }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, Sink}
import akka.util.Timeout
import com.codahale.metrics.MetricRegistry
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.leader.LeaderLatch
import org.rogach.scallop.ScallopOption
import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}

import mesosphere.marathon.PrePostDriverCallback
import mesosphere.marathon.Protos.StorageVersion
import mesosphere.marathon.core.base.{JvmExitsCrashStrategy, LifecycleState}
import mesosphere.marathon.core.instance.Instance
import mesosphere.marathon.core.storage.store.impl.zk.ZkPersistenceStore
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.state.PathId
import mesosphere.marathon.storage._
import mesosphere.marathon.storage.migration.{Migration, StorageVersions}
import mesosphere.marathon.storage.repository._

case class StorageToolModule(
  appRepository: AppRepository,
  podRepository: PodRepository,
  instanceRepository: InstanceRepository,
  deploymentRepository: DeploymentRepository,
  taskFailureRepository: TaskFailureRepository,
  groupRepository: GroupRepository,
  frameworkIdRepository: FrameworkIdRepository,
  runtimeConfigurationRepository: RuntimeConfigurationRepository,
  migration: Migration
)

class MarathonStorage(args: List[String] = helpers.InternalHelpers.argsFromEnv) {
  import helpers.Helpers._
  implicit val actorSystem = ActorSystem()
  implicit val actorMaterializer = ActorMaterializer()
  implicit val scheduler: Scheduler = actorSystem.scheduler
  implicit val timeout = Timeout(5.seconds)
  /*private*/ class ScallopStub[A](name: String, value: Option[A]) extends ScallopOption[A](name) {
    override def get = value
    override def apply() = value.get
  }

  /*private*/ object ScallopStub {
    def apply[A](value: Option[A]): ScallopStub[A] = new ScallopStub("", value)
    def apply[A](name: String, value: Option[A]): ScallopStub[A] = new ScallopStub(name, value)
  }

  /*private*/ class MyStorageConf(args: List[String] = Nil, override val availableFeatures: Set[String] = Set.empty) extends org.rogach.scallop.ScallopConf(args) with StorageConf {
    import StorageVersions.current
    version(s"Marathon Storage Tool ${StorageToolVersion} for storage version ${current.getMajor}.${current.getMinor}.${current.getPatch}")

    import org.rogach.scallop.exceptions._
    override def onError(e: Throwable): Unit = e match {
      case Help("") =>
        builder.printHelp
        sys.exit(0)
      case e => println(e)
    }

    override lazy val storeCache = ScallopStub(Some(false))
    override lazy val versionCacheEnabled = ScallopStub(Some(false))
    override lazy val defaultNetworkName = ScallopStub(Some("temp"))
  }

  private val config = new MyStorageConf(args); config.verify
  implicit lazy val storage = StorageConfig(config, LifecycleState.Ignore, JvmExitsCrashStrategy) match {
    case zk: CuratorZk => zk
  }
  implicit lazy val client = storage.client
  implicit lazy val underlyingModule = StorageModule(storage, "mesos-bridge-name")
  lazy val store = {
    val s: ZkPersistenceStore = underlyingModule.persistenceStore match {
      case persistenceStore: ZkPersistenceStore => persistenceStore
    }
    // We need to call this method before using the storage module if it is defined
    s.getClass.getMethods.find(_.getName == "markOpen").foreach { m =>
      m.invoke(s)
    }
    s
  }

  private lazy val initialModule = StorageToolModule(
    appRepository = AppRepository.zkRepository(store),
    podRepository = PodRepository.zkRepository(store),
    instanceRepository = underlyingModule.instanceRepository,
    deploymentRepository = underlyingModule.deploymentRepository,
    taskFailureRepository = underlyingModule.taskFailureRepository,
    groupRepository = underlyingModule.groupRepository,
    frameworkIdRepository = underlyingModule.frameworkIdRepository,
    runtimeConfigurationRepository = underlyingModule.runtimeConfigurationRepository,
    migration = underlyingModule.migration)

  implicit lazy val module = {
    assertStoreCompat(fail = true)
    initialModule
  }

  def unverifiedModule = {
    assertStoreCompat(fail = false)
    initialModule
  }

  def assertStoreCompat(fail: Boolean): Unit = {
    def formattedVersion(v: StorageVersion): String = s"${v.getMajor}.${v.getMinor}.${v.getPatch}-${v.getFormat}"
    val storageVersion = await(store.storageVersion()).getOrElse {
      error(s"Could not determine current storage version!")
    }
    if ((storageVersion.getMajor == StorageVersions.current.getMajor) &&
        (storageVersion.getMinor == StorageVersions.current.getMinor) &&
        (storageVersion.getPatch == StorageVersions.current.getPatch)) {
      println(s"Storage version ${formattedVersion(storageVersion)} matches tool version.")
    } else {
      val message = s"Storage version ${formattedVersion(storageVersion)} does not match tool version!"
      if (fail) sys.error(message)
      else println(message)
    }
  }
}