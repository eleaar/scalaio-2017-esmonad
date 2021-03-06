package esmonad

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FinalJournals { self: FinalHandlers =>

  trait WriteJournal[EVENT] {
    def persist(events: Seq[EVENT]): Future[Unit]
  }

  trait Hydratable[STATE] {
    def hydrate(id: String): Future[Option[STATE]]
  }

  class DefaultJournal[STATE, EVENT](
    handler: EventHandler[STATE, EVENT],
    eventID: EVENT => String
  ) extends WriteJournal[EVENT] with Hydratable[STATE] {
    private var journal = Seq.empty[EVENT]

    override def persist(event: Seq[EVENT]): Future[Unit] = Future {
      synchronized { journal = journal ++ event }
    }
    def journal(id: String): Future[Seq[EVENT]] = Future {
      synchronized { journal.filter(event => eventID(event) == id) }
    }
    override def hydrate(id: String): Future[Option[STATE]] =
      journal(id).map(_.foldLeft(Option.empty[STATE])(handler))
  }

  def hydrate[STATE : Hydratable](id: String): Future[Option[STATE]] =
    implicitly[Hydratable[STATE]].hydrate(id)

  def persist[EVENT : WriteJournal](events: Seq[EVENT]): Future[Unit] =
    implicitly[WriteJournal[EVENT]].persist(events)

}

// Complete Event Sourced Model
trait FinalModels extends FinalEvents with FinalHandlers with FinalJournals {

  case class Turtle(id: String, pos: Position, dir: Direction)

  private def withinRange(pos: Position): Boolean = pos.x.abs < 100 && pos.y.abs < 100

  object Turtle {
    def create(id: String, pos: Position, dir: Direction): Either[String, TurtleEvent] =
      if (withinRange(pos)) Right(Create(id, pos, dir))
      else Left("Too far away")

    def turn(rot: Rotation)(turtle: Turtle): Either[String, TurtleEvent] =
      Right(Turn(turtle.id, rot))

    def walk(dist: Int)(turtle: Turtle): Either[String, TurtleEvent] = {
      val newPos = turtle.pos.move(turtle.dir, dist)
      if (withinRange(newPos)) Right(Walk(turtle.id, dist))
      else Left("Too far away")
    }

    implicit val handler = EventHandler[Turtle, TurtleEvent] {
      case (None, Create(id, pos, dir)) => Turtle(id, pos, dir)
      case (Some(t), Turn(id, rot)) if id == t.id =>
        t.copy(dir = t.dir.rotate(rot))
      case (Some(t), Walk(id, dist)) if id == t.id =>
        t.copy(pos = t.pos.move(t.dir, dist))
    }

  }

  implicit object TurtleJournal extends DefaultJournal[Turtle, TurtleEvent](Turtle.handler, _.id)

}

object V3_2 extends FinalModels
