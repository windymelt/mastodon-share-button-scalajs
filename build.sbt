import org.scalajs.linker.interface.ModuleSplitStyle

val scala3Version = "3.3.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "mastodon-share-button-scalajs",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.1.0",
      "com.softwaremill.sttp.client3" %%% "core" % "3.8.8",
      "com.raquo" %%% "laminar" % "16.0.0",
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core" % "2.23.4",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.23.4" % "compile-internal"
    ),
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(
          ModuleSplitStyle.FewestModules
        )
    }
  )
  .enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
