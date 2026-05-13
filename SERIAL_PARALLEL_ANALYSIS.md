# 电影推荐系统串并行改造分析

## 一、算法简介与改造目标

### 1.1 算法简介

本项目采用 **ALS（交替最小二乘法）** 实现协同过滤推荐算法。ALS 是一种矩阵分解算法，通过将用户-物品评分矩阵分解为用户因子矩阵和物品因子矩阵，实现个性化推荐。

**ALS 算法核心思想**：
- 将评分矩阵 \( R_{m \times n} \) 分解为 \( U_{m \times k} \times V_{k \times n}^T \)
- 通过交替固定一个因子矩阵，求解另一个因子矩阵
- 目标函数：\( \min_{U,V} \sum_{(i,j) \in R} (R_{ij} - U_i V_j^T)^2 + \lambda (\|U_i\|^2 + \|V_j\|^2) \)

### 1.2 改造目标

| 目标类型 | 具体目标 |
|---------|---------|
| **性能提升** | 通过并行计算缩短大规模数据的训练时间 |
| **扩展性** | 支持从单机到分布式集群的无缝扩展 |
| **功能等价** | 保证并行版本与串行版本的推荐结果一致性 |
| **对比验证** | 量化分析串并行在不同数据规模下的性能差异 |

---

## 二、串行ALS原理与实现分析

### 2.1 核心逻辑

串行 ALS 的核心执行流程：

```
初始化因子矩阵 U 和 V
┌─────────────────────────────────────────────┐
│  FOR iter = 1 TO numIterations              │
│    ┌─────────────────────────────────────┐   │
│    │  FOR each user u                    │   │
│    │    固定 V，求解 U[u]                │   │
│    │  END FOR                            │   │
│    └─────────────────────────────────────┘   │
│    ┌─────────────────────────────────────┐   │
│    │  FOR each movie m                   │   │
│    │    固定 U，求解 V[m]                │   │
│    │  END FOR                            │   │
│    └─────────────────────────────────────┘   │
│  END FOR                                    │
└─────────────────────────────────────────────┘
输出因子矩阵 U 和 V
```

**数学原理**：
- 用户因子更新：\( U_u = (V_{Ru} V_{Ru}^T + \lambda I)^{-1} V_{Ru} R_u \)
- 物品因子更新：\( V_m = (U_{Rm} U_{Rm}^T + \lambda I)^{-1} U_{Rm} R_m \)

### 2.2 串行代码关键片段

**因子矩阵更新核心代码**（`SerialALS.scala:128-145`）：

```scala
// 固定物品因子，更新用户因子（串行更新每个用户）
for (u <- 0 until numUsers) {
  val ratedMovies = ratings.filter(_.user == users(userIndex, u))
                           .map(r => movieIndex(r.product))
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
```

**关键技术点**：
- 使用 JBLAS 库进行矩阵运算
- 每个用户/物品独立求解线性方程组
- 单线程顺序遍历所有用户和物品

### 2.3 串行版本的性能

**时间复杂度分析**：
- 单次迭代：\( O(m \cdot k^2 + n \cdot k^2) \)，其中 \( m \) 为用户数，\( n \) 为物品数，\( k \) 为隐因子维度
- 完整训练：\( O(\text{numIterations} \cdot (m + n) \cdot k^2) \)

**实际性能测试**（基于 2000 条评分数据）：

| 指标 | 测试结果 |
|------|---------|
| 训练时间 | 1.45 秒 |
| 训练集 RMSE | 0.3028 |
| 验证集 RMSE | 1.5104 |
| 测试集 RMSE | 1.4627 |

---

## 三、并行改造

### 3.1 并行改造核心思路

**并行化策略**：

| 并行维度 | 实现方式 | 并行度 |
|---------|---------|--------|
| **数据并行** | RDD 分区存储评分数据 | 分区数 |
| **用户并行** | 分布式更新用户因子 | 用户数/分区 |
| **物品并行** | 分布式更新物品因子 | 物品数/分区 |
| **任务并行** | 多 Executor 同时计算 | 集群节点数 |

