# Serial_Movie 数据目录

## 数据格式

### ratings.dat
用户评分数据，每行格式：
```
userId,movieId,rating,timestamp
```

示例：
```
1,1,5.0,964982703
1,2,3.0,964981247
1,3,4.0,964982224
```

### 数据要求
- userId: 正整数
- movieId: 正整数
- rating: 1.0-5.0 的浮点数
- timestamp: 时间戳（可选）

## 使用说明

1. 将评分数据放入此目录，命名为 `ratings.dat`
2. 运行 SerialALS 主程序进行训练和推荐
3. 推荐结果将保存到 `serial_recommendations.txt`
