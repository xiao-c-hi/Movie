package com.local.ml

import com.local.caseclass.Rating
import org.jblas.DoubleMatrix
import org.jblas.Solve
import scala.io.Source

/**
  * 串行版本的ALS协同过滤算法
  * 不使用Spark，纯Scala实现
  */
object SerialALS {
  
  def main(args: Array[String]): Unit = {
    val startTime = System.currentTimeMillis()
    
    // 1. 生成测试数据（快速测试用）
    println("Generating test ratings data...")
    val ratings = generateTestData(2000)  // 生成2000条测试评分
    println(s"Generated ${ratings.size} ratings")
    
    // 获取用户和物品数量
    val users = ratings.map(_.user).distinct.sorted
    val movies = ratings.map(_.product).distinct.sorted
    val numUsers = users.size
    val numMovies = movies.size
    
    println(s"Number of users: $numUsers")
    println(s"Number of movies: $numMovies")
    
    // 创建索引映射
    val userIndex = users.zipWithIndex.toMap
    val movieIndex = movies.zipWithIndex.toMap
    
    // 2. 划分数据集（串行方式）
    val (training, validation, test) = splitData(ratings)
    println(s"Training: ${training.size}, Validation: ${validation.size}, Test: ${test.size}")
    
    // 3. 串行训练ALS模型
    val rank = 50
    val lambda = 0.01
    val numIterations = 10
    
    println(s"\nTraining serial ALS model with rank=$rank, lambda=$lambda, iterations=$numIterations...")
    val (userFactors, itemFactors) = trainALS(training, userIndex, movieIndex, numUsers, numMovies, rank, lambda, numIterations)
    
    // 4. 计算RMSE
    val trainRmse = computeRmse(userFactors, itemFactors, training, userIndex, movieIndex)
    val valRmse = computeRmse(userFactors, itemFactors, validation, userIndex, movieIndex)
    val testRmse = computeRmse(userFactors, itemFactors, test, userIndex, movieIndex)
    
    println(f"\nTrain RMSE: $trainRmse%.4f")
    println(f"Validation RMSE: $valRmse%.4f")
    println(f"Test RMSE: $testRmse%.4f")
    
    // 5. 生成推荐（串行方式）
    println("\nGenerating recommendations...")
    val recommendations = generateRecommendations(userFactors, itemFactors, users, movies, userIndex, movieIndex, topN = 5)
    println(s"Generated recommendations for ${recommendations.size} users")
    
    // 6. 保存推荐结果
    saveRecommendations(recommendations, "data/serial_recommendations.txt")
    
    val endTime = System.currentTimeMillis()
    println(f"\nTotal execution time: ${(endTime - startTime) / 1000.0}%.2f seconds")
  }
  
  /**
    * 串行加载评分数据
    */
  def loadRatings(filePath: String): List[Rating] = {
    Source.fromFile(filePath).getLines()
      .map { line =>
        val fields = line.split(",")
        Rating(fields(0).toInt, fields(1).toInt, fields(2).toDouble)
      }
      .toList
  }
  
  /**
    * 串行划分数据集（6:2:2）
    */
  def splitData(ratings: List[Rating]): (List[Rating], List[Rating], List[Rating]) = {
    val shuffled = scala.util.Random.shuffle(ratings)
    val total = shuffled.size
    val trainSize = (total * 0.6).toInt
    val valSize = (total * 0.2).toInt
    
    val training = shuffled.take(trainSize)
    val validation = shuffled.slice(trainSize, trainSize + valSize)
    val test = shuffled.drop(trainSize + valSize)
    
    (training, validation, test)
  }
  
