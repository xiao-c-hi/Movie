# 电影推荐系统 - Movie Recommendation System

基于SSM（Spring + Spring MVC + MyBatis）和Spark的电影推荐系统，采用协同过滤算法实现个性化电影推荐。

## 项目架构

```
├── MovieManager/          # SSM后端管理系统
│   ├── src/main/java/     # Java源代码
│   ├── src/main/resources/# 配置文件
│   └── src/main/webapp/   # Web前端页面
├── Movie/                 # 用户前端展示系统
│   └── src/main/webapp/   # 用户界面
├── Spark_Movie/           # Spark推荐算法模块
│   ├── src/main/scala/    # Scala源代码
│   ├── conf/              # Spark配置文件
│   └── data/              # 数据文件
└── Serial_Movie/          # 串行版本算法（对比实验）
    └── src/main/scala/    # Scala源代码
```

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring + Spring MVC + MyBatis | 4.x |
| 数据库 | MySQL | 5.7+ |
| 大数据处理 | Apache Spark | 2.x |
| 推荐算法 | ALS协同过滤 | - |
| 前端 | JSP + Bootstrap | 3.x |
| 构建工具 | Maven | 3.x |

## 快速开始

### 1. 环境要求

- JDK 1.8+
- Maven 3.6+
- MySQL 5.7+
- Spark 2.4+（可选，集群模式需要）

### 2. 数据库配置

创建数据库并导入初始数据：

```sql
CREATE DATABASE IF NOT EXISTS movie_recommend;
USE movie_recommend;
-- 导入数据库脚本（请参考项目中的SQL文件）
```

### 3. 后端服务启动

```bash
cd MovieManager
mvn clean package
mvn tomcat7:run
```

服务启动后访问：`http://localhost:8080/MovieManager`

### 4. Spark推荐模型训练

```bash
cd Spark_Movie
mvn clean package
# 本地模式运行
spark-submit --class com.local.ml.ModelTraining target/spark-movie-1.0.jar
```

### 5. 生成推荐结果

```bash
spark-submit --class com.local.rec.Recommand target/spark-movie-1.0.jar
```

## 核心功能

### 1. 用户管理
- 用户注册与登录
- 用户信息管理
- 浏览历史记录

### 2. 电影管理
- 电影信息CRUD
- 电影分类管理
- 电影评分管理

### 3. 推荐系统
- 基于ALS的协同过滤推荐
- 个性化电影推荐
- 热门电影推荐

### 4. 后台管理
- 管理员登录
- 用户管理
- 电影管理
- 评论管理

## Spark推荐算法说明

### 算法原理

采用交替最小二乘法（ALS）实现协同过滤推荐：

1. **数据准备**：读取用户评分数据，按时间戳划分训练集、验证集、测试集（6:2:2）
2. **模型训练**：使用交叉验证选择最优参数（rank、lambda、迭代次数）
3. **模型评估**：使用RMSE评估模型精度
4. **推荐生成**：为每个用户生成Top-N电影推荐

### 关键代码

```scala
// 训练ALS模型
val model = ALS.train(training, rank, numIterations, lambda)

// 生成推荐
val recommendations = model.recommendProducts(userId, numRecommendations)
```

## 配置说明

### Spark配置（conf/spark/spark-env.sh）

```bash
export SPARK_MASTER_IP=spark1          # Master节点地址
export SPARK_MASTER_PORT=7077          # Master监听端口
export SPARK_WORKER_CORES=2            # Worker核心数
export SPARK_WORKER_MEMORY=4g          # Worker内存
```

### 应用配置（AppConf.scala）

```scala
val spark = SparkSession.builder()
    .appName("MovieRecommendation")
    .master("local[*]")  // 本地模式，集群模式改为 spark://spark1:7077
    .getOrCreate()
```

## 项目结构详解

```
Spark_Movie/src/main/scala/
├── com/local/
│   ├── conf/              # 配置类
│   │   └── AppConf.scala  # Spark配置
│   ├── datacleaner/       # 数据清洗
│   │   ├── RatingsETL.scala
│   │   └── TrainETL.scala
│   ├── ml/                # 机器学习
│   │   └── ModelTraining.scala  # ALS模型训练
│   ├── rec/               # 推荐模块
│   │   ├── Recommand.scala
│   │   └── RecommandForAllUsers.scala
│   └── utils/             # 工具类
│       └── ToMySQLUtils.scala
```

## 运行模式说明

### 本地模式（开发测试）

```scala
.master("local[*]")  // 使用本地所有核心
```

### 集群模式（生产环境）

```scala
.master("spark://spark1:7077")  // 连接Spark集群
```

## 注意事项

1. 确保MySQL服务正常运行，数据库连接配置正确
2. Spark运行需要足够的内存资源
3. 首次运行需要准备评分数据文件（data/ratings.dat）
4. 模型训练完成后才能生成推荐结果

