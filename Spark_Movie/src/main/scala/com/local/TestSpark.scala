package com.local

object TestSpark {
  def main(args: Array[String]): Unit = {
    println("Testing Spark...")
    
    System.setProperty("HADOOP_USER_NAME", "test")
    System.setProperty("hadoop.security.authentication", "simple")
    System.setProperty("spark.hadoop.security.authentication", "simple")
    
    try {
      import org.apache.spark.SparkConf
      import org.apache.spark.SparkContext
      
      val conf = new SparkConf()
        .setAppName("TestSpark")
        .setMaster("local[*]")
        .set("spark.driver.allowMultipleContexts", "true")
        .set("spark.hadoop.security.authentication", "simple")
        .set("spark.executor.extraJavaOptions", "--add-opens java.base/javax.security.auth=ALL-UNNAMED")
        .set("spark.driver.extraJavaOptions", "--add-opens java.base/javax.security.auth=ALL-UNNAMED")
      
      val sc = new SparkContext(conf)
      println("SparkContext created successfully!")
      
      val rdd = sc.parallelize(Array(1, 2, 3, 4, 5))
      val sum = rdd.sum()
      println(s"Sum: $sum")
      
      sc.stop()
      println("Spark test completed successfully!")
    } catch {
      case e: Exception => 
        println(s"Error: ${e.getMessage}")
        e.printStackTrace()
    }
  }
}