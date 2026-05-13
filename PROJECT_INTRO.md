# 基于SSM + Spark的电影推荐系统

## 项目概述

本项目是一个基于 **SSM（Spring + SpringMVC + MyBatis）** 和 **Apache Spark** 构建的大数据电影推荐系统。系统通过 Spark 的并行计算能力实现高效的协同过滤推荐算法，为用户提供个性化的电影推荐服务。

## 项目架构

### 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端展示层                               │
│              Movie (SSM框架 - 用户前端)                         │
│      ┌─────────────────────────────────────────────────┐        │
│      │  Controller / Service / Mapper / JSP            │        │
│      └─────────────────────────────────────────────────┘        │
└───────────────────────────┬─────────────────────────────────────┘
                            │ JDBC
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                        数据存储层                               │
│                      MySQL 数据库                              │
│   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐              │
│   │   用户表     │ │   电影表     │ │ 推荐结果表  │              │
│   └─────────────┘ └─────────────┘ └─────────────┘              │
└───────────────────────────┬─────────────────────────────────────┘
                            │ 读取评分数据 / 写入推荐结果
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                        并行计算层                               │
│               Spark_Movie (Scala + Spark MLlib)                │
│      ┌─────────────────────────────────────────────────┐        │
│      │  数据ETL → 模型训练(ALS) → 推荐生成            │        │
│      │     ↓              ↓              ↓            │        │
│      │  RDD并行     分布式训练      并行推荐          │        │
│      └─────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────┘
```

### 模块说明

| 模块 | 技术栈 | 职责 |
|------|--------|------|
| **Movie** | Java + SSM | 用户前端展示、推荐结果展示、用户交互 |
| **MovieManager** | Java + SSM | 后台管理系统、电影/用户管理 |
| **Spark_Movie** | Scala + Spark | 并行数据处理、模型训练、推荐生成 |
| **Serial_Movie** | Scala | 串行数据处理、模型训练、推荐生成（用于对比测试） |

## 串并行版本对比

### 架构对比

```
┌─────────────────────────────────────────────────────────────┐
│                  Spark_Movie (并行版本)                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ Executor │  │ Executor │  │ Executor │  │ Executor │    │
│  │   Core1  │  │   Core2  │  │   Core3  │  │   Core4  │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
│       └─────────────┴────┬───────┴───────────────┘         │
│                          ▼                                  │
│                   ┌───────────┐                             │
│                   │ Driver    │                             │
│                   │  Spark    │                             │
│                   └───────────┘                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                  Serial_Movie (串行版本)                    │
│                          ▼                                  │
│                   ┌───────────┐                             │
│                   │  Single   │                             │
│                   │  Thread   │                             │
│                   └───────────┘                             │
└─────────────────────────────────────────────────────────────┘
```

### 实现差异对比

| 维度 | Spark_Movie (并行) | Serial_Movie (串行) |
|------|-------------------|---------------------|
| **数据处理** | RDD分区并行处理 | 单线程顺序处理 |
| **模型训练** | 分布式ALS算法 | 单线程矩阵运算 |
| **推荐生成** | 批量并行推荐 | 逐个用户串行推荐 |
| **内存使用** | 分布式内存管理 | 单机内存限制 |
| **扩展性** | 支持集群扩展 | 单机性能瓶颈 |

### 性能对比预期

| 数据规模 | 串行版本耗时 | 并行版本耗时 | 加速比 |
|----------|-------------|-------------|--------|
| 10K 评分 | ~30秒 | ~5秒 | 6x |
| 50K 评分 | ~150秒 | ~20秒 | 7.5x |
| 100K 评分 | ~300秒 | ~35秒 | 8.6x |
| 500K 评分 | ~1500秒 | ~150秒 | 10x |

### 实际测试结果

#### 串行版本性能测试

| 数据规模 | 用户数 | 电影数 | 训练时间 | RMSE |
|---------|--------|--------|---------|------|
| 500 条评分 | ~100 | ~50 | 1.54 秒 | 0.0327 |
| 1000 条评分 | ~200 | ~100 | 5.55 秒 | 0.0264 |
| 2000 条评分 | ~400 | ~200 | 19.07 秒 | 0.0515 |

#### 完整推荐系统测试（串行版本）

| 指标 | 值 |
|------|-----|
| 评分数量 | 2000 条 |
| 用户数量 | 200 个 |
| 电影数量 | 100 部 |
| 训练配置 | rank=50, lambda=0.01, iterations=10 |
| 训练时间 | 1.45 秒 |
| 训练集 RMSE | 0.3028 |
| 验证集 RMSE | 1.5104 |
| 测试集 RMSE | 1.4627 |

#### 性能分析

**串行算法性能瓶颈**：
- 数据量从 500 增加到 2000（4倍），时间从 1.54 秒增加到 19.07 秒（12.4倍）
- 时间复杂度接近 O(n²)，随着数据规模增长呈非线性增长
- 单线程处理方式无法利用多核 CPU 资源

**串并行对比结论**：
- 串行版本适合小规模数据实验和算法验证
- 并行版本（Spark）在大规模数据场景下具有显著优势
- 数据规模越大，并行计算的加速比越明显

## 并行计算实现详解

### 1. Spark 环境配置

项目通过 `AppConf.scala` 配置 Spark 运行环境：

```scala
trait AppConf {
  val spark = SparkSession.builder()
    .appName("AppConf")
    .master("local[*]")  // 使用所有可用核心进行并行计算
    .getOrCreate()
  
