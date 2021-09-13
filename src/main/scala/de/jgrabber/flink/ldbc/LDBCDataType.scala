package de.jgrabber.flink.ldbc

import java.time.{LocalDate, OffsetDateTime}

sealed trait LDBCDataType {
  def parse(value: String): Any
}

object LDBCDataType {
  case object Id extends LDBCDataType {
    override def parse(value: String): Long = value.toLong
  }

  case object Integer64 extends LDBCDataType {
    override def parse(value: String): Long = value.toLong
  }

  case object Integer32 extends LDBCDataType {
    override def parse(value: String): Long = value.toInt
  }

  case object String extends LDBCDataType {
    override def parse(value: String): String = value
  }

  case object LongString extends LDBCDataType {
    override def parse(value: String): String = value
  }

  case object Text extends LDBCDataType {
    override def parse(value: String): String = value
  }

  case object Date extends LDBCDataType {
    override def parse(value: String): LocalDate = LocalDate.parse(value)
  }

  case object DateTime extends LDBCDataType {
    override def parse(value: String): OffsetDateTime =
      OffsetDateTime.parse(value)
  }

  case object StringList extends LDBCDataType {
    override def parse(value: String): List[String] = value.split(";").toList
  }

  case object LongStringList extends LDBCDataType {
    override def parse(value: String): List[String] = value.split(";").toList
  }
}
