# Node.js 流传输大文件解决方案

## 问题背景

在图表渲染服务的流式接口中，发现Java客户端和Postman等工具无法完整接收大于64KB的图片数据。例如，一个82KB的饼图只能接收到76KB，导致图片损坏。

## 根本原因

某些HTTP客户端（如Java的HttpURLConnection、部分版本的Postman）内部有64KB的单次读取缓冲区限制。当服务端一次性发送超过64KB的数据时，客户端可能无法完整接收。

## 解决方案

### 最终实现（简单有效）

```javascript
/**
 * 发送Buffer数据 - 简单方式，仅对大文件进行必要处理
 * @param {Response} res Express响应对象
 * @param {Buffer} buffer 要发送的数据
 */
function sendBuffer(res, buffer) {
  const size = buffer.length;

  // 小于64KB的直接发送，就像Java一样简单
  if (size <= 65536) {
    res.end(buffer);
    return;
  }

  // 大于64KB的需要分块，以兼容某些客户端的64KB缓冲区限制
  const chunkSize = 32768; // 32KB chunks
  let offset = 0;

  while (offset < size) {
    const end = Math.min(offset + chunkSize, size);
    const chunk = buffer.slice(offset, end);
    res.write(chunk);
    offset = end;
  }

  res.end();
}
```

### 使用方式

```javascript
// 在路由中使用
router.post('/render/unified/stream', async (req, res) => {
  // ... 渲染逻辑

  res.set({
    'Content-Type': mimeType,
    'Content-Length': renderResult.buffer.length,
    // 其他响应头...
  });

  // 使用sendBuffer函数发送
  sendBuffer(res, renderResult.buffer);
});
```

## 为什么不使用Node.js Stream API？

虽然尝试过多种"标准"方案：
- `stream.Readable.from(buffer).pipe(res)`
- `stream.pipeline()`
- `PassThrough` 流

但在实际测试中发现这些方案反而会导致传输问题。最简单的分块写入方式反而最可靠。

## 关键点

1. **不是Node.js的问题** - 这是客户端的限制
2. **分块大小很重要** - 32KB是个安全的选择，避免超过客户端缓冲区
3. **必须设置Content-Length** - 让客户端知道总大小
4. **小文件直接发送** - 避免不必要的开销

## 测试验证

### 测试方法
```bash
# 使用curl测试（curl没有64KB限制）
curl -X POST http://localhost:3000/render/unified/stream \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d @test_data.json \
  --output test.png

# 验证文件完整性
ls -lh test.png
md5sum test.png
```

### 测试结果
- ✅ 82KB饼图完整传输
- ✅ MD5哈希值一致
- ✅ Java客户端正常接收
- ✅ Postman正常下载

## 常见错误

1. **使用Node.js Stream API导致的问题**
   - 症状：客户端只接收到76KB（实际应该是82KB）
   - 原因：Stream API的自动缓冲可能创建过大的块

2. **TCP Nagle算法合并小块**
   - 症状：即使分块发送，客户端仍然接收不完整
   - 解决：使用同步的res.write()而不是异步流

3. **没有设置Content-Length**
   - 症状：客户端不知道何时结束
   - 解决：始终设置准确的Content-Length头

## 参考资料

- Java HttpURLConnection默认缓冲区：64KB
- Express.js res.write()文档
- TCP Nagle算法与Node.js

---
最后更新：2025-09-25