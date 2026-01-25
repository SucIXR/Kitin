# Kitin Server Configuration / 服务器配置说明

本页面简单说明了 `config/kitin.yml` 中部分配置的作用与建议值。  
This page explains the configuration options found in `config/kitin.yml`.

## 🛠️ Fixes / 修复与调整

### `disable-max-tnt-per-tick-and-optimize`
- **默认值:** `false`
- **说明:** - 设为 `true` 时，服务器将不再限制每Tick引爆的TNT数量,而是优化超限的TNT。
    - 这可以让世吞等高TNT发包的机器能够正常运行。

### `ender-pearl-chunk-loading.player-max-save-ender-pearl`
- **默认值:** `-1`
- **说明:** - 需要先开启"ender-pearl-chunk-loading.enable"才生效。
    - 设为 `-1` 时，玩家抛出的末影珍珠数据将保存到 NBT
    - 设为 `0` 时，玩家抛出的末影珍珠数据将**不会**保存到 NBT
    - 设为正整数时，玩家抛出的末影珍珠数据将只会将最后的指定数量保存到 NBT
    - **效果:** 这可以防止玩家的珍珠数量不断增加导致的数据膨胀。如果一直在线，那珍珠加载效果将保留。

### `sand-duper.blacklistblocks`
- **类型:** 列表 (List)
- **说明:** - 定义哪些方块**禁止**通过末地传送门机制进行复制（刷沙机）。
    - 支持格式:
        - `minecraft:bedrock` (特定方块)
        - `#minecraft:shulker_boxes` (标签，包含所有颜色的潜影盒)
        - `*concrete_powder` (通配符，包含所有混凝土粉末)

---

## 📡 Network / 网络与带宽

### `global-max-chunk-send-rate`
- **默认值:** `-1` (无限制)
- **说明:** - 限制服务器每 Tick 向所有玩家发送区块包的总量。如果你的服务器带宽较小或者吃紧，强烈建议设置此项以防止丢包或掉线。
    - **计算公式:** `服务器上行带宽(Mbps) * 0.9(地形占用带宽比例很高,甚至能达到99%) / (平均区块大小KB * 20)`
      *(简单来说：直接填写你的服务器带宽 Mbps*0.8(向下取整) 即可，例如 30Mbps 就填 24-30，没有进行仔细测量，以实际为准)*

### `reduce-high-frequency-entity-sync-packets.entitys`
- **类型:** 列表 (List)
- **说明:** - Paper虽然优化了实体Sync包的发包频率，但是当实体受到碰撞或者伤害时，会强制发包，导致大带宽占用。
    - 列表内的实体在受到伤害或碰撞时，将不再强制立即发送同步数据包。
    - 能显著减少生电机器（如猪人塔，潜影贝农场）运行时的网络拥堵，且看不出实体状态差异。
    - **特殊值:** `$AbstractMinecart` (所有矿车), `$AbstractBoat` (所有船)。

---

## ⚡ Performance / 性能优化

### `use-simpler-entity-push`
- **默认值:** `true`
- **说明:** - 启用简化的实体推挤算法。
    - **效果:** 貌似性能优化幅度很低，但能显著减少密集实体堆叠时的带宽占用，目前没找到什么需要非常精密的挤压逻辑的生电机器。

### `optimize-dropper`
- **默认值:** `false`
- **说明:** - **警告:** 开启此选项会导致 Bukkit API `InventoryMoveItemEvent` 对投掷器失效！
    - 当投掷器装满潜影盒并且拥堵时，依旧会计算NBT等，这造成了大量无意义卡顿
    - 设为 `true` 时，投掷器将跳过复杂的 Bukkit 事件系统和 Hopper 逻辑，直接操作底层库存，并且当投掷器状态未发生变化时不进行冗余的NBT计算。

---

## 🛡️ Safety / 安全机制

### `lazy-chunk-barrier.items`
- **类型:** 列表 (List)
- **说明:** - 定义哪些掉落物实体在接近"弱加载区块"边缘,且在移动时会被**直接清除**。
    - **背景:** 在原版机制下，掉落物进入弱加载区块后不会自然清除，长期运行可能导致该区域堆积成千上万个实体，一旦玩家靠近加载该区块，服务器会瞬间崩溃。
