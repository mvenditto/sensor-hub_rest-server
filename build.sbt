name := "sh_rest-service"

version := "0.1"

scalaVersion := "2.12.4"

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
  "org.json4s" % "json4s-jackson_2.12" % "3.6.0-M2",
  "io.reactivex" %% "rxscala" % "0.26.5",
  "io.reactivex.rxjava2" % "rxjava" % "2.1.10",
  "com.github.pureconfig" %% "pureconfig" % "0.9.0",
  "io.javalin" % "javalin" % "1.6.1"
)