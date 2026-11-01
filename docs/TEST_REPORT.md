# 1.0.0 测试报告（2026-11-02）

本报告区分确定性检查、音频回放、模拟器与实体设备。发布为正式版本并不表示原先所有算法验收门槛都已达到。

## 构建与软件检查

- JDK 17 / SDK 36 / Build Tools 36.0.0，Debug、签名 Release、lint 构建通过。
- JUnit 98 项、0 失败、4 项外部条件测试跳过；其中包含旧算法比较基线，不应将通过数量视为新算法精度。
- Node 乐谱图、双行简谱、导航测试 13 项通过。
- Release 清单版本 1.0.0 / code 18；无 debuggable 属性或 DebugReceiver 注册；apksigner 校验通过。
- 正式包在新 MuMu Android 15 实例安装成功；保留原模拟器中的旧签名包与数据。

## 乐谱约束预测回归

直接将真实录音按实时顺序推理，缓存模型输出按可用时间喂给实际 AudioAnalyzer 后处理和 FollowerEngine。
没有用未来帧或真值帮助对齐。下表计算所有有效演奏时间中位置误差不超过半拍的比例，低置信度帧也计入分母。

| 组别 | 上一版本 | 1.0 速度预测 | 曲目数 |
| --- | --- | --- | --- |
| 开发组 | 96.9711% | 97.0401% | 9 |
| 固定回归组 | 97.4782% | 97.6446% | 9 |
| 另一回归组 | 97.6704% | 97.7231% | 5 |

这些数据已用于开发与诊断，不能充作新盲测、实体麦克风准确率或所有曲目保证。
新预测器使用确认音符的间隔中位数及对齐速度，抑制和弦内的多起音、单次异常间隔。
期望的后续谱音单独输出，不进入演奏记录或评分。翻页仍依据确认事件与置信度。

### 逐曲结果

| 组别 / 曲目 | 半拍内占比 | 误差中位数（拍） | P95（拍） |
| --- | --- | --- | --- |
| development-full / Bach_Fugue_bwv_858_VuV01M | 98.20% | 0.028 | 0.225 |
| development-full / Bach_Fugue_bwv_858_Zhang01M | 98.37% | 0.035 | 0.213 |
| development-full / Bach_Fugue_bwv_862_Song04M | 99.41% | 0.029 | 0.170 |
| development-full / k279_1 | 98.22% | 0.079 | 0.353 |
| development-full / k279_2 | 96.68% | 0.035 | 0.318 |
| development-full / Chopin_op10_no3_p01 | 98.49% | 0.033 | 0.355 |
| development-full / Chopin_op10_no3_p02 | 98.20% | 0.028 | 0.344 |
| development-full / Chopin_op38_p01 | 93.39% | 0.080 | 0.618 |
| development-full / Chopin_op38_p02 | 92.17% | 0.091 | 0.757 |
| heldout-full / Bach_Fugue_bwv_863_LeeN01M | 99.47% | 0.022 | 0.112 |
| heldout-full / Bach_Fugue_bwv_863_Shychko01M | 99.47% | 0.025 | 0.125 |
| heldout-full / Bach_Fugue_bwv_873_Lisiecki13M | 99.80% | 0.063 | 0.157 |
| heldout-full / k279_3 | 96.91% | 0.074 | 0.337 |
| heldout-full / k280_1 | 96.20% | 0.067 | 0.410 |
| heldout-full / Mozart_K331_1st-mov_p01 | 99.56% | 0.059 | 0.259 |
| heldout-full / Mozart_K331_1st-mov_p02 | 97.94% | 0.073 | 0.270 |
| heldout-full / Schubert_D783_no15_p01 | 92.88% | 0.165 | 0.611 |
| heldout-full / Schubert_D783_no15_p02 | 89.20% | 0.174 | 0.682 |
| verification-full / Bach_Fugue_bwv_891_Duepree06M | 96.94% | 0.068 | 0.275 |
| verification-full / Bach_Prelude_bwv_891_BLINOV04M | 99.80% | 0.051 | 0.142 |
| verification-full / Bach_Prelude_bwv_891_Duepree06M | 99.49% | 0.061 | 0.187 |
| verification-full / k281_1 | 97.81% | 0.045 | 0.267 |
| verification-full / k281_2 | 96.48% | 0.038 | 0.405 |

## 重弹恢复：尚未通过原门槛

20 个构造的停顿／跳回／跳段案例，部分带 20 dB SNR 噪声，全部最终恢复；
16/20 在三秒内恢复，P95 为 3.674 秒。原门槛为 95% 在三秒内，当前为 80%。
较激进的确认设置出现误定位，未采用；保留较长上下文以防突然跳谱。
这些恢复案例已用于诊断，后续需要新增未见案例检验。

## 界面与网页

MuMu 实测：首次演奏不自动打开歌曲；曲库有《你离开的事实》；
深色演奏背景与白色琴谱；五线谱／简谱切换；简谱上下两行、不显示左右手文字；
平板横屏与手机尺寸重排；控制条显示与隐藏；关于页离线 BSD 全文、第三方项目与链接。
正式 Release 可安装；旧签名覆盖安装被系统拒绝属于签名不兼容，未删除旧数据。

Pages 本地浏览器自动检查 12 组：393×852、1024×768、1440×900 × 中文／英文 × 浅色／深色，
无横向溢出、图片加载失败或脚本异常。界面截图已更新为 1.0 实测。

## 真实硬件边界

尚未在用户实体平板上复测新的麦克风声学效果，也没有实体 USB 钢琴插拔验收。
软件注入和网上录音不能证明这些硬件路径已通过。
完整用户音源的 Android 实际推理见 USER_AUDIO_REPORT.md。
历史结果见 history/，复现步骤见 TESTING.md。
