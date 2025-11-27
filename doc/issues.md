# 未解决 / 备忘

- 为兼容 Spring Boot 3.3.x 的 ASM 版本，后端使用 JDK 25 运行但编译 `--release 21`。如需完全使用 class 版本 69，请升级到支持 Java 25 的 Spring 版本或更新 ASM 依赖。
- Playwright/MCP 在无 hosts 情况下仍无法直接访问 `http://socialgame.seekerhut.com`（公网 DNS 尚未配置，`nslookup` 返回 NXDOMAIN）。本地测试依赖已有 hosts 指向 127.0.0.1，若需外网联调请为该域名添加 A 记录或允许 hosts 覆写。
- 默认 `java` 指向 /usr/local 的 Oracle 发行版时 Maven 会报 classworlds 缺失，需将 `JAVA_HOME` 指向 `/usr/lib/jvm/java-25-openjdk-amd64` 或调整 PATH 使用 openjdk。
