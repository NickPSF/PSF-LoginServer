// Copyright (c) 2017 PSForever
package net.psforever.packet.game.objectcreate

import net.psforever.packet.game.PlanetSideGUID
import net.psforever.packet.{Marshallable, PacketHelpers}
import scodec.Attempt.{Failure, Successful}
import scodec.{Attempt, Codec, Err}
import shapeless.HNil
import scodec.codecs._
import net.psforever.types.DriveState

import scala.collection.mutable.ListBuffer

/**
  * An `Enumeration` of the various formats that known structures that the stream of bits for `VehicleData` can assume.
  */
object VehicleFormat extends Enumeration {
  type Type = Value

  val
  Battleframe, //future expansion?
  Normal,
  Utility,
  Variant = Value
}

/**
  * A basic `Trait` connecting all of the vehicle data formats (excepting `Normal`/`None`).
  */
sealed trait SpecificVehicleData extends StreamBitSize

/**
  * The format of vehicle data for the type of vehicles that are considered "utility."
  * The vehicles in this category are two:
  * the advanced nanite transport, and
  * the advanced mobile station.
  * @param unk na
  */
final case class UtilityVehicleData(unk : Int) extends SpecificVehicleData {
  override def bitsize : Long = 6L
}

/**
  * A common format variant of vehicle data.
  * This category includes all flying vehicles and the ancient cavern vehicles.
  * @param unk na
  */
final case class VariantVehicleData(unk : Int) extends SpecificVehicleData {
  override def bitsize : Long = 8L
}

/**
  * A representation of a generic vehicle.<br>
  * <br>
  * Vehicles utilize their own packet to communicate position to the server, known as `VehicleStateMessage`.
  * This takes the place of `PlayerStateMessageUpstream` when the player avatar is in control;
  * and, it takes the place of `PlayerStateMessage` for other players when they are in control.
  * If the vehicle is sufficiently complicated, a `ChildObjectStateMessage` will be used.
  * This packet will control any turret(s) on the vehicle.
  * For very complicated vehicles, the packets `FrameVehicleStateMessage` and `VehicleSubStateMessage` will also be employed.
  * The tasks that these packets perform are different based on the vehicle that responds or generates them.
  * @param basic data common to objects
  * @param unk1 na
  * @param health the amount of health the vehicle has, as a percentage of a filled bar (255)
  * @param unk2 na
  * @param no_mount_points do not display entry points for the seats
  * @param driveState a representation for the current mobility state;
  *                   various vehicles also use this field to indicate "deployment," e.g., AMS
  * @param unk3 na
  * @param unk5 na
  * @param cloak if a cloakable vehicle is cloaked
  * @param unk4 na
  * @param inventory the seats, mounted weapons, and utilities (such as terminals) that are currently included;
  *                  will also include trunk contents
  * @param vehicle_type a modifier for parsing the vehicle data format differently;
  *                     defaults to `Normal`
  */
final case class VehicleData(basic : CommonFieldData,
                             unk1 : Int,
                             health : Int,
                             unk2 : Boolean,
                             no_mount_points : Boolean,
                             driveState : DriveState.Value,
                             unk3 : Boolean,
                             unk5 : Boolean,
                             cloak : Boolean,
                             unk4 : Option[SpecificVehicleData],
                             inventory : Option[InventoryData] = None
                            )(val vehicle_type : VehicleFormat.Value = VehicleFormat.Normal) extends ConstructorData {
  override def bitsize : Long = {
    val basicSize = basic.bitsize
    val extraBitsSize : Long = if(unk4.isDefined) { unk4.get.bitsize } else { 0L }
    val inventorySize = if(inventory.isDefined) { inventory.get.bitsize } else { 0L }
    24L + basicSize + extraBitsSize + inventorySize
  }
}

object VehicleData extends Marshallable[VehicleData] {
  /**
    * Overloaded constructor for specifically handling `Normal` vehicle format.
    * @param basic data common to objects
    * @param unk1 na
    * @param health the amount of health the vehicle has, as a percentage of a filled bar (255)
    * @param unk2 na
    * @param driveState a representation for the current mobility state;
    * @param unk3 na
    * @param unk4 na
    * @param inventory the seats, mounted weapons, and utilities (such as terminals) that are currently included
    * @return a `VehicleData` object
    */
  def apply(basic : CommonFieldData, unk1 : Int, health : Int, unk2 : Int, driveState : DriveState.Value, unk3 : Boolean, unk4 : Int, inventory : Option[InventoryData]) : VehicleData = {
    new VehicleData(basic, unk1, health, unk2>0, false, driveState, unk3, unk4>0, false, None, inventory)(VehicleFormat.Normal)
  }

