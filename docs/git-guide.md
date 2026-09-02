# Git 与 GitHub 协作操作规范（新手向）

> 适用对象：从未使用过 Git 的团队成员。
> 阅读方式：按章节顺序，**照着命令敲就行**。全部操作约 30 分钟可学会。
> 更新时间：2026-08-05

---

## 0. 先花 30 秒理解 Git 和 GitHub

| 概念 | 是什么 | 类比 |
|---|---|---|
| **Git** | 你电脑上的版本管理工具，记录每一次代码改动 | 游戏存档，随时可回退 |
| **GitHub** | 云端代码仓库平台，大家共享代码的地方 | 共享网盘 + 存档合并器 |
| **commit（提交）** | 把一次改动「存档」 | 保存游戏进度 |
| **push（推送）** | 把本地存档上传到云端 | 上传网盘 |
| **pull（拉取）** | 把云端的更新下载到本地 | 下载网盘最新版 |
| **分支（branch）** | 独立的开发线，互不干扰 | 各写各的草稿 |
| **PR（Pull Request）** | 请求把你的分支合并进主干 | 交作业申请审核 |

**团队协作总流程**：

```
本地建分支 → 改代码 → 提交(commit) → 推送(push) → 网页发起 PR → 审核通过 → 合并
```

---

## 1. 注册 GitHub 账号（5 分钟）

1. 打开 https://github.com/signup
2. 用邮箱注册，去邮箱点验证链接
3. 记下你的**用户名**（英文，如 `zhangsan2026`）
4. 把用户名发到微信群，队长会把你添加为协作者

---

## 2. 安装 Git（Windows，5 分钟）

1. 打开 https://git-scm.com/download/win 下载
2. 双击安装，一路 **Next / 默认选项**即可
3. 验证：桌面空白处**右键** → 菜单里出现 **Git Bash Here** → 点击打开黑窗口，输入：

   ```bash
   git --version
   ```

   显示 `git version 2.x.x` 即安装成功 ✅

---

## 3. 配置身份（只需一次，2 分钟）

在刚才的 Git Bash 窗口里，把下面两行**改成你自己的信息**后回车：

```bash
git config --global user.name "你的GitHub用户名"
git config --global user.email "你的注册邮箱"
```

> 这一步决定了你的提交记录显示谁的名字，必须做。

---

## 4. 接受邀请并克隆仓库（5 分钟）

### 4.1 接受协作者邀请

队长添加你后，GitHub 会给你发通知/邮件，点击 **Accept invitation（接受邀请）**。

### 4.2 克隆仓库到本地

1. 在你电脑上新建一个文件夹（如 `D:\work`），右键 → **Git Bash Here**
2. 输入：

   ```bash
   git clone https://github.com/August13-B/ai-scientist.git
   cd ai-scientist
   ```

3. 进入开发主干分支：

   ```bash
   git checkout develop
   ```

> 第一次 clone 可能弹出 GitHub 登录窗口（Git Credential Manager），登录一次即可，之后免密。

---

## 5. 日常开发流程（黄金流程，记住这 7 步）

> **核心原则：永远不要直接在 develop 分支上改代码。** 每次开发先建自己的分支。

### 第 1 步：拉取最新代码

```bash
git pull
```

> 每次开工前先做这步，拿到队友的最新改动。

### 第 2 步：创建自己的分支

```bash
git checkout -b feature/我的功能名
```

> 分支名规范：`feature/功能名`、`bugfix/问题名`、`docs/文档名`。
> 例：`git checkout -b feature/hypothesis-agent`

### 第 3 步：修改代码

用编辑器（VS Code / IDEA）改代码，改完保存。

### 第 4 步：查看改动（确认改了什么）

```bash
git status        # 列出有改动的文件
git diff          # 查看具体改动内容
```

### 第 5 步：提交（存档）

```bash
git add .
git commit -m "feat(模块): 做了什么"
```

