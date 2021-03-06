// Copyright (c) 2017 PSForever
package net.psforever.objects

import akka.actor.ActorRef
import net.psforever.objects.avatar.{Avatar, Certification}

import scala.concurrent.duration._
import net.psforever.objects.ce.{Deployable, DeployedItem}
import net.psforever.objects.zones.Zone
import net.psforever.packet.game.{DeployableInfo, DeploymentAction}
import net.psforever.types.PlanetSideGUID
import net.psforever.services.RemoverActor
import net.psforever.services.local.{LocalAction, LocalServiceMessage}

object Deployables {
  //private val log = org.log4s.getLogger("Deployables")

  object Make {
    def apply(item: DeployedItem.Value): () => PlanetSideGameObject with Deployable = cemap(item)

    private val cemap: Map[DeployedItem.Value, () => PlanetSideGameObject with Deployable] = Map(
      DeployedItem.boomer                 -> { () => new BoomerDeployable(GlobalDefinitions.boomer) },
      DeployedItem.he_mine                -> { () => new ExplosiveDeployable(GlobalDefinitions.he_mine) },
      DeployedItem.jammer_mine            -> { () => new ExplosiveDeployable(GlobalDefinitions.jammer_mine) },
      DeployedItem.spitfire_turret        -> { () => new TurretDeployable(GlobalDefinitions.spitfire_turret) },
      DeployedItem.spitfire_cloaked       -> { () => new TurretDeployable(GlobalDefinitions.spitfire_cloaked) },
      DeployedItem.spitfire_aa            -> { () => new TurretDeployable(GlobalDefinitions.spitfire_aa) },
      DeployedItem.motionalarmsensor      -> { () => new SensorDeployable(GlobalDefinitions.motionalarmsensor) },
      DeployedItem.sensor_shield          -> { () => new SensorDeployable(GlobalDefinitions.sensor_shield) },
      DeployedItem.tank_traps             -> { () => new TrapDeployable(GlobalDefinitions.tank_traps) },
      DeployedItem.portable_manned_turret -> { () => new TurretDeployable(GlobalDefinitions.portable_manned_turret) },
      DeployedItem.portable_manned_turret -> { () => new TurretDeployable(GlobalDefinitions.portable_manned_turret) },
      DeployedItem.portable_manned_turret_nc -> { () =>
        new TurretDeployable(GlobalDefinitions.portable_manned_turret_nc)
      },
      DeployedItem.portable_manned_turret_tr -> { () =>
        new TurretDeployable(GlobalDefinitions.portable_manned_turret_tr)
      },
      DeployedItem.portable_manned_turret_vs -> { () =>
        new TurretDeployable(GlobalDefinitions.portable_manned_turret_vs)
      },
      DeployedItem.deployable_shield_generator -> { () =>
        new ShieldGeneratorDeployable(GlobalDefinitions.deployable_shield_generator)
      },
      DeployedItem.router_telepad_deployable -> { () =>
        new TelepadDeployable(GlobalDefinitions.router_telepad_deployable)
      }
    ).withDefaultValue({ () => new ExplosiveDeployable(GlobalDefinitions.boomer) })
  }

