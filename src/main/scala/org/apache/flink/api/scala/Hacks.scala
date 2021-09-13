package org.apache.flink.api.scala

import org.apache.flink.api.java.{DataSet => JavaDataSet}

object Hacks {
  implicit class ScalaDataSetWithPublicJavaDataSet[T](val dataSet: DataSet[T]) {
    def getJavaDataSet: JavaDataSet[T] = dataSet.javaSet
  }
}
