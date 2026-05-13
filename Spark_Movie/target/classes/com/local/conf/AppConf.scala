package com.local.conf

import com.local.utils.LoggerLevels
import org.apache.spark._
import org.apache.spark.sql._

trait AppConf {
  LoggerLevels.setStreamingLogLevels()

  System.setProperty("HADOOP_USER_NAME", "root")
  
  val spark = SparkSession.builder()
    .appName("AppConf")
    .master("local[*]")
    .getOrCreate()
  
  val sc = spark.sparkContext
  val sqlContext = spark.sqlContext
}