> 提交信息格式：`<类型>(<范围>): <中文描述>`，如：
> - `feat(agent): 完成知识发现 Agent 检索逻辑`
> - `fix(rag): 修复混合检索排序异常`
> - `docs(readme): 补充环境变量说明`

### 第 6 步：推送到云端

```bash
git push -u origin 分支名
```

> 分支名 = 你第 2 步创建的名字，如 `git push -u origin feature/hypothesis-agent`

### 第 7 步：网页发起 PR（合并申请）

1. 推送后终端会显示一个链接，点击；或打开仓库页面 https://github.com/August13-B/ai-scientist
2. 点黄色横幅 **Compare & pull request**（或 Pull requests → New pull request）
3. 确认 **base: develop**（合并到哪）← **compare: 你的分支**（你改的什么）
4. 填标题和说明 → 点 **Create pull request**
5. 等队友审核 → 通过后点 **Merge pull request** → 合并完成

---

## 6. 常用命令速查表

| 命令 | 作用 |
|---|---|
| `git status` | 查看当前改动状态 |
| `git pull` | 拉取远程最新代码 |
| `git checkout -b 分支名` | 创建并切换到新分支 |
| `git checkout 分支名` | 切换分支 |
| `git add .` | 暂存所有改动 |
| `git commit -m "信息"` | 提交（存档） |
| `git push` | 推送本地提交到云端 |
| `git log --oneline` | 查看提交历史 |
| `git diff` | 查看未提交的改动 |
| `git branch` | 查看所有分支（* 为当前分支） |

---

## 7. 常见问题排查

| 问题 | 原因 | 解决办法 |
|---|---|---|
| `push` 被拒绝（non-fast-forward） | 别人先推送了，本地代码旧了 | `git pull` 后再 `git push` |
| 文件冲突（CONFLICT） | 你和别人改了同一处 | 别慌：打开冲突文件，保留想要的代码，删掉 `<<<<<<<` `=======` `>>>>>>>` 标记行，然后 `git add .` + `git commit` |
| 在 develop 分支上改了代码 | 忘了建分支 | `git checkout -b feature/xxx` 把改动带到新分支，再提交推送 |
| 提交信息写错了 | 手滑 | `git commit --amend -m "新信息"` 重新提交 |
| 想撤销某文件改动 | 改坏了 | `git checkout -- 文件名`（注意会丢失该文件未提交的改动） |
| 终端中文乱码 | 编码问题 | 所有文件保存为 UTF-8；Windows 终端可执行 `git config --global core.quotepath false` |
| 忘记自己改了什么 | - | `git status` + `git diff` |

> 遇到解决不了的问题：**先在群里问，不要乱敲命令**。必要时把 `git status` 的输出发出来。

---

## 8. 团队红线（必须遵守）

1. **不直接推 develop / main**——所有改动走「建分支 → PR → 审核」流程
2. **提交信息按规范写**：`<类型>(<范围>): <中文描述>`
3. **小步提交**：完成一个功能点就提交一次，不要攒一大堆
4. **密钥绝不上传**：`.env`、API Key 严禁提交（已被 .gitignore 拦住的不要强行 `git add -f`）
5. **不提交临时文件**：`.pi/`、日志、缓存已在 .gitignore 中，正常提交不会带上
6. **开工先 `git pull`**，保持和队友同步，减少冲突

---

## 9. 完整示例：一次真实开发

```bash
# 开工：拉最新代码
git pull

# 建自己的分支（假设你要写假设生成 Agent）
git checkout -b feature/hypothesis-agent

# …写代码…完成后：

git status              # 看改了哪些文件
git add .
git commit -m "feat(agent): 完成假设生成 Agent 推理链逻辑"

# 推送
git push -u origin feature/hypothesis-agent

# 浏览器打开推送后显示的链接 → Compare & pull request
# base: develop ← compare: feature/hypothesis-agent
# 创建 PR → 等审核 → 合并 ✅
```

---

> 更多团队规范（分支策略 / 提交规范 / 代码规范）见 [contribution.md](contribution.md)。
> 详细接口与架构见 [README.md](../README.md) 文档索引。
