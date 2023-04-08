import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.Event
import org.scalajs.dom.HTMLAnchorElement
import org.scalajs.dom.HTMLFormElement
import org.scalajs.dom.HTMLInputElement
import org.scalajs.dom.Node
import org.scalajs.dom.document

import java.net.URI
import scala.concurrent.Future

import concurrent.ExecutionContext.Implicits.global

val LOCAL_STORAGE_KEY_FOR_INSTANCE = "windymelt-mstdn-share-button-instance"
var textTemplate = "{}"
var hovering = false

val templateDOM = """
<style>
  a.mstdn-share-button {
    border-radius: 3px;
    background-color: #6364FF;
    color: white;
    padding: 5px;
    margin: 10px;
    text-decoration: none;
    font-weight: 600;
    display: inline-block;
    height: 14px;
    min-width: 14px;
  }
  img.mstdn-share-button-logo {
    height: 14px;
  }
  span.mstdn-share-button-text {
    
  }
  .mstdn-share-popup {
    border-radius: 5px;
    background-color: #2F0C7A;
    color: white;
    width: min-content;
    padding: 10px;
    margin: 10px;
    position: fixed;
  }
  .mstdn-share-instance-save-button {
    border-radius: 5px;
    background-color: #6364FF;
    color: white;
    border: none;
    padding: 5px;
    margin: 5px;
    text-decoration: none;
    display: inline-block;
  }
  .mstdn-share-popup input {
    background-color: white;
    color: black;
  }
  .hidden {
    display: none;
  }
</style>
<div class="js-mstdn-share-button-container">
  <a href="#" tabindex="-1" class="js-mstdn-share mstdn-share-button">
    <img class="mstdn-share-button-logo" src="https://raw.githubusercontent.com/windymelt/mastodon-share-button-scalajs/main/logo-white.svg" alt="Mastodon">
    <span class="js-mstdn-share-button-text mstdn-share-button-text">Share</span>
  </a>
  <div class="js-mstdn-share-popup mstdn-share-popup hidden">
    <form>
    <label for="mstdn-instance-origin">Mastodon ID?</label>
    <!-- https://github.com/mastodon/mastodon/blob/69378eac99c013a0db7d2d5ff9a54dfcc287d9ce/app/models/account.rb#L64 -->
    <input class="js-mstdn-instance-origin" name="mstdn-instance-origin" type="text" size="30" pattern="@[a-z0-9_]+([a-z0-9_\.-]+[a-z0-9_]+)?@.*" value="" placeholder="@username@pawoo.net" />
    <div><small><i>To open this window, hover over the button.</i></small></div>
    <div><small><i>ボタン上でマウスホバーするとこのウィンドウが開きます。</i></small></div>
    <a href="#" class="js-mstdn-share-button-save mstdn-share-instance-save-button" type="button">Save and share</a>
  </form>
  </div>
</div>
"""

@main def hello: Unit =
  document.addEventListener("DOMContentLoaded", onLoad _)

def replaceTagWithTemplate(): Unit =
  val templateNode = document.createElement("TEMPLATE")
  templateNode.innerHTML = templateDOM
  templateNode.classList.add("js-mstdn-share-button-template")
  document.body.appendChild(templateNode)
  val template = document.querySelector(".js-mstdn-share-button-template")
  val target = document.querySelectorAll(".js-mstdn-share-button")
  target.foreach { elem =>
    if (!elem.innerText.isEmpty()) {
      textTemplate = elem.innerText
    }
    elem.outerHTML = template.innerHTML
  }