  /**
    * Overloaded constructor for specifically handling `Utility` vehicle format.
    * @param basic data common to objects
    * @param unk1 na
    * @param health the amount of health the vehicle has, as a percentage of a filled bar (255)
    * @param unk2 na
    * @param driveState a representation for the current mobility state;
    * @param unk3 na
    * @param unk4 utility-specific field
    * @param unk5 na
    * @param inventory the seats, mounted weapons, and utilities (such as terminals) that are currently included
    * @return a `VehicleData` object
    */
  def apply(basic : CommonFieldData, unk1 : Int, health : Int, unk2 : Int, driveState : DriveState.Value, unk3 : Boolean, unk4 : UtilityVehicleData, unk5 : Int, inventory : Option[InventoryData]) : VehicleData = {
    new VehicleData(basic, unk1, health, unk2>0, false, driveState, unk3, unk5>0, false, Some(unk4), inventory)(VehicleFormat.Utility)
  }

  /**
    * Overloaded constructor for specifically handling `Variant` vehicle format.
    * @param basic data common to objects
    * @param unk1 na
    * @param health the amount of health the vehicle has, as a percentage of a filled bar (255)
    * @param unk2 na
    * @param driveState a representation for the current mobility state;
    * @param unk3 na
    * @param unk4 variant-specific field
    * @param unk5 na
    * @param inventory the seats, mounted weapons, and utilities (such as terminals) that are currently included
    * @return a `VehicleData` object
    */
  def apply(basic : CommonFieldData, unk1 : Int, health : Int, unk2 : Int, driveState : DriveState.Value, unk3 : Boolean, unk4 : VariantVehicleData, unk5 : Int, inventory : Option[InventoryData]) : VehicleData = {
    new VehicleData(basic, unk1, health, unk2>0, false, driveState, unk3, unk5>0, false, Some(unk4), inventory)(VehicleFormat.Variant)
  }

  private val driveState8u = PacketHelpers.createEnumerationCodec(DriveState, uint8L)

  /**
    * `Codec` for the "utility" format.
    */
  private val utility_data_codec : Codec[SpecificVehicleData] = {
    import shapeless.::
    uintL(6).hlist.exmap[SpecificVehicleData] (
      {
        case n :: HNil =>
          Successful(UtilityVehicleData(n).asInstanceOf[SpecificVehicleData])
      },
      {
        case UtilityVehicleData(n) =>
          Successful(n :: HNil)
        case _ =>
          Failure(Err("wrong kind of vehicle data object (wants 'Utility')"))
      }
    )
  }
  /**
    * `Codec` for the "variant" format.
    */
  private val variant_data_codec : Codec[SpecificVehicleData] = {
    import shapeless.::
    uint8L.hlist.exmap[SpecificVehicleData] (
      {
        case n :: HNil =>
          Successful(VariantVehicleData(n).asInstanceOf[SpecificVehicleData])
      },
      {
        case VariantVehicleData(n) =>
          Successful(n :: HNil)
        case _ =>
          Failure(Err("wrong kind of vehicle data object (wants 'Variant')"))
      }
    )
  }

  /**
    * Select an appropriate `Codec` in response to the requested stream format
    * @param vehicleFormat the requested format
    * @return the appropriate `Codec` for parsing that format
    */
  private def selectFormatReader(vehicleFormat : VehicleFormat.Value) : Codec[SpecificVehicleData] = vehicleFormat match {
    case VehicleFormat.Utility =>
      utility_data_codec
    case VehicleFormat.Variant =>
      variant_data_codec
    case _ =>
      Failure(Err(s"$vehicleFormat is not a valid vehicle format for parsing data")).asInstanceOf[Codec[SpecificVehicleData]]
  }