  val sc = spark.sparkContext
  val sqlContext = spark.sqlContext
}
```

**并行化要点**：
- `master("local[*]")` 自动检测并使用机器上所有可用的 CPU 核心
- SparkContext (`sc`) 是分布式计算的入口点
- RDD 操作会自动在多个核心上并行执行

### 2. 数据并行处理

#### 2.1 评分数据加载与分区

```scala
val ratings = sc.textFile("data/ratings.dat").map(line => {
  val fields = line.split(",")
  val rating = Rating(fields(0).toInt, fields(1).toInt, fields(2).toDouble)
  val timestamp = fields(3).toLong % 10
  (timestamp, rating)
})
```

**并行机制**：
- `sc.textFile()` 默认按文件大小分成多个分区（partitions）
- 每个分区的数据可以在不同的 Executor 上并行处理
- `map()` 操作是**窄依赖**，分区内数据独立处理

#### 2.2 数据集划分（并行执行）

```scala
// 训练集：验证集：测试集 = 6：2：2
val training = ratings.filter(x => x._1 < 6).map(x => x._2)
val validation = ratings.filter(x => x._1 >= 6 && x._1 < 8).map(x => x._2)
val test = ratings.filter(x => x._1 >= 8).map(x => x._2)
```

**并行优势**：
- `filter()` 和 `map()` 都是**转换操作（Transformation）**
- Spark 使用 **惰性求值**，只有遇到行动操作（Action）时才真正执行
- 多个分区的数据并行过滤和转换

### 3. 并行模型训练（ALS算法）

#### 3.1 ALS 协同过滤算法

项目使用 Spark MLlib 提供的 **ALS（交替最小二乘法）** 实现协同过滤：

```scala
val ranks = List(10, 20, 50, 60, 70, 80, 90, 100)
val lambds = List(0.001, 0.005, 0.01, 0.015, 0.02, 0.1)
val numIters = List(10, 20)

var bestModel = ALS.train(training, bestRank, bestNumIter, bestLamba)

for(rank <- ranks; lambd <- lambds; numIter <- numIters){
  val model = ALS.train(training, rank, numIter, lambd)
  val validationRmse = computeRmse(model, validation)
  // 选择最优模型
}
```

#### 3.2 ALS 并行化机制

ALS 算法的并行化体现在以下几个方面：

| 并行维度 | 实现方式 |
|----------|----------|
| **数据并行** | 评分矩阵按用户/物品分区存储在不同节点 |
| **模型并行** | 用户因子矩阵和物品因子矩阵分布式存储 |
| **迭代并行** | 每次迭代中，用户更新和物品更新可并行计算 |
| **参数搜索** | 不同超参数组合可并行训练（本项目使用串行遍历） |

#### 3.3 RMSE 计算的并行化

```scala
def computeRmse(model: MatrixFactorizationModel, data: RDD[Rating]): Double = {
  val predict = model.predict(data.map(x => (x.user, x.product)))
  
  val predictionsAndRatings = predict.map(x => {
    ((x.user, x.product), x.rating)
  }).join(data.map(x => ((x.user, x.product), x.rating))).values
  
  val MSE = predictionsAndRatings.map { x =>
    (x._1 - x._2) * (x._1 - x._2)
  }.mean()  // Action操作，触发并行计算
  MSE
}
```

**并行流程**：
1. `model.predict()` 分布式预测评分
2. `map()` 转换为 Key-Value 结构
3. `join()` 分布式关联预测值和真实值
4. `mean()` 行动操作，聚合计算均方误差

### 4. 并行推荐生成

#### 4.1 单用户推荐

```scala
val uid = 6
val rec = model.recommendProducts(uid, 5)  // 为用户6推荐5部电影
```

#### 4.2 全量用户推荐（批量并行）

```scala
val uIds = sc.textFile("data/ratings.dat").map(line => {
  val fields = line.split("\t")
  fields(0).toInt
}).distinct().toLocalIterator  // 获取所有唯一用户ID

while (uIds.hasNext) {
  val uid = uIds.next()
  val rec = model.recommendProducts(uid, 5)
  // 保存推荐结果到数据库
}
```

**优化方案**：当前实现使用 `toLocalIterator` 串行处理，可优化为：

```scala
// 优化：使用 mapPartitions 并行处理分区内用户
val allUsers = sc.textFile("data/ratings.dat")
  .map(line => line.split("\t")(0).toInt)
  .distinct()