  /**
    * Distribute information that a deployable has been destroyed.
    * The deployable may not have yet been eliminated from the game world (client or server),
    * but its health is zero and it has entered the conditions where it is nearly irrelevant.<br>
    * <br>
    * The typical use case of this function involves destruction via weapon fire, attributed to a particular player.
    * Contrast this to simply destroying a deployable by being the deployable's owner and using the map icon controls.
    * This function eventually invokes the same routine
    * but mainly goes into effect when the deployable has been destroyed
    * and may still leave a physical component in the game world to be cleaned up later.
    * That is the task `EliminateDeployable` performs.
    * Additionally, since the player who destroyed the deployable isn't necessarily the owner,
    * and the real owner will still be aware of the existence of the deployable,
    * that player must be informed of the loss of the deployable directly.
    * @see `DeployableRemover`
    * @see `Vitality.DamageResolution`
    * @see `LocalResponse.EliminateDeployable`
    * @see `DeconstructDeployable`
    * @param target the deployable that is destroyed
    * @param time length of time that the deployable is allowed to exist in the game world;
    *             `None` indicates the normal un-owned existence time (180 seconds)
    */
  def AnnounceDestroyDeployable(target: PlanetSideGameObject with Deployable, time: Option[FiniteDuration]): Unit = {
    val zone = target.Zone
    target.OwnerName match {
      case Some(owner) =>
        target.OwnerName = None
        zone.LocalEvents ! LocalServiceMessage(owner, LocalAction.AlertDestroyDeployable(PlanetSideGUID(0), target))
      case None => ;
    }
    zone.LocalEvents ! LocalServiceMessage(
      s"${target.Faction}",
      LocalAction.DeployableMapIcon(
        PlanetSideGUID(0),
        DeploymentAction.Dismiss,
        DeployableInfo(target.GUID, Deployable.Icon(target.Definition.Item), target.Position, PlanetSideGUID(0))
      )
    )
    zone.LocalEvents ! LocalServiceMessage.Deployables(RemoverActor.ClearSpecific(List(target), zone))
    zone.LocalEvents ! LocalServiceMessage.Deployables(RemoverActor.AddTask(target, zone, time))
  }

  /**
    * Collect all deployables previously owned by the player,
    * dissociate the avatar's globally unique identifier to remove turnover ownership,
    * and, on top of performing the above manipulations, dispose of any boomers discovered.
    * (`BoomerTrigger` objects, the companions of the boomers, should be handled by an external implementation
    * if they had not already been handled by the time this function is executed.)
    * @return all previously-owned deployables after they have been processed;
    *         boomers are listed before all other deployable types
    */
  def Disown(zone: Zone, avatar: Avatar, replyTo: ActorRef): List[PlanetSideGameObject with Deployable] = {
    val (boomers, deployables) =
      avatar.deployables
        .Clear()
        .map(zone.GUID)
        .collect { case Some(obj) => obj.asInstanceOf[PlanetSideGameObject with Deployable] }
        .partition(_.isInstanceOf[BoomerDeployable])
    //do not change the OwnerName field at this time
    boomers.collect({
      case obj: BoomerDeployable =>
        zone.LocalEvents.tell(
          LocalServiceMessage.Deployables(RemoverActor.AddTask(obj, zone, Some(0 seconds))),
          replyTo
        ) //near-instant
        obj.Owner = None
        obj.Trigger = None
    })
    deployables.foreach(obj => {
      zone.LocalEvents.tell(LocalServiceMessage.Deployables(RemoverActor.AddTask(obj, zone)), replyTo) //normal decay
      obj.Owner = None
    })
    boomers ++ deployables
  }

  /**
    * Initialize the deployables backend information.
    * @param avatar the player's core
    */
  def InitializeDeployableQuantities(avatar: Avatar): Boolean = {
    avatar.deployables.Initialize(avatar.certifications)
  }

  /**
    * Initialize the UI elements for deployables.
    * @param avatar the player's core
    */
  def InitializeDeployableUIElements(avatar: Avatar): List[(Int, Int, Int, Int)] = {
    avatar.deployables.UpdateUI()
  }

  /**
    * Compare sets of certifications to determine if
    * the requested `Engineering`-like certification requirements of the one group can be found in a another group.
    * @see `CertificationType`
    * @param sample the certifications to be compared against
    * @param test the desired certifications
    * @return `true`, if the desired certification requirements are met; `false`, otherwise
    */
  def constructionItemPermissionComparison(
                                            sample: Set[Certification],
                                            test: Set[Certification]
                                          ): Boolean = {
    import Certification._
    val engineeringCerts: Set[Certification] = Set(AssaultEngineering, FortificationEngineering)
    val testDiff: Set[Certification]         = test diff (engineeringCerts ++ Set(AdvancedEngineering))
    //substitute `AssaultEngineering` and `FortificationEngineering` for `AdvancedEngineering`
    val sampleIntersect = if (sample contains AdvancedEngineering) {
      engineeringCerts
    } else {
      sample intersect engineeringCerts
    }
    val testIntersect = if (test contains AdvancedEngineering) {
      engineeringCerts
    } else {
      test intersect engineeringCerts
    }
    (sample intersect testDiff equals testDiff) && (sampleIntersect intersect testIntersect equals testIntersect)
  }
}