  def codec(vehicle_type : VehicleFormat.Value) : Codec[VehicleData] = {
    import shapeless.::
    (
      ("basic" | CommonFieldData.codec) >>:~ { com =>
        ("unk1" | uint2L) ::
          ("health" | uint8L) ::
          ("unk2" | bool) :: //usually 0
          ("no_mount_points" | bool) ::
          ("driveState" | driveState8u) :: //used for deploy state
          ("unk3" | bool) :: //unknown but generally false; can cause stream misalignment if set when unexpectedly
          ("unk4" | bool) ::
          ("cloak" | bool) :: //cloak as wraith, phantasm
          conditional(vehicle_type != VehicleFormat.Normal, "unk5" | selectFormatReader(vehicle_type)) :: //padding?
          optional(bool, "inventory" | custom_inventory_codec(InitialStreamLengthToSeatEntries(com.pos.vel.isDefined, vehicle_type)))
      }
      ).exmap[VehicleData] (
      {
        case basic :: u1 :: health :: u2 :: no_mount :: driveState :: u3 :: u4 :: u5 :: cloak :: inv :: HNil =>
          Attempt.successful(new VehicleData(basic, u1, health, u2, no_mount, driveState, u3, u4, u5, cloak, inv)(vehicle_type))

        case _ =>
          Attempt.failure(Err("invalid vehicle data format"))
      },
      {
        case obj @ VehicleData(basic, u1, health, u2, no_mount, driveState, u3, u4, cloak, Some(u5), inv) =>
          if(obj.vehicle_type == VehicleFormat.Normal) {
            Attempt.failure(Err("invalid vehicle data format; variable bits not expected; will ignore ..."))
          }
          else {
            Attempt.successful(basic :: u1 :: health :: u2 :: no_mount :: driveState :: u3 :: u4 :: cloak :: Some(u5) :: inv :: HNil)
          }

        case obj @ VehicleData(basic, u1, health, u2, no_mount, driveState, u3, u4, cloak, None, inv) =>
          if(obj.vehicle_type != VehicleFormat.Normal) {
            Attempt.failure(Err("invalid vehicle data format; variable bits expected"))
          }
          else {
            Attempt.successful(basic :: u1 :: health :: u2 :: no_mount :: driveState :: u3 :: u4 :: cloak :: None :: inv :: HNil)
          }

        case _ =>
          Attempt.failure(Err("invalid vehicle data format"))
      }
    )
  }

  /**
    * Distance from the length field of a vehicle creation packet up until the start of the vehicle's inventory data.
    * The only field excluded belongs to the original opcode for the packet.
    * The parameters outline reasons why the length of the stream would be different
    * and are used to determine the exact difference value.
    * @see `ObjectCreateMessage`
    * @param hasVelocity the presence of a velocity field - `vel` - in the `PlacementData` object for this vehicle
    * @param format the `Codec` subtype for this vehicle
    * @return the length of the bitstream
    */
  def InitialStreamLengthToSeatEntries(hasVelocity : Boolean, format : VehicleFormat.Type) : Long = {
    198 +
      (if(hasVelocity) { 42 } else { 0 }) +
      (format match {
        case VehicleFormat.Utility => 6
        case VehicleFormat.Variant => 8
        case _ => 0
      })
  }

  /**
    * Increment the distance to the next mounted player's `name` field with the length of the previous entry,
    * then calculate the new padding value for that next entry's `name` field.
    * @param base the original distance to the last entry
    * @param next the length of the last entry, if one was parsed
    * @return the padding value, 0-7 bits
    */
  def CumulativeSeatedPlayerNamePadding(base : Long, next : Option[StreamBitSize]) : Int = {
    PlayerData.CumulativeSeatedPlayerNamePadding(base + (next match {
      case Some(o) => o.bitsize
      case None => 0
    }))
  }

  /**
    * A special method of handling mounted players within the same inventory space as normal `Equipment` can be encountered.
    * Due to variable-length fields within `PlayerData` extracted from the input,
    * the distance of the bit(stream) vector to the initial inventory entry is calculated
    * to produce the initial value for padding the `PlayerData` object's name field.
    * After player-related entries have been extracted and processed in isolation,
    * the remainder of the inventory must be handled as standard inventory
    * and finally both groups must be repackaged into a single standard `InventoryData` object.
    * Due to the unique value for the mounted players that must be updated for each entry processed,
    * the entries are temporarily formatted into a linked list before being put back into a normal `List`.
    * @param length the distance in bits to the first inventory entry
    * @return a `Codec` that translates `InventoryData`
    */
  private def custom_inventory_codec(length : Long) : Codec[InventoryData] = {
    import shapeless.::
    (
      uint8 >>:~ { size =>
        uint2 ::
          (inventory_seat_codec(
            length, //length of stream until current seat
              PlayerData.CumulativeSeatedPlayerNamePadding(length) //calculated offset of name field in next seat
          ) >>:~ { seats =>
            PacketHelpers.listOfNSized(size - countSeats(seats), InternalSlot.codec).hlist
          })
      }
      ).xmap[InventoryData] (
      {
        case _ :: _ :: None :: inv :: HNil =>
          InventoryData(inv)

        case _ :: _ :: seats :: inv :: HNil =>
          InventoryData(unlinkSeats(seats) ++ inv)
      },
      {
        case InventoryData(inv) =>
          val (seats, slots) = inv.partition(entry => entry.objectClass == ObjectClass.avatar)
          inv.size :: 0 :: chainSeats(seats) :: slots :: HNil
      }
    )
  }

  /**
    * The format for the linked list of extracted mounted `PlayerData`.
    * @param seat data for this entry extracted via `PlayerData`
    * @param next the next entry
    */
  private case class InventorySeat(seat : Option[InternalSlot], next : Option[InventorySeat])

