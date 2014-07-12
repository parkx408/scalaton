package scalaton.async.akka.rabbit


import akka.actor._, SupervisorStrategy._
import com.rabbitmq.client._
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scalaton.async.akka._
import scalaz._, Scalaz._
import argonaut._, Argonaut._

object AMQP {
  case class Ack()
  case class Nack()

  sealed trait ExchangeType {
    def name: String
  }
  object ExchangeType {
    case object Direct extends ExchangeType { val name = "direct" }
  }
}

object RabbitManager {
  import AMQP._

  case object Connect
  case class DeclareExchange(exchange: String, exchangeType: ExchangeType = ExchangeType.Direct, isDurable: Boolean = false, autoDelete: Boolean = true, isInternal: Boolean = true, arguments: Map[String,java.lang.Object] = Map.empty)
  case class DeclareQueue(exchange: String, queue: String, routingKey: String = "", durable: Boolean = false, exclusive: Boolean = false, autoDelete: Boolean = true, arguments: Map[String,java.lang.Object] = Map.empty)
  case class BindQueue(exchange: String, queue: String, routingKey: String)

  case class ExchangeDeclared(exchange: String)
  case class QueueDeclared(exchange: String, queue: String)

  sealed trait State
  case class Connected(since: Long = System.currentTimeMillis) extends State
  case class Disconnected(since: Long = System.currentTimeMillis) extends State
  case object Idle extends State

  def queueName(exchange: String, queue: String) = s"${exchange}+${queue}+queue"
}

class RabbitManager(connF: ConnectionFactory) extends Actor with ActorLogging {
  import AMQP._
  import RabbitManager._

  val reconnectRetryInterval = 500.millis
  val maxDisconnectedDuration = 5.seconds


  private var connCh: Option[(Connection, Channel)] = None
  private var exchanges: Map[String,DeclareExchange] = Map.empty // exchange name -> exchange declaration
  private var queues: Map[(String,String),DeclareQueue] = Map.empty // (exchange name, queue name) -> queue declaration
  private var state: State = Idle

  lazy val receive = ({
    case Connect => connect()

    case de@DeclareExchange(exchange, exchangeType, isDurable, autoDelete, isInternal, arguments) =>
      connCh match {
        case Some((_, channel)) =>
          log.info(s"declaring exchange $exchange")
          exchanges = exchanges + (exchange -> de)
          log.debug(s"""exchanges (${exchanges.size}): ${exchanges.keys.toSeq.sorted.mkString(",")}""")
          channel.exchangeDeclare(exchange, exchangeType.name, isDurable, autoDelete, isInternal, arguments)
        case None =>
          log.error(s"no valid rabbit connection")
      }

    case dq@DeclareQueue(exchange, queue, routingKey, durable, exclusive, autoDelete, arguments) =>
      connCh match {
        case Some((_, channel)) =>
          log.info(s"declaring queue $queue on exchange $exchange")
          queues = queues + ((exchange, queue) -> dq)
          channel.queueDeclare(queue, durable, exclusive, autoDelete, arguments)
          self ! BindQueue(exchange, queue, routingKey)
        case None =>
          log.error(s"no valid rabbit connection")
      }

    case bq@BindQueue(exchange, queue, routingKey) =>
      connCh match {
        case Some((_, channel)) =>
          log.info(s"binding queue $queue on exchange $exchange with routing key $routingKey")
          channel.queueBind(queue, exchange, routingKey)
        case None =>
          log.error(s"no valid rabbit connection")
      }

  }: Receive).orElse(onError)

  lazy val onError: Receive = {
    case e: ShutdownSignalException =>
      log.warning(s"connection lost due to: ${e} - reconnecting")
      disconnected()
      reconnect(reconnectRetryInterval)
  }

  override def postStop(): Unit = {
    close()
  }




  def connected() = {
    state match {
      case Connected(_) =>
      case _ =>
        state = Connected()
    }
  }

  def disconnected() = {
    state = Disconnected()
    connCh = None
  }

  def idle() = {
    state = Idle
    connCh = None
  }

  def close() = {
    log.info("closing rabbit connections")
    connCh.foreach{ case (connection, channel) =>
      try {
        channel.close()
        connection.close()
      } catch {
        case _: Throwable =>
      }
    }
    idle()
  }

  def reconnect(t: FiniteDuration) = state match {
    case Connected(_) =>
    case Disconnected(since) if (System.currentTimeMillis - since).millis > maxDisconnectedDuration =>
      log.warning("exceeded maxDisconnectedDuration - idling")
      close()
    case _ =>
      if (state != Idle) log.info(s"reconnecting in $t")
      context.system.scheduler.scheduleOnce(t, self, Connect)(context.dispatcher)
  }


  def connect(): Unit = {
    try {
      connCh = connCh.map{ x =>
        log.debug(s"already connected to ${connCh}")
        x.some
      }.getOrElse{
        log.info(s"connecting to ${connF.toUri()}")

        val connection = connF.newConnection

        connection.addShutdownListener(new ShutdownListener {
          def shutdownCompleted(cause: ShutdownSignalException) = {
            self ! cause
          }
        })

        val channel = connection.createChannel
          (connection, channel).some
      }

      connected()

      log.debug(s"connected to ${connCh}")
    } catch {
      case _: java.net.ConnectException =>
        reconnect(reconnectRetryInterval)
    }
  }
}

abstract class RabbitQueue[Msg : EncodeJson : DecodeJson] extends Actor with ActorLogging {
  def akkaReceive: Receive
  def processMessage(msg: Msg): Unit

  lazy val receive: Receive = rabbitReceive.orElse(akkaReceive)

  lazy val rabbitReceive: Receive = {
    case s: String => s.decodeEither[Msg] match {
      case \/-(msg) => processMessage(msg)
      case -\/(_) => akkaReceive(s)
    }
  }
}

object Main extends App {
  import scala.concurrent.ExecutionContext.Implicits.global

  val system = ActorSystem("rabbit")
  val manager = system.actorOf(Props(new RabbitManager(new ConnectionFactory)))
  manager ! RabbitManager.Connect

  akka.pattern.after(30.seconds, system.scheduler)(Future{
    println("shutting down system...")
    system.shutdown
  })

}