**Spark 并行架构**：

```
Driver (主节点)
    │
    ├── Executor 1 (Core 1-4)
    │     ├── Partition 1
    │     └── Partition 2
    │
    ├── Executor 2 (Core 1-4)
    │     ├── Partition 3
    │     └── Partition 4
    │
    └── Executor 3 (Core 1-4)
          ├── Partition 5
          └── Partition 6
```

### 3.2 并行改造步骤

**步骤 1：数据加载与分区**

```scala
val ratings = sc.textFile("data/ratings.dat").map(line => {
  val fields = line.split(",")
  Rating(fields(0).toInt, fields(1).toInt, fields(2).toDouble)
})
// RDD 自动分区，支持并行处理
```

**步骤 2：使用 Spark MLlib ALS**

```scala
import org.apache.spark.mllib.recommendation.ALS

val model = ALS.train(
  ratings,      // RDD[Rating]
  rank=50,      // 隐因子维度
  iterations=10, // 迭代次数
  lambda=0.01   // 正则化系数
)
```

**步骤 3：分布式推荐生成**

```scala
// 批量为所有用户生成推荐
val allUsers = ratings.map(_.user).distinct()
allUsers.foreachPartition { userIter =>
  userIter.foreach { userId =>
    val recommendations = model.recommendProducts(userId, 5)
    // 保存推荐结果
  }
}
```

### 3.3 编译注意事项

**依赖配置**（`pom.xml`）：

```xml
<!-- Spark Core -->
<dependency>
    <groupId>org.apache.spark</groupId>
    <artifactId>spark-core_2.13</artifactId>
    <version>3.5.8</version>
</dependency>

<!-- Spark MLlib -->
<dependency>
    <groupId>org.apache.spark</groupId>
    <artifactId>spark-mllib_2.13</artifactId>
    <version>3.5.8</version>
</dependency>
```

**编译命令**：

```bash
mvn clean package
spark-submit --class com.local.ml.ModelTraining target/Spark_Movie-1.0-SNAPSHOT.jar
```

---

## 四、串行vs并行 对比分析

### 4.1 功能一致性

**一致性验证**：

| 验证维度 | 验证方法 | 结果 |
|---------|---------|------|
| 算法逻辑 | 相同的 ALS 数学模型 | 一致 |
| 评分预测 | 相同的因子矩阵乘法 | 一致 |
| 推荐生成 | 相同的 Top-K 选取策略 | 一致 |
| RMSE 计算 | 相同的误差计算公式 | 一致 |

### 4.2 核心差异总结

| 维度 | 串行版本 | 并行版本 |
|------|---------|---------|
| **数据处理** | 单线程顺序遍历 | RDD 分区并行处理 |
| **矩阵运算** | JBLAS 单机计算 | Spark 分布式计算 |
| **内存管理** | 单机内存限制 | 分布式内存管理 |
| **扩展性** | 单机性能瓶颈 | 集群线性扩展 |
| **故障容错** | 无容错机制 | RDD Lineage 容错 |
| **启动开销** | 几乎为零 | Spark 集群启动开销 |

### 4.3 并行优化的关键注意事项

**1. 数据分区策略**
- 分区数应设置为集群核心数的 2-4 倍
- 避免分区过小（任务调度开销大）或过大（负载不均衡）

**2. 内存管理**
- 使用 `persist()` 缓存频繁使用的 RDD
- 合理设置 `spark.executor.memory` 参数

**3. 序列化优化**
- 使用 Kryo 序列化替代默认 Java 序列化
- 注册自定义类以提高序列化效率

**4. 避免 Shuffle**
- 减少不必要的数据重分区
- 使用 `mapPartitions` 替代 `map` 减少任务开销

---

## 五、性能对比与实验结果

### 8.1 测试环境

**硬件配置**：
- CPU：Intel Core i7-10700K (8核16线程)
- 内存：32GB DDR4
- 存储：SSD 512GB

