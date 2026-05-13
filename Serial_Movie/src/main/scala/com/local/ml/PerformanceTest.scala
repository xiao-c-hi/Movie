package com.local.ml

import com.local.caseclass.Rating
import org.jblas.DoubleMatrix
import org.jblas.Solve
import scala.io.Source

/**
  * 串行版本性能测试
  * 用于对比串并行效果差异
  */
object PerformanceTest {
  
  def main(args: Array[String]): Unit = {
    // 测试不同数据规模的性能
    val dataSizes = List(500, 1000, 2000)
    
    println("=" * 80)
    println("Serial ALS Performance Test")
    println("=" * 80)
    
    dataSizes.foreach { size =>
      println(s"\nTesting with $size ratings...")
      
      // 生成测试数据
      val ratings = generateTestData(size)
      
      // 测试训练时间
      val startTime = System.currentTimeMillis()
      
      val users = ratings.map(_.user).distinct.sorted
      val movies = ratings.map(_.product).distinct.sorted
      val numUsers = users.size
      val numMovies = movies.size
      
      val userIndex = users.zipWithIndex.toMap
      val movieIndex = movies.zipWithIndex.toMap
      
      // 训练模型
      val (userFactors, itemFactors) = trainALS(
        ratings, userIndex, movieIndex, numUsers, numMovies,
        rank = 50, lambda = 0.01, numIterations = 5
      )
      
      val endTime = System.currentTimeMillis()
      val duration = (endTime - startTime) / 1000.0
      
      // 计算RMSE
      val rmse = computeRmse(userFactors, itemFactors, ratings, userIndex, movieIndex)
      
      println(f"  Time: $duration%.2f seconds")
      println(f"  RMSE: $rmse%.4f")
    }
    
    println("\n" + "=" * 80)
    println("Test completed!")
    println("=" * 80)
  }
  
  /**
    * 生成测试数据
    */
  def generateTestData(size: Int): List[Rating] = {
    val rand = new java.util.Random(42)
    (1 to size).map { i =>
      val userId = rand.nextInt(1000) + 1
      val movieId = rand.nextInt(500) + 1
      val rating = rand.nextDouble() * 4 + 1  // 1-5分
      Rating(userId, movieId, rating)
    }.toList
  }
  
  /**
    * 简化版串行ALS训练
    */
  def trainALS(
    ratings: List[Rating],
    userIndex: Map[Int, Int],
    movieIndex: Map[Int, Int],
    numUsers: Int,
    numMovies: Int,
    rank: Int,
    lambda: Double,
    numIterations: Int
  ): (DoubleMatrix, DoubleMatrix) = {
    
    val rand = new java.util.Random(42)
    var userFactors = DoubleMatrix.rand(numUsers, rank)
    var itemFactors = DoubleMatrix.rand(numMovies, rank)
    
    val ratingMatrix = Array.ofDim[Double](numUsers, numMovies)
    ratings.foreach { r =>
      val u = userIndex(r.user)
      val m = movieIndex(r.product)
      ratingMatrix(u)(m) = r.rating
    }
    
    for (_ <- 1 to numIterations) {
      // 更新用户因子
      for (u <- 0 until numUsers) {
        val ratedMovies = ratings.filter(_.user == getKeyByValue(userIndex, u)).map(r => movieIndex(r.product))
        if (ratedMovies.nonEmpty) {
          val Ru = DoubleMatrix.zeros(1, ratedMovies.size)
          val Mi = DoubleMatrix.zeros(rank, ratedMovies.size)
          
          ratedMovies.zipWithIndex.foreach { case (m, idx) =>
            Ru.put(0, idx, ratingMatrix(u)(m))
            Mi.putColumn(idx, itemFactors.getRow(m))
          }
          
          val A = Mi.mmul(Mi.transpose()).add(DoubleMatrix.eye(rank).mul(lambda))
          val B = Mi.mmul(Ru.transpose())
          val Uu = Solve.solve(A, B)
          userFactors.putRow(u, Uu.transpose())
        }
      }
      
      // 更新物品因子
      for (m <- 0 until numMovies) {
        val ratedUsers = ratings.filter(_.product == getKeyByValue(movieIndex, m)).map(r => userIndex(r.user))
        if (ratedUsers.nonEmpty) {
          val Rm = DoubleMatrix.zeros(1, ratedUsers.size)
          val Ui = DoubleMatrix.zeros(rank, ratedUsers.size)
          
          ratedUsers.zipWithIndex.foreach { case (u, idx) =>
            Rm.put(0, idx, ratingMatrix(u)(m))
            Ui.putColumn(idx, userFactors.getRow(u))
          }
          
          val A = Ui.mmul(Ui.transpose()).add(DoubleMatrix.eye(rank).mul(lambda))
          val B = Ui.mmul(Rm.transpose())
          val Im = Solve.solve(A, B)
          itemFactors.putRow(m, Im.transpose())
        }
      }
    }
    
    (userFactors, itemFactors)
  }
  
  def getKeyByValue(map: Map[Int, Int], value: Int): Int = {
    map.find(_._2 == value).get._1
  }
  
  def computeRmse(
    userFactors: DoubleMatrix,
    itemFactors: DoubleMatrix,
    ratings: List[Rating],
    userIndex: Map[Int, Int],
    movieIndex: Map[Int, Int]
  ): Double = {
    var totalError = 0.0
    var count = 0
    
    ratings.foreach { r =>
      val u = userIndex(r.user)
      val m = movieIndex(r.product)
      val prediction = userFactors.getRow(u).dot(itemFactors.getRow(m))
      val error = prediction - r.rating
      totalError += error * error
      count += 1
    }
    
    math.sqrt(totalError / count)
  }
}
