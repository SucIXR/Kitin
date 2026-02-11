# Kitin Server Configuration / 服务器配置说明

本页面简单说明了 `config/kitin.yml` 中部分配置的作用与建议值
This page explains the configuration options found in `config/kitin.yml`.
## ✒️ 写法规范说明

- **类型:** 列表 (List)
    - 如果列表为空，则显示为[]，需要去除[]并按标准填写
    - 支持格式:
        - `-'$AbstractMinecart'` (特殊值，所有矿车，仅指定配置有效，会在配置出说明)
        - `-minecraft:bedrock` (特定方块)
        - `-'#minecraft:shulker_boxes'` (标签，包含所有颜色的潜影盒)
        - `-'*concrete_powder'` (通配符，包含所有混凝土粉末)
        - 可以不写前缀`minecraft:`，对于带有`$``#``*`等特殊符号的需要加单引号

## 🛠️ Fixes / 修复调整

### `ender-pearl-chunk-loading`
- #### `enabled`
- **默认值:** `true`
- **说明:** 
    - 修复Folia无法使用珍珠加载器的问题，以及珍珠在跨区、重新上线后消失
- #### `player-max-save-ender-pearl`
- **默认值:** `-1`
- **说明:** 
    - 需要先开启"ender-pearl-chunk-loading.enable"才生效
    - 设为 `-1` 时，玩家抛出的末影珍珠数据将保存到NBT(不限制数量)
    - 设为 `0` 时，玩家抛出的末影珍珠数据将**不会**保存到NBT
    - 设为正整数时，玩家抛出的末影珍珠数据将只会将最后的指定数量保存到NBT(如果不是这次登录时的珍珠，则随机保存)
    - **效果:** 这可以防止玩家的珍珠数量不断增加导致的数据膨胀。如果一直在线，那珍珠加载效果将保留

### `disable-max-tnt-per-tick-and-optimize`
- **默认值:** `false`
- **说明:** 
    - 设为 `true` 时，服务器将不再限制每Tick引爆的TNT数量，而是优化超限的TNT
    - 这可以让世吞等高TNT使用量的机器能够正常运行

---

## 📡 Network / 网络带宽

### `chunk-lazy-loading`
- **默认值:** `true`
- **说明:** 
    - 玩家在一小片区块内来回转悠时，不断穿越区块将反复发送区块包。这导致了带宽浪费
    - 设为true时，玩家在一小块区域内(玩家附近的3x3区块)反复横跳将不进行地形加载，这几乎无感(视距小于7时会强制禁用)

### `chunk-send`
- **特别声明:** 
- 以下参数在BGP线路且"带宽稳定"的情况下表现极佳，单线服务器、共享带宽不保证效果
- 很多小厂喜欢"虚标带宽"，标注的带宽是"峰值带宽"，与大厂完全不同，大厂标注的带宽一般是"保底带宽"，实际上突发能跑到比标注值高，而小厂则是突发带宽就是标注带宽，因此二者使用体验完全不同。
- 对于小厂，以下参数配置后若仍卡顿，请打折（一般打到原来的2/3，某些虚标严重的服务器可能要打到1/2）
- #### `global-max-chunk-send-rate`  **强烈建议设置此值**
- **默认值:** `-1`
- **建议值:** `你的服务器带宽*9(大厂可以*12)`
- **说明:** 
  - 如果你的服务器带宽较小或者吃紧，且网络十分稳定(例如BGP服务器，或者多线，且带宽能保证不掉速)，强烈建议设置此项，能显著提高服务器的延迟与体验(跨网QoS问题没法解决)
  - 限制服务器**每秒**向所有玩家发送区块包的总量。
  - **计算公式:** `服务器上行带宽(Mbps) * (0.6-0.9)(地形占用带宽比例，可高达99%，如果挂机很多则低) * (平均区块大小KB)`
  - *(简单来说：直接填写你的服务器带宽 Mbps\*9(向下取整)即可(大厂的带宽往往不虚标，可以\*12)，例如 30Mbps 就填 270，没有进行仔细测量，以实际为准，如果你想低带宽承载更多人就降到更低)*
  - **建议**:paper-global.yml内的player-max-chunk-send-rate如果大于此值，建议降低到此值以下，可能比修改此值更有效
  - **注意**: 如果配置了 `qos-groups`，此值将作为**默认兜底策略**（即未匹配到任何组的玩家将使用此速率）。
