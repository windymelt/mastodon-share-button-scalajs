package io.github.windymelt.mstdnshare

import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.Event
import org.scalajs.dom.HTMLAnchorElement
import org.scalajs.dom.HTMLFormElement
import org.scalajs.dom.HTMLInputElement
import org.scalajs.dom.Node
import org.scalajs.dom.document
import com.raquo.laminar.api.L.{*, given}

import java.net.URI
import scala.concurrent.Future

import concurrent.ExecutionContext.Implicits.global

val LOCAL_STORAGE_KEY_FOR_INSTANCE = "windymelt-mstdn-share-button-instance"

val styleString = """
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
    margin-top: 30%;
    margin-bottom: 30%;
    margin-left: 30%;
    margin-right: 30%;
    position: sticky;
    top: 50%;
    left: 50%;
  }
  .mstdn-share-popup-screen {
    position: fixed;
    width: 100%;
    height: 100%;
    background-color: rgba(50, 47, 45, 0.9);
    top: 0;
    left: 0;
    transition: opacity .2s ease-out;
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
    opacity: 0;
    pointer-events: none;
  }
"""

val targetSelector: String = ".js-mstdn-share-button"

@main def hello: Unit =
  lazy val buttonContainer =
    dom.document.querySelector(targetSelector)
  val buttonElement = shareButton
  renderOnDomContentLoaded(buttonContainer, buttonElement)

val hiddenVar: Var[Boolean] = Var(true)
val originVar: Var[String] = Var(defaultInstanceString)
val currentOriginStream: Var[String] = Var("")

// State for waiting hover
enum DelayState:
  case Initial
  case Waiting

val delayStateVar: Var[DelayState] = Var(DelayState.Initial)
val enterHandler: EventBus[dom.MouseEvent] = EventBus[dom.MouseEvent]()
val enterTimeoutHandler = enterHandler.events.flatMap { _ =>
  EventStream.fromValue((), emitOnce = true).delay(1000)
}
val textTemplateVar: Var[String] = Var("{}")

def shareButton: HtmlElement = {
  val originalContainer = dom.document.querySelector(targetSelector)
  val originalContent = originalContainer.innerText
  originalContainer.innerText = ""
  if (!originalContent.isEmpty()) {
    textTemplateVar.set(originalContent)
  }

  div(
    styleTag(styleString),
    a(
      href := "#",
      tabIndex := -1,
      className := "mstdn-share-button",
      img(
        className := "mstdn-share-button-logo",
        src := "https://raw.githubusercontent.com/windymelt/mastodon-share-button-scalajs/main/logo-white.svg",
        alt := "Mastodon"
      ),
      span(
        className := "mstdn-share-button-text",
        "Share"
      ),
      onClick --> shareHandler,
      onMouseEnter --> enterHandler,
      onMouseLeave --> { _ => delayStateVar.set(DelayState.Initial) },
      enterHandler --> { _ => delayStateVar.set(DelayState.Waiting) },
      enterTimeoutHandler --> { _ =>
        if (delayStateVar.now() == DelayState.Waiting) {
          delayStateVar.set(DelayState.Initial)
          hiddenVar.set(false)
        }
      },
      modal
    )
  )
}

def modal: HtmlElement = {
  div(
    className <-- hiddenVar.signal.map(isHidden =>
      Option.when(isHidden)("hidden").toSeq ++ Seq("mstdn-share-popup-screen")
    ),
    onClick --> clickScreenHandler,
    modalBox
  )
}

def modalBox: HtmlElement = {
  div(
    className := "mstdn-share-popup",
    form(
      label(forId := "mstdn-instance-origin", "Mastodon ID?"),
      input(
        idAttr := "mstdn-instance-origin",
        `type` := "text",
        size := 30,
        value <-- currentOriginStream,
        onChange.mapToValue --> currentOriginStream,
        placeholder := "@username@pawoo.net"
      ),
      div(small(i("To open this window, hover over the button."))),
      div(small(i("ボタン上でマウスホバーするとこのウィンドウが開きます。"))),
      a(
        href := "#",
        className := "mstdn-share-instance-save-button",
        `type` := "button",
        onClick --> saveAndShareHandler,
        "Save and Share"
      ),
      onClick --> modalBoxClickHandler,
      onSubmit --> { ev =>
        ev.preventDefault(); ev.stopPropagation()
      }
    )
  )
}

val saveAndShareHandler: Observer[dom.MouseEvent] = Observer { ev =>
  resolveAndSetAsDefaultInstanceHost(currentOriginStream.now()).andThen { _ =>
    if (!originVar.now().isEmpty()) {
      dom.window.open(
        shareUrl(originVar.now(), shareText),
        "_blank"
      )
    }
    ev.stopPropagation()
    ev.preventDefault()
  }
}

val shareHandler: Observer[dom.MouseEvent] = Observer { ev =>
  if (originVar.now().isEmpty) {
    // no origin registered
    hiddenVar.set(false)
  } else {
    // already origin registered
    println("registered")
    dom.window.open(
      shareUrl(originVar.now(), shareText),
      "_blank"
    )
    ev.stopPropagation()
    ev.preventDefault()
  }
}

val clickScreenHandler: Observer[dom.MouseEvent] = Observer { ev =>
  hiddenVar.set(true)
  ev.stopPropagation()
  ev.preventDefault()
}
val modalBoxClickHandler: Observer[dom.MouseEvent] = Observer { ev =>
  ev.stopPropagation()
  ev.preventDefault()
}

def defaultInstanceString: String =
  dom.window.localStorage.hasOwnProperty(LOCAL_STORAGE_KEY_FOR_INSTANCE) match
    case true =>
      dom.window.localStorage.getItem(LOCAL_STORAGE_KEY_FOR_INSTANCE)
    case false => ""

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
  originVar.set(instance)

def shareText: String =
  val title =
    Option(document.querySelector("head title")).map(_.innerText).getOrElse("")
  textTemplateVar
    .now()
    .replaceAllLiterally("{}", dom.window.location.toString())
    .replaceAllLiterally("{title}", title)

def shareUrl(origin: String, text: String): String =
  sttp.model.Uri(URI(s"$origin/share")).addParam("text", text).toString
