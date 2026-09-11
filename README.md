# MD4a

**M**obile **D**own **4**ndroid — 移动端优先的 GitHub Flavored Markdown 解析 + 渲染 SDK（Android 原生，Jetpack Compose）。

仓库分两部分：

| 模块 | 说明 |
|------|------|
| `md4a/` | **核心 SDK**。任何 Android 项目一行依赖接入，把 GFM/README 渲染成手机友好的原生界面 |
| `app/`  | **演示壳**。极简 UI：输入 `owner/repo` 或任意 README 链接、或一键 🎲 随机抓取一个热门仓库的 README 展示 |

## 为什么不是 WebView / 不是别人的库

- **手机屏优化排版**：字号、行距、标题层级、表格列宽、图片纵横比都是按 6-7 英寸屏调的，不是桌面网页的缩放
- **高性能**：解析跑在 `Dispatchers.Default`，渲染基于 `LazyColumn` 分块复用 + 每段落单条 `AnnotatedString`，长 README 不卡顿
- **GFM 完整支持**：表格（对齐）、任务列表、删除线、自动链接、代码围栏、嵌套列表、引用块
- **代码高亮**：零依赖分词器（关键词/字符串/注释/数字，20+ 语言映射），SVG/GIF badge 由 Coil 渲染
- **纯 Compose 无 WebView**，包体小、启动快、主题自动跟随系统深浅色

## 快速接入（其他项目）

```kotlin
dependencies {
    implementation("com.github.wochatchat:MD4a:md4a-0.1.0") // JitPack
}
```

### 渲染一段 Markdown

```kotlin
Md4aDocument(
    markdown = readmeText,
    baseUrl = "https://raw.githubusercontent.com/owner/repo/main/", // 相对图片地址
    onLinkClick = { url -> /* 打开浏览器 */ },
)
```

### 只要解析不要渲染

```kotlin
val blocks: List<MdBlock> = Md4a.parse(markdown) // 纯 Kotlin AST，可缓存/可自渲染
```

### 解析引擎：Kotlin / C（JNI）双实现

MD4a 自带两套解析引擎，输出同一套 `MdBlock` AST，可随时切换：

| 引擎 | 实现 | 适用场景 |
|------|------|----------|
| `KOTLIN` | commonmark-java（纯 Kotlin/JVM） | 默认；无 NDK 顾虑，JVM 单元测试可用 |
| `NATIVE` | md4c + JNI（C） | 大文档：解析快 2~4 倍（端到端），1MB 文档从 ~120ms 降到 ~60ms 以内 |
| `AUTO` | native 优先，`.so` 不可用时静默回落 Kotlin | 推荐生产环境使用 |

```kotlin
val blocks = Md4a.parse(markdown, Md4a.Engine.NATIVE)

// Compose 入口同样支持：
Md4aDocument(markdown = readmeText, engine = Md4a.Engine.AUTO)
```

`.so` 覆盖 armeabi-v7a / arm64-v8a / x86 / x86_64；C 侧源码在 `md4a/src/main/cpp/`（vendored [md4c](https://github.com/mity/md4c)，MIT）。已知方言差异：`www.` 自动链接会补全 `http://` 前缀（与 GitHub cmark-gfm 一致），个别 autolink 边界与 commonmark-java 略有出入。

`Md4aBlocks(blocks)` 接收预解析的 AST；`Md4aColorScheme` / `Md4aTypography` 可完全自定义主题。

## Demo App 使用

1. 顶部输入框填 `owner/repo`、GitHub 仓库链接或任意 raw markdown URL → **打开**
2. **🎲 随机**：从 stars>8000 的仓库里随机抓一个 README 展示
3. **Kotlin / C (JNI)**：切换解析引擎，实时显示 parse 毫秒数对比；**🧪 长文档** 加载 25 倍拼接的内置示例做压力对比
4. 不联网也能看内置示例（覆盖全部 GFM 特性）

## 构建

- `./build.sh` 本地出 APK（会自动 bump `version.properties`）
- CI（`.github/workflows/build.yml`）：push main 自动构建签名 APK → 传 GitHub Release + Artifact
- PR / feature 分支走 `.github/workflows/compile-check.yml`

## License

MIT
