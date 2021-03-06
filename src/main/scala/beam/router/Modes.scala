package beam.router

import enumeratum.values._
import org.matsim.api.core.v01.TransportMode
import com.conveyal.r5.api.util.LegMode
import com.conveyal.r5.api.util.TransitModes

import scala.collection.immutable


/**
  * [[ValueEnum]] containing all of the translations b/w BEAM <==> R5[[LegMode]] MATSim [[TransportMode]].
  *
  * Note: There is an implicit conversion
  *
  * Created by sfeygin on 4/5/17.
  */
object Modes {

  sealed abstract class BeamMode(val value: String, val r5Mode: Option[Either[LegMode,TransitModes]], val matsimMode: String) extends StringEnumEntry {
    def isTransit(): Boolean = isR5TransitMode(this)
  }

  object BeamMode extends StringEnum[BeamMode] with StringCirceEnum[BeamMode] {

    override val values: immutable.IndexedSeq[BeamMode] = findValues

    // Driving / Automobile-like (hailed rides are a bit of a hybrid)

    case object CAR extends BeamMode(value = "car", Some(Left(LegMode.CAR)), TransportMode.car)

    case object RIDEHAIL extends BeamMode(value = "ride_hailing", Some(Left(LegMode.CAR)), TransportMode.other)

    case object EV extends BeamMode(value = "ev", Some(Left(LegMode.CAR)), TransportMode.other)

    // Transit

    case object BUS extends BeamMode(value = "bus",Some(Right(TransitModes.BUS)), TransportMode.pt)

    case object FUNICULAR extends BeamMode(value = "funicular",  Some(Right(TransitModes.FUNICULAR)), TransportMode.pt)

    case object GONDOLA extends BeamMode(value = "gondola", Some(Right(TransitModes.GONDOLA)), TransportMode.pt)

    case object CABLE_CAR extends BeamMode(value = "cable_car", Some(Right(TransitModes.CABLE_CAR)), TransportMode.pt)

    case object FERRY extends BeamMode(value = "ferry", Some(Right(TransitModes.FERRY)), TransportMode.pt)

    case object TRANSIT extends BeamMode(value = "transit", Some(Right(TransitModes.TRANSIT)), TransportMode.pt)

    case object RAIL extends BeamMode(value = "rail", Some(Right(TransitModes.RAIL)), TransportMode.pt)

    case object SUBWAY extends BeamMode(value = "subway", Some(Right(TransitModes.SUBWAY)), TransportMode.pt)

    case object TRAM extends BeamMode(value = "tram", Some(Right(TransitModes.TRAM)), TransportMode.pt)

    // Non-motorized

    case object WALK extends BeamMode(value = "walk", Some(Left(LegMode.WALK)), TransportMode.walk)

    case object BIKE extends BeamMode(value = "bike", Some(Left(LegMode.BICYCLE)), TransportMode.walk)

    // Transit-specific non-motorized
    case object LEG_SWITCH extends BeamMode(value = "leg_switch", None, TransportMode.other) // This is kind-of like a transit walk, but not really... best to make leg_switch its own type

    case object WALK_TRANSIT extends BeamMode(value = "walk_transit", Some(Right(TransitModes.TRANSIT)), TransportMode.transit_walk)

    case object DRIVE_TRANSIT extends BeamMode(value = "drive_transit", Some(Right(TransitModes.TRANSIT)), TransportMode.pt)


    case object WAITING extends BeamMode(value = "waiting", None, TransportMode.other)

  }

  implicit def beamMode2R5Mode(beamMode: BeamMode): Either[LegMode,TransitModes] = beamMode.r5Mode.get

  def isR5TransitMode(beamMode: BeamMode): Boolean = {
    beamMode.r5Mode match {
      case Some(Right(_)) =>
        true
      case _ => false
    }
  }
  def isR5LegMode(beamMode: BeamMode): Boolean = {
    beamMode.r5Mode match {
      case Some(Left(_)) =>
        true
      case _ => false
    }
  }
  def isOnStreetTransit(beamMode: BeamMode): Boolean = {
    beamMode.r5Mode match {
      case Some(Left(streetMode)) =>
        false
      case Some(Right(transitMode)) =>
        transitMode match {
          case TransitModes.BUS =>
            true
          case _ =>
            false
        }
    }
  }

  def mapLegMode(mode: LegMode): BeamMode = mode match {
    case LegMode.BICYCLE | LegMode.BICYCLE_RENT => BeamMode.BIKE
    case LegMode.WALK => BeamMode.WALK
    case LegMode.CAR | LegMode.CAR_PARK => BeamMode.CAR
  }

  def mapTransitMode(mode: TransitModes): BeamMode = mode match  {
    case TransitModes.TRANSIT => BeamMode.TRANSIT
    case TransitModes.SUBWAY => BeamMode.SUBWAY
    case TransitModes.BUS => BeamMode.BUS
    case TransitModes.FUNICULAR => BeamMode.FUNICULAR
    case TransitModes.GONDOLA => BeamMode.GONDOLA
    case TransitModes.CABLE_CAR => BeamMode.CABLE_CAR
    case TransitModes.FERRY => BeamMode.FERRY
    case TransitModes.RAIL => BeamMode.RAIL
    case TransitModes.TRAM => BeamMode.TRAM
  }

  def filterForTransit(modes: Vector[BeamMode]): Vector[BeamMode] = modes.filter( mode =>  isR5TransitMode(mode))
  def filterForStreet(modes: Vector[BeamMode]): Vector[BeamMode] = modes.filter( mode =>  isR5LegMode(mode))

}
