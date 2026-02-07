# 蜻蜓 (Kitin) 核心 

> **轻量级Folia分支，提供更加卓越的网络质量与网络体验**

---

[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Folia-Based](https://img.shields.io/badge/Based%20on-Folia-brightgreen.svg)](https://github.com/PaperMC/Folia)
[![Downloads](https://img.shields.io/github/downloads/SucIXR/Kitin/total?color=blue&label=Downloads)](https://github.com/SucIXR/Kitin/releases)

**Kitin** 是一个针对 **IXRMC** 以及部分服务器进行特调的Folia分支，针对生存服务器，提供更加卓越的网络质量与网络体验，并针对性提供微优化，以及修复被Folia破坏的Paper特性

---

## ✨ 核心特色 (Features)

* **网络优化**: 针对网络层面进行了细致优化，这使得本核心比其他核心带宽占用量更低，网络也更稳定
* **特性修复**: 尝试将Folia破坏的特性恢复到Paper端水平，支持定位条、珍珠加载器、刷沙机等
* **极致轻量**: 对Folia仅进行“蜻蜓点水”式的修改，保留原始风味

---

## 📚 文档 (Documentation)

* [📄 配置文件详解 (kitin.yml)](readme/kitin-yml.md)
* [🔧 修复与优化列表 (Fixes & Optimizations)](readme/fix-and-opt-list.md)
* [🛠️ 核心命令手册 (Kitin Commands)](readme/kitin-command.md)

---

## 📈 项目统计 (Project Statistics)

[![bStats](https://bstats.org/signatures/server-implementation/Kitin.svg)](https://bstats.org/plugin/server-implementation/Kitin "bStats")
## 🛠️ 如何编译 (How to Build)

Releases内已经有发布好的文件可以直接使用，若要自行编译，请按照以下方法进行编译：

```bash
# 1. 拉取代码
git clone https://github.com/SucIXR/Kitin.git
cd Kitin

# 2. 应用补丁
./gradlew applyAllPatches

# 3. 编译核心
./gradlew createMojmapPaperclipJar
