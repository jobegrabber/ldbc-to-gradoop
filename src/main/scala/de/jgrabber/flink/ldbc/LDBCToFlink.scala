package de.jgrabber.flink.ldbc

import com.typesafe.scalalogging.LazyLogging
import de.jgrabber.flink.ldbc.LDBCEntity.{LDBCEdge, LDBCVertex}
import org.apache.flink.api.java.io.RowCsvInputFormat
import org.apache.flink.api.scala.Hacks.ScalaDataSetWithPublicJavaDataSet
import org.apache.flink.api.scala.typeutils.Types
import org.apache.flink.api.scala.{DataSet, ExecutionEnvironment, _}
import org.apache.flink.core.fs.{FileSystem, Path}
import org.apache.flink.types.Row
import org.gradoop.common.model.impl.properties.{Properties, PropertyValue}
import org.gradoop.flink.io.impl.csv.CSVDataSink
import org.gradoop.flink.io.impl.graph.GraphDataSource
import org.gradoop.flink.io.impl.graph.tuples.{ImportEdge, ImportVertex}
import org.gradoop.flink.util.GradoopFlinkConfig
import pureconfig.ConfigReader
import pureconfig.ConfigSource.default.loadOrThrow
import pureconfig.generic.auto._
import pureconfig.generic.semiauto.deriveEnumerationReader

import java.net.URI
import java.time.OffsetDateTime
import java.util
import scala.collection.JavaConverters._
import scala.util.chaining.scalaUtilChainingOps

object LDBCToFlink extends LazyLogging {
  implicit val ldbcDataTypeConfigReader: ConfigReader[LDBCDataType] =
    deriveEnumerationReader[LDBCDataType]