- #### `global-chunk-send-burst-factor`
- **默认值:** `0.05`
- **建议值:** `默认，除非你真的很了解这个值的作用，否则修改其他值可能更有用`
- **说明:**
  - 需要启用global-max-chunk-send-rate此值才生效
  - **建议**:默认值理论上适应大多数情况，调低global-max-chunk-send-rate以及paper-global.yml内player-max-chunk-send-rate的可能比修改此值更有效
  - 这个值影响很大，允许的突发系数(默认0.05，即允许积攒突发发包0.05秒的配额)，调大可能导致带宽峰值，造成网络拥堵，调小可能导致玩家感觉地形加载过慢
  - 如果global-max-chunk-send-rate设置的值很小(应保证至少乘以此值>1)，那此值不应过小，否则会导致每tick发包量极低;如果带宽非常吃紧，此值不应过大(会导致带宽峰值造成延迟抖动)
  - 有些服务器商标注的3M，但实际上突发能跑到4M甚至更高，(大厂一般都不会虚标)，这类服务器可以提高此值;
  - 像小厂，一般标注的带宽都是"峰值带宽"，最多只能跑到他们标注的值，标注值就是突发值，因此你会感觉实际上跑不满，这类服务器的带宽可能要打六七折，甚至更低。应该首先等比例降低global-max-chunk-send-rate，以及首先降低paper-global.yml内的player-max-chunk-send-rate(如果此值大于global)。如果你的服务器属于这种峰值带宽情况，且玩家数量确实多，但是带宽总量也大，那可以把此值调到很低

- #### `qos-groups` **(高级多线路限流)**
- **说明:**
  - 允许根据玩家的连接方式（IP、域名、线路）设置不同的限流策略。
  - 支持**级联限流**（Upstream），完美模拟真实网络拓扑（如 FRP 转发受限于主站带宽）。
  - **优先级**: 此处的规则优先级**高于** `global-max-chunk-send-rate`。系统会优先匹配这里的规则，如果都未匹配上，则回退使用 `global-max-chunk-send-rate`。
- **配置项详解:**
  - `rate`: 每秒允许发送的最大区块数（Chunk/s）。
  - `bind-address`: 玩家连接的服务端 IP（Local Address）。适用于多网卡或 FRP 转发到不同内网 IP。
  - `virtual-host`: 玩家连接时输入的域名。适用于 Velocity/BungeeCord 多入口或单 IP 多域名。
  - `upstream`: 上游组名。当前组的流量会同时扣除上游组的配额（级联限流）。

- **配置案例:**

  **案例 1：单ip服务器（最简单）**
  ```yaml
  # 无需配置 qos-groups，直接设置 global-max-chunk-send-rate 即可
  # 如果你有需求本地不限速，只限制远程，可以往下看
  ```

  **案例 2：多ip服务器,且带宽互不影响（多网卡/多IP）**
  ```yaml
  qos-groups:
    telecom-line:
      bind-address: "1.1.1.1" # 电信IP
      rate: 100.0
    mobile-line:
      bind-address: "2.2.2.2" # 移动IP
      rate: 20.0
  ```

  **案例 3：Velocity多入口（使用域名区分）**
  ```yaml
  qos-groups:
    cn-proxy:
      virtual-host: "cn.mc.com" # 国内优化域名
      rate: 50.0
    us-direct:
      virtual-host: "us.mc.com" # 美国直连域名
      rate: 30.0
  ```

  **案例 4：FRP内网穿透、Velocity嵌套等套娃情景（级联限流）**
  ```yaml
  qos-groups:
    # === 1. 定义主服务器的总带宽池 ===
    main-bandwidth-pool:
      rate: 100.0 # 总物理带宽限制，当然，如果你的情景比较简单，可以不配置upstream和此值,直接控制global-max-chunk-send-rate即可

    # === 2. 定义各条线路 ===
    frp-node-a:
      bind-address: "127.0.0.2"  # FRP A 转发到的内网 IP A,请注意，不要将不同的FRP都转发到127.0.0.1，否则无法区分不同流量
      rate: 20.0                 # 自身限速 20
      upstream: "main-bandwidth-pool" # 同时受总闸限制

    frp-node-b:
      bind-address: "127.0.0.3"  # FRP B 转发到的内网 IP B
      rate: 60.0                 # 自身限速 60
      upstream: "main-bandwidth-pool" # 同时受总闸限制

    local-admin:
      bind-address: "127.0.0.1"  # 本地直连
      rate: 200.0                # 自身限速200,且不受总闸限制注意仍会受global-max-chunk-send-rate限制
  ```

