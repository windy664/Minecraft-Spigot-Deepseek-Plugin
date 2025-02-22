# Minecraft-Spigot-Deepseek-Plugins

将 Deepseek 接入 Minecraft

将jar文件放入服务器下的 `plugins` 文件夹，重启服务器，修改 `/plugins/meowplugin/config.yml` ,中的api密钥后，使用 `/meow reload` 重载配置即可使用


 > **注**：因为 Deepseek 官方api现在几乎不可用（2025-2-21），因此配置文件默认使用 [硅基流动](https://siliconflow.cn/zh-cn/) 的api，如果要使用 Deepseek 官方api或者其他平台的api，在配置文件中修改api地址即可

 > 目前在 `Minecraft1.20.4` 测试可以正常使用

## 下载

[haobo2333/Minecraft-Spigot-Deepseek-Plugins/releases](https://github.com/haobo2333/Minecraft-Spigot-Deepseek-Plugins/releases)

## 编译

使用如下命令编译：

```
mvn clean package
```

## 配置

```
api-url: "https://api.siliconflow.cn/v1/chat/completions"   # api地址
api-key: "your-api-key"                                     # api密钥
model: "deepseek-ai/DeepSeek-R1"                            # 模型选择
system-prompt: "你是一只可爱的猫娘，用'喵～'结尾"              # prompt
temperature: 0.7                                            # 温度系数
max-tokens: 512                                             # 最大输出tokens
restrict-to-op: true                                        # 是否仅限OP使用
broadcast-reply: true                                       # 是否广播回复
```

## 命令

`/meow ask <question>` - 提问

`/meow reload` - 重载配置文件

`/meow help` - 查看帮助

`/meow about` - 关于 

## 演示

![ 2025-02-21 200325.png](https://s2.loli.net/2025/02/21/FhrGTDa7xyjVbIg.png)