  def main(args: Array[String]): Unit = {
    val env = ExecutionEnvironment.getExecutionEnvironment

    val config = loadOrThrow[Config]

    logger.info(s"Using pipeline configuration: $config")

    val fs = FileSystem
      .get(config.inputUri)

    val entityConfigToCSVs: Map[EntityCSVConfig, Path] =
      (config.edges ++ config.vertices).map { entityConfig =>
        val absolutePath =
          new Path(config.inputUri.getPath, entityConfig.relativePath)
        if (!fs.exists(absolutePath)) {
          val msg =
            s"Could not find directory '${entityConfig.relativePath}' in '${config.inputUri}!"
          logger.error(msg)
          throw new IllegalArgumentException(msg)
        }

        val fileStatus = fs.getFileStatus(absolutePath)

        if (fileStatus.isDir)
          entityConfig -> fileStatus.getPath
        else {
          val msg =
            s"Found file with name '${entityConfig.relativePath}' in '${config.inputUri}, but expected a directory!"
          logger.error(msg)
          throw new IllegalArgumentException(msg)
        }
      }.toMap

    val entityConfigsToRows = entityConfigToCSVs
      .map { case (entityConfig, csvPath) =>
        val typeInformations =
          Array.fill(entityConfig.columns.length)(Types.STRING)

        val inputFormat =
          new RowCsvInputFormat(csvPath, typeInformations.toArray)

        inputFormat.setFieldDelimiter("|")

        inputFormat
          .setSkipFirstLineAsHeader(true)

        entityConfig -> env.createInput(inputFormat)
      }
      .map { case (entityConfig, rows) =>
        entityConfig -> rows
          .map { row =>
            entityConfig.columns.zipWithIndex
              .foldLeft(Row.withNames()) {
                case (newRow, ((name, ldbcDataType), idx)) =>
                  newRow.tap(
                    _.setField(
                      name,
                      ldbcDataType.parse(row.getFieldAs[String](idx))
                    )
                  )
              }
          }
      }

    val ldbcVertices = entityConfigsToRows
      .collect { case (config: VerticesCSVConfig, rows) =>
        rows.map { row =>
          LDBCVertex(
            id = row.getFieldAs[Long](config.idColumn),
            label = config.label,
            properties = config.columns
              .filter(_._1 != config.idColumn)
              .map {
                case (key, dataType) if dataType == LDBCDataType.DateTime =>
                  key -> row
                    .getField(key)
                    .asInstanceOf[OffsetDateTime]
                    .toLocalDateTime // Gradoop doesn't know OffsetDateTime
                case (key, _) =>
                  key -> row.getField(key)
              }
              .toMap
          )
        }
      }
      .reduce((a: DataSet[LDBCVertex], b: DataSet[LDBCVertex]) => a union b)

    val ldbcEdges = entityConfigsToRows
      .collect { case (config: EdgesCSVConfig, rows) =>
        rows
          .map { row =>
            LDBCEdge(
              sourceId = row.getFieldAs[Long](config.sourceVertexIdColumn),
              sourceLabel = config.sourceVertexLabel,
              targetId = row.getFieldAs[Long](config.targetVertexIdColumn),
              targetLabel = config.targetVertexLabel,
              label = config.label,
              properties = config.columns
                .diff(
                  Seq(
                    config.sourceVertexIdColumn,
                    config.targetVertexIdColumn
                  )
                )
                .map {
                  case (key, dataType) if dataType == LDBCDataType.DateTime =>
                    key -> row
                      .getField(key)
                      .asInstanceOf[OffsetDateTime]
                      .toLocalDateTime // Gradoop doesn't know OffsetDateTime
                  case (key, _) =>
                    key -> row.getField(key)
                }
                .toMap
            )
          }
      }
      .reduce((a: DataSet[LDBCEdge], b: DataSet[LDBCEdge]) => a union b)

    val importVertices: DataSet[ImportVertex[String]] = ldbcVertices
      .map { ldbcVertex =>
        new ImportVertex[String](
          s"${ldbcVertex.label}-${ldbcVertex.id}",
          ldbcVertex.label,
          ldbcVertex.properties.foldLeft(Properties.create()) {
            case (properties, (key, value)) =>
              value match {
                case multiValue: List[_] =>
                  val props: util.List[PropertyValue] =
                    multiValue.map(PropertyValue.create).asJava
                  properties.set(key, props)
                case _ =>
                  properties.set(key, value)
              }
              properties
          }
        )
      }

    val importEdges = ldbcEdges
      .flatMap { ldbcEdge =>
        val sourceId = s"${ldbcEdge.sourceLabel}-${ldbcEdge.sourceId}"
        val targetId = s"${ldbcEdge.targetLabel}-${ldbcEdge.targetId}"

        val forwardEdge = new ImportEdge[String](
          s"$sourceId-${ldbcEdge.label}-$targetId",
          sourceId,
          targetId,
          ldbcEdge.label,
          ldbcEdge.properties.foldLeft(Properties.create()) {
            case (properties, (key, value)) =>
              value match {
                case multiValue: List[_] =>
                  val props = multiValue.map(PropertyValue.create).asJava
                  properties.tap { _.set(key, props) }
                case _ =>
                  properties.tap { _.set(key, value) }
              }
          }
        )

        // knows is a bidirectional relationship, but only encoded unidirectional in the LDBC dataset
        if (ldbcEdge.label == "knows") {
          val backwardEdge = new ImportEdge[String](
            s"$targetId-${ldbcEdge.label}-$sourceId",
            targetId,
            sourceId,
            ldbcEdge.label,
            forwardEdge.getProperties
          )
          Seq(forwardEdge, backwardEdge)
        }
        Seq(forwardEdge)
      }

    val gradoopFlinkConfig = GradoopFlinkConfig.createConfig(env.getJavaEnv)

    val gradoopDataSource =
      new GraphDataSource[String](
        importVertices.getJavaDataSet,
        importEdges.getJavaDataSet,
        gradoopFlinkConfig
      )

    new CSVDataSink(config.outputUri.toString, gradoopFlinkConfig)
      .write(gradoopDataSource.getLogicalGraph)

    env.execute("new-ldbc-to-flink")
  }

  case class Config(
      inputUri: URI,
      outputUri: URI,
      edges: Set[EdgesCSVConfig],
      vertices: Set[VerticesCSVConfig]
  )

  sealed trait EntityCSVConfig {
    def label: String

    def relativePath: Path

    def columns: Seq[(String, LDBCDataType)]

    def isStatic: Boolean
  }

  case class EdgesCSVConfig(
      label: String,
      sourceVertexLabel: String,
      targetVertexLabel: String,
      sourceVertexIdColumn: String,
      targetVertexIdColumn: String,
      isStatic: Boolean = false,
      columns: Seq[(String, LDBCDataType)]
  ) extends EntityCSVConfig {
    require(
      Seq(
        sourceVertexIdColumn -> LDBCDataType.Id,
        targetVertexIdColumn -> LDBCDataType.Id
      )
        .forall(columns.contains),
      "columns of a EdgesCSVConfig must contain the ID, source vertex ID and target vertex ID columns as specified"
    )
    lazy val relativePath: Path = new Path(
      if (isStatic) "static" else "dynamic",
      s"${sourceVertexLabel}_${label}_$targetVertexLabel"
    )
  }

  case class VerticesCSVConfig(
      label: String,
      idColumn: String = "id",
      isStatic: Boolean = false,
      columns: Seq[(String, LDBCDataType)]
  ) extends EntityCSVConfig {
    require(
      columns.contains(idColumn -> LDBCDataType.Id),
      "columns of a VerticesCSVConfig must contain the ID column as specified"
    )
    lazy val relativePath: Path =
      new Path(if (isStatic) "static" else "dynamic", label)
  }

}