def registerEvents(): Unit =
  val shareContainers =
    document.querySelectorAll(".js-mstdn-share-button-container")

  shareContainers.foreach { e =>
    val instanceSaveButton = e.querySelector(".js-mstdn-share-button-save")
    val instanceInput: HTMLInputElement = e
      .querySelector(".js-mstdn-instance-origin")
      .asInstanceOf[HTMLInputElement]

    val shareButton: HTMLAnchorElement =
      e.querySelector(".js-mstdn-share").asInstanceOf[HTMLAnchorElement]

    instanceSaveButton.addEventListener(
      "click",
      (ev) => {
        resolveAndSetAsDefaultInstanceHost(instanceInput.value).andThen(_ =>
          enableAnchor(shareButton)
          shareToDefaultInstance()
        )
      }
    )

    defaultInstance match
      case None => // We can do nothing. We show configuration box
        shareButton.addEventListener(
          "click",
          (_) =>
            val popup = document.querySelectorAll(".js-mstdn-share-popup")
            popup.foreach(_.classList.remove("hidden"))
        )
      case Some(value) =>
        shareButton.href = shareUrl(value, shareText)
        shareButton.target = "_blank"
        enableAnchor(shareButton)

    shareButton.addEventListener(
      "mouseover",
      _ => {
        hovering = true
        dom.window.setTimeout(
          hoverTimeout(() => {
            val popup = document.querySelectorAll(".js-mstdn-share-popup")
            popup.foreach(_.classList.remove("hidden"))
          }),
          1000
        )
      }
    )
    shareButton.addEventListener("mouseleave", _ => hovering = false)

    e.querySelector("form").asInstanceOf[HTMLFormElement].onsubmit = ev => {
      resolveAndSetAsDefaultInstanceHost(instanceInput.value).andThen(_ =>
        enableAnchor(shareButton)
        shareToDefaultInstance()
      )
      ev.stopPropagation()
      false
    }

    e.classList.remove("js-mstdn-share-button-container")
    e.classList.add("js-mstdn-share-button-container-extracted")
  }

def onLoad(ev: Event): Unit =
  replaceTagWithTemplate()
  registerEvents()

def shareToDefaultInstance(): Unit =
  val instanceOrigin = defaultInstance
  val popup = document.querySelectorAll(".js-mstdn-share-popup")

  instanceOrigin match
    case None => popup.foreach(_.classList.remove("hidden"))
    case Some(value) =>
      dom.window.open(
        shareUrl(value, shareText),
        "_blank"
      )

def enableAnchor(e: HTMLAnchorElement): Unit = try {
  e.attributes.removeNamedItem("tabindex")
} catch { case _: Exception => }

def defaultInstance: Option[String] =
  dom.window.localStorage.hasOwnProperty(LOCAL_STORAGE_KEY_FOR_INSTANCE) match
    case true =>
      Some(dom.window.localStorage.getItem(LOCAL_STORAGE_KEY_FOR_INSTANCE))
    case false => None

def resolveAndSetAsDefaultInstanceHost(userId: String): Future[Unit] =
  // webfinger
  import sttp.client3._
  val userIdWithoutAtSign = userId match
    case s"@$id" => id
    case _       => ???

  val domainOfUserId = userIdWithoutAtSign match
    case s"$name@$domain" => domain
    case _                => ???

  val request = basicRequest.get(
    uri"https://$domainOfUserId/.well-known/webfinger?resource=acct:$userIdWithoutAtSign"
  )
  val backend = FetchBackend()
  val response = request.send(backend)
  response.map { res =>
    import io.circe.syntax._
    import io.circe.parser._
    val parsedJson = res.body match
      case Left(value)  => throw new Exception("body failure")
      case Right(value) => parse(value)

    val firstAlias = parsedJson match
      case Left(value) => throw new Exception("JSON parsing failure")
      case Right(value) =>
        val aliases = (value \\ "aliases").head
        aliases.asArray.flatMap(_.head.asString)

    firstAlias match
      case Some(s"$origin/@$user") =>
        println(s"[Mastodon share button] detected $origin")
        setDefaultInstance(origin)
      case Some(value) => throw new Exception(s"match failure: $value")
      case None        => throw new Exception("JSON pointing failure")
  }

def setDefaultInstance(instance: String): Unit =
  dom.window.localStorage.setItem(LOCAL_STORAGE_KEY_FOR_INSTANCE, instance)

def shareText: String =
  val title =
    Option(document.querySelector("head title")).map(_.innerText).getOrElse("")
  textTemplate
    .toString()
    .replaceAllLiterally("{}", dom.window.location.toString())
    .replaceAllLiterally("{title}", title)

def shareUrl(origin: String, text: String): String =
  sttp.model.Uri(URI(s"$origin/share")).addParam("text", text).toString

def hoverTimeout(f: () => Unit): () => Unit = () => if (hovering) f()