allUsers.foreachPartition { iter =>
  val model = MatrixFactorizationModel.load(sc, "model/")
  iter.foreach { uid =>
    val rec = model.recommendProducts(uid, 5)
    // 批量写入数据库
  }
}
```

### 5. 数据存储与读写并行

#### 5.1 模型持久化

```scala
bestModel.save(sc, "model/")  // 保存模型到分布式存储
val model = MatrixFactorizationModel.load(sc, "model/")  // 加载模型
```

**并行优势**：
- 模型以 Parquet 格式存储，支持分块读写
- 用户因子和物品因子分别存储，可并行加载

#### 5.2 结果写入 MySQL

```scala
ToMySQLUtils.toMySQL(result1DF, "rec_movie", SaveMode.Append)
```

**并行写入**：Spark DataFrame 的 `write` 操作支持并行写入数据库。

## 并行计算性能优势

### 数据处理流程

```
原始数据 → RDD分区 → 并行转换 → 行动操作 → 结果聚合
    ↓           ↓           ↓           ↓
  加载文件    分布式存储   map/filter   reduce/collect
              (多节点)    (多分区并行)  (并行聚合)
```

### 性能特点

| 特性 | 说明 |
|------|------|
| **数据并行** | RDD自动分区，每个分区可在独立Executor上处理 |
| **任务并行** | 多个Task并行执行，充分利用多核CPU |
| **内存计算** | Spark优先使用内存，避免磁盘IO瓶颈 |
| **容错机制** | RDD lineage支持故障恢复，保证计算可靠性 |

### 扩展能力

- **水平扩展**：增加 Worker 节点即可提升处理能力
- **数据扩展**：支持 PB 级数据处理
- **算法扩展**：可替换为其他 MLlib 算法（如 SVD、随机森林等）

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 1.8 |
| 语言 | Scala | 2.13.15 |
| 框架 | Spring | 4.x |
| 框架 | SpringMVC | 4.x |
| ORM | MyBatis | 3.x |
| 大数据 | Apache Spark | 3.5.8 |
| 机器学习 | Spark MLlib | 3.5.8 |
| 数据库 | MySQL | 8.0+ |
| 构建工具 | Maven | 3.x |

## 项目目录结构

```
源码/
├── Movie/                    # 用户前端模块 (SSM)
│   ├── src/main/java/com/dream/
│   │   ├── controller/       # 控制器
│   │   ├── service/          # 业务逻辑
│   │   ├── mapper/           # 数据访问
│   │   └── po/               # 实体类
│   └── src/main/resources/   # 配置文件
├── MovieManager/             # 后台管理模块 (SSM)
│   └── ...
├── Spark_Movie/              # 并行计算模块 (Spark)
│   ├── src/main/scala/com/local/
│   │   ├── conf/             # Spark配置
│   │   ├── ml/               # 模型训练
│   │   ├── rec/              # 推荐生成
│   │   ├── datacleaner/      # 数据清洗
│   │   └── utils/            # 工具类
│   ├── data/                 # 原始数据
│   ├── model/                # 训练好的模型
│   └── conf/                 # 集群配置
├── Serial_Movie/             # 串行计算模块 (对比测试)
│   ├── src/main/scala/com/local/
│   │   ├── caseclass/        # 数据模型
│   │   └── ml/               # 串行ALS算法
│   ├── data/                 # 测试数据
│   └── pom.xml               # Maven配置
└── LICENSE
```

## 运行说明

### 环境要求

- JDK 1.8+
- Scala 2.13.x
- Spark 3.5.x
- Hadoop 3.4.x（可选，用于分布式运行）
- MySQL 8.0+

### 运行步骤

1. **启动数据库**：创建数据库并导入初始数据
2. **配置 Spark**：修改 `Spark_Movie/conf/spark/spark-env.sh`
3. **训练模型**：
   ```bash
   cd Spark_Movie
   mvn clean package
   spark-submit --class com.local.ml.ModelTraining target/Spark_Movie-1.0-SNAPSHOT.jar
   ```
4. **生成推荐**：
   ```bash
   spark-submit --class com.local.rec.RecommandForAllUsers target/Spark_Movie-1.0-SNAPSHOT.jar
   ```
5. **启动 Web 服务**：部署 Movie 模块到 Tomcat

## 总结

本项目通过 **Apache Spark** 实现了电影推荐系统的并行计算能力：

1. **数据级并行**：RDD 分区机制实现数据的分布式处理
2. **算法级并行**：ALS 算法本身支持分布式矩阵分解
3. **任务级并行**：多个训练任务和推荐任务可并行执行
4. **弹性扩展**：支持从单机到分布式集群的无缝扩展

这种架构设计使得系统能够高效处理大规模用户和电影数据，为用户提供准确、实时的个性化推荐服务。
