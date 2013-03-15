package scalaton.util

import scala.util.control.Exception._

import java.net.{URLEncoder, URLDecoder, URL}

import scalaz._
import Scalaz._
import Validation.fromTryCatch

trait UrlModule{
  private val leadingSlashRegex = "^[/]?".r

  def encode(u: String, encoding: String = "UTF-8"): Validation[String, String] =
    fromTryCatch(URLEncoder.encode(u, encoding)) fold (
      _ => "failed to encode string: %s".format(u).failure[String],
      _.success[String]
    )

  def encodeIfPossible(u: String, encoding: String = "UTF-8"): String =
    encode(u) fold (_ => u, identity)

  def decode(u: String, encoding: String = "UTF-8"): Validation[String, String] =
    fromTryCatch(URLDecoder.decode(u, encoding)) fold (
      _ => "failed to decode string: %s".format(u).failure[String],
      _.success[String]
    )

  def decodeIfPossible(u: String, encoding: String = "UTF-8"): String =
    decode(u) fold (_ => u, identity)


  def constructQueryString(queryParams: Map[String,String], encoding: String = "UTF-8"): String =
    queryParams.map{ case (k, v) =>
                     encodeIfPossible(k, encoding) + "=" +
                     encodeIfPossible(v, encoding) }.mkString("&")

  def parseQueryString(queryString: String, encoding: String = "UTF-8"): ValidationNEL[String,Map[String,String]] =
    queryString.split("&").foldLeft(Map[String,String]().successNel[String]){ (acc, next) =>
      val pair = next.split("=")
      if (pair.size == 2 && pair(0).nonEmpty)
        acc |+|  Map(decodeIfPossible(pair(0)) -> decodeIfPossible(pair(1))).successNel[String]
      else
        acc |+| "failed to parse key-value from \"%s\"".format(next).failureNel[Map[String,String]]
    }

  def apply(host: String, path: String = "", queryParams: Map[String,String] = Map.empty, port: Int = -1, protocol: String = "http") = {
    val file = leadingSlashRegex.replaceFirstIn(path, "/")
    val qry = (queryParams isEmpty) ? "" | ("?" + constructQueryString(queryParams))

    (new URL(protocol, host, port, file + qry)) toString
  }

  def unapply(u: String) = {
    val url = catching(classOf[Exception]).opt(new URL(u))

    url.some(u => ((u.getHost, u.getPath, parseQueryString(u.getQuery ?? ""), u.getPort, u.getProtocol)).success[String])
      .none("failed to parse url \"%s\"".format(u).failure[(String, String, ValidationNEL[String,Map[String,String]], Int, String)])
  }
}

object url
extends UrlModule