### `extra-listeners` **(多端口监听)**
- **说明:**
  - 允许服务器监听额外的端口，并为每个端口单独配置 `proxy-protocol`。
  - 解决了 FRP (开启 PPv2) 和直连玩家无法共存的问题。
  - **注意**: 此配置**不支持热重载**，修改后必须重启服务器。
- **配置项详解:**
  - `port`: 监听端口。
  - `bind-address`: 绑定 IP (默认 0.0.0.0)。
  - `proxy-protocol`: 是否开启 PROXY Protocol v2 支持 (true/false)。

- **配置案例:**
  ```yaml
  extra-listeners:
    # FRP 专用端口 (开启 PPv2)
    frp-listener:
      port: 25566
      proxy-protocol: true
      bind-address: "0.0.0.0"

    # 管理员专用端口 (仅限本地)
    admin-listener:
      port: 25567
      proxy-protocol: false
      bind-address: "127.0.0.1"
  ```

### `reduce-high-frequency-entity-sync-packets`
- #### `entitys`
- **类型:** 列表 (List)
- **说明:** 
    - Paper虽然优化了实体Sync包的发包频率，但是当实体受到碰撞或者伤害时，会强制发包，导致大带宽占用
    - 列表内的实体在受到伤害或碰撞时，将不再强制立即发送同步数据包
    - 能显著减少生电机器（如猪人塔，潜影贝农场）运行时的网络拥堵，且看不出实体状态差异
    - **特殊值:** `'$AbstractMinecart'` (所有矿车)， `'$AbstractBoat'` (所有船)

### `particle`
- **提示:** 
  - 经测试100个粒子包大约会占用100KiB/S的突发带宽，突发发送过量粒子会导致服务器网络卡顿，尤其是领地插件
  - Kitin会先进行了视野、距离、遮挡剔除，再忽略多余的粒子，这能获得更好的观感
- #### `player-max-particles-per-packet`
- **默认值:** `250`
- **说明:**
  - 玩家每个包的最大粒子数量，超过的会忽略
- #### `player-min-optimize-threshold`
- **默认值:** `50`
- **说明:**
  - 玩家粒子开始打包以及剔除的阈值，并不是越小越好，打包和剔除会消耗大量性能
- #### `global-max-delay-ticks`
- **默认值:** `10`
- **说明:**
  - 全局粒子包最多可以推迟多少tick再发送,超时将会直接清除不发送
- #### `global-max-packet-particles-per-tick`
- **默认值:** `499`
- **说明:**
  - 全局每tick限制的打包后的粒子数量(只计算打包后的(打包后的粒子总量依旧计算为原值)，不会限制没有被打包的粒子)，如果超过数量，则会被推迟

---

## ⚡ Performance / 性能优化

### `use-simpler-entity-push`
- **默认值:** `true`
- **说明:** 
    - 启用简化的实体推挤算法
    - **效果:** 貌似性能优化幅度很低，但能显著减少密集实体堆叠时的带宽占用，目前没找到什么需要非常精密的挤压逻辑的生电机器

### `optimize-dropper`
- **默认值:** `false`
- **说明:** 
    - **警告:** 开启此选项会导致 Bukkit API `InventoryMoveItemEvent` 对投掷器失效！
    - 当投掷器装满潜影盒并且拥堵时，依旧会计算NBT等，这造成了大量无意义卡顿
    - 设为 `true` 时，投掷器将跳过复杂的 Bukkit 事件系统和 Hopper 逻辑，直接操作底层库存，并且当投掷器状态未发生变化时不进行冗余的NBT计算

---

## 🛡️ Safety / 安全机制

### `lazy-chunk-barrier`
- #### `items`
- **类型:** 列表 (List)
- **说明:** 
    - 定义哪些掉落物实体在接近"弱加载区块"边缘，且在移动时会被**直接清除**(玩家死亡掉落自然下坠不会清除，但是掉到流动的水里就不好说了)
    - **背景:** 在原版机制下，掉落物进入弱加载区块后不会自然清除，长期运行可能导致该区域堆积成千上万个实体，一旦玩家靠近加载该区块，服务器会瞬间崩溃

### `sand-duper`
- #### `blacklistblocks`
- **类型:** 列表 (List)
- **说明:**
  - 定义哪些方块**禁止**通过末地传送门进行复制
  - 提示:只有下落方块能刷取，TNT不是下落方块
- #### `min-tps-threshold`
- **默认值:** `5.0`
- **说明:**
  - 刷沙机出口处TPS低于指定值后禁止刷沙(设为-1禁用此命令)