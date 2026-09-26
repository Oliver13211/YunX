> 本软件基于 AGPL-3.0 开源、完全免费，任何收费版本均为诈骗。安装前请核对文件 SHA-256 校验和（见下方 `SHA256SUMS.txt`）。

## 下载

| 文件 | 平台 |
|---|---|
| `YunX Desktop-<版本>.dmg` | macOS（Apple Silicon） |
| `YunX Desktop-<版本>.msi` | Windows 10/11（x64） |

## macOS 首次运行（未公证 dmg）

本版本未购买 Apple 开发者账号、未做公证，首次打开会被 Gatekeeper 拦截，请任选其一放行：

1. **右键打开**：在 Finder 中右键「YunX Desktop」→「打开」→ 在弹窗中再点「打开」；
2. **系统设置放行**：「系统设置 → 隐私与安全性」→ 底部「YunX Desktop 已被阻止」→ 点「仍要打开」。

仅需首次启动操作一次，之后可正常打开。

## Windows 首次运行（未签名 msi）

SmartScreen 可能提示「Windows 已保护你的电脑」：点「更多信息」→「仍要运行」。安装需要管理员权限（全机安装，自动创建开始菜单与桌面快捷方式）。

## 说明

- 安装包已内置 Java 运行时，无需另装 Java；
- 内嵌浏览器登录（可选功能）首次使用时由 KCEF 自动下载运行时（约 365MB，受限网络可设环境变量 `YUNX_PROXY=host:port` 走代理）；推荐优先使用各网盘的网页登录 / 系统浏览器授权方式；
- 崩溃日志位于 `~/.yunx/logs/`（Windows 为用户目录下 `.yunx\logs`），反馈问题时请附上最新一份；
- 本软件仅供个人学习与技术交流，请勿用于商业用途。

---

YunX Desktop 基于 [YunX（云析）](https://github.com/CYQawa/YunX) 移植，遵循 [GNU AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.html) 开源。