  /**
    * Look ahead at the next value to determine if it is an example of a player character
    * and would be processed as a `PlayerData` object.
    * Update the stream read position with each extraction.
    * Continue to process values so long as they represent player character data.
    * @param length the distance in bits to the current inventory entry
    * @param offset the padding value for this entry's player character's `name` field
    * @return a recursive `Codec` that translates subsequent `PlayerData` entries until exhausted
    */
  private def inventory_seat_codec(length : Long, offset : Int) : Codec[Option[InventorySeat]] = {
    import shapeless.::
    (
      PacketHelpers.peek(uintL(11)) >>:~ { objClass =>
        conditional(objClass == ObjectClass.avatar, seat_codec(offset)) >>:~ { seat =>
          conditional(objClass == ObjectClass.avatar, inventory_seat_codec(
          { //length of stream until next seat
            length + (seat match {
              case Some(o) => o.bitsize
              case None => 0
            })
          },
            VehicleData.CumulativeSeatedPlayerNamePadding(length, seat) //calculated offset of name field in next seat
          )).hlist
        }
      }
      ).exmap[Option[InventorySeat]] (
      {
        case _ :: None :: None :: HNil =>
          Successful(None)

        case _ :: slot :: Some(next) :: HNil =>
          Successful(Some(InventorySeat(slot, next)))
      },
      {
        case None =>
          Successful(0 :: None :: None :: HNil)

        case Some(InventorySeat(slot, None)) =>
          Successful(ObjectClass.avatar :: slot :: None :: HNil)

        case Some(InventorySeat(slot, next)) =>
          Successful(ObjectClass.avatar :: slot :: Some(next) :: HNil)
      }
    )
  }

  /**
    * Translate data the is verified to involve a player who is seated (mounted) to the parent object at a given slot.
    * The operation performed by this `Codec` is very similar to `InternalSlot.codec`.
    * @param pad the padding offset for the player's name;
    *            0-7 bits;
    *            this padding value must recalculate for each represented seat
    * @see `CharacterAppearanceData`<br>
    *       `VehicleData.InitialStreamLengthToSeatEntries`<br>
    *       `PlayerData.CumulativeSeatedPlayerNamePadding`
    * @return a `Codec` that translates `PlayerData`
    */
  private def seat_codec(pad : Int) : Codec[InternalSlot] = {
    import shapeless.::
    (
      ("objectClass" | uintL(11)) ::
        ("guid" | PlanetSideGUID.codec) ::
        ("parentSlot" | PacketHelpers.encodedStringSize) ::
        ("obj" | PlayerData.codec(pad))
      ).xmap[InternalSlot] (
      {
        case objectClass :: guid :: parentSlot :: obj :: HNil =>
          InternalSlot(objectClass, guid, parentSlot, obj)
      },
      {
        case InternalSlot(objectClass, guid, parentSlot, obj) =>
          objectClass :: guid :: parentSlot :: obj.asInstanceOf[PlayerData] :: HNil
      }
    )
  }

  /**
    * Count the number of entries in a linked list.
    * @param chain the head of the linked list
    * @return the number of entries
    */
  private def countSeats(chain : Option[InventorySeat]) : Int = {
    chain match {
      case Some(_) =>
        var curr = chain
        var count = 0
        do {
          val link = curr.get
          count += (if(link.seat.nonEmpty) { 1 } else { 0 })
          curr = link.next
        }
        while(curr.nonEmpty)
        count

      case None =>
        0
    }
  }

  /**
    * Transform a linked list of `InventorySlot` slot objects into a formal list of `InternalSlot` objects.
    * @param chain the head of the linked list
    * @return a proper list of the contents of the input linked list
    */
  private def unlinkSeats(chain : Option[InventorySeat]) : List[InternalSlot] = {
    var curr = chain
    val out = new ListBuffer[InternalSlot]
    while(curr.isDefined) {
      val link = curr.get
      link.seat match {
        case None =>
          curr = None
        case Some(seat) =>
          out += seat
          curr = link.next
      }
    }
    out.toList
  }

  /**
    * Transform a formal list of `InternalSlot` objects into a linked list of `InventorySlot` slot objects.
    * @param list a proper list of objects
    * @return a linked list composed of the contents of the input list
    */
  private def chainSeats(list : List[InternalSlot]) : Option[InventorySeat] = {
    list match {
      case Nil =>
        None
      case x :: Nil =>
        Some(InventorySeat(Some(x), None))
      case _ :: _ =>
        var link = InventorySeat(Some(list.last), None)
        list.reverse.drop(1).foreach(seat => {
          link = InventorySeat(Some(seat), Some(link))
        })
        Some(link)
    }
  }

  implicit val codec : Codec[VehicleData] = codec(VehicleFormat.Normal)
}
