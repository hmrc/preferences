import play.sbt.PlayImport.*
import play.sbt.routes.RoutesKeys.routesImport
import sbt.{ Def, * }

private object AppDependencies {
  private val hmrcMongoVersion = "2.12.0"
  private val bootstrapVersion = "10.7.0"
  private val pekkoVersion = "1.0.3"
  private val hmrcDomainVersion = "13.0.0"

  def apply(): Seq[ModuleID] = Seq(
    ws,
    "org.typelevel"     %% "cats-core"                         % "2.13.0",
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30"         % bootstrapVersion,
    "uk.gov.hmrc"       %% "domain-play-30"                    % hmrcDomainVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-work-item-repo-play-30" % hmrcMongoVersion,
    "net.codingwell"    %% "scala-guice"                       % "6.0.0",
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"            % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30"           % hmrcMongoVersion % Test,
    // Core Pekko Stream testkit
    "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion % Test,
    // Classic actor testkit (for TestKit base class)
    "org.apache.pekko" %% "pekko-testkit"             % pekkoVersion      % Test,
    "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion      % Test,
    "uk.gov.hmrc"      %% "domain-test-play-30"       % hmrcDomainVersion % Test
  )

  val it: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVersion % "it/test",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % "it/test"
  )

  val playSettings: Seq[Def.Setting[Seq[String]]] = Seq(
    routesImport ++= Seq(
      "uk.gov.hmrc.preferences._",
      "uk.gov.hmrc.preferences.model._",
      "uk.gov.hmrc.domain._"
    )
  )

  val overrides: Seq[ModuleID] = Seq(
    "ch.qos.logback"          % "logback-classic"    % "1.5.16",
    "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"
  )
}
