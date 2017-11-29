package beam.log

import akka.actor.Actor
import akka.event.Logging._


class MonitoringLogger extends Actor {

  //val mon = context.actorOf(Props[MonitorEndpoint], "MonitorEndpoint")

  def receive = {
    case InitializeLogger(bus) => sender ! LoggerInitialized
    case Error(cause, logSource, logClass, message: String) =>
      //println(">> err -> " + message)
      println(format(logSource, message, Console.RED))
      //mon ! format(logSource, message, Console.RED)
    case Warning(logSource, logClass, message: String) =>
      //println(">>warn -> " + message)
      println(format(logSource, message, Console.YELLOW))
      //mon ! format(logSource, message, Console.YELLOW)
    case Info(logSource, logClass, message: String) =>
      //println(">>info -> " + message)
      println(format(logSource, message, Console.GREEN))
      //mon ! format(logSource, message, Console.GREEN)
    case Debug(logSource, logClass, message: String) =>
      //println(">>debug -> " + message)
      println(format(logSource, message, Console.GREEN))
      //mon ! format(logSource, message, Console.GREEN)
  }

  def format(src: String, msg: String, code: String) = s"${Console.BLUE}R> $code[${src.split('/').last}] ${Console.RESET}$msg"
}
