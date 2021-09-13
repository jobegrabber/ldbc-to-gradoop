package de.jgrabber.flink.ldbc

sealed trait LDBCEntity {
  def label: String
  def properties: Map[String, Any]
}

object LDBCEntity {
  case class LDBCEdge(
      label: String,
      sourceId: Long,
      sourceLabel: String,
      targetId: Long,
      targetLabel: String,
      properties: Map[String, Any]
  ) extends LDBCEntity

  case class LDBCVertex(
      id: Long,
      label: String,
      properties: Map[String, Any]
  ) extends LDBCEntity
}
