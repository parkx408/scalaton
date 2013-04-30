/*
 Copyright 2013 Elliot Chow

 Licensed under the Apache License, Version 2.0 (the "License")
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

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
    encode(u, encoding) fold (_ => u, identity)

  def decode(u: String, encoding: String = "UTF-8"): Validation[String, String] =
    fromTryCatch(URLDecoder.decode(u, encoding)) fold (
      _ => "failed to decode string: %s".format(u).failure[String],
      _.success[String]
    )

  def decodeIfPossible(u: String, encoding: String = "UTF-8"): String =
    decode(u, encoding) fold (_ => u, identity)


  def constructQueryString(queryParams: Map[String,String], encoding: String = "UTF-8"): String =
    queryParams.map{ case (k, v) =>
                     encodeIfPossible(k, encoding) + "=" +
                     encodeIfPossible(v, encoding) }.mkString("&")

  def parseQueryString(queryString: String, encoding: Option[String] = "UTF-8".some): ValidationNel[String,Map[String,String]] =
    str.splitByChar(queryString, '&').view.filter(_.nonEmpty).foldLeft(Map[String,String]().successNel[String]){ (acc, next) =>
      val pair = str.splitByChar(next, '=')
      if (pair.size == 2 && pair(0).nonEmpty){
        for{
          map <- acc
        } yield {
          map ++ Map(encoding.some(e => decodeIfPossible(pair(0), e)).none(pair(0)) ->
                     encoding.some(e => decodeIfPossible(pair(1), e)).none(pair(1)))
        }
      }else
        acc |+| "failed to parse key-value from \"%s\"".format(next).failureNel[Map[String,String]]
    }

  def apply(host: String, path: String = "", queryParams: Map[String,String] = Map.empty, port: Int = -1, protocol: String = "http") = {
    val file = leadingSlashRegex.replaceFirstIn(path, "/")
    val qry = (queryParams isEmpty) ? "" | ("?" + constructQueryString(queryParams))

    (new URL(protocol, host, port, file + qry)) toString
  }

  def apply(x: (String, String, Map[String,String], Int, String)): String = apply(x._1, x._2, x._3, x._4, x._5)


  def parse(u: String) = {
    val url = catching(classOf[Exception]).opt(new URL(u))

    // (host, path, queryParams, port, protocol)
    url.some(u => ((u.getHost, u.getPath, parseQueryString(u.getQuery ?? ""), u.getPort, u.getProtocol)).success[String])
      .none("failed to parse url \"%s\"".format(u).failure[(String, String, ValidationNel[String,Map[String,String]], Int, String)])
  }
}

object url
extends UrlModule