  /**
    * 串行训练ALS模型
    * 使用交替最小二乘法学习用户因子和物品因子
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
    
    // 初始化因子矩阵（随机初始化）
    val rand = new java.util.Random(42)
    var userFactors = DoubleMatrix.rand(numUsers, rank)
    var itemFactors = DoubleMatrix.rand(numMovies, rank)
    
    // 创建评分矩阵（稀疏表示）
    val ratingMatrix = Array.ofDim[Double](numUsers, numMovies)
    ratings.foreach { r =>
      val u = userIndex(r.user)
      val m = movieIndex(r.product)
      ratingMatrix(u)(m) = r.rating
    }
    
    // 串行迭代训练
    for (iter <- 1 to numIterations) {
      println(s"  Iteration $iter/$numIterations")
      
      // 固定物品因子，更新用户因子（串行更新每个用户）
      for (u <- 0 until numUsers) {
        val ratedMovies = ratings.filter(_.user == users(userIndex, u)).map(r => movieIndex(r.product))
        if (ratedMovies.nonEmpty) {
          val Ru = DoubleMatrix.zeros(1, ratedMovies.size)
          val Mi = DoubleMatrix.zeros(rank, ratedMovies.size)
          
          ratedMovies.zipWithIndex.foreach { case (m, idx) =>
            Ru.put(0, idx, ratingMatrix(u)(m))
            Mi.putColumn(idx, itemFactors.getRow(m))
          }
          
          // 求解: (Mi * Mi' + lambda * I) * Uu = Mi * Ru'
          val A = Mi.mmul(Mi.transpose()).add(DoubleMatrix.eye(rank).mul(lambda))
          val B = Mi.mmul(Ru.transpose())
          val Uu = Solve.solve(A, B)
          
          userFactors.putRow(u, Uu.transpose())
        }
      }
      
      // 固定用户因子，更新物品因子（串行更新每个物品）
      for (m <- 0 until numMovies) {
        val ratedUsers = ratings.filter(_.product == movies(movieIndex, m)).map(r => userIndex(r.user))
        if (ratedUsers.nonEmpty) {
          val Rm = DoubleMatrix.zeros(1, ratedUsers.size)
          val Ui = DoubleMatrix.zeros(rank, ratedUsers.size)
          
          ratedUsers.zipWithIndex.foreach { case (u, idx) =>
            Rm.put(0, idx, ratingMatrix(u)(m))
            Ui.putColumn(idx, userFactors.getRow(u))
          }
          
          // 求解: (Ui * Ui' + lambda * I) * Im = Ui * Rm'
          val A = Ui.mmul(Ui.transpose()).add(DoubleMatrix.eye(rank).mul(lambda))
          val B = Ui.mmul(Rm.transpose())
          val Im = Solve.solve(A, B)
          
          itemFactors.putRow(m, Im.transpose())
        }
      }
    }
    
    (userFactors, itemFactors)
  }
  
  /**
    * 从索引获取原始用户ID
    */
  def users(indexMap: Map[Int, Int], idx: Int): Int = {
    indexMap.find(_._2 == idx).get._1
  }
  
  /**
    * 从索引获取原始电影ID
    */
  def movies(indexMap: Map[Int, Int], idx: Int): Int = {
    indexMap.find(_._2 == idx).get._1
  }
  
  /**
    * 串行计算RMSE
    */
  def computeRmse(
    userFactors: DoubleMatrix,
    itemFactors: DoubleMatrix,
    ratings: List[Rating],
    userIndex: Map[Int, Int],
    movieIndex: Map[Int, Int]
  ): Double = {
    var totalError = 0.0
    var count = 0
    
    // 串行遍历所有评分
    ratings.foreach { r =>
      val u = userIndex(r.user)
      val m = movieIndex(r.product)
      
      // 预测评分
      val prediction = userFactors.getRow(u).dot(itemFactors.getRow(m))
      val error = prediction - r.rating
      totalError += error * error
      count += 1
    }
    
    math.sqrt(totalError / count)
  }
  
  /**
    * 串行生成推荐
    */
  def generateRecommendations(
    userFactors: DoubleMatrix,
    itemFactors: DoubleMatrix,
    users: List[Int],
    movies: List[Int],
    userIndex: Map[Int, Int],
    movieIndex: Map[Int, Int],
    topN: Int
  ): Map[Int, List[(Int, Double)]] = {
    
    val recommendations = scala.collection.mutable.Map[Int, List[(Int, Double)]]()
    
    // 串行为每个用户生成推荐
    users.foreach { userId =>
      val u = userIndex(userId)
      val userVec = userFactors.getRow(u)
      
      // 串行计算与所有电影的相似度
      var predictions = List[(Int, Double)]()
      movies.foreach { movieId =>
        val m = movieIndex(movieId)
        val movieVec = itemFactors.getRow(m)
        val score = userVec.dot(movieVec)
        predictions = (movieId, score) :: predictions
      }
      
      // 获取Top-N推荐
      val topMovies = predictions.sortBy(-_._2).take(topN)
      recommendations(userId) = topMovies
    }
    
    recommendations.toMap
  }
  
  /**
    * 保存推荐结果
    */
  def saveRecommendations(recommendations: Map[Int, List[(Int, Double)]], filePath: String): Unit = {
    val writer = new java.io.PrintWriter(filePath)
    recommendations.foreach { case (userId, movies) =>
      val movieIds = movies.map(_._1).mkString(",")
      writer.println(s"$userId\t$movieIds")
    }
    writer.close()
    println(s"Recommendations saved to $filePath")
  }
  
  /**
    * 生成测试数据
    */
  def generateTestData(size: Int): List[Rating] = {
    val rand = new java.util.Random(42)
    (1 to size).map { i =>
      val userId = rand.nextInt(200) + 1    // 200个用户
      val movieId = rand.nextInt(100) + 1   // 100部电影
      val rating = rand.nextDouble() * 4 + 1  // 1-5分
      Rating(userId, movieId, rating)
    }.toList
  }
}
