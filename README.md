# 蜻蜓 (Kitin) 核心 🛸

> **轻量级Folia分支**

---

[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Folia-Based](https://img.shields.io/badge/Based%20on-Folia-brightgreen.svg)](https://github.com/PaperMC/Folia)
[![Stability-None](https://img.shields.io/badge/Stability-None-red.svg)]()

**Kitin** 是一个针对 **IXRMC** 进行特调的 Folia 分支，仅进行了一些必要的轻量级适配，不保证泛用性。

---

## 🚫 使用者特别提醒

在您决定尝试这个核心之前，请务必阅读以下**核心价值观**：

1.  **不保证可靠性**：它可能在凌晨三点因为心情不好而选择“原地解散”。
2.  **不保证泛用性**：这是为 IXRMC 量身定制的，如果您拿去跑别的服跑不动，那是正常的。
3.  **不保证稳定性**：稳定是什么？能吃吗？好吃吗？
4.  **没有性能提升**：我们没有说过我们的核心能提供性能提升。
5.  **没有任何售后**：由于是自用核心，崩了请先检查自己的补丁逻辑。

> **一句话总结：用得好是奇迹，崩了是日常。**

---

## ✨ 核心特色 (Features)

* **极致轻量**：对 Folia 仅进行“蜻蜓点水”式的修改，保留原生风味。
* **IXRMC 特供**：内置了一些针对我们服务器的神秘玄学优化，或者特性。

---

## 🛠️ 如何编译 (How to Build)

如果你真的想试试我们的核心，请按照以下指令进行编译：

```bash
# 1. 拉取代码
git clone [https://github.com/SucIXR/Kitin.git](https://github.com/SucIXR/Kitin.git)
cd Kitin

# 2. 缝合补丁（如果报 Duplicate Class 请怪作者）
./gradlew applyAllPatches

# 3. 编译核心文件
./gradlew createMojmapPaperclipJar