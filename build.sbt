val scala3Version = "3.2.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "mastodon-share-button-scalajs",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.1.0"
    ),
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
  )
  .enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