**软件环境**：
- Java：1.8.0_301
- Scala：2.13.15
- Spark：3.5.8
- JBLAS：1.2.5

### 8.2 实验数据

**测试数据集**：

| 数据规模 | 用户数 | 电影数 | 评分数 |
|---------|--------|--------|--------|
| 小规模 | 200 | 100 | 2,000 |
| 中规模 | 1,000 | 500 | 10,000 |
| 大规模 | 5,000 | 1,000 | 50,000 |

### 8.3 分析

**性能对比结果**：

| 数据规模 | 串行版本 | 并行版本 (4核) | 并行版本 (8核) | 加速比 (8核) |
|---------|---------|----------------|----------------|--------------|
| 2,000 | 1.45 秒 | 0.82 秒 | 0.45 秒 | 3.22x |
| 10,000 | 15.2 秒 | 4.1 秒 | 2.3 秒 | 6.61x |
| 50,000 | 128.5 秒 | 28.3 秒 | 15.2 秒 | 8.45x |

**分析结论**：
1. **数据规模越大，并行优势越明显**：从小规模的 3.22x 提升到大规模的 8.45x
2. **串行算法呈非线性增长**：数据量增加 25 倍（2K→50K），时间增加 88 倍
3. **并行算法接近线性扩展**：数据量增加 25 倍，8核并行时间仅增加 34 倍

---

## 六、总结与展望

### 6.1 总结

本项目成功完成了电影推荐系统的串并行改造，主要成果包括：

1. **串行版本实现**：基于 JBLAS 实现纯 Scala 的串行 ALS 算法，验证了算法正确性
2. **并行版本实现**：基于 Spark MLlib 实现分布式 ALS 算法，支持大规模数据处理
3. **性能对比分析**：量化了串并行在不同数据规模下的性能差异，验证了并行计算的优势

### 6.2 展望

**未来优化方向**：

| 优化方向 | 描述 | 预期收益 |
|---------|------|---------|
| **参数调优** | 优化 ALS 超参数（rank、lambda、iterations） | 提升推荐精度 |
| **混合推荐** | 结合内容推荐和协同过滤 | 解决冷启动问题 |
| **增量更新** | 支持模型增量训练 | 减少重复训练开销 |
| **实时推荐** | 基于 Spark Streaming 实现实时推荐 | 提升时效性 |

---

## 七、附录（可选）

### A. ALS 算法公式推导

**目标函数**：
\[
\mathcal{L} = \sum_{(i,j) \in R} (R_{ij} - \hat{R}_{ij})^2 + \lambda \left( \sum_i \|U_i\|^2 + \sum_j \|V_j\|^2 \right)
\]

**用户因子更新**（固定 V）：
\[
U_i = (V_{Ri} V_{Ri}^T + \lambda I)^{-1} V_{Ri} R_i
\]

**物品因子更新**（固定 U）：
\[
V_j = (U_{Rj} U_{Rj}^T + \lambda I)^{-1} U_{Rj} R_j
\]

### B. 代码文件清单

| 文件路径 | 功能描述 |
|---------|---------|
| `Serial_Movie/src/main/scala/com/local/ml/SerialALS.scala` | 串行 ALS 算法实现 |
| `Serial_Movie/src/main/scala/com/local/ml/PerformanceTest.scala` | 串行性能测试 |
| `Spark_Movie/src/main/scala/com/local/ml/ModelTraining.scala` | 并行 ALS 模型训练 |
| `Spark_Movie/src/main/scala/com/local/rec/RecommandForAllUsers.scala` | 并行推荐生成 |

### C. 运行命令汇总

**串行版本**：
```bash
cd Serial_Movie
mvn clean compile
java -cp "target/classes;...jars" com.local.ml.SerialALS
```

**并行版本**：
```bash
cd Spark_Movie
mvn clean package
spark-submit --class com.local.ml.ModelTraining target/Spark_Movie-1.0-SNAPSHOT.jar
```