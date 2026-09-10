package com.md4a.demo

/** Built-in showcase document (no network needed) covering the GFM surface. */
internal const val SAMPLE_MD = """# MD4a 渲染示例

这是一个 **内置示例**，用于离线验证 SDK 的渲染能力。支持 *斜体*、~~删除线~~、`行内代码`、[链接](https://github.com) 与 GFM 任务列表。

## 功能清单

- [x] CommonMark + GFM 解析（表格 / 任务列表 / 删除线 / 自动链接）
- [x] 手机端优化的排版与字号
- [x] 代码块语法着色（零依赖分词器）
- [x] LazyColumn 分块渲染，长文档不掉帧
- [ ] HTML 高级标签（`<details>` 等）

### 代码块

```kotlin
class Md4a {
    fun parse(markdown: String): List<MdBlock> {
        val doc = parser.parse(markdown)   // 0.24.0
        return blockChildren(doc)
    }
}
```

```python
# python 注释
def hello(name: str) -> str:
    return f"Hello, {name}!"
```

### 表格

| 特性 | 状态 | 备注 |
|:-----|:----:|-----:|
| 表格 | ✅ | 左/中/右对齐 |
| 图片 | ✅ | SVG / GIF |
| 高亮 | ✅ | 20+ 语言 |

> 引用块：性能优先，展示效果优先。
> 两行引用。

---

### 嵌套列表

1. 第一层有序
   - 嵌套无序
   - 再来一条
2. 第二项
   - [x] 任务项已勾选
   - [ ] 任务项未勾选
"""
